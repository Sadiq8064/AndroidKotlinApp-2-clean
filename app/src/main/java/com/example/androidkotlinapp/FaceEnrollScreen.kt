package com.example.androidkotlinapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val GOLD = Color(0xFFFFC24B)

/**
 * The one-time face enrolment, shown the first time the Alarm app is opened.
 *
 * A live selfie only -- no gallery -- because the whole check rests on this being genuinely the
 * person. The guidance is on screen because at enrolment there is time to read it.
 */
@Composable
fun FaceEnrollScreen(onEnrolled: () -> Unit) {
    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val askCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) askCamera.launch(Manifest.permission.CAMERA)
    }

    val controller = remember { SelfieController() }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(22.dp))
        Text(
            "Set up your face",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "One clear selfie, in good light. Your alarm asks for a matching live one to turn off — so a fingerprint on a sleeping hand won't cut it.",
            color = Color(0xFF8A8A8A),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0C0C0C))
        ) {
            if (hasCamera) {
                SelfiePreview(controller, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Camera access is needed to set up your face.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 30.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { askCamera.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = GOLD)
                    ) {
                        Text("Allow camera", color = Color(0xFF1A1004), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Face the light and keep both eyes open.",
            color = Color(0xFF6E6E6E),
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (saving) return@Button
                saving = true
                controller.take(
                    context,
                    onResult = { bmp ->
                        FaceStore.save(context, bmp)
                        saving = false
                        onEnrolled()
                    },
                    onError = { saving = false }
                )
            },
            enabled = hasCamera && !saving,
            colors = ButtonDefaults.buttonColors(
                containerColor = GOLD,
                disabledContainerColor = Color(0xFF1A1A1A)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (saving) {
                CircularProgressIndicator(
                    color = Color(0xFF1A1004),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    "TAKE SELFIE",
                    color = if (hasCamera) Color(0xFF1A1004) else Color(0xFF555555),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
