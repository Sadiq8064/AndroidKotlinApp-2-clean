package com.example.androidkotlinapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/** What the gate decided about a link, before or after asking. */
enum class LinkVerdict { ALLOW, BLOCK, UNKNOWN }

/**
 * The gate every tapped link passes through.
 *
 * A verdict is reached once per host and then remembered, so the second link into a site the
 * user already opened costs nothing -- which is what keeps the gate quick enough to sit in
 * front of an ordinary tap.
 */
object LinkGate {

    private const val PREFS = "link_gate_prefs"
    private const val CLASSIFIER = "https://gemma-api.mrsadiq471.workers.dev/?q="
    private const val USER_AGENT = "FocusLauncher/1.0"
    private const val TIMEOUT_MS = 20_000

    /**
     * Blocked outright, with no page fetch and no model call. These are not borderline, and
     * spending two seconds deciding that Instagram is a distraction helps nobody.
     */
    private val ALWAYS_BLOCKED = setOf(
        "instagram.com", "facebook.com", "fb.com", "tiktok.com",
        "snapchat.com", "reddit.com", "x.com", "twitter.com",
        "netflix.com", "primevideo.com", "hotstar.com", "twitch.tv"
    )

    /** Trusted without asking: the user's own tools, and the places study links point at. */
    private val ALWAYS_ALLOWED = setOf(
        "google.com", "docs.google.com", "drive.google.com", "classroom.google.com",
        "github.com", "stackoverflow.com", "wikipedia.org", "geeksforgeeks.org",
        "leetcode.com", "w3schools.com", "developer.android.com", "kotlinlang.org"
    )

    /** The host a link belongs to, without `www.` and lowercased. Null if unparseable. */
    fun host(link: String): String? = try {
        val h = URI(link).host?.lowercase() ?: return null
        if (h.startsWith("www.")) h.substring(4) else h
    } catch (e: Exception) {
        null
    }

    /** True when the host is the given domain or a subdomain of it. */
    private fun matches(host: String, domain: String) =
        host == domain || host.endsWith(".$domain")

    fun isYouTube(link: String): Boolean {
        val h = host(link) ?: return false
        return matches(h, "youtube.com") || matches(h, "youtu.be")
    }

    /**
     * The verdict already on file for this link's host, if any. Sub-links inherit it, which is
     * the point: a site is judged once, not once per page.
     */
    fun known(context: Context, link: String): LinkVerdict {
        val h = host(link) ?: return LinkVerdict.BLOCK
        if (ALWAYS_BLOCKED.any { matches(h, it) }) return LinkVerdict.BLOCK
        if (ALWAYS_ALLOWED.any { matches(h, it) }) return LinkVerdict.ALLOW

        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("v_$h", null) ?: return LinkVerdict.UNKNOWN
        return runCatching { LinkVerdict.valueOf(stored) }.getOrDefault(LinkVerdict.UNKNOWN)
    }

    fun remember(context: Context, link: String, verdict: LinkVerdict) {
        val h = host(link) ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("v_$h", verdict.name).apply()
    }

    fun forget(context: Context, host: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("v_$host").apply()
    }

    /**
     * Judges a page from its own text.
     *
     * The bar is not "is this a lesson" -- a recruitment portal, a college form and a bank are
     * all legitimate reasons to open a link mid-session. The bar is whether the page exists to
     * be consumed for entertainment, which is the thing a focus session is protecting against.
     */
    fun classify(pageTitle: String, pageText: String, link: String): LinkVerdict {
        val body = pageText.take(1200).replace(Regex("\\s+"), " ").trim()
        val prompt = buildString {
            append("You are a strict web page classifier for a focus app. ")
            append("Reply with exactly one word, ALLOW or BLOCK, and nothing else.\n\n")
            append("Reply ALLOW if the page serves a genuine purpose: education, tutorials, ")
            append("documentation, research, news of record, government or college services, ")
            append("forms, job or recruitment listings, banking, productivity or developer tools.\n")
            append("Reply BLOCK if the page exists mainly to entertain or distract: social ")
            append("media feeds, short-form video, streaming, memes, gaming, gambling, ")
            append("shopping for leisure, gossip, or adult content.\n\n")
            append("URL: ").append(link).append('\n')
            append("Title: ").append(pageTitle).append('\n')
            append("Page text: ").append(body)
        }
        val answer = fetchText(CLASSIFIER + URLEncoder.encode(prompt, "UTF-8"))
            ?: return LinkVerdict.UNKNOWN
        val trimmed = answer.trim().trimStart('"').uppercase()
        return when {
            trimmed.startsWith("ALLOW") -> LinkVerdict.ALLOW
            trimmed.startsWith("BLOCK") -> LinkVerdict.BLOCK
            else -> LinkVerdict.UNKNOWN
        }
    }

    /** Raised wherever a link is turned away, by the gate or by the Chrome watcher. */
    fun notifyBlocked(context: Context, host: String) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                manager.deleteNotificationChannel(LEGACY_NOTIFY_CHANNEL_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFY_CHANNEL_ID,
                    "Blocked Links",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Tells you when a link was not allowed through"
                    setSound(NotificationSound.uri(context), NotificationSound.attributes())
                }
            )
        }
        val notification = NotificationCompat.Builder(context, NOTIFY_CHANNEL_ID)
            .setContentTitle("Link blocked \uD83D\uDEAB")
            .setContentText("$host is not allowed right now.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$host was judged a distraction, so it was not opened.")
            )
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setSound(NotificationSound.uri(context))
            .build()
        try {
            manager.notify(NOTIFY_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    const val NOTIFY_CHANNEL_ID = "LinkGateChannelV2"
    private const val LEGACY_NOTIFY_CHANNEL_ID = "LinkGateChannel"
    private const val NOTIFY_ID = 6000

    private fun fetchText(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
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
