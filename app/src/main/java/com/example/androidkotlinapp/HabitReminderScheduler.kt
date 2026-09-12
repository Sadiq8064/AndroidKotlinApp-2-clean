package com.example.androidkotlinapp

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * The two times a day a habit gets asked about.
 *
 * Morning is an invitation and night is a last call, so they are deliberately different in
 * tone: at nine the day is ahead of you, at ten at night it is nearly spent and the cost of
 * letting it go is worth stating plainly.
 *
 * Neither fires if there is nothing left to ask about. A notification that says "well done,
 * nothing to do" is how an app teaches someone to swipe it away without reading it.
 */
object HabitReminderScheduler {

    private const val CHANNEL_ID = "HabitDailyChannel_v1"

    private const val REQUEST_MORNING = 8700
    private const val REQUEST_NIGHT = 8701

    private const val MORNING_HOUR = 9
    private const val NIGHT_HOUR = 22

    private const val ID_MORNING = 8710
    private const val ID_NIGHT = 8711

    const val EXTRA_SLOT = "slot"
    const val SLOT_MORNING = "morning"
    const val SLOT_NIGHT = "night"

    // ---------------------------------------------------------------- scheduling

    /** Books both daily alarms. Safe to call as often as you like; it replaces its own. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarms.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextAt(MORNING_HOUR),
                AlarmManager.INTERVAL_DAY,
                pending(context, REQUEST_MORNING, SLOT_MORNING)
            )
            alarms.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextAt(NIGHT_HOUR),
                AlarmManager.INTERVAL_DAY,
                pending(context, REQUEST_NIGHT, SLOT_NIGHT)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pending(context: Context, code: Int, slot: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, HabitReminderReceiver::class.java).apply {
                putExtra(EXTRA_SLOT, slot)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun nextAt(hour: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    // ---------------------------------------------------------------- the two rounds

    fun run(context: Context, slot: String) {
        ensureChannel(context)

        // The night round also closes the books on yesterday, so a day missed with no session
        // running is on record well before the next session starts.
        if (slot == SLOT_NIGHT) HabitDebt.sweep(context)

        val outstanding = try {
            val db = FocusDatabaseHelper(context)
            val today = HabitStats.today()
            val done = db.getHabitIdsDoneOn(today)
            db.getActiveHabits().filter { !done.contains(it.id) }
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        if (outstanding.isEmpty()) return

        val names = outstanding.joinToString(", ") { "${it.emoji} ${it.text}" }
        val count = outstanding.size

        if (slot == SLOT_MORNING) {
            post(
                context,
                ID_MORNING,
                if (count == 1) "One habit for today 🌤️" else "$count habits for today 🌤️",
                "$names\n\nTick them off as you go. A day with all of them done is a day that counts."
            )
        } else {
            post(
                context,
                ID_NIGHT,
                if (count == 1) "Last call: 1 habit left 🌙" else "Last call: $count habits left 🌙",
                "$names\n\nStill time tonight. Let the day go unfinished and it costs a whole extra day on your next block session."
            )
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Habit reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Morning and last-call nudges for the day's habits"
                setSound(NotificationSound.uri(context), NotificationSound.attributes())
            }
        )
    }

    private fun post(context: Context, id: Int, title: String, body: String) {
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_HABITS)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setSound(NotificationSound.uri(context))
            .build()

        try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/** Wakes at nine and at ten, says what is still owed, and books itself again. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val slot = intent?.getStringExtra(HabitReminderScheduler.EXTRA_SLOT)
            ?: HabitReminderScheduler.SLOT_MORNING
        HabitReminderScheduler.run(context, slot)
        // Rebooked each time, so a missed or drifted alarm cannot end the series.
        HabitReminderScheduler.schedule(context)
    }
}
