package com.example.androidkotlinapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * The banner shown between face-check rounds.
 *
 * A round passes, the alarm goes quiet, and the next one is five minutes out -- long enough
 * that a plain silence feels like the alarm simply gave up. This says otherwise: it names the
 * round just done and counts down, live, to the next ring, so the gap reads as part of the
 * alarm rather than the end of it.
 *
 * The countdown is a chronometer the system ticks for free -- no work is done in the app while
 * it runs.
 */
object RoundGapNotifier {

    private const val CHANNEL_ID = "AlarmRoundGapChannel_v1"
    private const val NOTIFICATION_ID = 9620

    fun show(context: Context, roundsPassed: Int, nextRingAtMs: Long) {
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_ALARM)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val left = FaceRounds.REQUIRED - roundsPassed
        // The exact clock time it rings again, rather than a live chronometer -- some vendor
        // builds ignore the count-down flag and let it run backwards into negatives.
        val ringsAt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(nextRingAtMs))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Check $roundsPassed of ${FaceRounds.REQUIRED} done ✅")
            .setContentText(
                if (left == 1) "One more to go — rings again at $ringsAt."
                else "$left more to go — rings again at $ringsAt."
            )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(nextRingAtMs)
            .setShowWhen(true)
            .setContentIntent(open)
            .setSilent(true)
            .build()

        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Cleared when the next round starts ringing, or when the whole alarm is done. */
    fun clear(context: Context) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Alarm round countdown",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Counts down to the next face check between rounds"
                    setSound(null, null)
                }
            )
    }
}
