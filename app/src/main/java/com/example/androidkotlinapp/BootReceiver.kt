package com.example.androidkotlinapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // Alarms do not survive a reboot, so they are re-booked whether or not a session
            // is running. They used to sit inside the session check, which meant rebooting
            // without one silently ended every daily reminder until the app was next opened.
            TaskReminderScheduler.schedule(context)
            HabitReminderScheduler.schedule(context)
            // System alarms do not survive a reboot, so every armed alarm is re-booked.
            Alarms.rescheduleAll(context)

            if (FocusService.hasLiveSession(context)) {
                // Blocked first and fastest, before anything else in this method: this is a
                // BroadcastReceiver callback, not an activity, so it is the earliest point in
                // the whole boot sequence this app gets a chance to run code at all. A battery-
                // drain reboot has no other guard standing between boot and Settings until the
                // service and the launcher activity below actually come up.
                SessionLockdown.setUninstallBlocked(context, true)

                val serviceIntent = Intent(context, FocusService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)

                // Brings the launcher to the foreground immediately rather than waiting for
                // the system to get around to showing the home screen on its own -- that gap
                // is exactly the "buffer period" Settings was reachable through. MainActivity's
                // own onResume is what engages LockTask (it needs an activity; a receiver
                // can't start it), so getting here fast is what closes the window.
                try {
                    context.startActivity(
                        Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
