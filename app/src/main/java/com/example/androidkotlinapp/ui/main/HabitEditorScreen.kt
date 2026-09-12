package com.example.androidkotlinapp.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidkotlinapp.HabitRecord

/**
 * Full screen habit creation / rename.
 *
 * Icon and name sit on one line: the icon is a compact tap target, the name takes the rest of
 * the width, and the two read as the single thing they produce rather than as two form
 * sections stacked on top of each other.
 */
@Composable
fun HabitEditorScreen(
    existing: HabitRecord?,
    onCancel: () -> Unit,
    onSave: (emoji: String, text: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember(existing) { mutableStateOf(existing?.text ?: "") }
    var emoji by remember(existing) { mutableStateOf(existing?.emoji ?: "") }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val canSave = title.trim().isNotEmpty()

    BackHandler { onCancel() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HabitTheme.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = HabitTheme.TextPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onCancel() }
            )
            Text(
                text = if (existing != null) "Edit habit" else "New habit",
                color = HabitTheme.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "SAVE",
                color = if (canSave) HabitTheme.TextPrimary else HabitTheme.TextDisabled,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = canSave) {
                    onSave(emoji.ifBlank { "🎯" }, title.trim())
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(HabitTheme.Surface, RoundedCornerShape(18.dp))
                    .border(
                        width = 1.dp,
                        color = if (emoji.isBlank()) HabitTheme.Border else HabitTheme.BorderStrong,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { showEmojiPicker = true },
                contentAlignment = Alignment.Center
            ) {
                if (emoji.isBlank()) {
                    Text(text = "🙂", fontSize = 24.sp, color = HabitTheme.TextDisabled)
                } else {
                    Text(text = emoji, fontSize = 30.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text("Habit name", color = HabitTheme.TextDisabled, fontSize = 18.sp)
                },
                singleLine = false,
                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = HabitTheme.BorderStrong,
                    unfocusedIndicatorColor = HabitTheme.Border,
                    focusedTextColor = HabitTheme.TextPrimary,
                    unfocusedTextColor = HabitTheme.TextPrimary,
                    cursorColor = HabitTheme.TextPrimary
                )
            )
        }
    }

    if (showEmojiPicker) {
        EmojiPickerSheet(
            selected = emoji,
            onDismiss = { showEmojiPicker = false },
            onPick = {
                emoji = it
                showEmojiPicker = false
            }
        )
    }
}

/**
 * Category-grouped emoji picker.
 *
 * Opens fully expanded at a fixed height so it never needs dragging to be usable, and the
 * grid scrolls inside that frame instead of the sheet growing to fit every category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
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
                .height(460.dp)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Choose an icon",
                color = HabitTheme.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EmojiCatalog.categories.forEach { category ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = category.name.uppercase(),
                            color = HabitTheme.TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(category.emojis) { choice ->
                        val isSelected = choice == selected
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(
                                    if (isSelected) HabitTheme.SurfaceRaised else Color.Transparent,
                                    CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) HabitTheme.TextPrimary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onPick(choice) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = choice, fontSize = 21.sp)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}
