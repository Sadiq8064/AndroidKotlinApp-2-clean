package com.example.androidkotlinapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * What a missed day costs, and when it is collected.
 *
 * A day in which any habit went undone costs one day of extra block session -- once, however
 * many habits were missed, because the thing being punished is the broken day rather than each
 * individual lapse.
 *
 * The debt is kept separately from the session on purpose. Miss a day with no session running
 * and there is nothing to add the time to, but the day is still owed; it is collected the next
 * time a session starts. That is the whole reason this is a table and not a number on a
 * running timer.
 */
object HabitDebt {

    private const val CHANNEL_ID = "HabitDebtChannel_v1"
    private const val ID_DEBT_COLLECTED = 9310

    /**
     * Books every day between the last sweep and yesterday in which a habit went undone.
     *
     * Today is deliberately left alone: the day is not over, and a habit not yet done at noon
     * is not a habit missed. Only closed days can be judged.
     *
     * Safe to call as often as you like -- a day already on the books is not booked again.
     */
    fun sweep(context: Context): Int {
        return try {
            val db = FocusDatabaseHelper(context)
            val habits = db.getActiveHabits()
            if (habits.isEmpty()) return 0

            val today = HabitStats.today()
            var booked = 0

            // Walk back over the closed days, most recent first, and stop at the bound.
            for (back in 1..FocusDatabaseHelper.DEBT_SWEEP_MAX_DAYS) {
                val day = HabitStats.shiftDays(today, -back)
                if (db.isDayBooked(day)) continue

                // A habit cannot be missed on a day before it existed.
                val owed = habits.filter { it.createdDate.isNotBlank() && it.createdDate <= day }
                if (owed.isEmpty()) continue

                val done = db.getHabitIdsDoneOn(day)
                val missed = owed.filter { !done.contains(it.id) }
                if (missed.isEmpty()) continue

                if (db.recordMissedDay(day, missed.map { it.text })) booked++
            }
            booked
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /** Seconds currently owed and not yet served. */
    fun owedSeconds(context: Context): Int = try {
        FocusDatabaseHelper(context).unservedDebtSeconds()
    } catch (e: Exception) {
        e.printStackTrace()
        0
    }

    /** How many days are owed, for wording a message. */
    fun owedDays(context: Context): Int = try {
        FocusDatabaseHelper(context).unservedDebtDays().size
    } catch (e: Exception) {
        e.printStackTrace()
        0
    }

    /**
     * Settles everything owed against a session that is about to run, and returns the seconds
     * to add. Marks the debt served, so it is charged exactly once.
     */
    fun collect(context: Context): Int {
        return try {
            val db = FocusDatabaseHelper(context)
            val seconds = db.unservedDebtSeconds()
            if (seconds <= 0) return 0
            val days = db.unservedDebtDays()
            db.markDebtServed()
            notifyCollected(context, days.size, seconds)
            seconds
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    // ---------------------------------------------------------------- telling the user

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Missed habit days",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Says when a missed day has been added to a session"
                setSound(NotificationSound.uri(context), NotificationSound.attributes())
            }
        )
    }

    private fun notifyCollected(context: Context, days: Int, seconds: Int) {
        ensureChannel(context)

        val dayWord = if (days == 1) "day" else "days"
        val addedWord = if (seconds / 86_400 == 1) "1 day" else "${seconds / 86_400} days"
        val title = "$addedWord added to this session ⛓️"
        val body = if (days == 1) {
            "You let a habit slip on one day. That day is being paid back now — see it through."
        } else {
            "You let habits slip on $days $dayWord. They are being paid back now — see it through."
        }

        val open = PendingIntent.getActivity(
            context,
            ID_DEBT_COLLECTED,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_HABITS)
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
            manager.notify(ID_DEBT_COLLECTED, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
