package com.example.androidkotlinapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

private val ACCENT = Color(0xFF64B5F6)

/**
 * The origin the player page is served under.
 *
 * It deliberately is not youtube.com: a page claiming to be youtube.com that then embeds a
 * youtube.com player fails the embed's own origin check and comes back as error 152. Any
 * ordinary https origin satisfies it, and localhost is the one the API documents.
 */
private const val PLAYER_ORIGIN = "https://localhost/"

/** Chrome on Android, so the embed treats the WebView as an ordinary mobile browser. */
private const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

/** Speeds offered by the rate button, cycled in order. */
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun clock(totalSeconds: Float): String {
    val t = totalSeconds.toInt().coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

/**
 * The page handed to the WebView.
 *
 * Two things have to line up or YouTube answers with error 152 rather than a video. The page
 * is served from an ordinary https origin -- see [PLAYER_ORIGIN] -- and the embed is told to
 * expect that same origin. And because a WebView does not send the Referer header the embed
 * wants, the iframe is written out by hand carrying `referrerpolicy` rather than left for the
 * IFrame API to create; the API is then attached to that existing element, which keeps play,
 * pause, seek and rate reachable from Kotlin while the referrer policy survives.
 */
private fun playerHtml(videoId: String, startAt: Int): String = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
  html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
  #p{position:absolute;top:0;left:0;width:100%;height:100%;border:0}
</style></head><body>
<iframe id="p"
  src="https://www.youtube.com/embed/$videoId?enablejsapi=1&controls=0&rel=0&playsinline=1&modestbranding=1&iv_load_policy=3&fs=0&disablekb=1&start=$startAt&origin=https://localhost"
  referrerpolicy="strict-origin-when-cross-origin"
  frameborder="0"
  allow="autoplay; encrypted-media; accelerometer; gyroscope; picture-in-picture"
  allowfullscreen></iframe>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player, ticker;
function onYouTubeIframeAPIReady(){
  player = new YT.Player('p', {
    events: {
      onReady: function(e){ e.target.playVideo(); tick(); },
      onStateChange: function(e){ Bridge.onState(e.data); },
      onError: function(e){ Bridge.onError(e.data); }
    }
  });
}
function tick(){
  if (ticker) clearInterval(ticker);
  ticker = setInterval(function(){
    try {
      if (player && player.getCurrentTime) {
        Bridge.onTime(player.getCurrentTime(), player.getDuration());
      }
    } catch (err) {}
  }, 400);
}
function ytPlay(){ try { player.playVideo(); } catch(e){} }
function ytPause(){ try { player.pauseVideo(); } catch(e){} }
function ytSeekBy(d){ try { player.seekTo(player.getCurrentTime() + d, true); } catch(e){} }
function ytSeekTo(t){ try { player.seekTo(t, true); } catch(e){} }
function ytRate(r){ try { player.setPlaybackRate(r); } catch(e){} }
</script></body></html>
""".trimIndent()

/** What the page reports back. Both callbacks arrive off the main thread. */
private class PlayerBridge(
    private val onTime: (Float, Float) -> Unit,
    private val onState: (Int) -> Unit,
    private val onError: (Int) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onTime(position: Double, duration: Double) {
        main.post { onTime(position.toFloat(), duration.toFloat()) }
    }

    @JavascriptInterface
    fun onState(state: Int) {
        main.post { onState(state) }
    }

    @JavascriptInterface
    fun onError(code: Int) {
        main.post { onError(code) }
    }
}

/**
 * What the IFrame API's error codes mean in words the viewer can act on.
 *
 * 101, 150 and 152 all say the same thing: whoever uploaded the video turned embedding off.
 * YouTube enforces that on its own servers, so no embedded player anywhere can show it -- the
 * only honest thing to do is say so rather than leave a black rectangle.
 */
private fun errorMessage(code: Int): String = when (code) {
    101, 150, 152 -> "The uploader has turned off embedding for this video, so it cannot play outside YouTube."
    100 -> "This video has been removed or made private."
    2 -> "That video id is not valid."
    5 -> "The player could not start this video."
    else -> "This video could not be played (error $code)."
}

/**
 * Full screen landscape player for one video.
 *
 * The launcher itself is pinned to portrait, so this rotates the activity on the way in and
 * hands the orientation back on the way out -- a lecture watched in a portrait letterbox is
 * not what anyone means by watching it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    videoId: String,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var position by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0f) }
    var isPlaying by remember { mutableStateOf(true) }
    var speedIndex by remember { mutableStateOf(SPEEDS.indexOf(1f)) }
    var controlsVisible by remember { mutableStateOf(true) }
    var seekFlash by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var errorCode by remember { mutableStateOf<Int?>(null) }
    val startAt = remember(videoId) { YouTubeProgress.resumeAt(context, videoId) }

    fun js(call: String) {
        webView?.evaluateJavascript(call, null)
    }

    // Landscape and edge to edge for as long as the player is on screen; both handed back
    // afterwards. A clock and a battery meter over a lecture are exactly the sort of thing
    // this launcher exists to keep off the screen.
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val insets = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
        insets?.apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null) WindowCompat.setDecorFitsSystemWindows(window, true)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Progress is written as it goes, so closing the app mid-lecture still remembers the spot.
    LaunchedEffect(videoId) {
        while (true) {
            delay(3000)
            if (duration > 0f) YouTubeProgress.record(context, videoId, position, duration)
        }
    }

    DisposableEffect(videoId) {
        onDispose {
            if (duration > 0f) YouTubeProgress.record(context, videoId, position, duration)
            webView?.destroy()
        }
    }

    // The controls get out of the way on their own while something is playing.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekFlash) {
        if (seekFlash != null) {
            delay(650)
            seekFlash = null
        }
    }

    BackHandler(enabled = true) { onBack() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    // YouTube refuses to play into a client that announces itself as a
                    // WebView -- the embed comes back as error 152 rather than a video. A
                    // plain Chrome-on-Android string is what the player expects to see.
                    settings.userAgentString = CHROME_UA
                    // The embed page carries links out of itself -- "Watch on YouTube" on an
                    // error, the channel name on the end card -- and following one launches
                    // the YouTube app, which is the single thing this screen exists to avoid.
                    // Nothing is allowed to navigate: the player document is the whole world.
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = true

                        @Deprecated("Kept for API levels below 24")
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?
                        ): Boolean = true
                    }
                    // A chrome client with no fullscreen callbacks: the player is already
                    // full screen and landscape, so YouTube's own fullscreen must stay out.
                    webChromeClient = WebChromeClient()
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addJavascriptInterface(
                        PlayerBridge(
                            onTime = { p, d ->
                                position = p
                                duration = d
                            },
                            onState = { s ->
                                // 1 playing, 2 paused, 0 ended.
                                isPlaying = s == 1
                                if (s == 0 && duration > 0f) {
                                    YouTubeProgress.record(context, videoId, duration, duration)
                                }
                            },
                            onError = { code -> errorCode = code }
                        ),
                        "Bridge"
                    )
                    loadDataWithBaseURL(
                        PLAYER_ORIGIN,
                        playerHtml(videoId, startAt),
                        "text/html",
                        "utf-8",
                        null
                    )
                    webView = this
                }
            }
        )

        // Gesture layer: tap anywhere for the controls, double tap either side to jump.
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                js("ytSeekBy(-10)")
                                seekFlash = "-10s"
                            }
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                js("ytSeekBy(10)")
                                seekFlash = "+10s"
                            }
                        )
                    }
            )
        }

        if (!isPlaying && errorCode == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.97f))
            )
        }

        errorCode?.let { code ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .padding(horizontal = 48.dp)
            ) {
                Text("Cannot play here", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    errorMessage(code),
                    color = Color.Gray,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("BACK", color = Color.White, fontSize = 13.sp) }
            }
        }

        seekFlash?.let { label ->
            Box(
                modifier = Modifier
                    .align(if (label.startsWith("-")) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "${SPEEDS[speedIndex]}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = {
                                        speedIndex = (speedIndex + 1) % SPEEDS.size
                                        js("ytRate(${SPEEDS[speedIndex]})")
                                    })
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                if (isPlaying) js("ytPause()") else js("ytPlay()")
                                isPlaying = !isPlaying
                                controlsVisible = true
                            })
                        }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Slider(
                        value = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { f ->
                            if (duration > 0f) {
                                position = f * duration
                                js("ytSeekTo(${f * duration})")
                                controlsVisible = true
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = ACCENT,
                            activeTrackColor = ACCENT,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(clock(position), color = Color.White, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(clock(duration), color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
