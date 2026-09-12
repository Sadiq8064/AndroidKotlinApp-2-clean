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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Resource app's colour: the warm gold a filament actually glows.
 *
 * Replaces the YouTube red the screen used to borrow. Red said "this is YouTube", which the
 * screen stopped being the moment links and videos moved into the same collections.
 */
val BULB_GOLD = Color(0xFFFFC24B)
val BULB_GOLD_DEEP = Color(0xFFE09B18)
private val GLASS = Color(0xFFFFD97A)
private val BASE = Color(0xFF8A6A2F)

/**
 * A lit bulb that breathes.
 *
 * The glow swells and fades rather than blinking: an idea getting brighter is the thing being
 * drawn, and a hard on/off would read as a fault indicator instead.
 *
 * [glowing] costs a frame of drawing while the mark is composed and nothing at all when it is
 * not, so the launcher shelf can keep it on without it running in the background.
 */
@Composable
fun BulbGlyph(size: Dp, glowing: Boolean = true, modifier: Modifier = Modifier) {
    val pulse = if (glowing) {
        val breathe = rememberInfiniteTransition(label = "bulb")
        breathe.animateFloat(
            initialValue = 0.42f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1900, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "glow"
        ).value
    } else {
        0.85f
    }

    Canvas(modifier = modifier.size(size)) { drawBulb(pulse) }
}

private fun DrawScope.drawBulb(pulse: Float) {
    val s = this.size.minDimension
    val cx = this.size.width / 2f
    // The glass sits high so the screw base has room underneath without the whole mark
    // drifting off centre.
    val cy = this.size.height * 0.42f
    val r = s * 0.235f

    // The halo, drawn first and widest, so everything else sits inside the light rather than
    // on top of it.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                BULB_GOLD.copy(alpha = 0.34f * pulse),
                BULB_GOLD.copy(alpha = 0.11f * pulse),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = r * 2.9f
        ),
        radius = r * 2.9f,
        center = Offset(cx, cy)
    )

    // Four short rays. Any more and the mark turns into a sun at small sizes.
    val rayInner = r * 1.45f
    val rayOuter = r * 1.45f + s * 0.09f * pulse
    listOf(-38f, 38f, 142f, 218f).forEach { deg ->
        val a = Math.toRadians(deg.toDouble())
        drawLine(
            color = BULB_GOLD.copy(alpha = 0.55f * pulse),
            start = Offset(cx + rayInner * kotlin.math.cos(a).toFloat(), cy + rayInner * kotlin.math.sin(a).toFloat()),
            end = Offset(cx + rayOuter * kotlin.math.cos(a).toFloat(), cy + rayOuter * kotlin.math.sin(a).toFloat()),
            strokeWidth = s * 0.045f,
            cap = StrokeCap.Round
        )
    }

    // The glass, brightest at the top left where a light source would catch it.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(GLASS, BULB_GOLD, BULB_GOLD_DEEP),
            center = Offset(cx - r * 0.3f, cy - r * 0.35f),
            radius = r * 1.7f
        ),
        radius = r,
        center = Offset(cx, cy)
    )

    // The neck, joining glass to base.
    val neckTop = cy + r * 0.72f
    val neckHalf = r * 0.42f
    drawPath(
        Path().apply {
            moveTo(cx - neckHalf, neckTop)
            lineTo(cx + neckHalf, neckTop)
            lineTo(cx + neckHalf * 0.82f, neckTop + s * 0.055f)
            lineTo(cx - neckHalf * 0.82f, neckTop + s * 0.055f)
            close()
        },
        color = BULB_GOLD_DEEP
    )

    // The screw base: three bands, which reads as threading without drawing threading.
    val baseTop = neckTop + s * 0.055f
    val bandH = s * 0.036f
    for (i in 0..2) {
        val top = baseTop + i * (bandH + s * 0.014f)
        val half = neckHalf * (0.80f - i * 0.09f)
        drawRoundRect(
            color = BASE,
            topLeft = Offset(cx - half, top),
            size = Size(half * 2, bandH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bandH / 2, bandH / 2)
        )
    }

    // The filament, brightening with the pulse -- the one part that should look genuinely lit.
    val fw = r * 0.42f
    val fh = r * 0.34f
    drawPath(
        Path().apply {
            moveTo(cx - fw, cy + fh * 0.5f)
            lineTo(cx - fw * 0.35f, cy - fh)
            lineTo(cx + fw * 0.35f, cy + fh * 0.35f)
            lineTo(cx + fw, cy - fh * 0.8f)
        },
        color = Color.White.copy(alpha = 0.45f + 0.5f * pulse),
        style = Stroke(width = s * 0.032f, cap = StrokeCap.Round)
    )
}
