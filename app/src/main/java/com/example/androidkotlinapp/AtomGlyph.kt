package com.example.androidkotlinapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val CORE = Color(0xFF7C6BFF)
private val DEEP = Color(0xFF4B3BD6)

/** The three shells, each tilted a third of a turn from the last. */
private val SHELL_TILT = listOf(0f, 60f, 120f)

/**
 * The mark for the Time app: three ions running rings around a nucleus.
 *
 * Drawn rather than set as an emoji, so it takes the app's own purple instead of whatever the
 * system font happens to paint, and stays sharp at any size. Every measurement is a fraction of
 * the size it is given, so one glyph serves a 20dp header and a 30dp shelf tile alike.
 *
 * [spinning] costs a frame of drawing, but only while the mark is actually composed -- leaving
 * the home screen or turning the display off stops it, so both the shelf and the app bar can
 * keep their ions moving.
 */
@Composable
fun AtomGlyph(size: Dp, spinning: Boolean = false) {
    val phase = if (spinning) {
        val turn = rememberInfiniteTransition(label = "atom")
        turn.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
            label = "orbit"
        ).value
    } else {
        // A still atom is posed rather than stopped: the three ions sit apart from one another
        // instead of collecting on one side, which is what a frozen animation tends to look like.
        0.6f
    }

    Canvas(modifier = Modifier.size(size)) { drawAtom(phase) }
}

private fun DrawScope.drawAtom(phase: Float) {
    val s = this.size.minDimension
    val mid = Offset(this.size.width / 2f, this.size.height / 2f)

    val rx = s * 0.46f
    val ry = s * 0.175f
    val ring = s * 0.055f
    val ion = s * 0.082f

    SHELL_TILT.forEachIndexed { i, tilt ->
        // The shells sit at different depths, so the mark reads as a sphere rather than as a
        // flat knot of overlapping loops.
        val depth = 1f - i * 0.22f

        rotate(degrees = tilt, pivot = mid) {
            drawOval(
                color = CORE.copy(alpha = 0.85f * depth),
                topLeft = Offset(mid.x - rx, mid.y - ry),
                size = Size(rx * 2, ry * 2),
                style = Stroke(width = ring)
            )

            // Each ion is a third of a turn ahead of the one on the shell inside it, so no two
            // ever bunch up at the same point on the mark.
            val t = phase + i * (2f * PI.toFloat() / 3f)
            val at = Offset(mid.x + rx * cos(t), mid.y + ry * sin(t))

            drawCircle(color = CORE.copy(alpha = 0.28f * depth), radius = ion * 1.9f, center = at)
            drawCircle(color = CORE, radius = ion, center = at)
        }
    }

    // The nucleus last, so it sits over every shell that passes behind it.
    drawCircle(color = DEEP.copy(alpha = 0.45f), radius = s * 0.21f, center = mid)
    drawCircle(color = CORE, radius = s * 0.125f, center = mid)
    drawCircle(color = Color.White.copy(alpha = 0.75f), radius = s * 0.05f, center = mid)
}
