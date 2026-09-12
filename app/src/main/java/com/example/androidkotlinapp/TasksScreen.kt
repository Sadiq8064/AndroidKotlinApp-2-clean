package com.example.androidkotlinapp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ACCENT = Color(0xFF7C6BFF)
private val ACCENT_DEEP = Color(0xFF4B3BD6)
private val WARM = Color(0xFFFFB74D)
private val DANGER = Color(0xFFE57373)
private val OK = Color(0xFF66BB6A)
private val CARD = Color(0xFF0E0E0E)
private val EDGE = Color(0xFF1C1C1C)
private val MUTED = Color(0xFF7A7A7A)
private val FAINT = Color(0xFF4A4A4A)


/**
 * Goals and reminders.
 *
 * Two shelves rather than one list, because they are different kinds of thing: a goal is work
 * broken into steps and measured by how much of it is done, a reminder is a date and nothing
 * more. Both are shown by how long is left, since that is the question either one is kept for.
 */
@Composable
fun TasksScreen(onBack: () -> Unit) {
    val context = LocalContext.current


    // Held rather than deleted on the spot: both of these destroy work with no way back, so
    // the menu item asks and the dialog is what actually removes anything.
    var goalToDelete by remember { mutableStateOf<Goal?>(null) }
    var reload by remember { mutableStateOf(0) }
    var goals by remember { mutableStateOf(Tasks.goals(context)) }
    var reminders by remember { mutableStateOf(Tasks.reminders(context)) }

    var composingGoal by remember { mutableStateOf(false) }
    var openGoalId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        goals = Tasks.goals(context)
    }

    // ---------------------------------------------------------------- editors and detail
    if (composingGoal) {
        GoalEditorScreen(
            onDone = { composingGoal = false; reload++ },
            onCancel = { composingGoal = false }
        )
        return
    }
    val open = goals.firstOrNull { it.id == openGoalId }
    if (open != null) {
        GoalDetailScreen(
            goal = open,
            onChanged = { reload++ },
            onBack = { openGoalId = null; reload++ }
        )
        return
    }

    BackHandler(enabled = true) { onBack() }

    // ---------------------------------------------------------------- the two shelves
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 16.dp)
            ) {
                AtomGlyph(size = 24.dp, spinning = true)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Target",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                GoalsShelf(
                        goals = goals,
                        onOpen = { openGoalId = it.id },
                        onToggle = { g, t ->
                            Tasks.toggleTask(context, g.id, t.id)
                            // Ticking the last outstanding step is what finishes a goal, and
                            // that is worth saying out loud -- but only on the tick that does
                            // it, never on a step being un-ticked and ticked again.
                            val wasDone = g.isDone
                            val nowDone = g.tasks.all { if (it.id == t.id) !it.done else it.done }
                            if (!wasDone && nowDone && g.total > 0) {
                                TaskReminderScheduler.celebrateGoal(context, g)
                            }
                            reload++
                        },
                        onRemove = { goalToDelete = it }
                    )

            }

        }

        val droppingGoal = goalToDelete
        if (droppingGoal != null) {
            ConfirmDelete(
                title = "Delete this goal?",
                body = if (droppingGoal.total > 0) {
                    "\"${droppingGoal.title}\" and its ${droppingGoal.total} steps will go. " +
                        "This cannot be undone."
                } else {
                    "\"${droppingGoal.title}\" will go. This cannot be undone."
                },
                confirm = "Delete goal",
                onDismiss = { goalToDelete = null },
                onConfirm = {
                    Tasks.removeGoal(context, droppingGoal.id)
                    goalToDelete = null
                    reload++
                }
            )
        }


        FloatingActionButton(
            onClick = {
                composingGoal = true
            },
            containerColor = ACCENT,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, "Add")
        }
    }
}




/**
 * The question asked before anything is destroyed.
 *
 * Cancel is the wide, quiet option and delete is the narrow red one, because the common reason
 * to be looking at this dialog is having opened it by accident.
 */
@Composable
internal fun ConfirmDelete(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121212),
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(body, color = MUTED, fontSize = 13.sp, lineHeight = 19.sp)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = DANGER, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White, fontSize = 13.sp)
            }
        }
    )
}

// ---------------------------------------------------------------------- goals

@Composable
private fun GoalsShelf(
    goals: List<Goal>,
    onOpen: (Goal) -> Unit,
    onToggle: (Goal, GoalTask) -> Unit,
    onRemove: (Goal) -> Unit
) {
    if (goals.isEmpty()) {
        EmptyShelf("🎯", "No goals yet", "Tap + to set one, with a deadline and the steps to get there.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(goals, key = { it.id }) { goal ->
            GoalCard(
                goal = goal,
                onOpen = { onOpen(goal) },
                onToggle = { onToggle(goal, it) },
                onRemove = { onRemove(goal) }
            )
        }
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    onOpen: () -> Unit,
    onToggle: (GoalTask) -> Unit,
    onRemove: () -> Unit
) {
    val left = Tasks.daysUntil(goal.endMs)
    val urgency = when {
        goal.isDone -> OK
        left < 0 -> DANGER
        left <= 2 -> WARM
        left <= 6 -> ACCENT
        else -> ACCENT
    }
    var menuOpen by remember { mutableStateOf(false) }
    // A finished goal folds into a single line; the chevron below the menu reopens it.
    var expanded by remember(goal.isDone) { mutableStateOf(!goal.isDone) }

    // Every step, in its own order, ticked or not. A step that vanishes when it is ticked takes
    // the evidence of the work with it, and the list jumps under the finger that just tapped it.
    val shown = goal.tasks

    Surface(
        color = CARD,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, EDGE),
        // No whole-card tap: editing is a deliberate act through the menu, not something a
        // stray touch triggers. Ticking a step and the collapse chevron still work.
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                // The one thing the eye lands on. Everything else on the card is deliberately
                // smaller, so a shelf of goals can be read at a glance rather than studied.
                CountRing(
                    fraction = goal.progress,
                    figure = "${goal.completed}",
                    caption = "",
                    // A completed goal draws a full circle; a bright green one would wash the
                    // whole card green, so the finished ring is a quiet, muted green instead.
                    tint = if (goal.isDone) Color(0xFF2F5A38) else urgency,
                    dimFigure = goal.isDone
                )

                Spacer(Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        goal.title,
                        color = if (goal.isDone) MUTED else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 23.sp,
                        letterSpacing = (-0.2).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(7.dp))

                    // One quiet line carries the rest: how long is left, and the day it is due.
                    // Two facts on one line read faster than two labelled fields stacked up.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (goal.isDone) "Finished" else Tasks.countdown(goal.endMs),
                            color = if (goal.isDone) OK else urgency,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "  ·  ",
                            color = FAINT,
                            fontSize = 12.sp
                        )
                        Text(
                            Tasks.shortDate(goal.endMs),
                            color = MUTED,
                            fontSize = 12.sp
                        )
                    }
                }

                // Menu and, for a finished goal, the collapse chevron -- stacked in a column so
                // the chevron sits clearly below the dots rather than on top of them.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Top)
                ) {
                    Box {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Goal options",
                            tint = FAINT,
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
                                onClick = { menuOpen = false; onOpen() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete goal", color = DANGER, fontSize = 13.sp) },
                                onClick = { menuOpen = false; onRemove() }
                            )
                        }
                    }

                    if (goal.isDone) {
                        Spacer(Modifier.height(14.dp))
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MUTED,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { expanded = !expanded }
                        )
                    }
                }
            }

            if (shown.isNotEmpty() && expanded) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF191919))
                )
                Spacer(Modifier.height(4.dp))
                shown.forEachIndexed { index, task ->
                    StepRow(
                        task = task,
                        isFirst = index == 0,
                        isLast = index == shown.lastIndex,
                        onToggle = { onToggle(task) }
                    )
                }
            }
        }
    }
}

/**
 * The dominant element both cards are built around.
 *
 * A ring that fills, a large figure inside it, and a small caption under that. Goals fill it
 * with progress and reminders empty it with time, but the shape and the weight are the same on
 * both, so the two shelves read as one app rather than as two screens that met by accident.
 */
@Composable
internal fun CountRing(
    fraction: Float,
    figure: String,
    caption: String,
    tint: Color,
    dimFigure: Boolean = false
) {
    val swept by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "ring")

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
                figure,
                color = if (dimFigure) MUTED else Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                lineHeight = 25.sp
            )
            if (caption.isNotBlank()) {
                Text(
                    caption,
                    color = MUTED,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 11.sp
                )
            }
        }
    }
}

/**
 * One step, tickable where it stands, joined to its neighbours by a dashed rail.
 *
 * The connecting line is what turns a list of checkboxes into a path with a start and an end --
 * the same visual language a delivery tracker or a setup wizard uses. Done steps are green and
 * struck through; the one still open is a hollow ring in the app's own colour.
 */
@Composable
private fun StepRow(task: GoalTask, isFirst: Boolean, isLast: Boolean, onToggle: () -> Unit) {
    val connector = Color(0xFF2A2A33)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
    ) {
        // The rail: a dashed line through the middle with the check sat on top, so the steps
        // read as one connected path.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(34.dp).fillMaxHeight()
        ) {
            Canvas(modifier = Modifier.fillMaxHeight().width(34.dp)) {
                val cx = size.width / 2f
                val midY = size.height / 2f
                val gap = 12.dp.toPx()
                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(5f, 6f), 0f
                )
                if (!isFirst) drawLine(
                    color = connector,
                    start = androidx.compose.ui.geometry.Offset(cx, 0f),
                    end = androidx.compose.ui.geometry.Offset(cx, midY - gap),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dash
                )
                if (!isLast) drawLine(
                    color = connector,
                    start = androidx.compose.ui.geometry.Offset(cx, midY + gap),
                    end = androidx.compose.ui.geometry.Offset(cx, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dash
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(CARD)
                    .border(
                        width = 1.5.dp,
                        color = if (task.done) OK else ACCENT.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            ) {
                if (task.done) {
                    Icon(Icons.Default.Check, null, tint = OK, modifier = Modifier.size(13.dp))
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Tighter rows, larger text, centred against the check.
        Text(
            text = task.title,
            color = if (task.done) FAINT else Color(0xFFE8E8E8),
            fontSize = 15.sp,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 9.dp)
        )
    }
}

@Composable
private fun Pill(text: String, tint: Color) {
    Text(
        text = text,
        color = tint,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

// ---------------------------------------------------------------------- reminders



// ---------------------------------------------------------------------- goal detail

@Composable
private fun GoalDetailScreen(goal: Goal, onChanged: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var newTask by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    val live = remember(version, goal.id) {
        Tasks.goals(context).firstOrNull { it.id == goal.id } ?: goal
    }
    val left = Tasks.daysUntil(live.endMs)
    val urgency = when {
        live.isDone -> OK
        left < 0 -> DANGER
        left <= 2 -> WARM
        else -> ACCENT
    }

    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(8.dp, 12.dp, 20.dp, 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text(
                live.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(if (live.isDone) "Done" else Tasks.countdown(live.endMs), urgency)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${Tasks.shortDate(live.startMs)} – ${Tasks.shortDate(live.endMs)}",
                    color = MUTED,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(18.dp))

            val progress by animateFloatAsState(live.progress, tween(500), label = "detail")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF161616))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(ACCENT_DEEP, urgency)))
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${live.completed} of ${live.total} done",
                color = FAINT,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // A plain scrolling column rather than a lazy one: a goal has a handful of steps, and
        // dragging one over the others needs every row measured, which a lazy list will not do
        // for anything scrolled off screen.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(18.dp, 0.dp, 18.dp, 16.dp)
        ) {
            var dragging by remember { mutableStateOf(-1) }
            var dragOffset by remember { mutableStateOf(0f) }
            val rowPitchPx = with(LocalDensity.current) { (ROW_HEIGHT + 8.dp).toPx() }

            live.tasks.forEachIndexed { index, task ->
                TaskRow(
                    task = task,
                    index = index,
                    dragging = dragging == index,
                    offsetY = if (dragging == index) dragOffset else 0f,
                    onToggle = {
                        Tasks.toggleTask(context, live.id, task.id); version++; onChanged()
                    },
                    onRemove = {
                        Tasks.removeTask(context, live.id, task.id); version++; onChanged()
                    },
                    onDragStart = { dragging = index; dragOffset = 0f },
                    onDrag = { dragOffset += it },
                    onDragEnd = {
                        val shift = Math.round(dragOffset / rowPitchPx)
                        if (shift != 0) {
                            val target = (index + shift)
                                .coerceIn(0, live.tasks.lastIndex)
                            if (target != index) {
                                val reordered = live.tasks.toMutableList()
                                val moved = reordered.removeAt(index)
                                reordered.add(target, moved)
                                Tasks.updateGoal(context, live.copy(tasks = reordered))
                                version++
                                onChanged()
                            }
                        }
                        dragging = -1
                        dragOffset = 0f
                    }
                )
            }
        }

        // Adding a step is the thing done most often on this screen, so it sits at the thumb.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(18.dp, 0.dp, 18.dp, 18.dp)
        ) {
            OutlinedTextField(
                value = newTask,
                onValueChange = { newTask = it },
                placeholder = { Text("Add a step", color = FAINT, fontSize = 13.sp) },
                singleLine = true,
                colors = fieldColours(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (newTask.isBlank()) Color(0xFF1A1A1A) else ACCENT)
                    .clickable(enabled = newTask.isNotBlank()) {
                        Tasks.addTask(context, live.id, newTask)
                        newTask = ""
                        version++
                        onChanged()
                    }
            ) {
                Icon(
                    Icons.Default.Add,
                    null,
                    tint = if (newTask.isBlank()) FAINT else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** Height of one step row, so a drag can be turned into a number of places moved. */
private val ROW_HEIGHT = 50.dp

@Composable
private fun TaskRow(
    task: GoalTask,
    index: Int,
    dragging: Boolean,
    offsetY: Float,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(140),
        label = "lift"
    )

    Surface(
        color = if (dragging) Color(0xFF17162A) else if (task.done) Color(0xFF0B0B0B) else CARD,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            when {
                dragging -> ACCENT
                task.done -> Color(0xFF151515)
                else -> EDGE
            }
        ),
        // Lifted out of the list while held, so it is obvious which row is being moved.
        shadowElevation = (10 * lift).dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = offsetY
                scaleX = 1f + 0.02f * lift
                scaleY = 1f + 0.02f * lift
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (task.done) OK.copy(alpha = 0.18f) else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (task.done) OK else Color(0xFF2E2E2E),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle
                    )
            ) {
                if (task.done) {
                    Icon(Icons.Default.Check, null, tint = OK, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = task.title,
                color = if (task.done) FAINT else Color.White,
                fontSize = 13.sp,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                Icons.Default.Close,
                "Remove step",
                tint = FAINT,
                modifier = Modifier
                    .size(15.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove
                    )
            )
            Spacer(Modifier.width(14.dp))

            // Dragging is confined to the handle. Anywhere else and a scroll of the list would
            // pick a row up by accident.
            Icon(
                Icons.Default.DragHandle,
                "Reorder",
                tint = if (dragging) ACCENT else Color(0xFF3E3E3E),
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(task.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
            )
        }
    }
}

// ---------------------------------------------------------------------- editors

@Composable
private fun GoalEditorScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var startMs by remember { mutableStateOf(Tasks.startOfDay(System.currentTimeMillis())) }
    var endMs by remember { mutableStateOf(Tasks.startOfDay(System.currentTimeMillis() + 7L * 86400000)) }
    var steps by remember { mutableStateOf(listOf<String>()) }
    var draft by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) { onCancel() }

    EditorScaffold(
        heading = "New goal",
        subheading = "Something to finish, and the day it has to be finished by.",
        actionLabel = "CREATE GOAL",
        actionEnabled = title.isNotBlank(),
        error = error,
        onCancel = onCancel,
        onAction = {
            if (endMs < startMs) {
                error = "The deadline cannot be before the start."
                return@EditorScaffold
            }
            Tasks.addGoal(context, title, startMs, endMs, steps)
            onDone()
        }
    ) {
        LabelledField("Goal", title, { title = it; error = null }, "Finish the DSA course")

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            DateBox("Starts", startMs, Modifier.weight(1f)) { picking = "start" }
            Spacer(Modifier.width(12.dp))
            DateBox("Ends", endMs, Modifier.weight(1f)) { picking = "end" }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "${Tasks.daysUntil(endMs).coerceAtLeast(0)} days to work with",
            color = FAINT,
            fontSize = 10.5.sp
        )

        Spacer(Modifier.height(22.dp))

        Text("Steps", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Optional now -- more can be added once the goal exists.",
            color = FAINT,
            fontSize = 10.5.sp
        )
        Spacer(Modifier.height(12.dp))

        steps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CARD)
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text("${index + 1}", color = ACCENT, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(step, color = Color.White, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.Close,
                    "Remove",
                    tint = FAINT,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { steps = steps.filterIndexed { i, _ -> i != index } }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Add a step", color = FAINT, fontSize = 13.sp) },
                singleLine = true,
                colors = fieldColours(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (draft.isBlank()) Color(0xFF1A1A1A) else ACCENT)
                    .clickable(enabled = draft.isNotBlank()) {
                        steps = steps + draft.trim()
                        draft = ""
                    }
            ) {
                Icon(
                    Icons.Default.Add,
                    null,
                    tint = if (draft.isBlank()) FAINT else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    if (picking != null) {
        DatePickerSheet(
            onDismiss = { picking = null },
            onPicked = { ms ->
                if (picking == "start") startMs = Tasks.startOfDay(ms)
                else endMs = Tasks.startOfDay(ms)
                error = null
                picking = null
            }
        )
    }
}


/** The frame both editors share, so a goal and a reminder are made the same way. */
@Composable
internal fun EditorScaffold(
    heading: String,
    subheading: String,
    actionLabel: String,
    actionEnabled: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onAction: () -> Unit,
    accent: Color = ACCENT,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(8.dp, 12.dp, 20.dp, 4.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Text(heading, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subheading, color = MUTED, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(26.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }

        if (error != null) {
            Text(
                error,
                color = DANGER,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }

        Button(
            onClick = onAction,
            enabled = actionEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                disabledContainerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp, 0.dp, 22.dp, 20.dp)
                .height(54.dp)
        ) {
            Text(
                actionLabel,
                color = if (actionEnabled) Color.White else FAINT,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
internal fun LabelledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String
) {
    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = FAINT, fontSize = 13.sp) },
        singleLine = true,
        colors = fieldColours(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun DateBox(
    label: String,
    ms: Long,
    modifier: Modifier = Modifier,
    accent: Color = ACCENT,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CARD)
            .border(1.dp, EDGE, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(label, color = FAINT, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CalendarMonth,
                null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(Tasks.dateLabel(ms), color = Color.White, fontSize = 12.5.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerSheet(onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val state = rememberDatePickerState()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF101010),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, EDGE)
        ) {
            Column {
                DatePicker(
                    state = state,
                    colors = DatePickerDefaults.colors(
                        containerColor = Color(0xFF101010),
                        selectedDayContainerColor = ACCENT,
                        todayDateBorderColor = ACCENT
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = MUTED) }
                    TextButton(
                        onClick = { state.selectedDateMillis?.let(onPicked) },
                        enabled = state.selectedDateMillis != null
                    ) {
                        Text("Choose", color = ACCENT, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color(0xFF101010),
    unfocusedContainerColor = Color(0xFF101010),
    focusedBorderColor = ACCENT,
    unfocusedBorderColor = EDGE,
    cursorColor = ACCENT
)

@Composable
private fun EmptyShelf(glyph: String, title: String, body: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 44.dp)
        ) {
            Text(glyph, fontSize = 40.sp)
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color.Gray, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                color = Color(0xFF555555),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
