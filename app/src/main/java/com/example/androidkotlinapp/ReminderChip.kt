package com.example.androidkotlinapp

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CALM = Color(0xFF64B5F6)
private val SOON = Color(0xFFFFB74D)
private val TODAY = Color(0xFFFFD54F)

/**
 * The next reminder, sitting under the FOCUS mark.
 *
 * Only ever one, and only ever a future one: a home screen is not a list, and the thing worth
 * knowing at a glance is what is coming next, not everything that is coming.
 *
 * On the day itself the chip breathes. A date that has arrived is the one case where the home
 * screen should insist a little, and a moving thing is the only element on this screen that
 * asks to be looked at.
 */
@Composable
fun NextReminderChip(reminder: Reminder, alsoWaiting: Int = 0, onOpen: () -> Unit) {
    val at = reminder.nextOccurrenceMs
    val left = Tasks.daysUntil(at)
    val isToday = left == 0

    val tint = when {
        isToday -> TODAY
        left <= 3 -> SOON
        else -> CALM
    }

    // Only today's chip pulses. Anything further out is information, not a prompt.
    val glow = if (isToday) {
        val breathe = rememberInfiniteTransition(label = "reminderChip")
        breathe.animateFloat(
            initialValue = 0.34f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1400, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "chipGlow"
        ).value
    } else {
        1f
    }

    // One row, the size it was. The hierarchy is carried by size and colour rather than by
    // stacking: a small tracked label, then the title at a larger size, then the date dimmest
    // of all at the end. Bolding all three, which is what this used to do, gave the eye three
    // things shouting and no idea which to read first.
    val lead = when {
        isToday -> "TODAY"
        left == 1 -> "TOMORROW"
        else -> "IN $left DAYS"
    }

    Surface(
        color = tint.copy(alpha = if (isToday) 0.07f + 0.09f * glow else 0.07f),
        border = BorderStroke(
            1.dp,
            tint.copy(alpha = if (isToday) 0.30f + 0.40f * glow else 0.26f)
        ),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onOpen
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (isToday) glow else 0.9f))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = lead,
                color = tint.copy(alpha = if (isToday) 0.7f + 0.3f * glow else 0.95f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                // Tracked out so a nine-point label reads as a label rather than as small text
                // someone forgot to make bigger.
                letterSpacing = 1.3.sp
            )

            Spacer(Modifier.width(9.dp))

            Text(
                text = reminder.title,
                color = Color(0xFFE0E0E0),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Dimmest thing in the row, and last: there for anyone who wants it, ignorable for
            // anyone who does not. Only past two days, since before that the words say it.
            if (left > 2) {
                Spacer(Modifier.width(9.dp))
                Text(
                    text = Tasks.shortDate(at).uppercase(),
                    color = Color(0xFF4E4E4E),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.7.sp
                )
            }

            if (alsoWaiting > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "+$alsoWaiting",
                    color = Color(0xFF5E5E5E),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
