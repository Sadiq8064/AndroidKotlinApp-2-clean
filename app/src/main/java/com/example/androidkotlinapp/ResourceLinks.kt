package com.example.androidkotlinapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A link the user vouched for, kept with the description the model wrote for it. */
data class ResourceLink(
    val url: String,
    val title: String,
    val about: String,
    val host: String,
    val addedAt: Long,
    /** Which collection it is filed under. Empty means unfiled. */
    val collectionId: String = ""
)

/** How adding a link turned out. */
sealed class ResourceResult {
    data class Added(val link: ResourceLink) : ResourceResult()
    data class Rejected(val reason: String) : ResourceResult()
    data class Failed(val reason: String) : ResourceResult()
}

/**
 * The user's own shelf of links: anything they came across and want back later, checked once
 * on the way in.
 *
 * A link that earns a place here is also written into [LinkGate] as allowed, so tapping it
 * later goes straight through instead of being judged a second time.
 */
object ResourceLinks {

    private const val PREFS = "resource_links_prefs"
    private const val KEY = "links"
    private const val CLASSIFIER = "https://gemma-api.mrsadiq471.workers.dev/?q="
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private const val TIMEOUT_MS = 20_000

    // ---------------------------------------------------------------- storage

    fun all(context: Context): List<ResourceLink> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ResourceLink(
                    o.optString("url"),
                    o.optString("title"),
                    o.optString("about"),
                    o.optString("host"),
                    o.optLong("addedAt"),
                    o.optString("collectionId")
                )
            }.sortedByDescending { it.addedAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Writes the whole list back. Used when links are re-filed into a collection. */
    fun saveAll(context: Context, list: List<ResourceLink>) = save(context, list)

    private fun save(context: Context, list: List<ResourceLink>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("url", it.url)
                    .put("title", it.title)
                    .put("about", it.about)
                    .put("host", it.host)
                    .put("addedAt", it.addedAt)
                    .put("collectionId", it.collectionId)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun remove(context: Context, link: ResourceLink) {
        save(context, all(context).filterNot { it.url == link.url })
    }

    // ---------------------------------------------------------------- adding

    /**
     * Reads the page, has the model judge and describe it, and keeps it if it passes.
     * Blocking throughout, so callers belong on IO.
     */
    fun add(
        context: Context,
        rawUrl: String,
        collectionId: String = "",
        onStage: (String) -> Unit = {}
    ): ResourceResult {
        val url = rawUrl.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val host = LinkGate.host(url)
            ?: return ResourceResult.Failed("That does not look like a web address.")

        if (LinkGate.isYouTube(url)) {
            return ResourceResult.Failed("That is a YouTube link; it is saved as a video instead.")
        }
        if (all(context).any { it.url == url }) {
            return ResourceResult.Failed("That link is already on your shelf.")
        }

        onStage("Fetching the page")
        val page = fetchPage(url)

        onStage("Asking the AI what it is")
        val verdict = describe(url, page?.first.orEmpty(), page?.second.orEmpty())
            ?: return ResourceResult.Failed("Could not reach the classifier. Try again.")

        if (!verdict.allowed) {
            return ResourceResult.Rejected("\"${verdict.title}\" looks like entertainment, so it was not added.")
        }

        val link = ResourceLink(
            url = url,
            title = verdict.title.ifBlank { host },
            about = verdict.about,
            host = host,
            addedAt = System.currentTimeMillis(),
            collectionId = collectionId
        )
        save(context, all(context) + link)

        // Vouched for once, so the gate need not stop it again on the way to the browser.
        LinkGate.remember(context, url, LinkVerdict.ALLOW)
        return ResourceResult.Added(link)
    }

    private data class Described(val allowed: Boolean, val title: String, val about: String)

    /**
     * One call does both jobs. Asking twice would double the wait for no gain -- the model is
     * reading the same page either way, and a verdict without a title would leave the shelf
     * full of bare URLs.
     */
    private fun describe(url: String, pageTitle: String, pageText: String): Described? {
        val body = pageText.take(1100).replace(Regex("\\s+"), " ").trim()
        val prompt = buildString {
            append("You are cataloguing a web page for a focus app. Reply in exactly this ")
            append("format, three lines, nothing else:\n")
            append("VERDICT: ALLOW or BLOCK\n")
            append("TITLE: a short name for this page, at most 8 words\n")
            append("ABOUT: one sentence describing it, at most 20 words\n\n")
            append("VERDICT is ALLOW if the page has a genuine purpose: learning, ")
            append("documentation, research, tools, forms, government or college services, ")
            append("jobs, news of record, or reference. VERDICT is BLOCK if it exists mainly ")
            append("to entertain or distract: social feeds, streaming, memes, gaming, ")
            append("gambling, gossip, leisure shopping, or adult content.\n\n")
            append("URL: ").append(url).append('\n')
            append("Page title: ").append(pageTitle).append('\n')
            append("Page text: ").append(body)
        }
        val answer = fetchText(CLASSIFIER + URLEncoder.encode(prompt, "UTF-8")) ?: return null

        fun field(name: String): String =
            Regex("(?im)^\\s*$name\\s*:\\s*(.+)$").find(answer)?.groupValues?.get(1)?.trim()
                ?.trim('"', '*')
                .orEmpty()

        val verdict = field("VERDICT").uppercase()
        val allowed = when {
            verdict.startsWith("ALLOW") -> true
            verdict.startsWith("BLOCK") -> false
            // No usable verdict is not the same as a refusal: an unreadable page is waved on
            // rather than held against the user.
            else -> return Described(true, field("TITLE").ifBlank { pageTitle }, field("ABOUT"))
        }
        return Described(allowed, field("TITLE").ifBlank { pageTitle }, field("ABOUT"))
    }

    // ---------------------------------------------------------------- page reading

    /**
     * Title and visible text, straight over HTTP.
     *
     * Nothing here needs a rendering engine: the pages people save are articles, docs and
     * forms, which put their title and prose in the markup. A WebView would cost seconds and
     * a window for no more than a script-built page every so often.
     */
    private fun fetchPage(url: String): Pair<String, String>? {
        val html = fetchText(url, browserLike = true) ?: return null

        val title = Regex("(?is)<title[^>]*>(.*?)</title>")
            .find(html)?.groupValues?.get(1)?.let(::unescape)?.trim().orEmpty()

        val description = Regex(
            "(?is)<meta[^>]+(?:name|property)=[\"'](?:description|og:description)[\"'][^>]+content=[\"']([^\"']+)"
        ).find(html)?.groupValues?.get(1)?.let(::unescape)?.trim().orEmpty()

        val text = html
            .replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?s)<[^>]+>"), " ")
            .let(::unescape)
            .replace(Regex("\\s+"), " ")
            .trim()

        val combined = listOf(description, text).filter { it.isNotBlank() }.joinToString(" ")
        return if (title.isBlank() && combined.isBlank()) null else title to combined
    }

    private fun unescape(s: String) = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")

    private fun fetchText(url: String, browserLike: Boolean = false): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", if (browserLike) USER_AGENT else "FocusLauncher/1.0")
                if (browserLike) setRequestProperty("Accept", "text/html,*/*")
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
}
