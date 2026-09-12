package com.example.androidkotlinapp

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One day's worth of focus. */
data class DayStat(
    val dayKey: String,
    val startMs: Long,
    val seconds: Int,
    val sessions: Int
)

/** Seven days, from the first day of the week to the last. */
data class WeekStat(
    val startMs: Long,
    val days: List<DayStat>
) {
    val totalSeconds: Int get() = days.sumOf { it.seconds }
    val totalSessions: Int get() = days.sumOf { it.sessions }
    val activeDays: Int get() = days.count { it.seconds > 0 }
    val bestSeconds: Int get() = days.maxOfOrNull { it.seconds } ?: 0
}

/**
 * The record of time actually spent focusing.
 *
 * Only the working half of a cycle is counted -- breaks are rest, not output, and a total that
 * flattered itself with them would be worth nothing. Time is added a second at a time as it is
 * spent rather than in a lump when a session ends, which is what makes the awkward cases come
 * out right on their own: a session stopped half way keeps the half that was done, a paused
 * session accrues nothing while it waits, and one that is paused overnight puts each part of
 * itself in the day it actually happened.
 */
object FocusStats {

    private const val PREFS = "focus_stats_prefs"
    private const val KEY_FIRST_DAY = "first_day_ms"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val keyFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun dayKey(atMs: Long): String = keyFormat.format(Date(atMs))

    // ---------------------------------------------------------------- writing

    /** Adds focused time to the day it belongs to. */
    fun addSeconds(context: Context, dayKey: String, seconds: Int) {
        if (seconds <= 0) return
        val p = prefs(context)
        p.edit()
            .putInt("d_$dayKey", p.getInt("d_$dayKey", 0) + seconds)
            .apply()
        rememberFirstDay(context, dayKey)
    }

    /** Counts one focus session run through to its end. */
    fun addCompletedSession(context: Context, atMs: Long = System.currentTimeMillis()) {
        val key = dayKey(atMs)
        val p = prefs(context)
        p.edit().putInt("c_$key", p.getInt("c_$key", 0) + 1).apply()
        rememberFirstDay(context, key)
    }

    /** The chart starts at the first week anything was recorded, so that day is kept. */
    private fun rememberFirstDay(context: Context, dayKey: String) {
        val p = prefs(context)
        if (p.contains(KEY_FIRST_DAY)) return
        val ms = runCatching { keyFormat.parse(dayKey)?.time }.getOrNull() ?: return
        p.edit().putLong(KEY_FIRST_DAY, ms).apply()
    }

    // ---------------------------------------------------------------- reading

    fun secondsOn(context: Context, dayKey: String): Int =
        prefs(context).getInt("d_$dayKey", 0)

    fun sessionsOn(context: Context, dayKey: String): Int =
        prefs(context).getInt("c_$dayKey", 0)

    fun today(context: Context): DayStat {
        val now = System.currentTimeMillis()
        val key = dayKey(now)
        return DayStat(key, startOfDay(now), secondsOn(context, key), sessionsOn(context, key))
    }

    /** Null until something has been recorded. */
    fun firstDayMs(context: Context): Long? =
        prefs(context).getLong(KEY_FIRST_DAY, 0L).takeIf { it > 0L }

    /** All time focused, across every day on record. */
    fun lifetimeSeconds(context: Context): Int =
        prefs(context).all.entries
            .filter { it.key.startsWith("d_") }
            .sumOf { (it.value as? Int) ?: 0 }

    fun lifetimeSessions(context: Context): Int =
        prefs(context).all.entries
            .filter { it.key.startsWith("c_") }
            .sumOf { (it.value as? Int) ?: 0 }

    /** The week containing [anchorMs], from its first day to its seventh. */
    fun week(context: Context, anchorMs: Long): WeekStat {
        val start = startOfWeek(anchorMs)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        val days = (0 until 7).map {
            val ms = cal.timeInMillis
            val key = dayKey(ms)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            DayStat(key, ms, secondsOn(context, key), sessionsOn(context, key))
        }
        return WeekStat(start, days)
    }

    /** True when there is an earlier week worth showing. */
    fun hasWeekBefore(context: Context, anchorMs: Long): Boolean {
        val first = firstDayMs(context) ?: return false
        return startOfWeek(anchorMs) > startOfWeek(first)
    }

    fun hasWeekAfter(anchorMs: Long): Boolean =
        startOfWeek(anchorMs) < startOfWeek(System.currentTimeMillis())

    // ---------------------------------------------------------------- calendar helpers

    fun startOfDay(atMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = atMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfWeek(atMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(atMs)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        // Rolling the day-of-week can land in the week ahead when the anchor is early in it.
        if (timeInMillis > atMs) add(Calendar.DAY_OF_YEAR, -7)
    }.timeInMillis

    fun shiftWeeks(atMs: Long, weeks: Int): Long = Calendar.getInstance().apply {
        timeInMillis = startOfWeek(atMs)
        add(Calendar.DAY_OF_YEAR, weeks * 7)
    }.timeInMillis

    // ---------------------------------------------------------------- formatting

    /** "2h 15m", "45m", "0m" -- short enough to sit under a bar. */
    fun pretty(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun dayLetter(atMs: Long): String =
        SimpleDateFormat("EEE", Locale.getDefault()).format(Date(atMs)).take(1).uppercase()

    fun dayNumber(atMs: Long): String =
        SimpleDateFormat("d", Locale.getDefault()).format(Date(atMs))

    fun weekLabel(startMs: Long): String {
        val end = startMs + 6L * 24 * 60 * 60 * 1000
        val sameMonth = SimpleDateFormat("M", Locale.US).format(Date(startMs)) ==
            SimpleDateFormat("M", Locale.US).format(Date(end))
        val from = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(startMs))
        val to = SimpleDateFormat(if (sameMonth) "d" else "MMM d", Locale.getDefault())
            .format(Date(end))
        return "$from – $to"
    }

    fun isToday(dayKey: String): Boolean = dayKey == dayKey(System.currentTimeMillis())

    /** "Tuesday 25 Aug", for when one day is being asked about rather than the week. */
    fun fullDayLabel(atMs: Long): String =
        SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(Date(atMs))

    // ---------------------------------------------------------------- the chart's scale

    /**
     * Rounds the tallest bar up to a figure worth printing on an axis.
     *
     * Scaling to the tallest bar exactly would make every week look equally full and put the
     * gridlines on numbers like "37 minutes". These steps keep a light week visibly lighter
     * than a heavy one, and keep the labels readable.
     */
    fun axisCeiling(peakSeconds: Int): Int {
        val ladder = listOf(
            15, 30, 45, 60, 90, 120, 180, 240, 300, 360, 480, 600, 720
        ).map { it * 60 }
        return ladder.firstOrNull { it >= peakSeconds } ?: ladder.last()
    }

    /** The gridline values under a ceiling: quarters for short days, hours for long ones. */
    fun axisSteps(ceilingSeconds: Int): List<Int> {
        val step = when {
            ceilingSeconds <= 60 * 60 -> ceilingSeconds / 2
            ceilingSeconds <= 180 * 60 -> 60 * 60
            else -> 120 * 60
        }
        if (step <= 0) return emptyList()
        return generateSequence(step) { it + step }
            .takeWhile { it <= ceilingSeconds }
            .toList()
    }

    fun axisLabel(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h${m}"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    // ---------------------------------------------------------------- mirroring

    /**
     * Every day on record, as JSON, for the encrypted copy.
     *
     * Read straight off the preference file rather than rebuilt from a date range: a gap in
     * the record is meaningful, and a walk over dates would invent zeroes for days that were
     * never written.
     */
    fun export(context: Context): String {
        val out = org.json.JSONObject()
        val days = org.json.JSONObject()
        prefs(context).all.forEach { (k, v) ->
            when {
                k.startsWith("d_") -> days.put(k.removePrefix("d_"), (v as? Int) ?: 0)
                k.startsWith("c_") -> Unit
            }
        }
        val sessions = org.json.JSONObject()
        prefs(context).all.forEach { (k, v) ->
            if (k.startsWith("c_")) sessions.put(k.removePrefix("c_"), (v as? Int) ?: 0)
        }
        out.put("seconds", days)
        out.put("sessions", sessions)
        out.put("firstDay", prefs(context).getLong(KEY_FIRST_DAY, 0L))
        return out.toString()
    }

    /**
     * Writes a mirrored record back.
     *
     * The larger of the two values wins per day, so a restore can never shrink a total that
     * this phone has already counted higher -- which is what would happen if a stale copy
     * landed on top of a day still being added to.
     */
    fun import(context: Context, json: String) {
        try {
            val o = org.json.JSONObject(json)
            val p = prefs(context)
            val edit = p.edit()

            o.optJSONObject("seconds")?.let { days ->
                days.keys().forEach { day ->
                    val incoming = days.optInt(day)
                    if (incoming > p.getInt("d_$day", 0)) edit.putInt("d_$day", incoming)
                }
            }
            o.optJSONObject("sessions")?.let { runs ->
                runs.keys().forEach { day ->
                    val incoming = runs.optInt(day)
                    if (incoming > p.getInt("c_$day", 0)) edit.putInt("c_$day", incoming)
                }
            }

            val first = o.optLong("firstDay", 0L)
            val known = p.getLong(KEY_FIRST_DAY, 0L)
            if (first > 0L && (known == 0L || first < known)) edit.putLong(KEY_FIRST_DAY, first)

            edit.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
