package com.example.androidkotlinapp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ACCENT = Color(0xFF64B5F6)
private val ACCENT_DEEP = Color(0xFF1565C0)
private val BAR_IDLE_TOP = Color(0xFF37516A)
private val BAR_IDLE_BOTTOM = Color(0xFF1E2C3A)
private val MUTED = Color(0xFF6E6E6E)
private val GRID = Color(0xFF1E1E1E)

/**
 * What the focus sessions added up to.
 *
 * The chart is the screen. Everything else on it exists to label the chart -- which week, how
 * much, which bar you just touched -- because a page opened to see how a week went should
 * answer that in one look rather than in a grid of tiles.
 */
@Composable
fun FocusStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var anchorMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedDay by remember { mutableStateOf<String?>(null) }

    val week = remember(anchorMs) { FocusStats.week(context, anchorMs) }
    val lifetime = remember(anchorMs) { FocusStats.lifetimeSeconds(context) }
    val lifetimeSessions = remember(anchorMs) { FocusStats.lifetimeSessions(context) }
    val hasBefore = remember(anchorMs) { FocusStats.hasWeekBefore(context, anchorMs) }
    val hasAfter = remember(anchorMs) { FocusStats.hasWeekAfter(anchorMs) }

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
            Text(
                "Focus Sessions",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (FocusStats.firstDayMs(context) == null) {
            EmptyState()
            return@Column
        }

        // The headline follows the finger: no bar chosen and it reports the week, one chosen
        // and it reports that day. One number, in one place, whatever is being asked about.
        val shown = week.days.firstOrNull { it.dayKey == selectedDay }
        val headlineSeconds = shown?.seconds ?: week.totalSeconds
        val headlineSessions = shown?.sessions ?: week.totalSessions
        val headlineLabel = shown?.let { FocusStats.fullDayLabel(it.startMs) } ?: "this week"

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 26.dp, top = 26.dp, end = 26.dp)
        ) {
            Text(
                text = FocusStats.pretty(headlineSeconds),
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(headlineLabel, color = MUTED, fontSize = 12.sp)
                Text("  ·  ", color = GRID, fontSize = 12.sp)
                Text(
                    text = when (headlineSessions) {
                        1 -> "1 session"
                        else -> "$headlineSessions sessions"
                    },
                    color = ACCENT,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            WeekArrow(Icons.Default.ChevronLeft, hasBefore) {
                anchorMs = FocusStats.shiftWeeks(anchorMs, -1)
                selectedDay = null
            }
            Text(
                text = FocusStats.weekLabel(week.startMs),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            WeekArrow(Icons.Default.ChevronRight, hasAfter) {
                anchorMs = FocusStats.shiftWeeks(anchorMs, 1)
                selectedDay = null
            }
        }

        Spacer(Modifier.height(22.dp))

        Histogram(
            week = week,
            selectedDay = selectedDay,
            onSelect = { key -> selectedDay = if (selectedDay == key) null else key },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(30.dp))

        // The running total, kept apart from the chart. The chart answers how this week went;
        // this answers how far it all adds up to, which is a different question and deserves
        // its own place rather than another tile crowding the first answer.
        LifetimeCard(
            seconds = lifetime,
            sessions = lifetimeSessions,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp)
        )
    }
}

@Composable
private fun LifetimeCard(seconds: Int, sessions: Int, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFF0D0D0D),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1C1C1C)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
        ) {
            Text(
                text = "TOTAL FOCUSED",
                color = MUTED,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = FocusStats.pretty(seconds),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (sessions) {
                    1 -> "across 1 session"
                    else -> "across $sessions sessions"
                },
                color = ACCENT,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Seven bars against a ruled axis.
 *
 * The axis is rounded up to a round number of minutes rather than to the tallest bar, so the
 * gridlines land on figures worth reading and a good week is visibly taller than a poor one
 * instead of both filling the frame.
 */
@Composable
private fun Histogram(
    week: WeekStat,
    selectedDay: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val axisMax = remember(week) { FocusStats.axisCeiling(week.bestSeconds) }
    val gridLines = remember(axisMax) { FocusStats.axisSteps(axisMax) }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f) }

    Row(modifier = modifier) {
        // The scale, read bottom to top.
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.height(210.dp).padding(end = 10.dp)
        ) {
            gridLines.reversed().forEach { seconds ->
                Text(
                    text = FocusStats.axisLabel(seconds),
                    color = Color(0xFF4A4A4A),
                    fontSize = 9.sp
                )
            }
            Text("0", color = Color(0xFF4A4A4A), fontSize = 9.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .drawBehind {
                        gridLines.forEach { seconds ->
                            val y = size.height * (1f - seconds.toFloat() / axisMax)
                            drawLine(
                                color = GRID,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f,
                                pathEffect = dash
                            )
                        }
                        // The baseline is solid: it is the floor, not another reading.
                        drawLine(
                            color = Color(0xFF2E2E2E),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 2f
                        )
                    }
            ) {
                week.days.forEach { day ->
                    Bar(
                        day = day,
                        fraction = day.seconds.toFloat() / axisMax,
                        selected = selectedDay == day.dayKey,
                        dimmed = selectedDay != null && selectedDay != day.dayKey,
                        onClick = { onSelect(day.dayKey) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                week.days.forEach { day ->
                    val isToday = FocusStats.isToday(day.dayKey)
                    val chosen = selectedDay == day.dayKey
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable { onSelect(day.dayKey) }
                    ) {
                        Text(
                            text = FocusStats.dayLetter(day.startMs),
                            color = when {
                                chosen || isToday -> Color.White
                                else -> MUTED
                            },
                            fontSize = 11.sp,
                            fontWeight = if (chosen || isToday) FontWeight.Bold
                            else FontWeight.Normal
                        )
                        Spacer(Modifier.height(4.dp))
                        // Today is marked with a dot rather than another colour, so the
                        // highlight can mean "the one you tapped" and nothing else.
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isToday) ACCENT else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(
    day: DayStat,
    fraction: Float,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val grown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "bar"
    )
    val fade by animateFloatAsState(
        targetValue = if (dimmed) 0.35f else 1f,
        animationSpec = tween(220),
        label = "fade"
    )

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick)
    ) {
        if (grown > 0.0001f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // A day with a minute on it still gets a visible sliver, or the chart
                    // would say nothing happened when something did.
                    .fillMaxHeight(grown.coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                    .background(
                        Brush.verticalGradient(
                            if (selected) listOf(Color.White, ACCENT)
                            else listOf(ACCENT.copy(alpha = fade), ACCENT_DEEP.copy(alpha = fade))
                        )
                    )
            )
        } else {
            // An empty day keeps a stub on the baseline so the week still reads as seven.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(Color(0xFF1A1A1A))
            )
        }
    }
}

@Composable
private fun WeekArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0xFF161616) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            icon,
            null,
            tint = if (enabled) Color.White else Color(0xFF2C2C2C),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Text("⏳", fontSize = 44.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Nothing recorded yet",
                color = Color.Gray,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Run a focus session and the time you spend on it shows up here.",
                color = Color(0xFF555555),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
