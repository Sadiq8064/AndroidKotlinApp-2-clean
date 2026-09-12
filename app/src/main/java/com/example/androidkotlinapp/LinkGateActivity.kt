package com.example.androidkotlinapp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val ACCENT = Color(0xFF64B5F6)
private val DENY = Color(0xFFE57373)

/** Where the gate has got to for the link in hand. */
private sealed class GateState {
    data class Checking(val stage: String) : GateState()
    data class Blocked(val host: String) : GateState()
}

/**
 * Stands between a tapped link and the browser.
 *
 * Every http link on the device is handed here first. Known hosts are waved through or turned
 * away on the spot; an unfamiliar one is loaded out of sight, read, and judged before the user
 * ever sees it. Allowed links go on to Chrome; refused ones end here and the user lands back
 * in whatever app they tapped from.
 */
class LinkGateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val link = intent?.dataString
        if (link.isNullOrBlank()) {
            finish()
            return
        }

        // Off session, the gate has no business standing in the way. The link is handed
        // straight to whatever would have opened it, and nothing is checked, judged or
        // remembered -- this is a focus tool, not a filter the user lives under all day.
        if (!FocusService.isRunning) {
            openOutside(link)
            finish()
            return
        }

        // A YouTube link never reaches a browser. It belongs to the in-app library, so the
        // add flow opens with it already filled in and the user decides there.
        if (LinkGate.isYouTube(link)) {
            MainActivity.pendingYouTubeLink = link
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
            return
        }

        when (LinkGate.known(this, link)) {
            LinkVerdict.ALLOW -> {
                openInBrowser(link)
                finish()
                return
            }
            LinkVerdict.BLOCK -> {
                notifyBlocked(LinkGate.host(link) ?: link)
                showBlockedThenLeave(link)
                return
            }
            LinkVerdict.UNKNOWN -> Unit
        }

        setContent { GateScreen(link) }
    }

    /** Only reached for a host already on file as blocked, so nothing needs checking. */
    private fun showBlockedThenLeave(link: String) {
        setContent {
            val host = remember { LinkGate.host(link) ?: link }
            LaunchedEffect(Unit) {
                delay(1800)
                finish()
            }
            BlockedPanel(host)
        }
    }

    @Composable
    private fun GateScreen(link: String) {
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf<GateState>(GateState.Checking("Checking the site")) }

        LaunchedEffect(link) {
            state = GateState.Checking("Opening the page quietly")
            val page = readPage(link)

            state = GateState.Checking("Reading what it is")
            val verdict = withContext(Dispatchers.IO) {
                if (page == null) {
                    // A page that will not load or yields nothing is not evidence of harm.
                    // Refusing it outright would block half the forms the user needs.
                    LinkVerdict.UNKNOWN
                } else {
                    LinkGate.classify(page.first, page.second, link)
                }
            }

            when (verdict) {
                LinkVerdict.ALLOW -> {
                    LinkGate.remember(this@LinkGateActivity, link, LinkVerdict.ALLOW)
                    openInBrowser(link)
                    finish()
                }
                LinkVerdict.BLOCK -> {
                    LinkGate.remember(this@LinkGateActivity, link, LinkVerdict.BLOCK)
                    notifyBlocked(LinkGate.host(link) ?: link)
                    state = GateState.Blocked(LinkGate.host(link) ?: link)
                    delay(2000)
                    finish()
                }
                LinkVerdict.UNKNOWN -> {
                    // Undecided is not remembered: the next tap gets a fresh look rather than
                    // a guess frozen into the record.
                    openInBrowser(link)
                    finish()
                }
            }
        }

        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "gate"
        ) { s ->
            when (s) {
                is GateState.Checking -> CheckingPanel(s.stage, LinkGate.host(link) ?: link)
                is GateState.Blocked -> BlockedPanel(s.host)
            }
        }
    }

    /**
     * Loads the link out of sight and reads its title and visible text.
     *
     * A plain HTTP fetch would be faster but returns an empty shell for anything that renders
     * itself in the browser, and a wrong read is worse than a slow one. The page therefore
     * gets a real WebView and a moment to settle, capped so a slow site cannot hang the gate.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun readPage(link: String): Pair<String, String>? = suspendCoroutine { cont ->
        var settled = false
        // The WebView is never attached to a window, and View.postDelayed on an unattached
        // view is queued until attach that never comes. Every timer here therefore runs on a
        // main-looper handler instead, or the gate would wait for a page that already loaded.
        val handler = Handler(Looper.getMainLooper())
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.blockNetworkImage = true          // text is all this needs
        web.settings.loadsImagesAutomatically = false

        fun deliver(result: Pair<String, String>?) {
            if (settled) return
            settled = true
            web.stopLoading()
            web.destroy()
            cont.resume(result)
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (view == null || settled) return
                handler.postDelayed({
                    if (settled) return@postDelayed
                    view.evaluateJavascript(
                        "(function(){return document.title + '\\u0000' + " +
                            "(document.body ? document.body.innerText : '');})();"
                    ) { raw ->
                        val cleaned = raw
                            ?.trim('"')
                            ?.replace("\\n", " ")
                            ?.replace("\\\"", "\"")
                            ?.replace("\\u0000", " ")
                            ?: ""
                        val parts = cleaned.split(' ', limit = 2)
                        val title = parts.getOrNull(0).orEmpty()
                        val text = parts.getOrNull(1).orEmpty()
                        deliver(if (title.isBlank() && text.isBlank()) null else title to text)
                    }
                }, 2200)
            }
        }

        // A page that has not spoken up in eight seconds is not going to. The gate has to
        // stay quicker than the user's patience, so an unresponsive site is simply waved on
        // rather than left spinning.
        handler.postDelayed({ deliver(null) }, 8_000)
        web.loadUrl(link)
    }

    /**
     * Hands a link to the app that would have taken it if this activity did not exist: the
     * YouTube app for a YouTube link, the browser for anything else.
     */
    private fun openOutside(link: String) {
        if (LinkGate.isYouTube(link)) {
            val yt = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.youtube")
            }
            try {
                startActivity(yt)
                return
            } catch (e: Exception) {
                // No YouTube app installed; the browser will do.
            }
        }
        openInBrowser(link)
    }

    private fun openInBrowser(link: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Named explicitly, or the chooser would offer this very activity again.
            setPackage("com.android.chrome")
        }
        BlockerActivity.isLaunchingWhitelistedApp = true
        justAllowedUrl = link
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // No Chrome on the device: fall back to whatever else can open it, minus us.
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(link))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun notifyBlocked(host: String) = LinkGate.notifyBlocked(this, host)

    companion object {
        /** Set while the gate is opening a URL, so the Chrome watcher lets that one through. */
        @Volatile
        var justAllowedUrl: String? = null
    }
}

@Composable
private fun CheckingPanel(stage: String, host: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 36.dp)
    ) {
        val pulse = rememberInfiniteTransition(label = "pulse")
        val ring by pulse.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                tween(900, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "ring"
        )
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(ring)
                    .clip(CircleShape)
                    .background(ACCENT.copy(alpha = 0.10f))
            )
            CircularProgressIndicator(
                color = ACCENT,
                strokeWidth = 3.dp,
                modifier = Modifier.size(74.dp)
            )
            Icon(Icons.Default.Public, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(30.dp))
        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
            label = "stage"
        ) { s ->
            Text("$s...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Text(host, color = ACCENT, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Checked once, then remembered.",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BlockedPanel(host: String) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(DENY.copy(alpha = 0.13f))
        ) {
            Icon(
                Icons.Default.Block,
                null,
                tint = DENY,
                modifier = Modifier.size((46 * scale.value).dp)
            )
        }
        Spacer(Modifier.height(22.dp))
        Text("Not allowed", color = DENY, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(host, color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "This one is a distraction, so it stays shut.",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
