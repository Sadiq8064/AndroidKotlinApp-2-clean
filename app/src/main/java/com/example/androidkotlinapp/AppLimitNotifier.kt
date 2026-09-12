package com.example.androidkotlinapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * The word before an app's daily time runs out, and the word when it has.
 *
 * A limit that closes an app with no warning reads as a crash. Two notices -- five minutes out
 * and one minute out -- turn it into something the user chose, with room to finish a sentence
 * or send the message they were part-way through.
 *
 * One notification id per app, so a second warning replaces the first rather than stacking, and
 * so two apps running low do not overwrite each other.
 */
object AppLimitNotifier {

    private const val CHANNEL_ID = "AppLimitChannel_v1"
    private const val ID_BASE = 9400

    /** Stable, small, and different per app. */
    private fun idFor(packageName: String): Int =
        ID_BASE + (packageName.hashCode() and 0x3FF)

    private fun labelFor(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    fun warn(context: Context, packageName: String, secondsLeft: Int) {
        val name = labelFor(context, packageName)
        val body = if (secondsLeft >= 300) {
            "Five minutes of $name left today. Wrap up what you are doing."
        } else {
            "One minute of $name left today. Finish up now."
        }
        post(
            context,
            idFor(packageName),
            if (secondsLeft >= 300) "$name — 5 min left ⏳" else "$name — 1 min left ⚠️",
            body,
            ongoing = false
        )
    }

    fun spent(context: Context, packageName: String, limitMinutes: Int) {
        val name = labelFor(context, packageName)
        post(
            context,
            idFor(packageName),
            "$name is done for today 🚫",
            "Your $limitMinutes minutes are spent. It opens again tomorrow.",
            ongoing = false
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "App time limits",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warns before an app's daily time runs out"
                setSound(NotificationSound.uri(context), NotificationSound.attributes())
            }
        )
    }

    private fun post(
        context: Context,
        id: Int,
        title: String,
        body: String,
        ongoing: Boolean
    ) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOngoing(ongoing)
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
