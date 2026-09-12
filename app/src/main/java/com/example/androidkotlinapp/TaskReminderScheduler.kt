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
 * The morning nudge for goals and reminders.
 *
 * One alarm a day, not one per item. Everything due is worked out when it fires, so adding or
 * deleting a goal never leaves a stale alarm behind, and a phone that was off overnight simply
 * catches the next morning rather than missing a schedule it can no longer reconstruct.
 */
object TaskReminderScheduler {

    private const val CHANNEL_ID = "TasksDailyChannel"
    private const val REQUEST_CODE = 8100

    /** Notified between eight and nine, so it lands with the morning rather than in it. */
    private const val HOUR = 8
    private const val MINUTE = 30

    private const val ID_REMINDER_BASE = 8200
    private const val ID_GOAL_BASE = 8400
    private const val ID_GOAL_DONE_BASE = 8600

    // ---------------------------------------------------------------- scheduling

    /** Books tomorrow morning. Safe to call as often as you like; it replaces its own alarm. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = nextMorning()

        try {
            // Inexact on purpose. "Some time in the morning" is the requirement, and an exact
            // alarm would ask for a permission and a wakeup this does not need.
            alarms.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                next,
                AlarmManager.INTERVAL_DAY,
                pendingIntent(context)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, TaskReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun nextMorning(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    // ---------------------------------------------------------------- the morning round

    /** Works out what is worth saying today and says it. */
    fun runDailyRound(context: Context) {
        ensureChannel(context)

        // Reminders speak twice: the day before, and the day itself. Anything further out is
        // not news yet, and anything past is not worth waking someone for.
        Tasks.reminders(context).forEachIndexed { index, reminder ->
            when (Tasks.daysUntil(reminder.nextOccurrenceMs)) {
                1 -> post(
                    context,
                    ID_REMINDER_BASE + index,
                    "Tomorrow: ${reminder.title} 📅",
                    "One day to go. Whatever it needs, today is the day to get it ready.",
                    MainActivity.ROUTE_REMINDER
                )
                0 -> post(
                    context,
                    ID_REMINDER_BASE + index,
                    "Today: ${reminder.title} ⏰",
                    "This is the day. Do not let it slip past.",
                    MainActivity.ROUTE_REMINDER
                )
            }
        }

        // A goal is nudged every morning it is live -- but only while there is something left
        // undone. Nagging someone about work they have already finished is how an app teaches
        // them to swipe its notifications away without reading them.
        Tasks.goals(context).forEachIndexed { index, goal ->
            val left = Tasks.daysUntil(goal.endMs)
            val started = Tasks.daysUntil(goal.startMs) <= 0
            if (!started || goal.isDone) return@forEachIndexed

            val remaining = goal.total - goal.completed
            val (title, body) = when {
                left < 0 -> "${goal.title} is overdue ⚠️" to
                    "$remaining still open. Finish what you can today -- late beats abandoned."
                left == 0 -> "Last day: ${goal.title} 🔥" to
                    "$remaining left and today to do it. Clear the lot."
                left == 1 -> "One day left: ${goal.title} ⏳" to
                    "$remaining still to go. Today decides whether tomorrow is comfortable."
                left <= 3 -> "${goal.title} — $left days ⚡" to
                    "${goal.completed} of ${goal.total} done. Keep the run going."
                goal.completed == 0 -> "Start ${goal.title} 🎯" to
                    "$left days in hand and nothing ticked yet. One step today is enough."
                else -> "${goal.title} 💪" to
                    "${goal.completed} of ${goal.total} done, $left days left. Take the next one."
            }
            post(context, ID_GOAL_BASE + index, title, body)
        }
    }


    /**
     * Says well done the moment a goal's last step is ticked.
     *
     * Fired from the tick itself rather than from the morning round, because the congratulation
     * is only worth anything while the finger is still on the screen -- told about it the next
     * morning, it reads as bookkeeping.
     */
    fun celebrateGoal(context: Context, goal: Goal) {
        ensureChannel(context)

        val lines = listOf(
            "That is the whole thing done. Pick the next one while the run is hot. 🔥",
            "Finished, start to end. This is what the streak is made of. ⭐",
            "All ${goal.total} steps, closed out. You said you would and you did. 💪",
            "Done. The version of you that started this would be pleased. 🎉",
            "Every step ticked. Momentum is the reward — spend it. 🚀"
        )

        post(
            context,
            ID_GOAL_DONE_BASE + (goal.id.hashCode() and 0xFF),
            "\"${goal.title}\" complete 🏆",
            lines.random()
        )
    }

    // ---------------------------------------------------------------- plumbing

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Goals and Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "The morning nudge about what is due"
                setSound(NotificationSound.uri(context), NotificationSound.attributes())
            }
        )
    }

    private fun post(
        context: Context,
        id: Int,
        title: String,
        body: String,
        route: String = MainActivity.ROUTE_TARGET
    ) {
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_ROUTE, route)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
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

/** Wakes once a morning, says what is due, and books itself again. */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TaskReminderScheduler.runDailyRound(context)
        // Rebooked each time, so a missed or drifted alarm cannot end the series.
        TaskReminderScheduler.schedule(context)
    }
}
