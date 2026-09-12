package com.example.androidkotlinapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * The voice of a focus session: it says what just finished, what starts now, and how long it
 * will last.
 *
 * Every phase change is announced, because the session is meant to be run with the phone face
 * down. If the only way to know a break had started were to look at the screen, the countdown
 * would be pulling the user back to the very thing it is supposed to free them from.
 */
object PomoNotifier {

    private const val CHANNEL_ID = "FocusPomoChannelV2"
    private const val LEGACY_CHANNEL_ID = "FocusPomoChannel"

    /** Separate ids so a break notice lands beside the one that announced the focus ending. */
    private const val ID_PHASE_DONE = 7001
    private const val ID_PHASE_START = 7002
    private const val ID_ALL_DONE = 7003
    private const val ID_STOPPED = 7004
    private const val ID_ONGOING = 7000

    /** Its own quiet channel: this one is a readout, not an announcement. */
    private const val ONGOING_CHANNEL_ID = "FocusPomoOngoingChannel"

    /** Long enough that the two notices read as a sequence rather than arriving as one. */
    private const val SECOND_NOTICE_DELAY_MS = 1600L

    private val main = Handler(Looper.getMainLooper())

    /**
     * Every line takes the same two arguments in the same order -- the focus length first, the
     * break length second -- and refers to them by position rather than by order of appearance.
     * A line that mentioned only one of them used to swallow whichever came first, which is how
     * a one minute break once announced itself as twenty-five.
     */
    private val focusStarts = listOf(
        "Locked in 🎯" to "%1\$s. The phone is out of the way -- go and do the thing.",
        "Clock's running ⏱️" to "%1\$s of focus. Nothing else needs you right now.",
        "Deep work 🧠" to "%1\$s. One task, no switching."
    )

    private val focusDone = listOf(
        "%1\$s done 🏆" to "That is focus you cannot get back by scrolling. Take %2\$s.",
        "Session complete ✅" to "%1\$s of real work behind you. %2\$s to breathe.",
        "Nailed it 💪" to "%1\$s focused. Step away for %2\$s -- you earned it."
    )

    private val breakStarts = listOf(
        "Break time ☕" to "%1\$s. Stand up, look out a window, drink something.",
        "Rest 🌿" to "%1\$s off. Your next session starts sharper for it.",
        "Pause 😌" to "%1\$s. Let your eyes go further than an arm's length."
    )

    private val breakDone = listOf(
        "Break's over ⚡" to "Back to it -- %1\$s of focus. You know what you are doing.",
        "Round two 🔥" to "%1\$s. Pick the task back up while it is still warm.",
        "Go again 🚀" to "%1\$s of focus. Momentum is on your side now."
    )

    private val allDone = listOf(
        "All sessions done 🎉" to "%1\$s finished. The block session is still running.",
        "That's the set 🏅" to "%1\$s in the bank. Start another whenever you like."
    )

    /** "1 minute", "25 minutes" -- so a one minute break never reads as "1 minutes". */
    private fun mins(n: Int) = if (n == 1) "1 minute" else "$n minutes"

    private fun sessions(n: Int) = if (n == 1) "1 focus session" else "$n focus sessions"

    // ---------------------------------------------------------------- the announcements

    fun focusStarted(context: Context, minutes: Int) {
        val (title, body) = focusStarts.random()
        post(context, ID_PHASE_START, title.format(mins(minutes)), body.format(mins(minutes)))
    }

    /**
     * The one notice that carries both halves: what was finished and what comes next. The
     * break's own greeting follows a moment later so the two do not land on top of each other.
     */
    fun focusFinished(context: Context, focusMinutes: Int, breakMinutes: Int) {
        val (title, body) = focusDone.random()
        post(
            context,
            ID_PHASE_DONE,
            title.format(mins(focusMinutes), mins(breakMinutes)),
            body.format(mins(focusMinutes), mins(breakMinutes))
        )
        main.postDelayed({ breakStarted(context, breakMinutes) }, SECOND_NOTICE_DELAY_MS)
    }

    /**
     * Says a focus stretch was ended before its time.
     *
     * Deliberately not a telling-off. The minutes already sat through were counted the moment
     * they passed, and the message says so -- an alarm-clock guilt-trip would only teach the
     * user to turn notifications off.
     */
    fun focusStopped(context: Context, focusedSeconds: Int) {
        val mins = focusedSeconds / 60
        val body = when {
            mins <= 0 -> "Focus session stopped. Start another whenever you are ready."
            mins == 1 -> "Focus session stopped — 1 minute counted. Every bit adds up."
            else -> "Focus session stopped — $mins minutes counted. Every bit adds up."
        }
        post(context, ID_STOPPED, "Focus session ended \u23F9\uFE0F", body)
    }

    fun breakStarted(context: Context, minutes: Int) {
        val (title, body) = breakStarts.random()
        post(context, ID_PHASE_START, title.format(mins(minutes)), body.format(mins(minutes)))
    }

    fun breakFinished(context: Context, nextFocusMinutes: Int) {
        val (title, body) = breakDone.random()
        post(
            context,
            ID_PHASE_DONE,
            title.format(mins(nextFocusMinutes)),
            body.format(mins(nextFocusMinutes))
        )
    }

    fun cycleFinished(context: Context, sessionCount: Int) {
        val (title, body) = allDone.random()
        post(
            context,
            ID_ALL_DONE,
            title.format(sessions(sessionCount)),
            body.format(sessions(sessionCount))
        )
    }

    // ---------------------------------------------------------------- the standing readout

    /**
     * The notification that stays for the length of a phase and counts itself down.
     *
     * The countdown is drawn by the system from an end time, not written by this app once a
     * second. That is the whole point: a phase can run for an hour, and an app that redrew a
     * notification three thousand times to say so would cost more battery than the session
     * saves. It cannot be swiped away while a phase is running, because a focus session that
     * quietly vanished from the shade would be one the user stops trusting.
     */
    fun showOngoing(context: Context, isFocus: Boolean, endsAtMillis: Long) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureOngoingChannel(manager)

        val notification = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setContentTitle(if (isFocus) "Focus in progress 🎯" else "On a break ☕")
            .setContentText(if (isFocus) "Time remaining" else "Back to it in")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(context))
            // Android renders and ticks the countdown itself from this end time.
            .setWhen(endsAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .build()

        try {
            manager.notify(ID_ONGOING, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** The same standing notice, held still, while the session is paused. */
    fun showOngoingPaused(context: Context, remainingSeconds: Int) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureOngoingChannel(manager)

        val left = "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)
        val notification = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setContentTitle("Paused ⏸️")
            .setContentText("$left left when you pick it back up")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(context))
            .build()

        try {
            manager.notify(ID_ONGOING, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Puts the standing readout back if it has gone.
     *
     * Android 14 onwards lets the user swipe away an ongoing notification whatever its flags
     * say, and once gone nothing would have brought it back until the next phase began -- so a
     * stray swipe could leave a running session with no visible countdown for half an hour.
     * The session's own clock checks now and then and re-posts it.
     */
    fun ensureOngoing(context: Context, isFocus: Boolean, endsAtMillis: Long) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val present = try {
            manager.activeNotifications.any { it.id == ID_ONGOING }
        } catch (e: Exception) {
            e.printStackTrace()
            true
        }
        if (!present) showOngoing(context, isFocus, endsAtMillis)
    }

    /** As above, for a session being held. */
    fun ensureOngoingPaused(context: Context, remainingSeconds: Int) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val present = try {
            manager.activeNotifications.any { it.id == ID_ONGOING }
        } catch (e: Exception) {
            e.printStackTrace()
            true
        }
        if (!present) showOngoingPaused(context, remainingSeconds)
    }

    fun clearOngoing(context: Context) {
        try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(ID_ONGOING)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun ensureOngoingChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                ONGOING_CHANNEL_ID,
                "Focus Session Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "The countdown that stays while a focus session runs"
                setShowBadge(false)
            }
        )
    }

    // ---------------------------------------------------------------- plumbing

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun post(context: Context, id: Int, title: String, body: String) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Focus Sessions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Tells you when a focus stretch or a break begins and ends"
                    setSound(NotificationSound.uri(context), NotificationSound.attributes())
                }
            )
        }

        val open = openApp(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(open)
            // Set on the notification too, for Android versions older than channels.
            .setSound(NotificationSound.uri(context))
            .build()

        try {
            manager.notify(id, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
