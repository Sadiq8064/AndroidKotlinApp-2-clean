package com.example.androidkotlinapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BELL_LIGHT = Color(0xFFFFD180)
private val BELL_MID = Color(0xFFFFB74D)
private val BELL_DARK = Color(0xFFE07C00)

/**
 * The Reminder app's mark.
 *
 * Drawn rather than set as an emoji, so it takes the app's own amber and stays sharp at every
 * size it is used at -- 24dp in the app bar, 52dp in an empty state, whatever the drawer asks
 * for. Every measurement is a fraction of the size given.
 *
 * Deliberately still. A bell that rang on its own would be a notification, and this is only a
 * label on a screen.
 */
@Composable
fun BellGlyph(size: Dp, dim: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) { drawBell(dim) }
}

private fun DrawScope.drawBell(dim: Boolean) {
    val s = this.size.minDimension
    val cx = this.size.width / 2f
    val alpha = if (dim) 0.28f else 1f

    val body = Brush.verticalGradient(
        colors = listOf(
            BELL_LIGHT.copy(alpha = alpha),
            BELL_MID.copy(alpha = alpha),
            BELL_DARK.copy(alpha = alpha)
        ),
        startY = s * 0.14f,
        endY = s * 0.74f
    )

    // The little loop on the crown.
    drawCircle(
        brush = body,
        radius = s * 0.058f,
        center = Offset(cx, s * 0.115f)
    )

    // The dome, flaring out to the skirt. Drawn as a path so the sides can curve rather than
    // taper in a straight line -- a straight taper reads as a tent, not a bell.
    val top = s * 0.19f
    val bottom = s * 0.665f
    val half = s * 0.30f
    drawPath(
        Path().apply {
            moveTo(cx - half, bottom)
            cubicTo(
                cx - half * 0.92f, bottom - s * 0.17f,
                cx - half * 0.78f, top + s * 0.02f,
                cx, top
            )
            cubicTo(
                cx + half * 0.78f, top + s * 0.02f,
                cx + half * 0.92f, bottom - s * 0.17f,
                cx + half, bottom
            )
            close()
        },
        brush = body
    )

    // The lip, a touch wider than the skirt so the bell reads as having an opening.
    drawRoundRect(
        brush = body,
        topLeft = Offset(cx - half * 1.13f, bottom),
        size = Size(half * 2.26f, s * 0.072f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.036f, s * 0.036f)
    )

    // The clapper, hanging just below.
    drawCircle(
        brush = body,
        radius = s * 0.062f,
        center = Offset(cx, bottom + s * 0.135f)
    )
}
