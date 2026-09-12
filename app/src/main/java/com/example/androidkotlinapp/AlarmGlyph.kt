package com.example.androidkotlinapp

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val GOLD = Color(0xFFFFC24B)
private val GOLD_DEEP = Color(0xFFE09B18)
private val FACE = Color(0xFF1A1206)

/**
 * The Alarm app's mark: a clock with two bells on top.
 *
 * Drawn rather than set as an emoji, for the same reason as the atom and the bulb -- it takes
 * the app's own gold and stays sharp at any size. Every measurement is a fraction of the size
 * given, so one glyph serves a 24dp app bar and an 84dp ring screen alike.
 *
 * [ringing] rocks the whole thing side to side, which is only used on the screen a sounding
 * alarm puts up. Everywhere else it sits still: a shaking icon in a drawer is a distraction,
 * which is the opposite of what this app is for.
 */
@Composable
fun AlarmGlyph(size: Dp, ringing: Boolean = false, modifier: Modifier = Modifier) {
    val tilt = if (ringing) {
        val shake = rememberInfiniteTransition(label = "alarm")
        shake.animateFloat(
            initialValue = -11f,
            targetValue = 11f,
            animationSpec = infiniteRepeatable(
                tween(180, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "tilt"
        ).value
    } else {
        0f
    }

    Canvas(modifier = modifier.size(size)) { drawAlarm(tilt) }
}

private fun DrawScope.drawAlarm(tilt: Float) {
    val s = this.size.minDimension
    val cx = this.size.width / 2f
    val cy = this.size.height * 0.56f
    val r = s * 0.315f

    rotate(degrees = tilt, pivot = Offset(cx, cy)) {
        // The two bells, tucked behind the face so only their caps show.
        val bellR = s * 0.115f
        listOf(-1f, 1f).forEach { side ->
            drawCircle(
                color = GOLD_DEEP,
                radius = bellR,
                center = Offset(cx + side * r * 0.74f, cy - r * 0.80f)
            )
        }

        // The feet.
        listOf(-1f, 1f).forEach { side ->
            drawLine(
                color = GOLD_DEEP,
                start = Offset(cx + side * r * 0.52f, cy + r * 0.80f),
                end = Offset(cx + side * r * 0.86f, cy + r * 1.18f),
                strokeWidth = s * 0.055f,
                cap = StrokeCap.Round
            )
        }

        // The case, lit from the top left the way a metal body would be.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GOLD, GOLD_DEEP),
                center = Offset(cx - r * 0.35f, cy - r * 0.35f),
                radius = r * 1.9f
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        // The face, dark so the hands read against it.
        drawCircle(color = FACE, radius = r * 0.76f, center = Offset(cx, cy))

        // Four ticks, at the quarters.
        for (i in 0..3) {
            val a = Math.toRadians((i * 90).toDouble())
            val inner = r * 0.56f
            val outer = r * 0.68f
            drawLine(
                color = GOLD.copy(alpha = 0.55f),
                start = Offset(cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat()),
                end = Offset(cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat()),
                strokeWidth = s * 0.022f,
                cap = StrokeCap.Round
            )
        }

        // Hands at ten past ten, which is where every clock in every advert sits -- it frames
        // the face rather than crossing it, and reads as a clock at a glance.
        drawLine(
            color = GOLD,
            start = Offset(cx, cy),
            end = Offset(cx - r * 0.34f, cy - r * 0.30f),
            strokeWidth = s * 0.036f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = GOLD,
            start = Offset(cx, cy),
            end = Offset(cx + r * 0.30f, cy - r * 0.42f),
            strokeWidth = s * 0.030f,
            cap = StrokeCap.Round
        )
        drawCircle(color = GOLD, radius = s * 0.028f, center = Offset(cx, cy))
    }
}
