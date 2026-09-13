package com.example.androidkotlinapp

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ALARM_GOLD = Color(0xFFFFC24B)
private val STOP_RED = Color(0xFFE05252)

/**
 * The screen a sounding alarm puts in front of you.
 *
 * Shows over the lock screen and turns the display on by itself, so the alarm is answered
 * rather than discovered later.
 *
 * The hardware keys are deliberately inert. Volume up, volume down and mute are swallowed, and
 * back does nothing, so the reflex jab at the side of the phone cannot silence anything. The
 * power button cannot be intercepted by an app at all -- no app can -- but it only turns the
 * screen off: the sound lives in a foreground service, so it keeps going and this screen comes
 * straight back. Stop and Snooze are the only two ways out.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        // Same lockdown MainActivity engages for a block session, armed here too: a ringing
        // alarm is exactly the "session" issue 2 is about, and it needs the power/restart
        // menu shut just as completely while it rings.
        if (SessionLockdown.shouldBeLocked(this)) {
            SessionLockdown.engage(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L

        setContent {
            AlarmRingRoot(
                alarmId = alarmId,
                onStopPlain = {
                    send(AlarmRingService.ACTION_STOP)
                    finish()
                },
                onAllRoundsDone = {
                    // The last face round passed: let the service finish the alarm off exactly
                    // as a plain Stop would -- book the next occurrence, or switch a one-shot off.
                    send(AlarmRingService.ACTION_STOP)
                    finish()
                },
                onRoundPassed = {
                    // A round passed with more to come: silence this ring and book the next one
                    // five minutes out. The service stops without advancing the schedule.
                    send(AlarmRingService.ACTION_SILENCE_FOR_ROUND)
                    val nextAt = System.currentTimeMillis() + FaceRounds.GAP_MILLIS
                    AlarmScheduler.reRingAfterGap(this, alarmId)
                    RoundGapNotifier.show(this, FaceRounds.passed(this, alarmId), nextAt)
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only drops lock task mode; if a block session is separately still running, the home
        // screen's own poll re-engages it within a second of this activity going away.
        if (!AlarmRingService.ringing) {
            SessionLockdown.release(this)
        }
    }

    /**
     * Comes straight back if anything pushes this aside.
     *
     * Home, recents and a notification tap all move this activity out of the way, which is
     * exactly what someone half asleep will try. While the service is still ringing, being
     * moved aside is treated as an instruction to come back.
     */
    override fun onStop() {
        super.onStop()
        if (AlarmRingService.ringing && !isFinishing) {
            try {
                startActivity(
                    Intent(this, AlarmRingActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                        putExtra(
                            AlarmScheduler.EXTRA_ALARM_ID,
                            intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
                        )
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun send(action: String) {
        try {
            startService(
                Intent(this, AlarmRingService::class.java).apply { this.action = action }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    /** Swallows the keys someone reaches for to make a noise stop. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MUTE,
        KeyEvent.KEYCODE_CAMERA,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> true
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MUTE -> true
        else -> super.onKeyUp(keyCode, event)
    }

    /** Back is not an answer to an alarm. */
    override fun onBackPressed() = Unit
}

@Composable
private fun AlarmRingRoot(
    alarmId: Long,
    onStopPlain: () -> Unit,
    onAllRoundsDone: () -> Unit,
    onRoundPassed: () -> Unit
) {
    val context = LocalContext.current
    val alarm = remember(alarmId) { Alarms.byId(context, alarmId) }
    var verifying by remember { mutableStateOf(false) }

    if (verifying && alarm != null) {
        FaceVerifyPane(
            alarm = alarm,
            onPassedRound = { total ->
                if (total >= FaceRounds.REQUIRED) {
                    FaceRounds.reset(context, alarmId)
                    onAllRoundsDone()
                } else {
                    onRoundPassed()
                }
            }
        )
        return
    }

    RingScreen(
        alarmId = alarmId,
        onStop = {
            // A plain alarm just stops. A face-checked one opens the live selfie; the service
            // keeps ringing until the model recognises the face.
            if (alarm?.needsFaceCheck == true) verifying = true else onStopPlain()
        }
    )
}

/**
 * The live selfie gate for a face-checked alarm.
 *
 * The label sits at the top as the one bit of guidance. The camera runs live; a capture goes to
 * the model with the enrolled face, and only "same person" passes the round. This is the whole
 * reason the alarm resists a fingerprint on a sleeping hand.
 */
@Composable
private fun FaceVerifyPane(alarm: Alarm, onPassedRound: (Int) -> Unit) {
    val context = LocalContext.current
    val controller = remember { SelfieController() }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            if (alarm.label.isNotBlank()) alarm.label else "Time to get up",
            color = ALARM_GOLD,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Check ${FaceRounds.passed(context, alarm.id) + 1} of ${FaceRounds.REQUIRED}",
            color = Color(0xFF8A8A8A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(16.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0C0C0C))
        ) {
            SelfiePreview(controller, modifier = Modifier.fillMaxSize())
            if (checking) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ALARM_GOLD, strokeWidth = 3.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("Checking…", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val note = message
        if (note != null) {
            Text(
                note,
                color = Color(0xFFE0B15A),
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }

        RingButton(
            label = if (checking) "CHECKING…" else "TAKE SELFIE",
            fill = ALARM_GOLD,
            textColor = Color(0xFF1A1004),
            onClick = {
                if (checking) return@RingButton
                val enrolled = FaceStore.enrolledBase64(context)
                if (enrolled == null) {
                    // No reference face means the check cannot run; do not trap the user.
                    onPassedRound(FaceRounds.REQUIRED)
                    return@RingButton
                }
                checking = true
                message = null
                controller.take(
                    context,
                    onResult = { bmp ->
                        val fresh = bmp.toBase64Jpeg()
                        Thread {
                            val result = FaceCheck.compare(enrolled, fresh)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                checking = false
                                when (result) {
                                    is FaceResult.Match -> {
                                        val total = FaceRounds.recordPass(context, alarm.id)
                                        // Refresh the reference only on the final round.
                                        if (total >= FaceRounds.REQUIRED) FaceStore.save(context, bmp)
                                        onPassedRound(total)
                                    }
                                    is FaceResult.NotRecognised ->
                                        message = "Can't recognise you. Face the camera and try again."
                                    is FaceResult.Error ->
                                        message = result.message + " Try again."
                                }
                            }
                        }.start()
                    },
                    onError = {
                        checking = false
                        message = "Camera error. Try again."
                    }
                )
            }
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RingScreen(alarmId: Long, onStop: () -> Unit) {
    val context = LocalContext.current
    val alarm = remember(alarmId) { Alarms.byId(context, alarmId) }

    // The clock keeps running, because an alarm screen showing a frozen time looks broken.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val breathe = rememberInfiniteTransition(label = "ring")
    val pulse by breathe.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp)
    ) {
        Spacer(Modifier.weight(1f))

        AlarmGlyph(size = 84.dp, ringing = true, modifier = Modifier.graphicsLayer { alpha = pulse })

        Spacer(Modifier.height(34.dp))

        Text(
            SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)),
            color = Color.White,
            fontSize = 68.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-2).sp
        )
        Text(
            SimpleDateFormat("a · EEEE, d MMM", Locale.getDefault()).format(Date(now)),
            color = Color(0xFF8A8A8A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (alarm?.label.isNullOrBlank()) "Time to get up ☀️" else alarm!!.label,
            color = ALARM_GOLD,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.weight(1f))

        // One way out, and it is the honest one. There is no snooze. For a face-checked alarm
        // this opens the selfie rather than stopping anything.
        val faceAlarm = remember(alarmId) { Alarms.byId(context, alarmId)?.needsFaceCheck == true }
        val roundsDone = if (faceAlarm) FaceRounds.passed(context, alarmId) else 0
        RingButton(
            label = if (faceAlarm) {
                "VERIFY TO STOP  ·  ${roundsDone + 1} / ${FaceRounds.REQUIRED}"
            } else "STOP",
            fill = ALARM_GOLD,
            textColor = Color(0xFF1A1004),
            onClick = onStop
        )

        Spacer(Modifier.height(28.dp))

        Text(
            "Volume, back and power are held while this is ringing.",
            color = Color(0xFF3E3E3E),
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun RingButton(
    label: String,
    fill: Color,
    textColor: Color,
    border: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        color = fill,
        shape = RoundedCornerShape(18.dp),
        border = border?.let { BorderStroke(1.dp, it) },
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}


