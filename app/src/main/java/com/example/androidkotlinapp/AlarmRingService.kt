package com.example.androidkotlinapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * The thing that actually rings.
 *
 * Deliberately a foreground service rather than an activity. An activity's sound dies the
 * moment the activity is pushed aside, which makes "press power, press home, alarm stops" the
 * easiest habit in the world to fall into. Here the screen can be turned off, the ring screen
 * dismissed, another app opened -- the sound keeps going until Stop or Snooze is pressed,
 * because those are the only two things that stop this service.
 *
 * The sound plays on the alarm stream at its own volume, so turning the media volume down does
 * not silence it either.
 */
class AlarmRingService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmId: Long = -1L

    companion object {
        private const val CHANNEL_ID = "AlarmRingChannel_v1"
        private const val NOTIFICATION_ID = 9600

        const val ACTION_STOP = "com.example.androidkotlinapp.ALARM_STOP"

        /** Silences the current ring for a face-check round, without touching the schedule. */
        const val ACTION_SILENCE_FOR_ROUND = "com.example.androidkotlinapp.ALARM_SILENCE_ROUND"

        /** True while an alarm is sounding, so the ring screen knows whether to stay up. */
        var ringing: Boolean = false
            private set

        var ringingId: Long = -1L
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // A face-check that finished all its rounds clears its counter before this,
                // so onFired here just advances the schedule the normal way.
                AlarmScheduler.onFired(this, alarmId)
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_SILENCE_FOR_ROUND -> {
                // Between rounds: go quiet, but leave the schedule alone -- the re-ring in five
                // minutes is booked by the caller, and onFired must not run yet.
                stopEverything()
                return START_NOT_STICKY
            }
        }

        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        if (id <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        alarmId = id
        ringingId = id
        ringing = true
        // A new ring means any between-rounds countdown is over.
        RoundGapNotifier.clear(this)

        val alarm = Alarms.byId(this, id)
        startForeground(NOTIFICATION_ID, buildNotification(alarm))
        acquireWakeLock()
        startSound()
        startVibration()

        // The full-screen intent on the notification is the intended route, but a device that
        // refuses it still gets the ring screen this way.
        try {
            startActivity(
                Intent(this, AlarmRingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    // ---------------------------------------------------------------- sound and shake

    private fun soundUri(): Uri =
        // The app's own tone if one has been dropped in, otherwise whatever the phone uses for
        // alarms. Falls back again to the notification tone on the rare device with neither.
        try {
            val res = resources.getIdentifier("alarm_tone", "raw", packageName)
            if (res != 0) {
                Uri.parse("android.resource://$packageName/$res")
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
        } catch (e: Exception) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

    private fun startSound() {
        try {
            player = MediaPlayer().apply {
                setDataSource(this@AlarmRingService, soundUri())
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // The alarm usage is what makes this play through Do Not Disturb and
                        // at the alarm volume rather than the media one.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            // An alarm stream sitting at zero would ring silently, which is indistinguishable
            // from a broken alarm. Nudged up to something audible if it has been muted.
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) < max / 3) {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, max / 2, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 400, 600, 400)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireWakeLock() {
        try {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "focus:alarm"
            ).apply {
                // Bounded so a bug can never hold the processor awake indefinitely. Ten
                // minutes is far longer than anyone leaves an alarm sounding.
                acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopEverything() {
        ringing = false
        ringingId = -1L
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        player = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vibrator = null
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
    }

    // ---------------------------------------------------------------- the banner

    private fun buildNotification(alarm: Alarm?): android.app.Notification {
        ensureChannel()

        val full = PendingIntent.getActivity(
            this,
            1,
            Intent(this, AlarmRingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, AlarmRingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val time = alarm?.let { "${it.clockText()} ${it.meridiem()}" }.orEmpty()
        val title = if (alarm?.label.isNullOrBlank()) "Alarm ⏰" else "${alarm?.label} ⏰"
        val body = if (time.isBlank()) "Time to get up." else "$time — time to get up."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            // Not dismissible by swiping it away, and it opens the ring screen over the lock
            // screen rather than waiting to be tapped.
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Stop", stop)
            // The service owns the sound, so the notification must not add a second one.
            .setSilent(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "A sounding alarm"
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
