package com.example.androidkotlinapp.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidkotlinapp.FocusDatabaseHelper
import com.example.androidkotlinapp.FocusService
import com.example.androidkotlinapp.HabitRecord
import com.example.androidkotlinapp.HabitStats
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Shared palette for the habit surfaces.
 *
 * Monochrome by design: the greys carry the structure and colour is reserved for the two
 * things that actually mean something -- green for a completed day, amber for a live streak.
 * Nothing else is tinted, so those two signals never have to compete for attention.
 */
object HabitTheme {
    val Background = Color(0xFF000000)
    val Sheet = Color(0xFF0D0D0D)
    val Surface = Color(0xFF111111)
    val SurfaceRaised = Color(0xFF1A1A1A)
    val Border = Color(0xFF242424)
    val BorderStrong = Color(0xFF3A3A3A)

    val TextPrimary = Color(0xFFF5F5F5)
    val TextMuted = Color(0xFF8E8E8E)
    val TextDisabled = Color(0xFF454545)

    val Flame = Color(0xFFFF9E4A)
    val FlameSoft = Color(0xFF231708)
    val Success = Color(0xFF4CAF50)
    val DoneSurface = Color(0xFF101710)
    val DoneBorder = Color(0xFF2F5A31)
    val Danger = Color(0xFFE06C6C)

    val GridEmpty = Color(0xFF181818)
    val GridDone = Color(0xFF4CAF50)
}

/**
 * Habit tracker.
 *
 * Every habit that exists is in force -- there is no per-session selection -- so anything
 * added, before a session or during one, counts from that moment. A habit left unperformed
 * for the day costs an extra hour of session time, which is why deleting is locked while a
 * session runs; renaming stays available throughout.
 *
 * Only today can be ticked: back-filling a past day would make both the streak and the
 * punishment record meaningless.
 */
@Composable
fun HabitTrackerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dbHelper = remember { FocusDatabaseHelper(context) }

    val isSessionActive = FocusService.isRunning

    var today by remember { mutableStateOf(HabitStats.today()) }
    var selectedDate by remember { mutableStateOf(today) }

    var habits by remember { mutableStateOf(emptyList<HabitRecord>()) }
    var doneOnSelected by remember { mutableStateOf(emptySet<Int>()) }
    var streaks by remember { mutableStateOf(emptyMap<Int, Int>()) }
    var completionsByHabit by remember { mutableStateOf(emptyMap<Int, Set<String>>()) }

    var editorHabit by remember { mutableStateOf<HabitRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HabitRecord?>(null) }
    var detailHabit by remember { mutableStateOf<HabitRecord?>(null) }

    fun reload() {
        habits = dbHelper.getAllHabits()
        completionsByHabit = habits.associate { it.id to dbHelper.getCompletionDatesForHabit(it.id) }
        doneOnSelected = dbHelper.getHabitIdsDoneOn(selectedDate)
        streaks = habits.associate { habit ->
            habit.id to HabitStats.currentStreak(completionsByHabit[habit.id].orEmpty(), today)
        }
    }

    LaunchedEffect(Unit) { reload() }

    // A session can run past midnight, so keep the current day live while this screen is open.
    LaunchedEffect(Unit) {
        while (true) {
            val currentDay = HabitStats.today()
            if (currentDay != today) {
                if (selectedDate == today) selectedDate = currentDay
                today = currentDay
                reload()
            }
            kotlinx.coroutines.delay(10_000)
        }
    }

    LaunchedEffect(selectedDate) {
        doneOnSelected = dbHelper.getHabitIdsDoneOn(selectedDate)
    }

    BackHandler { onBack() }

    if (showEditor) {
        HabitEditorScreen(
            existing = editorHabit,
            onCancel = { showEditor = false },
            onSave = { emoji, text ->
                val target = editorHabit
                if (target == null) {
                    dbHelper.insertHabit(emoji, text, today)
                } else {
                    dbHelper.updateHabit(target.id, emoji, text)
                }
                showEditor = false
                reload()
            },
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.fillMaxSize().background(HabitTheme.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HabitHeader()

            WeekStrip(
                today = today,
                selectedDate = selectedDate,
                onSelect = { selectedDate = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = formatLongDate(selectedDate),
                color = HabitTheme.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (habits.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No habits yet.\nTap + to add your first one. 🌱",
                        color = HabitTheme.TextMuted,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    habits.forEach { habit ->
                        HabitCard(
                            habit = habit,
                            isDone = doneOnSelected.contains(habit.id),
                            streak = streaks[habit.id] ?: 0,
                            // Only the live day is editable; history stays as it happened.
                            isEditableDay = selectedDate == today,
                            onOpen = { detailHabit = habit },
                            onToggle = { checked ->
                                dbHelper.setHabitDone(habit.id, today, checked)
                                reload()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editorHabit = null
                showEditor = true
            },
            containerColor = HabitTheme.Flame,
            contentColor = HabitTheme.Background,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 28.dp)
                .size(60.dp)
        ) {
            Text("+", fontSize = 30.sp, fontWeight = FontWeight.Light)
        }
    }

    detailHabit?.let { habit ->
        HabitDetailSheet(
            habit = habit,
            today = today,
            completions = completionsByHabit[habit.id].orEmpty(),
            canDelete = !isSessionActive,
            onDismiss = { detailHabit = null },
            onEdit = {
                editorHabit = habit
                detailHabit = null
                showEditor = true
            },
            onDelete = {
                pendingDelete = habit
                detailHabit = null
            }
        )
    }

    pendingDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = HabitTheme.Sheet,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete habit?", color = HabitTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "${habit.emoji}  ${habit.text} and its streak history will be removed. " +
                        "Penalties already served stay on your record.",
                    color = HabitTheme.TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dbHelper.deleteHabit(habit.id)
                    pendingDelete = null
                    reload()
                }) { Text("DELETE", color = HabitTheme.Danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("CANCEL", color = HabitTheme.TextMuted)
                }
            }
        )
    }
}

@Composable
private fun HabitHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🔥", fontSize = 24.sp)
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = "Streak",
            color = HabitTheme.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp
        )
    }
}

/** Monday-first week strip with the selected day filled. */
@Composable
private fun WeekStrip(today: String, selectedDate: String, onSelect: (String) -> Unit) {
    val weekDates = remember(today) {
        val mondayOffset = -mondayIndexOf(today)
        (0..6).map { HabitStats.shiftDays(today, mondayOffset + it) }
    }
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        weekDates.forEachIndexed { index, date ->
            val isSelected = date == selectedDate
            val isToday = date == today
            val isFuture = date > today

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isFuture) { onSelect(date) }
            ) {
                Text(
                    text = letters[index],
                    color = if (isFuture) HabitTheme.TextDisabled else HabitTheme.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            // The chosen day burns; the rest stay flat. Using the streak's own
                            // flame here rather than a white slab keeps the strip inside the
                            // screen's palette instead of borrowing a system control's look.
                            color = if (isSelected) HabitTheme.Flame else HabitTheme.Surface,
                            shape = CircleShape
                        )
                        .border(
                            width = 1.5.dp,
                            color = when {
                                isSelected -> Color.Transparent
                                isToday -> HabitTheme.Flame.copy(alpha = 0.55f)
                                else -> Color.Transparent
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayOfMonth(date),
                        color = when {
                            isSelected -> Color(0xFF1A0E02)
                            isFuture -> HabitTheme.TextDisabled
                            isToday -> HabitTheme.Flame
                            else -> HabitTheme.TextPrimary
                        },
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** Habit row: emoji tile, title over "Daily", streak flame, then the tick box. */
@Composable
private fun HabitCard(
    habit: HabitRecord,
    isDone: Boolean,
    streak: Int,
    isEditableDay: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) HabitTheme.DoneSurface else HabitTheme.Surface
        ),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, if (isDone) HabitTheme.DoneBorder else HabitTheme.Border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = habit.emoji, fontSize = 34.sp)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.text,
                    color = if (isDone) HabitTheme.TextMuted else HabitTheme.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Daily", color = HabitTheme.TextMuted, fontSize = 13.sp)
            }

            // The flame only appears once a streak actually exists.
            if (streak > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(HabitTheme.FlameSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(text = "🔥", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "$streak", color = HabitTheme.Flame, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Text(text = "🔥 0", color = HabitTheme.TextDisabled, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(12.dp))
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (isDone) HabitTheme.Success else Color.Transparent,
                        shape = RoundedCornerShape(13.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = when {
                            isDone -> HabitTheme.Success
                            isEditableDay -> HabitTheme.BorderStrong
                            else -> HabitTheme.TextDisabled.copy(alpha = 0.4f)
                        },
                        shape = RoundedCornerShape(13.dp)
                    )
                    .clickable(enabled = isEditableDay) { onToggle(!isDone) },
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Done",
                        tint = HabitTheme.Background,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Detail sheet: identity, streak counters and the completion history grid.
 *
 * The grid runs from the day the habit was created through today, always at least a month
 * wide so a new habit still shows a board to fill in, and simply keeps growing after that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitDetailSheet(
    habit: HabitRecord,
    today: String,
    completions: Set<String>,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val currentStreak = remember(completions, today) { HabitStats.currentStreak(completions, today) }
    val bestStreak = remember(completions) { HabitStats.longestStreak(completions) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HabitTheme.Sheet,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HabitTheme.Border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = habit.emoji, fontSize = 40.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.text,
                        color = HabitTheme.TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (currentStreak > 0) "🔥 $currentStreak day streak" else "No active streak",
                        color = if (currentStreak > 0) HabitTheme.Flame else HabitTheme.TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, "Edit habit", tint = HabitTheme.TextMuted, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onDelete, enabled = canDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        if (canDelete) "Delete habit" else "Locked during session",
                        tint = if (canDelete) HabitTheme.Danger else HabitTheme.TextDisabled,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip("Current", "$currentStreak", Modifier.weight(1f))
                StatChip("Best", "$bestStreak", Modifier.weight(1f))
                StatChip("Total", "${completions.size}", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = HabitTheme.Surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HabitTheme.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Completion History",
                        color = HabitTheme.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CompletionHistoryGrid(
                        startDate = habit.createdDate.ifBlank { today },
                        today = today,
                        completions = completions
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(HabitTheme.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, HabitTheme.Border, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, color = HabitTheme.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = HabitTheme.TextMuted, fontSize = 10.sp)
    }
}

/**
 * Contribution grid: one column per week, seven rows Monday..Sunday, running left to right
 * from the habit's creation day. Month labels sit above the column where the month turns over.
 */
@Composable
private fun CompletionHistoryGrid(
    startDate: String,
    today: String,
    completions: Set<String>
) {
    val weeks = remember(startDate, today) { buildWeekColumns(startDate, today) }
    val listState = rememberLazyListState()

    LaunchedEffect(weeks.size, today) {
        val todayColumn = weeks.indexOfFirst { it.contains(today) }
        if (weeks.isNotEmpty()) {
            listState.scrollToItem(if (todayColumn >= 0) todayColumn else weeks.lastIndex)
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(weeks) { index, week ->
            val label = monthLabelFor(weeks, index)
            Column(horizontalAlignment = Alignment.Start) {
                Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.CenterStart) {
                    if (label != null) {
                        Text(
                            text = label,
                            color = HabitTheme.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { date ->
                        val color = when {
                            date == null -> Color.Transparent
                            completions.contains(date) -> HabitTheme.GridDone
                            date > today -> Color.Transparent
                            else -> HabitTheme.GridEmpty
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(color, RoundedCornerShape(6.dp))
                                .then(
                                    if (date == today) {
                                        Modifier.border(1.5.dp, HabitTheme.BorderStrong, RoundedCornerShape(6.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

/** LazyRow itemsIndexed shim so the grid can look back at the previous column. */
private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<T>,
    crossinline itemContent: @Composable (Int, T) -> Unit
) = items(items.size) { index -> itemContent(index, items[index]) }

/** Month name for a column, shown only when it differs from the column before it. */
private fun monthLabelFor(weeks: List<List<String?>>, index: Int): String? {
    val month = weeks[index].filterNotNull().firstOrNull()?.let { monthLabel(it) } ?: return null
    if (index == 0) return month
    val previous = weeks[index - 1].filterNotNull().firstOrNull()?.let { monthLabel(it) }
    return if (month != previous) month else null
}

private const val MIN_GRID_DAYS = 28

/**
 * Splits the habit's lifetime into Monday-aligned week columns.
 *
 * The grid always spans at least [MIN_GRID_DAYS] so a brand new habit still shows a month of
 * board to fill in. Null entries pad the first and last weeks so every column has seven cells
 * and the weekday rows line up.
 */
private fun buildWeekColumns(startDate: String, today: String): List<List<String?>> {
    if (HabitStats.daysBetween(startDate, today) < 0) return emptyList()

    val minimumEnd = HabitStats.shiftDays(startDate, MIN_GRID_DAYS - 1)
    val end = if (HabitStats.daysBetween(today, minimumEnd) > 0) minimumEnd else today

    val dates = HabitStats.dateRange(startDate, end)
    if (dates.isEmpty()) return emptyList()

    val leadingBlanks = mondayIndexOf(dates.first())
    val cells = ArrayList<String?>(leadingBlanks + dates.size)
    repeat(leadingBlanks) { cells.add(null) }
    cells.addAll(dates)
    while (cells.size % 7 != 0) cells.add(null)

    return cells.chunked(7)
}

private fun utcFormatter(pattern: String) = SimpleDateFormat(pattern, Locale.getDefault()).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseDate(date: String) = try {
    utcFormatter("yyyy-MM-dd").parse(date)
} catch (e: Exception) {
    null
}

/** 0 for Monday through 6 for Sunday. */
private fun mondayIndexOf(date: String): Int {
    val parsed = parseDate(date) ?: return 0
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = parsed }
    return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

private fun dayOfMonth(date: String): String =
    parseDate(date)?.let { utcFormatter("d").format(it) } ?: ""

private fun monthLabel(date: String): String =
    parseDate(date)?.let { utcFormatter("MMM yyyy").format(it) } ?: ""

private fun formatLongDate(date: String): String =
    parseDate(date)?.let { utcFormatter("EEEE · MMM d, yyyy").format(it) } ?: date
