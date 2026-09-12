package com.example.androidkotlinapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ACCENT = Color(0xFF64B5F6)
private val ACCENT_DEEP = Color(0xFF1E88E5)
private val FIELD = Color(0xFF101010)
private val EDGE = Color(0xFF232323)
private val MUTED = Color(0xFF6E6E6E)

/**
 * Where the account is made or entered.
 *
 * One pair of fields serves both, because they ask for exactly the same two things. Flipping
 * between "create" and "sign in" changes the words and what the button does, and leaves what
 * has already been typed where it is.
 */
@Composable
fun AccountScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Someone returning to a phone that already has an account wants the sign-in wording.
    var creating by remember { mutableStateOf(!LocalAccount.exists(context)) }
    var email by remember { mutableStateOf(LocalAccount.email(context)) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        keyboard?.hide()
        error = null
        busy = true
        scope.launch {
            // A beat of thinking. The work itself is instant, and a form that answers before
            // the finger has left the glass reads as if it did not check anything.
            val result = withContext(Dispatchers.Default) {
                if (creating) LocalAccount.signUp(context, email, password)
                else LocalAccount.signIn(context, email, password)
            }
            delay(350)
            busy = false
            when (result) {
                is AuthResult.Success -> onSignedIn()
                is AuthResult.Failure -> error = result.message
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(56.dp))

        // The same ring the launcher opens on, so the account screen belongs to the app it
        // is the front door of rather than looking borrowed from somewhere else.
        val breathe = rememberInfiniteTransition(label = "breathe")
        val glow by breathe.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                tween(2600, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "glow"
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFF0C0C0C))
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF0F265C).copy(alpha = glow),
                            ACCENT.copy(alpha = glow),
                            Color(0xFF0F265C).copy(alpha = glow)
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Icon(
                Icons.Default.HourglassFull,
                null,
                tint = ACCENT,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(Modifier.height(30.dp))

        Text(
            text = if (creating) "Create your account" else "Welcome back",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (creating) "Your focus history stays on this phone."
            else "Sign in to pick up where you left off.",
            color = MUTED,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        Field(
            value = email,
            onChange = { email = it; error = null },
            placeholder = "you@example.com",
            leading = Icons.Default.MailOutline,
            keyboard = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onImeAction = {},
            enabled = !busy
        )

        Spacer(Modifier.height(14.dp))

        Field(
            value = password,
            onChange = { password = it; error = null },
            placeholder = "Password",
            leading = Icons.Default.Lock,
            keyboard = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            enabled = !busy,
            masked = !showPassword,
            trailing = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = MUTED,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        )

        // The message takes the room whether or not there is one, so nothing below it jumps
        // as errors come and go.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth().height(38.dp).padding(top = 8.dp)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = error.orEmpty(),
                    color = Color(0xFFE57373),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Button(
            onClick = { submit() },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = ACCENT_DEEP,
                disabledContainerColor = Color(0xFF1B2B3A)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(19.dp)
                )
            } else {
                Text(
                    text = if (creating) "CREATE ACCOUNT" else "SIGN IN",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (creating) "Already have an account?" else "No account yet?",
                color = MUTED,
                fontSize = 12.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (creating) "Sign in" else "Create one",
                color = ACCENT,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !busy
                ) {
                    creating = !creating
                    error = null
                }
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = "Kept on this phone. Nothing is uploaded.",
            color = Color(0xFF3E3E3E),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))
    }
}

/** One input row, styled once so both fields match exactly. */
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    leading: androidx.compose.ui.graphics.vector.ImageVector,
    keyboard: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    enabled: Boolean,
    masked: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val edge by animateColorAsState(
        targetValue = if (focused) ACCENT else EDGE,
        animationSpec = tween(180),
        label = "edge"
    )

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder, color = Color(0xFF4A4A4A), fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                leading,
                null,
                tint = if (focused) ACCENT else MUTED,
                modifier = Modifier.size(19.dp)
            )
        },
        trailingIcon = trailing,
        visualTransformation = if (masked) PasswordVisualTransformation()
        else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onImeAction() },
            onNext = { onImeAction() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = MUTED,
            focusedContainerColor = FIELD,
            unfocusedContainerColor = FIELD,
            disabledContainerColor = FIELD,
            focusedBorderColor = edge,
            unfocusedBorderColor = edge,
            disabledBorderColor = EDGE,
            cursorColor = ACCENT
        ),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            // The field colours its own border when it takes focus.
            .onFocusChanged { focused = it.isFocused }
    )
}
