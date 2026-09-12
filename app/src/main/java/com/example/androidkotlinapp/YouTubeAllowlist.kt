package com.example.androidkotlinapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One video inside a saved playlist, or the single video a saved entry stands for. */
data class AllowedVideo(
    val id: String,
    val title: String,
    val lengthText: String,
    val thumbnail: String
)

/**
 * A playlist or a single video the user has had approved and can watch in this app.
 *
 * There is no channel entry: nothing is allowed wholesale. The user vouches for one playlist
 * or one video at a time, and only what an approved entry contains is ever playable.
 */
data class AllowedEntry(
    val kind: String,          // "playlist" or "video"
    val id: String,            // playlist id, or video id
    val title: String,
    val channel: String,
    val thumbnail: String,
    val videos: List<AllowedVideo>,
    /** Which collection it is filed under. Empty means unfiled. */
    val collectionId: String = "",
    /** When it was saved, so the newest sits at the top of a collection. */
    val addedAt: Long = 0L
)

/** What came of handing a pasted link to the scraper and the classifier. */
sealed class AddResult {
    data class Added(val entry: AllowedEntry) : AddResult()
    data class Rejected(val title: String, val channel: String) : AddResult()
    data class Failed(val reason: String) : AddResult()
}

/**
 * The library behind the in-app YouTube screen: what the user is allowed to watch, and the
 * three calls that decide whether a pasted link earns a place in it.
 *
 * Nothing here opens the YouTube app. A link is resolved to its metadata, judged, and -- if
 * it passes -- kept as an entry the user plays inside this app through an embedded player.
 */
object YouTubeAllowlist {

    private const val PREFS = "youtube_prefs"
    private const val KEY_ENTRIES = "allowed_entries"

    /** The user's own scraper. It is the only source that lists a playlist's videos. */
    private const val SCRAPER = "https://ytscrap.pages.dev/api/scrape?url="

    /**
     * YouTube's public oEmbed endpoint. It needs no API key and, unlike the scraper, it
     * reports the channel -- which is the one thing the classifier most needs and the
     * scraper's playlist response leaves out entirely.
     */
    private const val OEMBED = "https://www.youtube.com/oembed?format=json&url="

    /** The user's Llama worker, asked for a single word. */
    private const val CLASSIFIER = "https://gemma-api.mrsadiq471.workers.dev/?q="

    /** The worker answers 403 to a request with no User-Agent, so every call carries one. */
    private const val USER_AGENT = "FocusLauncher/1.0"

    private const val TIMEOUT_MS = 30_000

    // ---------------------------------------------------------------- link parsing

    /** The `list=` id of a playlist link, or null if this is not one. */
    fun playlistId(link: String): String? =
        Regex("[?&]list=([A-Za-z0-9_-]+)").find(link)?.groupValues?.get(1)

    /** The video id of a watch, youtu.be, shorts or embed link, or null. */
    fun videoId(link: String): String? {
        val patterns = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("/shorts/([A-Za-z0-9_-]{11})"),
            Regex("/embed/([A-Za-z0-9_-]{11})")
        )
        return patterns.firstNotNullOfOrNull { it.find(link)?.groupValues?.get(1) }
    }

    // ---------------------------------------------------------------- storage

    fun entries(context: Context): List<AllowedEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val out = mutableListOf<AllowedEntry>()
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val vids = mutableListOf<AllowedVideo>()
                val va = o.optJSONArray("videos") ?: JSONArray()
                for (j in 0 until va.length()) {
                    val v = va.getJSONObject(j)
                    vids.add(
                        AllowedVideo(
                            v.optString("id"),
                            v.optString("title"),
                            v.optString("lengthText"),
                            v.optString("thumbnail")
                        )
                    )
                }
                out.add(
                    AllowedEntry(
                        o.optString("kind"),
                        o.optString("id"),
                        o.optString("title"),
                        o.optString("channel"),
                        o.optString("thumbnail"),
                        vids,
                        o.optString("collectionId"),
                        o.optLong("addedAt")
                    )
                )
            }
            out
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun save(context: Context, list: List<AllowedEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            val vids = JSONArray()
            e.videos.forEach { v ->
                vids.put(
                    JSONObject()
                        .put("id", v.id)
                        .put("title", v.title)
                        .put("lengthText", v.lengthText)
                        .put("thumbnail", v.thumbnail)
                )
            }
            arr.put(
                JSONObject()
                    .put("kind", e.kind)
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("channel", e.channel)
                    .put("thumbnail", e.thumbnail)
                    .put("videos", vids)
                    .put("collectionId", e.collectionId)
                    .put("addedAt", e.addedAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    /** Entries stay until the user takes them out; nothing expires them on its own. */
    fun remove(context: Context, entry: AllowedEntry) {
        save(context, entries(context).filterNot { it.kind == entry.kind && it.id == entry.id })
    }

    // ---------------------------------------------------------------- the add flow

    /**
     * Resolves a pasted link, asks the classifier whether it is educational, and returns the
     * entry to save if it is. Blocking on every network call, so callers belong on IO.
     */
    fun evaluate(
        context: Context,
        link: String,
        collectionId: String = "",
        onStage: (String) -> Unit = {}
    ): AddResult {
        onStage("Reading the link")
        val playlist = playlistId(link)
        val video = if (playlist == null) videoId(link) else null
        if (playlist == null && video == null) {
            return AddResult.Failed("That is not a YouTube video or playlist link.")
        }

        // Both kinds go through oEmbed, because it is the only call that names the channel.
        val canonical = if (playlist != null) {
            "https://www.youtube.com/playlist?list=$playlist"
        } else {
            "https://www.youtube.com/watch?v=$video"
        }
        onStage("Fetching video details")
        val meta = fetchJson(OEMBED + enc(canonical))
            ?: return AddResult.Failed("Could not read that link. Check it and try again.")

        val title = meta.optString("title").ifBlank { "Untitled" }
        val channel = meta.optString("author_name").ifBlank { "Unknown channel" }
        val thumbnail = meta.optString("thumbnail_url")

        // A playlist also needs its contents, which only the scraper can list.
        var videos = emptyList<AllowedVideo>()
        if (playlist != null) {
            onStage("Reading the playlist")
            val scraped = fetchJson(SCRAPER + enc(canonical))
                ?: return AddResult.Failed("The scraper did not answer. Try again in a moment.")
            if (scraped.has("error")) {
                return AddResult.Failed("Scraper: ${scraped.optString("error")}")
            }
            val arr = scraped.optJSONArray("videos") ?: JSONArray()
            videos = (0 until arr.length()).map { i ->
                val v = arr.getJSONObject(i)
                AllowedVideo(
                    v.optString("id"),
                    v.optString("title"),
                    v.optString("lengthText"),
                    v.optString("thumbnail")
                )
            }
            if (videos.isEmpty()) {
                return AddResult.Failed("That playlist came back empty.")
            }
        }

        onStage("Asking the AI if it is educational")
        val educational = classify(buildContext(title, channel, videos))
            ?: return AddResult.Failed("Could not reach the classifier. Try again.")
        if (!educational) return AddResult.Rejected(title, channel)

        val entry = AllowedEntry(
            kind = if (playlist != null) "playlist" else "video",
            id = playlist ?: video!!,
            title = title,
            channel = channel,
            thumbnail = thumbnail,
            videos = videos,
            collectionId = collectionId,
            addedAt = System.currentTimeMillis()
        )
        val kept = entries(context).filterNot { it.kind == entry.kind && it.id == entry.id }
        save(context, kept + entry)
        return AddResult.Added(entry)
    }

    /**
     * The classifier is given the channel, the title and a handful of video titles. A whole
     * playlist would not survive the trip -- the worker takes its prompt in the query string,
     * and thirty-five titles overrun what a URL can carry.
     */
    private fun buildContext(title: String, channel: String, videos: List<AllowedVideo>): String {
        val sb = StringBuilder()
        sb.append("Channel: ").append(channel).append('\n')
        sb.append("Title: ").append(title)
        if (videos.isNotEmpty()) {
            sb.append('\n').append("Videos in playlist: ").append(videos.size).append('\n')
            sb.append("Sample videos:")
            videos.take(8).forEach { sb.append("\n- ").append(it.title) }
        }
        return sb.toString()
    }

    /** True, false, or null when the worker could not be reached. */
    private fun classify(contextText: String): Boolean? {
        val prompt = "You are a strict classifier. Reply with exactly one word, YES or NO, " +
            "and nothing else. Is this YouTube content primarily educational (teaching, " +
            "tutorials, academic, or skill-building)?\n\n$contextText"
        val answer = fetchText(CLASSIFIER + enc(prompt)) ?: return null
        // The model is not reliably terse -- "NO." and "YES" both turn up.
        val trimmed = answer.trim().trimStart('"').uppercase()
        return when {
            trimmed.startsWith("YES") -> true
            trimmed.startsWith("NO") -> false
            else -> null
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun fetchText(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchJson(url: String): JSONObject? {
        val body = fetchText(url) ?: return null
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * The embedded player URL for an entry. A playlist plays as a series so the user can move
     * between its videos without ever leaving this screen.
     */
    fun embedUrl(entry: AllowedEntry): String = when (entry.kind) {
        "playlist" -> "https://www.youtube.com/embed/videoseries?list=${entry.id}&rel=0"
        else -> "https://www.youtube.com/embed/${entry.id}?rel=0"
    }

    fun embedUrl(videoId: String): String = "https://www.youtube.com/embed/$videoId?rel=0"
}

/**
 * How far through each video the user has got, kept so a card can show its progress and a
 * playlist can show how much of it is done.
 *
 * Positions are stored per video id rather than per entry, so the same video counts as watched
 * whether it was reached through a playlist or saved on its own.
 */
object YouTubeProgress {

    private const val PREFS = "youtube_progress"

    /** Below this, a video reads as barely started; above it, as finished. */
    private const val COMPLETE_AT = 0.95f

    fun record(context: Context, videoId: String, positionSec: Float, durationSec: Float) {
        if (videoId.isBlank() || durationSec <= 0f) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("pos_$videoId", positionSec)
            .putFloat("dur_$videoId", durationSec)
            .apply()
    }

    /** Seconds to resume from, or 0 for a video that is unwatched or already finished. */
    fun resumeAt(context: Context, videoId: String): Int {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pos = p.getFloat("pos_$videoId", 0f)
        val dur = p.getFloat("dur_$videoId", 0f)
        if (dur <= 0f || pos / dur >= COMPLETE_AT) return 0
        return pos.toInt()
    }

    /** 0f to 1f through a single video. */
    fun fraction(context: Context, videoId: String): Float {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dur = p.getFloat("dur_$videoId", 0f)
        if (dur <= 0f) return 0f
        return (p.getFloat("pos_$videoId", 0f) / dur).coerceIn(0f, 1f)
    }

    /**
     * How much of an entry is done. A playlist averages its videos, so ten half-watched
     * lectures and five finished ones both read as half a course rather than as nothing.
     */
    fun fraction(context: Context, entry: AllowedEntry): Float {
        if (entry.kind != "playlist") return fraction(context, entry.id)
        if (entry.videos.isEmpty()) return 0f
        return entry.videos.sumOf { fraction(context, it.id).toDouble() }.toFloat() / entry.videos.size
    }

    /** Videos in a playlist watched through to the end. */
    fun completedCount(context: Context, entry: AllowedEntry): Int =
        entry.videos.count { fraction(context, it.id) >= COMPLETE_AT }
}
