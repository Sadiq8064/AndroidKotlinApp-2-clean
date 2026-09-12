package com.example.androidkotlinapp

import android.content.ContentValues
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One alarm.
 *
 * [days] holds [Calendar.MONDAY]-style constants. Empty means it goes off once, at the next
 * occurrence of [hour]:[minute], and switches itself off afterwards.
 */
data class Alarm(
    val id: Long,
    val hour: Int,              // 0..23, stored in 24-hour form whatever the picker shows
    val minute: Int,
    val label: String,
    val days: Set<Int>,
    val enabled: Boolean,
    /** The one habit alarm. Only ever one exists; the first alarm ever set becomes it. */
    val special: Boolean = false,
    /** For a special alarm: how many days it must run (7 / 21 / 30) before it can be deleted. */
    val durationDays: Int = 0,
    val createdAt: Long = 0L,
    /** Non-special alarms may opt into the same face check the special one always uses. */
    val faceUnlock: Boolean = false
) {
    /** True once a special alarm has run its full stretch and may finally be deleted. */
    val specialFinished: Boolean
        get() {
            if (!special || durationDays <= 0 || createdAt <= 0L) return false
            val elapsed = System.currentTimeMillis() - createdAt
            return elapsed >= durationDays * 24L * 60 * 60 * 1000
        }

    /** Days left before a special alarm can be deleted. */
    val daysRemaining: Int
        get() {
            if (!special || durationDays <= 0 || createdAt <= 0L) return 0
            val elapsed = System.currentTimeMillis() - createdAt
            val left = durationDays - (elapsed / (24L * 60 * 60 * 1000)).toInt()
            return left.coerceAtLeast(0)
        }

    /** Whether dismissing this alarm needs the face check -- always for special, opt-in else. */
    val needsFaceCheck: Boolean get() = special || faceUnlock
    val isRepeating: Boolean get() = days.isNotEmpty()

    /**
     * Whether this is close enough to ringing that it may no longer be switched off.
     *
     * The decision to get up is made the night before, when it costs nothing. Inside the last
     * hour it is the half-asleep version of you making it instead, and that one should not get
     * a vote. Only an armed alarm can be locked -- one already off has nothing to protect.
     */
    fun isLocked(now: Long = System.currentTimeMillis()): Boolean {
        if (!enabled) return false
        val until = nextTriggerMs(now) - now
        return until in 0..(AlarmScheduler.LOCK_WINDOW_MINUTES * 60_000L)
    }

    /** How many minutes until it can be touched again. */
    fun minutesUntilUnlocked(now: Long = System.currentTimeMillis()): Int {
        val until = nextTriggerMs(now) - now
        return ((until - AlarmScheduler.LOCK_WINDOW_MINUTES * 60_000L) / 60_000L)
            .toInt()
            .coerceAtLeast(0)
    }

    /** "07:30 AM", in the phone's own locale. */
    fun clockText(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return SimpleDateFormat("h:mm", Locale.getDefault()).format(cal.time)
    }

    fun meridiem(): String = if (hour < 12) "AM" else "PM"

    /**
     * When this alarm should next go off.
     *
     * For a repeating alarm the search walks forward at most a week, which is as far as it can
     * ever need to go. For a one-shot it is today if the time is still ahead, tomorrow if not.
     */
    fun nextTriggerMs(from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (!isRepeating) {
            if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }

        for (i in 0..7) {
            val candidate = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            if (candidate.timeInMillis <= from) continue
            if (days.contains(candidate.get(Calendar.DAY_OF_WEEK))) return candidate.timeInMillis
        }
        // Unreachable while days is non-empty, but a sane answer beats an exception.
        return cal.timeInMillis + 7 * 24 * 60 * 60 * 1000L
    }

    /** "Mon, Wed, Fri", "Every day", "Weekdays", or the date it will next ring. */
    fun scheduleText(): String {
        if (!isRepeating) {
            return SimpleDateFormat("EEE, d MMM", Locale.getDefault())
                .format(Date(nextTriggerMs()))
        }
        if (days.size == 7) return "Every day"
        if (days == WEEKDAYS) return "Weekdays"
        if (days == WEEKEND) return "Weekends"
        return DAY_ORDER.filter { days.contains(it) }.joinToString(", ") { shortDayName(it) }
    }

    companion object {
        /** Monday first, which is how the picker reads and how most of the world counts. */
        val DAY_ORDER = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        val WEEKDAYS = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )
        val WEEKEND = setOf(Calendar.SATURDAY, Calendar.SUNDAY)

        fun shortDayName(day: Int): String = when (day) {
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> "Sun"
        }
    }
}

/**
 * Where alarms are kept and how they are read back.
 *
 * Every write re-books the actual system alarm, because a row in a table that nothing told
 * AlarmManager about is a promise the app quietly fails to keep.
 */
object Alarms {

    fun all(context: Context): List<Alarm> {
        val out = mutableListOf<Alarm>()
        try {
            FocusDatabaseHelper(context).readableDatabase.use { d ->
                d.rawQuery(
                    "SELECT id, hour, minute, label, days, enabled, " +
                        "IFNULL(special,0), IFNULL(duration_days,0), IFNULL(created_at,0), " +
                        "IFNULL(face_unlock,0) FROM " +
                        "${FocusDatabaseHelper.TABLE_ALARMS} ORDER BY special DESC, hour ASC, minute ASC",
                    null
                ).use { c ->
                    while (c.moveToNext()) {
                        out.add(
                            Alarm(
                                id = c.getLong(0),
                                hour = c.getInt(1),
                                minute = c.getInt(2),
                                label = c.getString(3).orEmpty(),
                                days = c.getString(4).orEmpty()
                                    .split(",")
                                    .filter { it.isNotBlank() }
                                    .map { it.toInt() }
                                    .toSet(),
                                enabled = c.getInt(5) == 1,
                                special = c.getInt(6) == 1,
                                durationDays = c.getInt(7),
                                createdAt = c.getLong(8),
                                faceUnlock = c.getInt(9) == 1
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }

    fun byId(context: Context, id: Long): Alarm? = all(context).firstOrNull { it.id == id }

    /** The one habit alarm, if it exists. */
    fun special(context: Context): Alarm? = all(context).firstOrNull { it.special }

    /** True when no alarm has ever been set, so the next one becomes the special one. */
    fun hasNoAlarms(context: Context): Boolean = all(context).isEmpty()

    /**
     * Deleting the special alarm takes every other alarm with it.
     *
     * That is the rule the habit is built on: the special alarm is the commitment, and the
     * others hang off it. Walk away from the commitment and the rest go too, so the next alarm
     * set starts a fresh commitment as the new special one. Refused until the stretch is served.
     */
    fun deleteSpecialAndAll(context: Context): Boolean {
        val sp = special(context) ?: return false
        if (!sp.specialFinished) return false
        all(context).forEach { AlarmScheduler.cancel(context, it.id) }
        try {
            FocusDatabaseHelper(context).writableDatabase.use { d ->
                d.delete(FocusDatabaseHelper.TABLE_ALARMS, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    /** Inserts or updates, then re-books the system alarm to match. */
    fun save(context: Context, alarm: Alarm): Long {
        var id = alarm.id
        try {
            FocusDatabaseHelper(context).writableDatabase.use { d ->
                val values = ContentValues().apply {
                    if (alarm.id > 0) put("id", alarm.id)
                    put("hour", alarm.hour)
                    put("minute", alarm.minute)
                    put("label", alarm.label)
                    put("days", alarm.days.joinToString(","))
                    put("enabled", if (alarm.enabled) 1 else 0)
                    put("special", if (alarm.special) 1 else 0)
                    put("duration_days", alarm.durationDays)
                    put("created_at", if (alarm.createdAt > 0L) alarm.createdAt else System.currentTimeMillis())
                    put("face_unlock", if (alarm.faceUnlock) 1 else 0)
                }
                val row = d.insertWithOnConflict(
                    FocusDatabaseHelper.TABLE_ALARMS,
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
                if (alarm.id <= 0) id = row
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        AlarmScheduler.reschedule(context, alarm.copy(id = id))
        return id
    }

    /**
     * Switches an alarm on or off, unless it is inside its lock window.
     *
     * Returns false when the change was refused, so the caller can say why rather than leaving
     * a switch that silently springs back.
     */
    fun setEnabled(context: Context, id: Long, enabled: Boolean): Boolean {
        val alarm = byId(context, id) ?: return false
        // Turning one on is always allowed; it is only turning one off that is guarded.
        // The habit alarm cannot be switched off until it has run its course, and any alarm
        // goes untouchable in the hour before it rings.
        if (!enabled && alarm.special && !alarm.specialFinished) return false
        if (!enabled && alarm.isLocked()) return false
        save(context, alarm.copy(enabled = enabled))
        return true
    }

    /** Refused inside the lock window, for the same reason switching off is. */
    fun remove(context: Context, id: Long): Boolean {
        val alarm = byId(context, id)
        if (alarm != null && alarm.isLocked()) return false
        AlarmScheduler.cancel(context, id)
        try {
            FocusDatabaseHelper(context).writableDatabase.use { d ->
                d.delete(FocusDatabaseHelper.TABLE_ALARMS, "id = ?", arrayOf(id.toString()))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    /**
     * Re-books everything. Called after a reboot and on every app open.
     *
     * An alarm mid-way through its face-check rounds is left alone: it has a re-ring booked for
     * a few minutes out, and rescheduling it to its normal daily time here would quietly cancel
     * that -- which is exactly the bug that made rounds two and three never arrive.
     */
    fun rescheduleAll(context: Context) {
        all(context).forEach { alarm ->
            val mid = FaceRounds.passed(context, alarm.id).let { it in 1 until FaceRounds.REQUIRED }
            if (!mid) AlarmScheduler.reschedule(context, alarm)
        }
    }
}
