package com.example.androidkotlinapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val A_GOLD = Color(0xFFFFC24B)
private val A_CARD = Color(0xFF0E0E0E)
private val A_EDGE = Color(0xFF1C1C1C)
private val A_MUTED = Color(0xFF7A7A7A)
private val A_FAINT = Color(0xFF4A4A4A)
private val A_DANGER = Color(0xFFE57373)

/**
 * The Alarm app: what is set, and what it will do.
 *
 * Built to match Target and Resource -- same card shape, same header, same quiet empty state --
 * so the launcher's own apps read as one family rather than as four things that happen to be
 * installed together.
 */
@Composable
fun AlarmScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // The live selfie is set up once, before the app can be used. A reinstall silently pulls
    // the enrolled face back from the server first.
    var enrolled by remember { mutableStateOf(FaceStore.isEnrolled(context)) }
    LaunchedEffect(Unit) {
        if (!enrolled) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                FaceStore.restoreIfMissing(context)
            }
            enrolled = FaceStore.isEnrolled(context)
        }
    }
    if (!enrolled) {
        FaceEnrollScreen(onEnrolled = { enrolled = true })
        return
    }

    var reload by remember { mutableStateOf(0) }
    val alarms = remember(reload) { Alarms.all(context) }

    var editing by remember { mutableStateOf<Alarm?>(null) }
    var composing by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<String?>(null) }

    if (composing || editing != null) {
        // A brand-new alarm is the special one when none exists yet. Editing keeps whatever the
        // alarm already is.
        val makingSpecial = editing?.special ?: Alarms.hasNoAlarms(context)
        AlarmEditor(
            existing = editing,
            makingSpecial = makingSpecial,
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
                AlarmGlyph(size = 24.dp)
                Spacer(Modifier.width(11.dp))
                Text(
                    "Alarm",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!AlarmScheduler.canScheduleExact(context)) {
                    // Said plainly rather than left to fail silently at 6am.
                    Text(
                        "Exact alarms off",
                        color = A_DANGER,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (alarms.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 44.dp)
                    ) {
                        AlarmGlyph(size = 54.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No alarms set",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap + to set one. It will ring even if the phone is asleep.",
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
                    items(alarms, key = { it.id }) { a ->
                        AlarmRow(
                            alarm = a,
                            onToggle = { wanted ->
                                if (!Alarms.setEnabled(context, a.id, wanted)) {
                                    refusal = if (a.special)
                                        "The habit alarm stays on until it has run its ${a.durationDays} days. ${a.daysRemaining} to go."
                                    else "This alarm rings in under an hour and is locked until it does. You set it for a reason."
                                }
                                reload++
                            },
                            onEdit = { editing = a }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { composing = true },
            containerColor = A_GOLD,
            contentColor = Color(0xFF1A1004),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, "New alarm")
        }
    }

    val message = refusal
    if (message != null) {
        AlertDialog(
            onDismissRequest = { refusal = null },
            containerColor = Color(0xFF121212),
            shape = RoundedCornerShape(22.dp),
            title = {
                Text("Locked in \u23F3", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            },
            text = { Text(message, color = A_MUTED, fontSize = 13.sp, lineHeight = 19.sp) },
            confirmButton = {
                TextButton(onClick = { refusal = null }) {
                    Text("OK", color = A_GOLD, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------- the card

@Composable
private fun AlarmRow(alarm: Alarm, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val live = alarm.enabled

    // One subtitle line: the schedule, then the habit countdown or any status, all beside each
    // other so the card stays two clean rows -- time on the left, switch on the right.
    val subtitle = buildString {
        append(alarm.scheduleText())
        when {
            alarm.special && !alarm.specialFinished -> append("  ·  🔥 ${alarm.daysRemaining} days left")
            alarm.special -> append("  ·  🔥 done")
            alarm.faceUnlock -> append("  ·  Face unlock")
        }
        if (alarm.isLocked()) append("  ·  Locked")
    }

    // The habit alarm wears a thicker, brighter gold edge so it reads as the special one at a
    // glance; ordinary alarms keep the quiet hairline.
    val borderColor = when {
        alarm.special -> A_GOLD.copy(alpha = if (live) 0.85f else 0.5f)
        live -> A_GOLD.copy(alpha = 0.20f)
        else -> A_EDGE
    }
    val borderWidth = if (alarm.special) 2.dp else 1.dp

    Surface(
        color = A_CARD,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier.fillMaxWidth().clickable { onEdit() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        alarm.clockText(),
                        color = if (live) Color.White else A_MUTED,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                        lineHeight = 42.sp
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        alarm.meridiem(),
                        color = if (live) A_GOLD else A_FAINT,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    color = if (live) A_MUTED else A_FAINT,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Switch(
                checked = live,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF1A1004),
                    checkedTrackColor = A_GOLD,
                    uncheckedThumbColor = A_MUTED,
                    uncheckedTrackColor = Color(0xFF161616),
                    uncheckedBorderColor = A_EDGE
                )
            )
        }
    }
}

// ---------------------------------------------------------------------- the editor

@Composable
private fun AlarmEditor(
    existing: Alarm?,
    makingSpecial: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // Held in 12-hour form because that is what the picker shows; converted on save.
    var hour12 by remember {
        mutableStateOf(
            existing?.let { if (it.hour % 12 == 0) 12 else it.hour % 12 } ?: 7
        )
    }
    var minute by remember { mutableStateOf(existing?.minute ?: 0) }
    var isPm by remember { mutableStateOf((existing?.hour ?: 7) >= 12) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var days by remember { mutableStateOf(existing?.days ?: emptySet()) }
    var durationDays by remember { mutableStateOf(existing?.durationDays?.takeIf { it > 0 } ?: 7) }
    var faceUnlock by remember { mutableStateOf(existing?.faceUnlock ?: false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var deleteRefusal by remember { mutableStateOf<String?>(null) }

    // The habit alarm's time is fixed once set -- only its label can change. Always AM.
    val timeLocked = makingSpecial && existing != null
    if (makingSpecial) isPm = false

    BackHandler(enabled = true) { onCancel() }

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
                Icon(Icons.Default.ArrowBack, "Cancel", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Text(
                when {
                    makingSpecial && existing == null -> "Your habit alarm"
                    makingSpecial -> "Edit habit alarm"
                    existing == null -> "New alarm"
                    else -> "Edit alarm"
                },
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (makingSpecial)
                    "The one that builds the habit. Morning only, runs for a set number of days, and cannot be deleted until it has."
                else "It will ring at this exact minute, awake or asleep.",
                color = A_MUTED,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(28.dp))

            // Steppers rather than a wheel: a wheel needs a fling to land on a number, and at
            // this size that is fiddly. Up and down always hit exactly what they say.
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                NumberSpinner(
                    value = hour12,
                    format = { it.toString() },
                    onUp = { hour12 = if (hour12 == 12) 1 else hour12 + 1 },
                    onDown = { hour12 = if (hour12 == 1) 12 else hour12 - 1 }
                )
                Text(
                    ":",
                    color = A_FAINT,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                NumberSpinner(
                    value = minute,
                    format = { "%02d".format(it) },
                    onUp = { minute = (minute + 1) % 60 },
                    onDown = { minute = if (minute == 0) 59 else minute - 1 }
                )

                Spacer(Modifier.width(16.dp))

                if (makingSpecial) {
                    // No PM for a habit alarm -- it is a morning commitment by definition.
                    MeridiemChip("AM", true) { }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MeridiemChip("AM", !isPm) { isPm = false }
                        MeridiemChip("PM", isPm) { isPm = true }
                    }
                }
            }

            if (timeLocked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "The habit alarm's time is locked. You can still change the label.",
                    color = A_FAINT,
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(30.dp))

            if (makingSpecial) {
                Text(
                    "RUN FOR",
                    color = A_MUTED,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 21, 30).forEach { d ->
                        DayChip(
                            label = "$d days",
                            selected = durationDays == d,
                            modifier = Modifier.weight(1f)
                        ) { if (!timeLocked) durationDays = d }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "It rings every morning for $durationDays days and cannot be turned off or deleted until then. Each morning takes three face checks to dismiss.",
                    color = A_FAINT,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            } else {
                Text(
                    "REPEAT",
                    color = A_MUTED,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Alarm.DAY_ORDER.forEach { d ->
                        DayChip(
                            label = Alarm.shortDayName(d),
                            selected = days.contains(d),
                            modifier = Modifier.weight(1f)
                        ) {
                            days = if (days.contains(d)) days - d else days + d
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (days.isEmpty()) "Rings once, then switches itself off."
                    else "Rings every ${Alarm.DAY_ORDER.filter { days.contains(it) }
                        .joinToString(", ") { Alarm.shortDayName(it) }}.",
                    color = A_FAINT,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(Modifier.height(22.dp))

                // Face unlock: the same three-check dismissal the habit alarm always uses,
                // offered to any other alarm that wants teeth.
                Surface(
                    color = A_CARD,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (faceUnlock) A_GOLD.copy(alpha = 0.4f) else A_EDGE),
                    modifier = Modifier.fillMaxWidth().clickable { faceUnlock = !faceUnlock }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Face unlock to stop",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Three face checks, five minutes apart, before it turns off.",
                                color = A_MUTED,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Switch(
                            checked = faceUnlock,
                            onCheckedChange = { faceUnlock = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1A1004),
                                checkedTrackColor = A_GOLD,
                                uncheckedThumbColor = A_MUTED,
                                uncheckedTrackColor = Color(0xFF161616),
                                uncheckedBorderColor = A_EDGE
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                "LABEL",
                color = A_MUTED,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(9.dp))
            Surface(
                color = A_CARD,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (makingSpecial && label.isBlank()) A_GOLD.copy(alpha = 0.4f) else A_EDGE),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.height(54.dp).padding(horizontal = 14.dp)
                ) {
                    if (label.isEmpty()) {
                        Text(
                            if (makingSpecial) "What are you getting up for? (required)" else "Gym",
                            color = Color(0xFF4A4A4A),
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = label,
                        onValueChange = { label = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(A_GOLD),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
        }

        // Delete lives here now, above Save, so the list card stays clean with no menu.
        if (existing != null) {
            TextButton(
                onClick = { confirmingDelete = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            ) {
                Text(
                    if (existing.special) "Delete habit alarm" else "Delete alarm",
                    color = A_DANGER,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        val canSave = !makingSpecial || label.isNotBlank()
        Button(
            onClick = {
                if (!canSave) return@Button
                // A locked habit alarm keeps its original time; otherwise take the picker's.
                val hour24 = if (timeLocked) existing!!.hour else when {
                    isPm && hour12 != 12 -> hour12 + 12
                    !isPm && hour12 == 12 -> 0
                    else -> hour12
                }
                val min = if (timeLocked) existing!!.minute else minute
                Alarms.save(
                    context,
                    Alarm(
                        id = existing?.id ?: 0L,
                        hour = hour24,
                        minute = min,
                        label = label.trim(),
                        // A habit alarm rings daily; its "run for" is the duration, not a day set.
                        days = if (makingSpecial) emptySet() else days,
                        enabled = true,
                        special = makingSpecial,
                        durationDays = if (makingSpecial) durationDays else 0,
                        // Keep the original start date when editing, so the countdown is honest.
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        faceUnlock = if (makingSpecial) true else faceUnlock
                    )
                )
                onDone()
            },
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = A_GOLD,
                disabledContainerColor = Color(0xFF1A1A1A)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 22.dp)
        ) {
            Text(
                if (existing == null) "SET ALARM" else "SAVE",
                color = if (canSave) Color(0xFF1A1004) else Color(0xFF555555),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    if (confirmingDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = Color(0xFF121212),
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    if (existing.special) "Delete the habit alarm?" else "Delete this alarm?",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    when {
                        existing.special && !existing.specialFinished ->
                            "Locked for ${existing.daysRemaining} more day" +
                                (if (existing.daysRemaining == 1) "" else "s") +
                                ". The habit alarm cannot be deleted until it has run its course."
                        existing.special ->
                            "This deletes the habit alarm AND every other alarm with it. The next alarm you set becomes the new habit alarm."
                        else -> "${existing.clockText()} ${existing.meridiem()} will no longer ring."
                    },
                    color = A_MUTED,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                if (!(existing.special && !existing.specialFinished)) {
                    TextButton(onClick = {
                        val ok = if (existing.special) Alarms.deleteSpecialAndAll(context)
                                 else Alarms.remove(context, existing.id)
                        confirmingDelete = false
                        if (ok) onDone()
                        else deleteRefusal = "This alarm rings in under an hour and cannot be deleted until it does."
                    }) {
                        Text("Delete", color = A_DANGER, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("Cancel", color = Color.White, fontSize = 13.sp)
                }
            }
        )
    }

    val dr = deleteRefusal
    if (dr != null) {
        AlertDialog(
            onDismissRequest = { deleteRefusal = null },
            containerColor = Color(0xFF121212),
            shape = RoundedCornerShape(22.dp),
            title = { Text("Locked in \u23F3", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = { Text(dr, color = A_MUTED, fontSize = 13.sp, lineHeight = 19.sp) },
            confirmButton = {
                TextButton(onClick = { deleteRefusal = null }) {
                    Text("OK", color = A_GOLD, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun NumberSpinner(
    value: Int,
    format: (Int) -> String,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.KeyboardArrowUp,
            "Up",
            tint = A_MUTED,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onUp
                )
        )
        Text(
            format(value),
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-2).sp
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            "Down",
            tint = A_MUTED,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDown
                )
        )
    }
}

@Composable
private fun MeridiemChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) A_GOLD.copy(alpha = 0.18f) else Color(0xFF101010))
            .border(
                1.dp,
                if (selected) A_GOLD.copy(alpha = 0.6f) else A_EDGE,
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) A_GOLD else A_MUTED,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) A_GOLD.copy(alpha = 0.18f) else Color(0xFF101010))
            .border(
                1.dp,
                if (selected) A_GOLD.copy(alpha = 0.6f) else A_EDGE,
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 11.dp)
    ) {
        Text(
            label,
            color = if (selected) A_GOLD else A_MUTED,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
