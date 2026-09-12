package com.example.androidkotlinapp

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Watches the phone's own clock app and records every timer the user runs in it.
 *
 * There is no API for this. Android will not let one app read another's timer, so the only
 * honest signal available is the ongoing notification the clock posts while a timer counts
 * down: it appears when the timer starts and goes when the timer stops. Timing that gap gives
 * the duration without depending on the wording of the countdown, which differs on every
 * manufacturer's clock -- and this phone's is OPPO's, not Google's.
 */
class TimerListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (DIAG) logClock(n, "posted")
        if (!isTimer(n)) return
        TimerLog.onTimerStarted(this, System.currentTimeMillis())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (DIAG) logClock(n, "removed")
        if (!isTimer(n)) return
        // A timer that rang was dismissed by the clock itself once it finished; one the user
        // cancelled goes the same way. The two are indistinguishable from out here, so a run
        // is recorded as completed and the distinction is simply not claimed.
        TimerLog.onTimerEnded(this, System.currentTimeMillis(), completed = true)
    }

    /**
     * Whether a notification is a running timer rather than an alarm or the clock's own
     * foreground-service placeholder.
     *
     * The package is matched loosely because the clock is a different one on every phone --
     * `com.coloros.alarmclock` here, `com.google.android.deskclock` on a Pixel, and so on.
     */
    private fun isTimer(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName?.lowercase() ?: return false
        val isClock = pkg.contains("alarmclock") ||
            pkg.contains("deskclock") ||
            pkg.contains("clock")
        if (!isClock) return false

        val channel = sbn.notification?.channelId?.lowercase().orEmpty()
        // The clock keeps a permanent notice of its own on a foreground-service channel.
        // That one is always there and says nothing about a timer, so it is ruled out first
        // or every reboot would look like a timer that never ends.
        if (channel.contains("foreground_service")) return false
        if (channel.contains("timer")) return true

        // Some clocks put every ongoing notice on one channel, so fall back to what it says.
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence("android.title")?.toString()?.lowercase().orEmpty()
        val text = extras?.getCharSequence("android.text")?.toString()?.lowercase().orEmpty()
        val sub = extras?.getCharSequence("android.subText")?.toString()?.lowercase().orEmpty()
        if (title.contains("timer") || text.contains("timer") || sub.contains("timer")) return true

        // A countdown chronometer is a timer whatever the words around it say.
        val counting = extras?.getBoolean("android.showChronometer", false) == true &&
            extras.getBoolean("android.chronometerCountDown", false)
        return counting
    }

    /** Temporary: reports every clock notification so the real ones can be identified. */
    private fun logClock(sbn: StatusBarNotification, what: String) {
        val pkg = sbn.packageName?.lowercase() ?: return
        if (!pkg.contains("clock")) return
        val e = sbn.notification?.extras
        android.util.Log.d(
            "FocusTimer",
            "$what pkg=$pkg id=${sbn.id} channel=${sbn.notification?.channelId} " +
                "title=${e?.getCharSequence("android.title")} " +
                "text=${e?.getCharSequence("android.text")} " +
                "chrono=${e?.getBoolean("android.showChronometer", false)} " +
                "countDown=${e?.getBoolean("android.chronometerCountDown", false)} " +
                "isTimer=${isTimer(sbn)}"
        )
    }

    companion object {

        /** Temporary clock-notification logging under the "FocusTimer" tag. */
        private const val DIAG = true

        /** Whether the user has granted this service notification access. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, TimerListenerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }

        /** The settings page where that access is granted. */
        fun settingsIntent() =
            android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
