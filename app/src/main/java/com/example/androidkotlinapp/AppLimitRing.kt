package com.example.androidkotlinapp

import android.content.Context
import com.example.androidkotlinapp.ui.main.CachedAppIcon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val PLENTY = Color(0xFF66BB6A)
private val LOW = Color(0xFFFFB74D)
private val NEARLY_OUT = Color(0xFFE57373)
private val TRACK = Color(0xFF262626)

/**
 * An app icon with the time it has left drawn around it.
 *
 * The ring is a rounded square rather than a circle because the icon it wraps is one: a circle
 * around a squircle leaves four uneven gaps and reads as a badge stuck on top, where a matching
 * outline reads as part of the tile.
 *
 * The arc shows what is **left**, not what is spent, so it empties as the day goes on and an
 * almost-gone ring is visibly almost gone. Colour carries the same message a second time --
 * green, amber, red -- for the glance that is too quick to measure an arc.
 *
 * Draws nothing at all when the app has no limit set, which is most of them.
 */
@Composable
fun AppIconWithLimit(
    context: Context,
    packageName: String,
    bitmap: ImageBitmap?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val limitMinutes = rememberLimit(context, packageName)

    if (limitMinutes <= 0) {
        CachedAppIcon(bitmap = bitmap, modifier = modifier.size(size))
        return
    }

    val usedSeconds = WhitelistManager.getAppUsedSeconds(context, packageName)
    val left = (limitMinutes * 60 - usedSeconds).coerceAtLeast(0)
    val fraction = (left.toFloat() / (limitMinutes * 60f)).coerceIn(0f, 1f)

    val tint = when {
        fraction <= 0.10f -> NEARLY_OUT
        fraction <= 0.25f -> LOW
        else -> PLENTY
    }
    val swept by animateFloatAsState(fraction, tween(500), label = "limitRing")

    // The ring sits outside the icon, so the icon keeps the size it has everywhere else and
    // the tile does not shrink just because a limit was put on it.
    val ringSize = size + 8.dp

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(ringSize)) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2f
            val radius = (this.size.minDimension * 0.28f)

            val outline = Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(
                            inset,
                            inset,
                            this@Canvas.size.width - inset,
                            this@Canvas.size.height - inset
                        ),
                        CornerRadius(radius, radius)
                    )
                )
            }

            drawPath(
                outline,
                color = if (swept <= 0f) NEARLY_OUT.copy(alpha = 0.45f) else TRACK,
                style = Stroke(width = stroke)
            )

            if (swept > 0.001f) {
                // A rounded rectangle has no angles to sweep, so the remaining portion is cut
                // out of the outline by length instead.
                val measure = PathMeasure().apply { setPath(outline, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * swept, partial, true)
                drawPath(
                    partial,
                    color = tint,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        // Spent apps go grey and dim. The tile stays where it is -- the app has not gone
        // away, it is just done for today -- but it stops competing with the ones you can
        // still open, which is the whole point of having set a limit.
        if (fraction <= 0f) {
            Image(
                bitmap = bitmap ?: ImageBitmap(1, 1),
                contentDescription = null,
                alpha = 0.30f,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                modifier = Modifier.size(size)
            )
        } else {
            CachedAppIcon(bitmap = bitmap, modifier = Modifier.size(size))
        }
    }
}

/** The limit in minutes, or -1 when the app has none. */
@Composable
private fun rememberLimit(context: Context, packageName: String): Int =
    WhitelistManager.getAppUsageLimitMinutes(context, packageName)
