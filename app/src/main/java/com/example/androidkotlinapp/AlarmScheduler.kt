package com.example.androidkotlinapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Books alarms with the system so they fire at the exact minute, awake or asleep.
 *
 * Uses [AlarmManager.setAlarmClock] rather than any of the inexact variants. That is the one
 * API the system never defers for battery: it wakes the device fully, ignores Doze, and puts
 * the alarm icon in the status bar. Anything else can slide by minutes, which for an alarm is
 * the same as not working.
 *
 * A repeating alarm is not set as a repeating system alarm. Each firing books the next one, so
 * a change to the days takes effect immediately rather than at the end of some cycle the user
 * cannot see.
 */
object AlarmScheduler {

    const val EXTRA_ALARM_ID = "alarm_id"

    /**
     * How close to ringing an alarm becomes untouchable.
     *
     * Inside this window it cannot be switched off or deleted. The decision to get up is made
     * the night before, when it is easy; this stops the version of you that is half asleep from
     * overruling it.
     */
    const val LOCK_WINDOW_MINUTES = 60

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun firePending(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, AlarmFiredReceiver::class.java).apply {
                putExtra(EXTRA_ALARM_ID, id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * Whether the system will let this app set an exact alarm.
     *
     * With USE_EXACT_ALARM declared this is granted at install and stays granted, but it is
     * still asked rather than assumed -- a denial should degrade to an inexact alarm, not to a
     * silent failure.
     */
    fun canScheduleExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager(context).canScheduleExactAlarms()
        } else {
            true
        }

    fun reschedule(context: Context, alarm: Alarm) {
        cancel(context, alarm.id)
        if (!alarm.enabled) return

        val at = alarm.nextTriggerMs()
        val pending = firePending(context, alarm.id)

        try {
            if (canScheduleExact(context)) {
                // The show-intent is what the status bar chip opens when tapped.
                val show = PendingIntent.getActivity(
                    context,
                    alarm.id.toInt(),
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_ALARM)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                alarmManager(context).setAlarmClock(
                    AlarmManager.AlarmClockInfo(at, show),
                    pending
                )
            } else {
                // Better late than never, and the user is told the permission is missing.
                alarmManager(context).setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, at, pending
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            try {
                alarmManager(context).set(AlarmManager.RTC_WAKEUP, at, pending)
            } catch (inner: Exception) {
                inner.printStackTrace()
            }
        }
    }

    /** Books the same alarm again a few minutes out, for the gap between face-check rounds. */
    fun reRingAfterGap(context: Context, id: Long) {
        val at = System.currentTimeMillis() + FaceRounds.GAP_MILLIS
        try {
            val show = PendingIntent.getActivity(
                context,
                id.toInt(),
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_ALARM)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager(context).setAlarmClock(
                AlarmManager.AlarmClockInfo(at, show),
                firePending(context, id)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancel(context: Context, id: Long) {
        try {
            alarmManager(context).cancel(firePending(context, id))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * Called once an alarm has gone off: books the next occurrence, or switches off a one-shot
     * so it does not sit in the list pretending it is still armed.
     */
    fun onFired(context: Context, id: Long) {
        val alarm = Alarms.byId(context, id) ?: return
        when {
            // The habit alarm is a daily commitment even though it carries no weekday set, so
            // it always books the next morning rather than switching itself off.
            alarm.special -> reschedule(context, alarm)
            alarm.isRepeating -> reschedule(context, alarm)
            else -> Alarms.save(context, alarm.copy(enabled = false))
        }
    }
}

/** Wakes when an alarm is due and hands straight over to the service that rings it. */
class AlarmFiredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        if (id <= 0) return

        // A foreground service does the ringing, not this receiver and not an activity. That
        // is what lets the sound survive the screen being turned off or the activity being
        // pushed aside -- the two things someone reaches for to make an alarm stop.
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmRingService::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
            }
        )
    }
}
