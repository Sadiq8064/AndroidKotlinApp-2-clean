package com.example.androidkotlinapp

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.io.File
import java.security.MessageDigest
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val ACCENT = Color(0xFF64B5F6)
// Named for what it is rather than where it came from. The whole screen now takes the colour
// of the bulb on its tile.
private val YT_RED = BULB_GOLD
private val OK_GREEN = Color(0xFF66BB6A)
private val CARD = Color(0xFF101010)
private val EDGE = Color(0xFF1E1E1E)

/** Decoded thumbnails, so a scroll back up the list costs nothing at all. */
private val thumbCache = mutableMapOf<String, ImageBitmap?>()

/**
 * Where a fetched thumbnail is kept between runs.
 *
 * The in-memory map alone died with the process, so every cold start re-downloaded the whole
 * library. On disk they are fetched once and never again -- which matters most on the metered
 * connection this app is likeliest to be opened on.
 */
private fun thumbFile(context: Context, url: String): File {
    val dir = File(context.cacheDir, "thumbs").apply { if (!exists()) mkdirs() }
    val name = MessageDigest.getInstance("SHA-1")
        .digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return File(dir, "$name.img")
}

@Composable
private fun RemoteThumb(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf(thumbCache[url]) }
    LaunchedEffect(url) {
        if (url.isBlank() || thumbCache.containsKey(url)) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            val cached = thumbFile(context, url)
            // Disk first. Only a thumbnail never seen before costs a request.
            if (cached.exists() && cached.length() > 0) {
                try {
                    BitmapFactory.decodeFile(cached.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else {
                try {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 15000
                        setRequestProperty("User-Agent", "FocusLauncher/1.0")
                    }
                    val bytes = conn.inputStream.use { it.readBytes() }
                    // Written before decoding, so a decode failure does not cost the download
                    // again next time -- and a partial write is caught by the length check.
                    try {
                        cached.writeBytes(bytes)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
        thumbCache[url] = loaded
        bitmap = loaded
    }

    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier.background(Color(0xFF181818)))
    }
}

/** A thin bar under a card, drawn only once there is something to show. */
@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    if (fraction <= 0.001f) return
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(listOf(YT_RED, Color(0xFFFF6B00)))
                )
        )
    }
}

private fun percent(f: Float) = "${(f * 100).toInt()}%"

/**
 * The library. Everything the user has had approved, watched inside this app -- the YouTube
 * app is never opened from here.
 */
@Composable
fun YouTubeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf(YouTubeAllowlist.entries(context)) }
    var openEntry by remember { mutableStateOf<AllowedEntry?>(null) }
    var playing by remember { mutableStateOf<Pair<String, String>?>(null) }
    // A link the gate handed over opens the add flow straight away, already filled in.
    var incomingLink by remember { mutableStateOf(MainActivity.pendingYouTubeLink) }
    var showAdd by remember { mutableStateOf(incomingLink != null) }
    var pendingRemoval by remember { mutableStateOf<AllowedEntry?>(null) }

    var links by remember { mutableStateOf(ResourceLinks.all(context)) }
    var linkToRemove by remember { mutableStateOf<ResourceLink?>(null) }

    // Collections, and which one is open. Null means the shelf of folders itself.
    var collectionsTick by remember { mutableStateOf(0) }
    val collections = remember(collectionsTick, entries, links) { Collections.all(context) }
    val unfiledBucket = remember(collectionsTick, entries, links) { Collections.unfiled(context) }
    var openCollection by remember { mutableStateOf<Collection?>(null) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(1) }              // 1 videos, 2 links
    var showNewCollection by remember { mutableStateOf(false) }
    var collectionToDelete by remember { mutableStateOf<Collection?>(null) }

    // Progress changes while a video plays, so the lists re-read it whenever one closes.
    var progressTick by remember { mutableStateOf(0) }

    // ---------------------------------------------------------------- player
    val nowPlaying = playing
    if (nowPlaying != null) {
        YouTubePlayer(
            videoId = nowPlaying.first,
            title = nowPlaying.second,
            onBack = {
                playing = null
                progressTick++
            }
        )
        return
    }

    // ---------------------------------------------------------------- add a link
    if (showNewCollection) {
        NewCollectionScreen(
            onDone = { name ->
                showNewCollection = false
                if (name != null && name.isNotBlank()) {
                    val id = Collections.create(context, name)
                    collectionsTick++
                    // Asked for after the folder is on screen, so a slow model never delays
                    // the thing the user actually pressed the button for.
                    scope.launch {
                        val picked = withContext(Dispatchers.IO) {
                            Collections.suggestEmoji(name)
                        }
                        if (picked != null) {
                            Collections.setEmoji(context, id, picked)
                            collectionsTick++
                        }
                    }
                }
            }
        )
        return
    }

    if (showAdd) {
        SmartAddScreen(
            initialLink = incomingLink.orEmpty(),
            collectionId = openCollection?.id ?: Collections.UNFILED,
            collectionName = openCollection?.name ?: "Unfiled",
            onDone = { added ->
                showAdd = false
                incomingLink = null
                MainActivity.pendingYouTubeLink = null
                if (added) {
                    entries = YouTubeAllowlist.entries(context)
                    links = ResourceLinks.all(context)
                    collectionsTick++
                }
            }
        )
        return
    }

    // ---------------------------------------------------------------- inside a playlist
    val opened = openEntry
    if (opened != null && opened.kind == "playlist") {
        BackHandler(enabled = true) { openEntry = null }

        val done = remember(progressTick, opened) { YouTubeProgress.completedCount(context, opened) }
        val frac = remember(progressTick, opened) { YouTubeProgress.fraction(context, opened) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 10.dp)) {
                Text(
                    opened.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(opened.channel, color = ACCENT, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                ProgressBar(frac, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    "${percent(frac)} complete  ·  $done of ${opened.videos.size} videos",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(opened.videos) { v ->
                    val vf = remember(progressTick, v.id) { YouTubeProgress.fraction(context, v.id) }
                    Surface(
                        color = CARD,
                        border = BorderStroke(1.dp, EDGE),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { playing = v.id to v.title })
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Box {
                                    RemoteThumb(
                                        v.thumbnail,
                                        Modifier
                                            .size(width = 112.dp, height = 63.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    if (v.lengthText.isNotBlank()) {
                                        Text(
                                            v.lengthText,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Black.copy(alpha = 0.75f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        v.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (vf > 0.001f) {
                                        Spacer(Modifier.height(5.dp))
                                        Text(
                                            if (vf >= 0.95f) "Watched" else "${percent(vf)} watched",
                                            color = if (vf >= 0.95f) OK_GREEN else Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                            ProgressBar(vf, Modifier.fillMaxWidth().padding(horizontal = 10.dp))
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        return
    }

    // ---------------------------------------------------------------- the shelf
    BackHandler(enabled = true) {
        if (openCollection != null) {
            openCollection = null
            query = ""
            kind = 1
        } else onBack()
    }

    val shownCollection = openCollection
    val needle = query.trim().lowercase()

    // Newest first, so what was just added is where the eye lands. Videos saved before this
    // field existed carry a zero and settle at the bottom rather than jumping to the top.
    val filedVideos = entries
        .filter { it.collectionId == (shownCollection?.id ?: "") }
        .filter {
            needle.isBlank() ||
                it.title.lowercase().contains(needle) ||
                it.channel.lowercase().contains(needle)
        }
        .sortedByDescending { it.addedAt }

    val filedLinks = links
        .filter { it.collectionId == (shownCollection?.id ?: "") }
        .filter {
            needle.isBlank() ||
                it.title.lowercase().contains(needle) ||
                it.about.lowercase().contains(needle) ||
                it.host.lowercase().contains(needle)
        }
        .sortedByDescending { it.addedAt }

    val shownVideos = if (kind == 2) emptyList() else filedVideos
    val shownLinks = if (kind == 1) emptyList() else filedLinks

    // On the shelf, a search reaches inside the folders too: someone looking for a title does
    // not know or care which collection they filed it under.
    val matchingCollections = if (needle.isBlank()) collections else collections.filter { c ->
        c.name.lowercase().contains(needle) ||
            entries.any { it.collectionId == c.id && it.title.lowercase().contains(needle) } ||
            links.any {
                it.collectionId == c.id &&
                    (it.title.lowercase().contains(needle) || it.host.lowercase().contains(needle))
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
            ) {
                if (shownCollection != null) {
                    Icon(
                        Icons.Default.ArrowBack,
                        "Back to collections",
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { openCollection = null }
                    )
                    Spacer(Modifier.width(14.dp))
                    if (shownCollection.emoji.isNotBlank()) {
                        Text(shownCollection.emoji, fontSize = 19.sp)
                        Spacer(Modifier.width(9.dp))
                    }
                } else {
                    BulbGlyph(size = 24.dp)
                    Spacer(Modifier.width(9.dp))
                }
                Text(
                    shownCollection?.name ?: "Resource",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (shownCollection != null && shownCollection.id.isNotBlank()) {
                    Icon(
                        Icons.Default.MoreVert,
                        "Collection options",
                        tint = Color(0xFF6E6E6E),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { collectionToDelete = shownCollection }
                    )
                }
            }

            SearchField(
                value = query,
                placeholder = if (shownCollection == null) "Search" else "Search this collection",
                onChange = { query = it }
            )

            if (shownCollection != null) {
                KindTabs(
                    current = kind,
                    videoCount = filedVideos.size,
                    linkCount = filedLinks.size,
                    onPick = { kind = it }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (shownCollection == null) {
                    if (matchingCollections.isEmpty() && needle.isNotBlank()) {
                        EmptyShelfNotice("Nothing found", "No collection matches \"$query\".")
                    } else {
                        CollectionsShelf(
                            collections = matchingCollections,
                            unfiled = unfiledBucket,
                            showUnfiled = needle.isBlank(),
                            onOpen = { openCollection = it; query = ""; kind = 1 }
                        )
                    }
                } else if (shownVideos.isEmpty() && shownLinks.isEmpty()) {
                    EmptyShelfNotice(
                        if (needle.isNotBlank()) "Nothing found" else "Nothing in here yet",
                        if (needle.isNotBlank()) "No item matches \"$query\"."
                        else "Tap + and paste a link. A YouTube link becomes a video, anything else becomes a link."
                    )
                } else {
                    CollectionContents(
                        context = context,
                        videos = shownVideos,
                        links = shownLinks,
                        progressTick = progressTick,
                        onOpenEntry = { e ->
                            if (e.kind == "playlist") openEntry = e
                            else playing = e.id to e.title
                        },
                        onRemoveEntry = { pendingRemoval = it },
                        onOpenLink = { l -> openLink(context, l.url) },
                        onRemoveLink = { linkToRemove = it }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                // On the shelf the button makes a folder; inside one it adds to that folder.
                if (shownCollection == null) showNewCollection = true else showAdd = true
            },
            containerColor = YT_RED,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Icon(
                if (shownCollection == null) Icons.Default.CreateNewFolder else Icons.Default.Add,
                if (shownCollection == null) "New collection" else "Add a link"
            )
        }
    }

    val droppingCollection = collectionToDelete
    if (droppingCollection != null) {
        AlertDialog(
            onDismissRequest = { collectionToDelete = null },
            containerColor = Color(0xFF121212),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Delete this collection?", color = Color.White, fontSize = 16.sp)
            },
            text = {
                Text(
                    "\"${droppingCollection.name}\" goes, but nothing inside it does -- its " +
                        "${droppingCollection.total} items become unfiled.",
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Collections.delete(context, droppingCollection.id)
                    collectionToDelete = null
                    openCollection = null
                    collectionsTick++
                    entries = YouTubeAllowlist.entries(context)
                    links = ResourceLinks.all(context)
                }) {
                    Text("Delete", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { collectionToDelete = null }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }


    val droppingLink = linkToRemove
    if (droppingLink != null) {
        AlertDialog(
            onDismissRequest = { linkToRemove = null },
            containerColor = Color(0xFF121212),
            title = { Text("Remove link?", color = Color.White, fontSize = 16.sp) },
            text = {
                Text(
                    "\"${droppingLink.title}\" will be taken off your shelf.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ResourceLinks.remove(context, droppingLink)
                    links = ResourceLinks.all(context)
                    linkToRemove = null
                }) { Text("REMOVE", color = Color(0xFFE57373)) }
            },
            dismissButton = {
                TextButton(onClick = { linkToRemove = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        )
    }

    val removing = pendingRemoval
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = Color(0xFF121212),
            title = { Text("Remove?", color = Color.White, fontSize = 16.sp) },
            text = {
                Text(
                    "\"${removing.title}\" will no longer be available here.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    YouTubeAllowlist.remove(context, removing)
                    entries = YouTubeAllowlist.entries(context)
                    pendingRemoval = null
                }) { Text("REMOVE", color = Color(0xFFE57373)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        )
    }
}

/** Where the add flow has got to. */
private sealed class AddPhase {
    object Idle : AddPhase()
    data class Working(val stage: String) : AddPhase()
    data class Success(val title: String, val channel: String) : AddPhase()
    data class Denied(val message: String) : AddPhase()
}

/**
 * The add screen. One field, one button, and an honest account of what is happening while the
 * link is checked -- the round trip takes several seconds, and a spinner alone says nothing
 * about which of the three calls is in flight.
 */


/**
 * The search field both shelves share.
 *
 * Flat and quiet until it is used: a box that shouts at the top of a screen competes with the
 * content it exists to find. It gains its colour and a clear button only once something has
 * been typed.
 */
@Composable
private fun SearchField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit
) {
    val active = value.isNotBlank()
    Surface(
        color = Color(0xFF0F0F0F),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (active) YT_RED.copy(alpha = 0.55f) else EDGE),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 2.dp)
        ) {
            Icon(
                Icons.Default.Search,
                null,
                tint = if (active) YT_RED else Color(0xFF5A5A5A),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            // The row sets the height and both layers are centred inside it, so the hint and
            // the typed text sit on exactly the same line.
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = Color(0xFF4A4A4A), fontSize = 13.5.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 13.5.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(YT_RED),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (active) {
                Icon(
                    Icons.Default.Close,
                    "Clear",
                    tint = Color(0xFF7A7A7A),
                    modifier = Modifier
                        .size(17.dp)
                        .clickable { onChange("") }
                )
            }
        }
    }
}

/**
 * The two kinds, switched from the top of a collection.
 *
 * At the top because it filters what is directly beneath it -- a control that changes a list
 * belongs above the list, where the eye already is when it starts reading.
 */
@Composable
private fun KindTabs(current: Int, videoCount: Int, linkCount: Int, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF0F0F0F))
            .padding(4.dp)
    ) {
        KindTab("Videos", current == 1, videoCount, Modifier.weight(1f)) { onPick(1) }
        KindTab("Links", current == 2, linkCount, Modifier.weight(1f)) { onPick(2) }
    }
}

@Composable
private fun KindTab(
    label: String,
    selected: Boolean,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) YT_RED.copy(alpha = 0.16f) else Color.Transparent,
        androidx.compose.animation.core.tween(180),
        label = "kindBg"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp)
    ) {
        Text(
            if (count > 0) "$label  $count" else label,
            color = if (selected) YT_RED else Color(0xFF6E6E6E),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** The folders, and whatever never got put in one. */
@Composable
private fun CollectionsShelf(
    collections: List<Collection>,
    unfiled: Collection,
    showUnfiled: Boolean,
    onOpen: (Collection) -> Unit
) {
    if (collections.isEmpty() && unfiled.isEmpty) {
        EmptyShelfNotice(
            "No collections yet",
            "Tap + to make one. Group things by subject -- videos and links together."
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(collections, key = { it.id }) { c -> CollectionRow(c) { onOpen(c) } }

        // Only offered once there is something in it. An empty "Unfiled" row is a row about
        // nothing.
        if (showUnfiled && !unfiled.isEmpty) {
            item { CollectionRow(unfiled) { onOpen(unfiled) } }
        }
    }
}

@Composable
private fun CollectionRow(collection: Collection, onOpen: () -> Unit) {
    Surface(
        color = CARD,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, EDGE),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(YT_RED.copy(alpha = 0.13f))
            ) {
                if (collection.emoji.isNotBlank()) {
                    Text(collection.emoji, fontSize = 21.sp)
                } else {
                    Icon(Icons.Default.Folder, null, tint = YT_RED, modifier = Modifier.size(21.dp))
                }
            }

            Spacer(Modifier.width(15.dp))

            // Just the name. A second line saying "Empty" is a line about nothing, and the
            // count is already obvious the moment the folder is opened.
            Text(
                collection.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Videos and links from one collection, in a single list. */
@Composable
private fun CollectionContents(
    context: Context,
    videos: List<AllowedEntry>,
    links: List<ResourceLink>,
    progressTick: Int,
    onOpenEntry: (AllowedEntry) -> Unit,
    onRemoveEntry: (AllowedEntry) -> Unit,
    onOpenLink: (ResourceLink) -> Unit,
    onRemoveLink: (ResourceLink) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp, 4.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Videos first: they are the bigger commitment, and a shelf that buries them under a
        // row of articles is a shelf nobody watches anything from.
        items(videos, key = { it.kind + it.id }) { e ->
            val frac = remember(progressTick, e) { YouTubeProgress.fraction(context, e) }
            VideoCard(entry = e, fraction = frac, onOpen = { onOpenEntry(e) }, onRemove = { onRemoveEntry(e) })
        }
        items(links, key = { it.url }) { l ->
            LinkRow(link = l, onOpen = { onOpenLink(l) }, onRemove = { onRemoveLink(l) })
        }
    }
}

@Composable
private fun VideoCard(
    entry: AllowedEntry,
    fraction: Float,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = CARD,
        border = BorderStroke(1.dp, EDGE),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onRemove)
    ) {
        Column {
            Box {
                RemoteThumb(
                    entry.thumbnail,
                    Modifier
                        .fillMaxWidth()
                        // A thumbnail is made 16:9. Cropping it to a fixed height cut the
                        // title text and faces out of the frame it was composed for.
                        .aspectRatio(16f / 9f)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            ProgressBar(fraction, Modifier.fillMaxWidth())
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    entry.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.channel,
                        color = YT_RED,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (fraction > 0.001f) {
                        Text(
                            if (fraction >= 0.95f) "Watched" else "${percent(fraction)} watched",
                            color = if (fraction >= 0.95f) OK_GREEN else Color.Gray,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    CountChip(
                        playlist = entry.kind == "playlist",
                        label = if (entry.kind == "playlist") "${entry.videos.size} videos" else "Video"
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkRow(link: ResourceLink, onOpen: () -> Unit, onRemove: () -> Unit) {
    Surface(
        color = CARD,
        border = BorderStroke(1.dp, EDGE),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onRemove)
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(YT_RED.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Default.Link, null, tint = YT_RED, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    link.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (link.about.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        link.about,
                        color = Color(0xFF9A9A9A),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(link.host, color = YT_RED, fontSize = 10.sp)
            }
        }
    }
}

/** The shared "nothing here" panel, so every empty state looks the same. */
@Composable
private fun EmptyShelfNotice(title: String, body: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Icon(
                Icons.Default.Folder,
                null,
                tint = Color(0xFF2A2A2A),
                modifier = Modifier.size(58.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                color = Color(0xFF555555),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Where a collection gets its name, and nothing else. */
@Composable
private fun NewCollectionScreen(onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }

    BackHandler(enabled = true) { onDone(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Icon(
            Icons.Default.ArrowBack,
            "Cancel",
            tint = Color.White,
            modifier = Modifier.size(22.dp).clickable { onDone(null) }
        )

        Spacer(Modifier.height(28.dp))
        Text(
            "New collection",
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Name it after the subject, not the kind of thing. Videos and links live together.",
            color = Color(0xFF6E6E6E),
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(30.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            placeholder = { Text("System Design", color = Color(0xFF4A4A4A), fontSize = 15.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF101010),
                unfocusedContainerColor = Color(0xFF101010),
                focusedBorderColor = YT_RED,
                unfocusedBorderColor = Color(0xFF262626),
                cursorColor = YT_RED
            ),
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onDone(name) },
            enabled = name.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = YT_RED,
                disabledContainerColor = Color(0xFF1A1A1A)
            ),
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(
                "CREATE",
                color = if (name.isBlank()) Color(0xFF555555) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }
    }
}


/**
 * One place to paste anything.
 *
 * There is no "is this a video or a link" question because the answer is in the link itself.
 * A YouTube address goes through the scraper and the classifier and lands as a watchable
 * entry; anything else is read, judged and kept as a link. Asking the user to sort their own
 * paste into the right bucket was work the app could always have done for them.
 */
@Composable
private fun SmartAddScreen(
    initialLink: String = "",
    collectionId: String,
    collectionName: String,
    onDone: (added: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var pasted by remember { mutableStateOf(initialLink) }
    var stage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }

    val looksYouTube = remember(pasted) { Collections.isYouTube(pasted) }

    BackHandler(enabled = !busy) { onDone(done != null) }

    fun submit() {
        if (busy || pasted.isBlank()) return
        keyboard?.hide()
        busy = true
        error = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                if (Collections.isYouTube(pasted)) {
                    when (val r = YouTubeAllowlist.evaluate(context, pasted, collectionId) { stage = it }) {
                        is AddResult.Added -> null to r.entry.title
                        is AddResult.Rejected ->
                            "\"${r.title}\" did not read as something to learn from, so it was not kept." to null
                        is AddResult.Failed -> r.reason to null
                    }
                } else {
                    when (val r = ResourceLinks.add(context, pasted, collectionId) { stage = it }) {
                        is ResourceResult.Added -> null to r.link.title
                        is ResourceResult.Rejected -> r.reason to null
                        is ResourceResult.Failed -> r.reason to null
                    }
                }
            }
            busy = false
            stage = ""
            error = outcome.first
            done = outcome.second
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Icon(
            Icons.Default.ArrowBack,
            "Back",
            tint = if (busy) Color(0xFF3A3A3A) else Color.White,
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = !busy) { onDone(done != null) }
        )

        Spacer(Modifier.height(26.dp))
        Text(
            "Add to $collectionName",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Paste any link. A YouTube one is saved as a video, anything else as a link.",
            color = Color(0xFF6E6E6E),
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(26.dp))

        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it; error = null; done = null },
            enabled = !busy,
            placeholder = { Text("https://\u2026", color = Color(0xFF4A4A4A), fontSize = 14.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color(0xFF7A7A7A),
                focusedContainerColor = Color(0xFF101010),
                unfocusedContainerColor = Color(0xFF101010),
                disabledContainerColor = Color(0xFF101010),
                focusedBorderColor = YT_RED,
                unfocusedBorderColor = Color(0xFF262626),
                disabledBorderColor = Color(0xFF1E1E1E),
                cursorColor = YT_RED
            ),
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // What it worked out, said back before anything is committed, so a mistyped address
        // is obvious while it can still be corrected.
        if (pasted.isNotBlank() && !busy && done == null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (looksYouTube) Icons.Default.PlayArrow else Icons.Default.Link,
                    null,
                    tint = YT_RED,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (looksYouTube) "Reads as a YouTube video or playlist"
                    else "Reads as a web link",
                    color = Color(0xFF8A8A8A),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = { submit() },
            enabled = !busy && pasted.isNotBlank() && done == null,
            colors = ButtonDefaults.buttonColors(
                containerColor = YT_RED,
                disabledContainerColor = Color(0xFF1A1A1A)
            ),
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    "CHECK AND ADD",
                    color = if (pasted.isBlank()) Color(0xFF555555) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        if (busy && stage.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(stage, color = Color(0xFF8A8A8A), fontSize = 12.5.sp)
        }

        val kept = done
        if (kept != null) {
            Spacer(Modifier.height(22.dp))
            Surface(
                color = Color(0xFF0C1A0E),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF2F5A31)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(15.dp)
                ) {
                    Icon(Icons.Default.Check, null, tint = OK_GREEN, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Added to $collectionName", color = OK_GREEN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(kept, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { pasted = ""; done = null },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("ADD ANOTHER", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        val failure = error
        if (failure != null) {
            Spacer(Modifier.height(22.dp))
            Surface(
                color = Color(0xFF1A0D0D),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF5A2F2F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    failure,
                    color = Color(0xFFE57373),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(15.dp)
                )
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}


@Composable
private fun ResultPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    headline: String,
    detail: String
) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 90.dp, start = 32.dp, end = 32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
        ) {
            Icon(
                icon,
                null,
                tint = tint,
                modifier = Modifier.size((42 * scale.value).dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(headline, color = tint, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            detail,
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}

/** One half of the header pill. */

/**
 * The two shelves, switched from the bottom.
 *
 * Down here rather than under the title because it is a control, and a control belongs where
 * the thumb already rests.
 */




/** The "34 videos" marker, in the card body rather than over the artwork. */
@Composable
private fun CountChip(playlist: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF1C1C1C))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            if (playlist) Icons.Default.PlaylistPlay else Icons.Default.SmartDisplay,
            null,
            tint = Color(0xFF999999),
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color(0xFF999999), fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

/** Opens a saved link. It is already on file as allowed, so the gate waves it through. */
private fun openLink(context: android.content.Context, url: String) {
    try {
        BlockerActivity.isLaunchingWhitelistedApp = true
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.android.chrome")
            }
        )
    } catch (e: Exception) {
        try {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}


/** Where the add flow has got to for a resource link. */
private sealed class LinkPhase {
    object Idle : LinkPhase()
    data class Working(val stage: String) : LinkPhase()
    data class Success(val title: String, val about: String) : LinkPhase()
    data class Denied(val message: String) : LinkPhase()
}

