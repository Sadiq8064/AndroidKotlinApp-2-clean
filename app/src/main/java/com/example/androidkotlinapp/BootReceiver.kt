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
                val serviceIntent = Intent(context, FocusService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
