package com.example.androidkotlinapp

import android.content.Context

/** A named folder in the Resource app, and how much is in it. */
data class Collection(
    val id: String,
    val name: String,
    val videoCount: Int,
    val linkCount: Int,
    /** Chosen by the model from the name. Blank until it answers, or if it never does. */
    val emoji: String = ""
) {
    val total: Int get() = videoCount + linkCount
    val isEmpty: Boolean get() = total == 0
}

/**
 * Folders for saved material, grouped by subject rather than by kind.
 *
 * Videos and links used to sit on two separate shelves, which is not how anyone looks for
 * anything -- you go looking for "the system design stuff", not "the links, as opposed to the
 * videos". A collection holds both.
 *
 * The folder is only a label. Deleting one leaves everything that was in it alone, unfiled,
 * so tidying up can never quietly destroy something that took a scrape and a model call to
 * add in the first place.
 */
object Collections {

    /** Where anything not put in a folder lives. Never stored; it is the absence of an id. */
    const val UNFILED = ""

    private fun db(context: Context) = FocusDatabaseHelper(context)

    private fun newId() = "col-" + System.currentTimeMillis().toString(36) +
        (0..9999).random().toString(36)

    fun all(context: Context): List<Collection> {
        val videos = YouTubeAllowlist.entries(context)
        val links = ResourceLinks.all(context)
        return db(context).getCollections().map { (id, name, emoji) ->
            Collection(
                id = id,
                name = name,
                videoCount = videos.count { it.collectionId == id },
                linkCount = links.count { it.collectionId == id },
                emoji = emoji
            )
        }
    }

    /** True when anything at all sits outside a folder, so the shelf can offer a home for it. */
    fun unfiled(context: Context): Collection {
        val videos = YouTubeAllowlist.entries(context).count { it.collectionId.isBlank() }
        val links = ResourceLinks.all(context).count { it.collectionId.isBlank() }
        return Collection(UNFILED, "Unfiled", videos, links)
    }

    fun create(context: Context, name: String, emoji: String = ""): String {
        val id = newId()
        db(context).addCollection(id, name.trim(), emoji)
        return id
    }

    fun setEmoji(context: Context, id: String, emoji: String) {
        db(context).setCollectionEmoji(id, emoji)
    }

    // ---------------------------------------------------------------- picking an emoji

    private const val CLASSIFIER = "https://gemma-api.mrsadiq471.workers.dev/?q="

    /** The worker answers 403 to a request with no User-Agent, so every call carries one. */
    private const val USER_AGENT = "FocusLauncher/1.0 (Android)"
    private const val TIMEOUT_MS = 15_000

    /** Used when the model is unreachable, slow, or answers with something that is not one. */
    const val FALLBACK_EMOJI = "\uD83D\uDCC1"

    /**
     * Asks the model for one emoji that suits the collection's name.
     *
     * Never blocks creating the folder: this runs after the row is written, and a failure just
     * leaves the plain folder icon in place. A collection the user has named should not be
     * held up by a network call about its decoration.
     */
    fun suggestEmoji(name: String): String? {
        val prompt = "Reply with exactly one emoji character and nothing else -- no words, no " +
            "punctuation, no explanation. Pick the single emoji that best represents this " +
            "topic: \"" + name.trim() + "\""
        val answer = fetchText(CLASSIFIER + java.net.URLEncoder.encode(prompt, "UTF-8"))
            ?: return null
        return firstEmoji(answer)
    }

    /**
     * Pulls the first real emoji out of whatever came back.
     *
     * The model is not reliably terse -- it returns quotes, trailing full stops, and sometimes
     * a sentence with the emoji buried in it -- so the answer is scanned rather than trusted.
     */
    private fun firstEmoji(raw: String): String? {
        val text = raw.trim().trim('"', '\'', '.', ' ')
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            if (isEmoji(cp)) {
                // Keep any variation selector or skin tone that follows, so the glyph renders
                // the way the model meant it to.
                var end = i + width
                while (end < text.length) {
                    val next = text.codePointAt(end)
                    if (next == 0xFE0F || next == 0x200D || (next in 0x1F3FB..0x1F3FF) ||
                        (next == 0x20E3)
                    ) {
                        end += Character.charCount(next)
                        // A zero-width joiner means another glyph is part of the same emoji.
                        if (next == 0x200D && end < text.length) {
                            end += Character.charCount(text.codePointAt(end))
                        }
                    } else break
                }
                return text.substring(i, end)
            }
            i += width
        }
        return null
    }

    private fun isEmoji(cp: Int): Boolean =
        cp in 0x1F300..0x1FAFF ||   // pictographs, symbols, supplemental
        cp in 0x1F000..0x1F0FF ||   // mahjong, cards
        cp in 0x2600..0x27BF ||     // misc symbols and dingbats
        cp in 0x2190..0x21FF ||     // arrows
        cp in 0x2B00..0x2BFF ||
        cp == 0x203C || cp == 0x2049

    private fun fetchText(url: String): String? {
        var conn: java.net.HttpURLConnection? = null
        return try {
            conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
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

    fun rename(context: Context, id: String, name: String) {
        db(context).renameCollection(id, name.trim())
    }

    /**
     * Drops the folder and leaves its contents unfiled.
     *
     * Deliberately not a cascade: a folder is a label someone chose, and changing their mind
     * about the label should not cost them the material underneath it.
     */
    fun delete(context: Context, id: String) {
        if (id.isBlank()) return
        YouTubeAllowlist.save(
            context,
            YouTubeAllowlist.entries(context).map {
                if (it.collectionId == id) it.copy(collectionId = UNFILED) else it
            }
        )
        ResourceLinks.saveAll(
            context,
            ResourceLinks.all(context).map {
                if (it.collectionId == id) it.copy(collectionId = UNFILED) else it
            }
        )
        db(context).removeCollection(id)
    }

    fun nameOf(context: Context, id: String): String =
        db(context).getCollections().firstOrNull { it.first == id }?.second ?: "Unfiled"

    // ---------------------------------------------------------------- what a link is

    /**
     * Whether a pasted link is a YouTube one.
     *
     * Checked on the host rather than by searching the whole string, so an article that merely
     * mentions youtube.com in its path is still treated as an article.
     */
    fun isYouTube(link: String): Boolean {
        val cleaned = link.trim()
        val host = try {
            java.net.URI(if (cleaned.startsWith("http")) cleaned else "https://$cleaned").host
                ?.lowercase()
                ?.removePrefix("www.")
                ?.removePrefix("m.")
                ?: return false
        } catch (e: Exception) {
            return false
        }
        return host == "youtube.com" || host == "youtu.be" || host == "music.youtube.com" ||
            host.endsWith(".youtube.com")
    }
}
