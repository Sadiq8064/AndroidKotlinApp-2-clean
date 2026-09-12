package com.example.androidkotlinapp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BELL = Color(0xFFFFB74D)
private val BELL_DEEP = Color(0xFFF57C00)
private val R_DANGER = Color(0xFFE57373)
private val R_CARD = Color(0xFF0E0E0E)
private val R_EDGE = Color(0xFF1C1C1C)
private val R_MUTED = Color(0xFF7A7A7A)
private val R_FAINT = Color(0xFF4A4A4A)

/**
 * Reminders, in an app of their own.
 *
 * Moved out of the Time app because the two were never the same job: a goal is work you break
 * into steps and grind through, a reminder is a date you must not walk past. Sharing a bottom
 * bar made each of them feel like half of something.
 *
 * There is no back button in the bar. The system gesture goes back, and a chrome button for it
 * would only take room from the one thing this screen is for.
 */
@Composable
fun ReminderScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var reload by remember { mutableStateOf(0) }
    val reminders = remember(reload) { Tasks.reminders(context) }

    var editing by remember { mutableStateOf<Reminder?>(null) }
    var composing by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Reminder?>(null) }

    if (composing || editing != null) {
        ReminderEditor(
            existing = editing,
            onDone = { composing = false; editing = null; reload++ },
            onCancel = { composing = false; editing = null }
        )
        return
    }

    BackHandler(enabled = true) { onBack() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 14.dp)
            ) {
                BellGlyph(size = 24.dp)
                Spacer(Modifier.width(11.dp))
                Text(
                    "Reminder",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }

            if (reminders.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 44.dp)
                    ) {
                        BellGlyph(size = 52.dp, dim = true)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No reminders yet",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap + for a date you must not walk past.",
                            color = Color(0xFF555555),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reminders, key = { it.id }) { r ->
                        ReminderRow(
                            reminder = r,
                            onEdit = { editing = r },
                            onDelete = { toDelete = r }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { composing = true },
            containerColor = BELL,
            contentColor = Color(0xFF1A1004),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, "New reminder")
        }
    }

    val dropping = toDelete
    if (dropping != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Color(0xFF121212),
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Delete this reminder?", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "\"${dropping.title}\" will go. This cannot be undone.",
                    color = R_MUTED,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Tasks.removeReminder(context, dropping.id)
                    toDelete = null
                    reload++
                }) {
                    Text("Delete", color = R_DANGER, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text("Cancel", color = Color.White, fontSize = 13.sp)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------- the card

@Composable
private fun ReminderRow(reminder: Reminder, onEdit: () -> Unit, onDelete: () -> Unit) {
    val at = reminder.nextOccurrenceMs
    val left = Tasks.daysUntil(at)
    val past = left < 0
    val tint = when {
        past -> R_FAINT
        left == 0 -> R_DANGER
        left <= 3 -> BELL
        else -> Color(0xFF64B5F6)
    }
    var menuOpen by remember { mutableStateOf(false) }

    // The ring empties over a month. Anything further out sits full, which is the honest
    // reading -- a date six weeks away has not started running down yet.
    val remaining = if (past) 0f else (left.toFloat() / 30f).coerceIn(0f, 1f)
    val swept by animateFloatAsState(remaining, tween(700), label = "reminderRing")

    Surface(
        color = R_CARD,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, R_EDGE),
        modifier = Modifier.fillMaxWidth().clickable { onEdit() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(62.dp)) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1F1F1F),
                    trackColor = Color.Transparent,
                    strokeWidth = 3.5.dp,
                    strokeCap = StrokeCap.Round
                )
                CircularProgressIndicator(
                    progress = { swept },
                    modifier = Modifier.fillMaxSize(),
                    color = tint,
                    trackColor = Color.Transparent,
                    strokeWidth = 3.5.dp,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (past) "${-left}" else "$left",
                        color = if (past) R_MUTED else Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        lineHeight = 25.sp
                    )
                    Text(
                        when {
                            past -> "days ago"
                            left == 1 -> "day"
                            else -> "days"
                        },
                        color = R_MUTED,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 11.sp
                    )
                }
            }

            Spacer(Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    color = if (past) R_MUTED else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 23.sp,
                    letterSpacing = (-0.2).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (past) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Tasks.countdown(at),
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("  ·  ", color = R_FAINT, fontSize = 12.sp)
                    Text(Tasks.shortDate(at), color = R_MUTED, fontSize = 12.sp)
                    if (reminder.repeatYearly) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Autorenew,
                            "Repeats yearly",
                            tint = R_MUTED,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Box(modifier = Modifier.align(Alignment.Top)) {
                Icon(
                    Icons.Default.MoreVert,
                    "Reminder options",
                    tint = R_FAINT,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { menuOpen = true }
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(Color(0xFF141414))
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit", color = Color.White, fontSize = 13.sp) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete reminder", color = R_DANGER, fontSize = 13.sp) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- the editor

/**
 * Making or changing a reminder: what, when, whether it comes round again, and how early the
 * home screen should start mentioning it.
 */
@Composable
private fun ReminderEditor(
    existing: Reminder?,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var dateMs by remember {
        mutableStateOf(
            existing?.nextOccurrenceMs
                ?: Tasks.startOfDay(System.currentTimeMillis() + 86_400_000)
        )
    }
    var repeatYearly by remember { mutableStateOf(existing?.repeatYearly ?: false) }
    var picking by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { onCancel() }

    EditorScaffold(
        heading = if (existing == null) "New reminder" else "Edit reminder",
        subheading = "A date, and what it is for.",
        actionLabel = if (existing == null) "ADD REMINDER" else "SAVE",
        actionEnabled = title.isNotBlank(),
        error = null,
        onCancel = onCancel,
        accent = BELL,
        onAction = {
            // Editing writes over the same row, so the reminder keeps its identity rather than
            // becoming a second copy of itself.
            Tasks.addReminder(
                context = context,
                title = title,
                dateMs = dateMs,
                repeatYearly = repeatYearly,
                id = existing?.id
            )
            onDone()
        }
    ) {
        LabelledField("Reminder", title, { title = it }, "Scholarship form closes")

        Spacer(Modifier.height(16.dp))
        DateBox("On", dateMs, Modifier.fillMaxWidth(), accent = BELL) { picking = true }

        Spacer(Modifier.height(22.dp))

        SectionLabel("Happens")
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ChoiceChip("Once", !repeatYearly, Modifier.weight(1f)) { repeatYearly = false }
            ChoiceChip("Every year", repeatYearly, Modifier.weight(1f)) { repeatYearly = true }
        }
        if (repeatYearly) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Comes round on the same day every year. Good for birthdays and renewals.",
                color = R_FAINT,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        Spacer(Modifier.height(22.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(R_CARD)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                Tasks.daysUntil(dateMs).coerceAtLeast(0).toString(),
                color = BELL,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (Tasks.daysUntil(dateMs) == 1) "day away" else "days away",
                color = R_MUTED,
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                Tasks.dateLabel(dateMs),
                color = R_FAINT,
                fontSize = 11.sp
            )
        }

        // The action button floats over the foot of the screen, so the scroll needs to end
        // clear of it rather than under it.
        Spacer(Modifier.height(28.dp))
    }

    if (picking) {
        DatePickerSheet(
            onDismiss = { picking = false },
            onPicked = { dateMs = Tasks.startOfDay(it); picking = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = R_MUTED,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

/** One pick-one-of option, sized by the row it sits in. */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) BELL.copy(alpha = 0.16f) else Color(0xFF101010))
            .then(
                if (selected) Modifier.border(
                    1.dp,
                    BELL.copy(alpha = 0.55f),
                    RoundedCornerShape(11.dp)
                ) else Modifier.border(1.dp, R_EDGE, RoundedCornerShape(11.dp))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 11.dp, horizontal = 4.dp)
    ) {
        Text(
            label,
            color = if (selected) BELL else R_MUTED,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
