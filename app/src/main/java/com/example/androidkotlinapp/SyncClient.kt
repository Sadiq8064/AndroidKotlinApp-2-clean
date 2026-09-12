package com.example.androidkotlinapp

import android.content.ContentValues
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.spec.SecretKeySpec

/**
 * The copy of everything, kept somewhere a reinstall can reach.
 *
 * Local first, and not by half measures: the phone's own database is the thing the app reads
 * and writes, always. This class only ever mirrors it. Nothing here is on the path of opening
 * a screen or ticking a habit, and a server that is down, slow, or unreachable changes nothing
 * about how the app behaves -- it just means the mirror is stale until the next push.
 *
 * The mirror is encrypted before it leaves the device, under a key derived from the account
 * password. See [CryptoBox]: the server holds blobs it cannot open.
 */
object SyncClient {

    private const val BASE = "https://focus-sync.mrsadiq471.workers.dev"

    private const val PREFS = "sync_prefs"
    private const val KEY_SALT = "key_salt"
    private const val KEY_REVISION = "revision"
    private const val KEY_LAST_PUSH = "last_push_at"

    // Kept so a background push does not have to ask for the password again. The key lives on
    // the device by design -- that is what end-to-end encryption means, and it is the server
    // this scheme is defending against, not the phone the owner is holding.
    private const val KEY_EMAIL = "email"
    private const val KEY_VERIFIER = "verifier"
    private const val KEY_MATERIAL = "key_material"

    /** No more than one mirror write a minute, however often the app asks. */
    private const val PUSH_INTERVAL_MS = 60_000L

    private const val TIMEOUT_MS = 20_000

    /** Collections, in the order a restore should apply them. */
    private const val C_HABITS = "habits"
    private const val C_DONE = "habit_completions"
    private const val C_DEBT = "habit_debt"
    private const val C_GOALS = "goals"
    private const val C_REMINDERS = "reminders"
    private const val C_RESOURCES = "resources"
    private const val C_LINKS = "links"
    private const val C_HOME = "home_apps"
    private const val C_WHITELIST = "whitelist"
    private const val C_STATS = "focus_stats"
    private const val C_POMODORO = "pomodoro"
    private const val C_SESSION = "session"
    private const val C_LINKGATE = "link_gate"
    private const val C_WATCH = "watch_progress"
    private const val C_LIMITS = "app_limits"
    private const val C_COLLECTIONS = "collections"
    private const val C_ALARMS = "alarms"

    // ---------------------------------------------------------------- account

    sealed class AuthOutcome {
        data class Ok(val keySalt: String) : AuthOutcome()
        data class Failed(val message: String) : AuthOutcome()
        /** The network was the problem, not the credentials -- the app carries on offline. */
        data class Offline(val message: String) : AuthOutcome()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun storedKeySalt(context: Context): String =
        prefs(context).getString(KEY_SALT, "").orEmpty()

    private fun rememberSalt(context: Context, salt: String) {
        prefs(context).edit().putString(KEY_SALT, salt).apply()
    }

    /** Claims the address, or signs in to it if the password matches. */
    fun register(context: Context, email: String, password: String): AuthOutcome =
        authCall(context, "/register", email, password)

    fun login(context: Context, email: String, password: String): AuthOutcome =
        authCall(context, "/login", email, password)

    private fun authCall(
        context: Context,
        path: String,
        email: String,
        password: String
    ): AuthOutcome {
        val body = JSONObject().apply {
            put("email", email.trim().lowercase())
            put("verifier", CryptoBox.verifier(email, password))
        }
        return when (val r = post(path, body)) {
            is Wire.Ok -> {
                val salt = r.json.optString("keySalt")
                if (salt.isBlank()) AuthOutcome.Failed("The server sent no key salt.")
                else {
                    rememberSalt(context, salt)
                    remember(context, email, password, salt)
                    AuthOutcome.Ok(salt)
                }
            }
            is Wire.Refused -> AuthOutcome.Failed(r.message)
            is Wire.Unreachable -> AuthOutcome.Offline(r.message)
        }
    }


    /** Stores what a later push needs, so it never has to hold the password itself. */
    private fun remember(context: Context, email: String, password: String, salt: String) {
        prefs(context).edit()
            .putString(KEY_EMAIL, email.trim().lowercase())
            .putString(KEY_VERIFIER, CryptoBox.verifier(email, password))
            .putString(
                KEY_MATERIAL,
                android.util.Base64.encodeToString(
                    CryptoBox.key(password, salt).encoded,
                    android.util.Base64.NO_WRAP
                )
            )
            .apply()
    }

    /** True once this phone has everything it needs to mirror on its own. */
    fun isLinked(context: Context): Boolean =
        prefs(context).getString(KEY_MATERIAL, null) != null

    fun forget(context: Context) {
        prefs(context).edit()
            .remove(KEY_EMAIL)
            .remove(KEY_VERIFIER)
            .remove(KEY_MATERIAL)
            .apply()
    }

    /**
     * Mirrors the phone up, using the material saved at sign-in.
     *
     * Rate limited, and silent about failure on purpose: this runs in the background behind a
     * working app, and a copy being a minute behind is not something worth interrupting anyone
     * over.
     */
    fun pushIfDue(context: Context, force: Boolean = false): Boolean {
        val p = prefs(context)
        val last = p.getLong(KEY_LAST_PUSH, 0L)
        if (!force && System.currentTimeMillis() - last < PUSH_INTERVAL_MS) return false

        val email = p.getString(KEY_EMAIL, null) ?: return false
        val verifier = p.getString(KEY_VERIFIER, null) ?: return false
        val material = p.getString(KEY_MATERIAL, null) ?: return false

        val key = SecretKeySpec(
            android.util.Base64.decode(material, android.util.Base64.NO_WRAP),
            "AES"
        )

        val revision = p.getInt(KEY_REVISION, 0) + 1
        val items = JSONArray()
        for ((collection, payload) in collect(context)) {
            val sealed = CryptoBox.seal(key, payload)
            items.put(
                JSONObject().apply {
                    put("collection", collection)
                    put("ciphertext", sealed.ciphertext)
                    put("iv", sealed.iv)
                    put("revision", revision)
                }
            )
        }
        if (items.length() == 0) return false

        val body = JSONObject().apply {
            put("email", email)
            put("verifier", verifier)
            put("items", items)
        }

        return when (post("/push", body)) {
            is Wire.Ok -> {
                p.edit()
                    .putInt(KEY_REVISION, revision)
                    .putLong(KEY_LAST_PUSH, System.currentTimeMillis())
                    .apply()
                true
            }
            else -> false
        }
    }

    // ---------------------------------------------------------------- pushing

    /**
     * Mirrors everything on the phone up to the server.
     *
     * Returns true when the write landed. A false is not an error the user needs to see: it
     * means the copy is behind, and the next push will carry the same data.
     */
    fun push(context: Context, email: String, password: String): Boolean {
        val salt = storedKeySalt(context).ifBlank { return false }
        val key = CryptoBox.key(password, salt)

        val revision = prefs(context).getInt(KEY_REVISION, 0) + 1
        val items = JSONArray()

        for ((collection, payload) in collect(context)) {
            val sealed = CryptoBox.seal(key, payload)
            items.put(
                JSONObject().apply {
                    put("collection", collection)
                    put("ciphertext", sealed.ciphertext)
                    put("iv", sealed.iv)
                    put("revision", revision)
                }
            )
        }
        if (items.length() == 0) return false

        val body = JSONObject().apply {
            put("email", email.trim().lowercase())
            put("verifier", CryptoBox.verifier(email, password))
            put("items", items)
        }

        return when (post("/push", body)) {
            is Wire.Ok -> {
                prefs(context).edit()
                    .putInt(KEY_REVISION, revision)
                    .putLong(KEY_LAST_PUSH, System.currentTimeMillis())
                    .apply()
                true
            }
            else -> false
        }
    }

    /** Reads every store on the phone into one JSON document per collection. */
    private fun collect(context: Context): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            val db = FocusDatabaseHelper(context)

            val habits = JSONArray()
            db.getAllHabits().forEach { h ->
                habits.put(
                    JSONObject().apply {
                        put("id", h.id)
                        put("emoji", h.emoji)
                        put("text", h.text)
                        put("order", h.displayOrder)
                        put("createdDate", h.createdDate)
                        put("createdAt", h.createdAt)
                    }
                )
            }
            out.add(C_HABITS to habits.toString())

            out.add(C_DONE to db.exportCompletions())
            out.add(C_DEBT to db.exportDebt())

            val goals = JSONArray()
            Tasks.goals(context).forEach { g ->
                goals.put(
                    JSONObject().apply {
                        put("id", g.id)
                        put("title", g.title)
                        put("startMs", g.startMs)
                        put("endMs", g.endMs)
                        put("createdAt", g.createdAt)
                        put(
                            "steps",
                            JSONArray().apply {
                                g.tasks.forEach { t ->
                                    put(
                                        JSONObject().apply {
                                            put("id", t.id)
                                            put("title", t.title)
                                            put("done", t.done)
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
            out.add(C_GOALS to goals.toString())

            val reminders = JSONArray()
            Tasks.reminders(context).forEach { r ->
                reminders.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("title", r.title)
                        put("dateMs", r.dateMs)
                        put("createdAt", r.createdAt)
                        put("repeatYearly", r.repeatYearly)
                        put("showBeforeDays", r.showBeforeDays)
                    }
                )
            }
            out.add(C_REMINDERS to reminders.toString())

            // These two already keep their state as JSON, so the stored string travels as-is
            // rather than being taken apart and rebuilt for no reason.
            out.add(
                C_RESOURCES to context.getSharedPreferences("youtube_prefs", Context.MODE_PRIVATE)
                    .getString("allowed_entries", "[]").orEmpty()
            )
            out.add(
                C_LINKS to context.getSharedPreferences("resource_links_prefs", Context.MODE_PRIVATE)
                    .getString("links", "[]").orEmpty()
            )
            out.add(
                C_HOME to JSONObject().apply {
                    put("apps", JSONArray(db.getHomeApps()))
                }.toString()
            )

            out.add(
                C_WHITELIST to JSONArray(
                    WhitelistManager.getCustomWhitelistedPackages(context).toList()
                ).toString()
            )

            out.add(C_STATS to FocusStats.export(context))

            val cols = JSONArray()
            db.getCollections().forEach { (id, name, emoji) ->
                cols.put(JSONObject().apply {
                    put("id", id); put("name", name); put("emoji", emoji)
                })
            }
            out.add(C_COLLECTIONS to cols.toString())

            val alarms = JSONArray()
            Alarms.all(context).forEach { a ->
                alarms.put(JSONObject().apply {
                    put("id", a.id)
                    put("hour", a.hour)
                    put("minute", a.minute)
                    put("label", a.label)
                    put("days", a.days.joinToString(","))
                    put("enabled", a.enabled)
                    put("special", a.special)
                    put("durationDays", a.durationDays)
                    put("createdAt", a.createdAt)
                    put("faceUnlock", a.faceUnlock)
                })
            }
            out.add(C_ALARMS to alarms.toString())

            // The rest are preference files whose whole contents are worth keeping. Copied
            // key by key rather than named one at a time, so a new setting is carried without
            // this having to be edited again.
            out.add(C_POMODORO to dumpPrefs(context, "pomodoro_prefs"))
            out.add(C_SESSION to dumpPrefs(context, "focus_session_prefs"))
            out.add(C_LINKGATE to dumpPrefs(context, "link_gate_prefs"))
            out.add(C_WATCH to dumpPrefs(context, "youtube_progress"))
            out.add(C_LIMITS to dumpPrefs(context, "app_usage_limits"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }


    /**
     * A whole preference file as JSON, types kept.
     *
     * The type is written alongside each value because JSON cannot tell a long from an int,
     * and putting a session end time back as an int would truncate it.
     */
    private fun dumpPrefs(context: Context, name: String): String {
        val out = JSONObject()
        try {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                val entry = JSONObject()
                when (v) {
                    is Boolean -> { entry.put("t", "b"); entry.put("v", v) }
                    is Int -> { entry.put("t", "i"); entry.put("v", v) }
                    is Long -> { entry.put("t", "l"); entry.put("v", v) }
                    is Float -> { entry.put("t", "f"); entry.put("v", v.toDouble()) }
                    is String -> { entry.put("t", "s"); entry.put("v", v) }
                    is Set<*> -> {
                        entry.put("t", "ss")
                        entry.put("v", JSONArray(v.filterIsInstance<String>()))
                    }
                    else -> return@forEach
                }
                out.put(k, entry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out.toString()
    }

    private fun loadPrefs(context: Context, name: String, json: String) {
        try {
            val o = JSONObject(json)
            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            o.keys().forEach { k ->
                val entry = o.optJSONObject(k) ?: return@forEach
                when (entry.optString("t")) {
                    "b" -> edit.putBoolean(k, entry.optBoolean("v"))
                    "i" -> edit.putInt(k, entry.optInt("v"))
                    "l" -> edit.putLong(k, entry.optLong("v"))
                    "f" -> edit.putFloat(k, entry.optDouble("v").toFloat())
                    "s" -> edit.putString(k, entry.optString("v"))
                    "ss" -> {
                        val arr = entry.optJSONArray("v") ?: JSONArray()
                        edit.putStringSet(
                            k,
                            (0 until arr.length()).map { arr.getString(it) }.toSet()
                        )
                    }
                }
            }
            edit.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------- pulling

    data class RestoreReport(val restored: List<String>, val message: String)

    /**
     * Brings the server's copy down and writes it into the local database.
     *
     * Only called when there is nothing here to lose -- see [hasLocalData]. A restore that ran
     * over live data would be a sync conflict resolved by destroying the newer side, which is
     * the one outcome local-first exists to avoid.
     */
    fun restore(context: Context, email: String, password: String): RestoreReport {
        val salt = storedKeySalt(context)
        if (salt.isBlank()) return RestoreReport(emptyList(), "No key salt yet.")

        val body = JSONObject().apply {
            put("email", email.trim().lowercase())
            put("verifier", CryptoBox.verifier(email, password))
        }

        val response = when (val r = post("/pull", body)) {
            is Wire.Ok -> r.json
            is Wire.Refused -> return RestoreReport(emptyList(), r.message)
            is Wire.Unreachable -> return RestoreReport(emptyList(), r.message)
        }

        val key = CryptoBox.key(password, salt)
        val items = response.optJSONArray("items") ?: JSONArray()
        val done = mutableListOf<String>()
        var highest = 0

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val collection = item.optString("collection")
            val plain = CryptoBox.open(
                key,
                item.optString("ciphertext"),
                item.optString("iv")
            ) ?: continue

            highest = maxOf(highest, item.optInt("revision", 0))
            if (apply(context, collection, plain)) done.add(collection)
        }

        prefs(context).edit().putInt(KEY_REVISION, highest).apply()

        return RestoreReport(
            done,
            if (done.isEmpty()) "Nothing stored for this account yet."
            else "Restored ${done.size} collections."
        )
    }

    private fun apply(context: Context, collection: String, plain: String): Boolean = try {
        val db = FocusDatabaseHelper(context)
        when (collection) {
            C_HABITS -> {
                val arr = JSONArray(plain)
                for (i in 0 until arr.length()) {
                    val h = arr.getJSONObject(i)
                    db.restoreHabit(
                        h.getInt("id"),
                        h.optString("emoji", FocusDatabaseHelper.DEFAULT_HABIT_EMOJI),
                        h.getString("text"),
                        h.optInt("order", i),
                        h.optString("createdDate"),
                        h.optLong("createdAt")
                    )
                }
                true
            }
            C_DONE -> { db.importCompletions(plain); true }
            C_DEBT -> { db.importDebt(plain); true }
            C_GOALS -> {
                val arr = JSONArray(plain)
                for (i in 0 until arr.length()) {
                    val g = arr.getJSONObject(i)
                    val steps = g.optJSONArray("steps") ?: JSONArray()
                    Tasks.restoreGoal(
                        context,
                        Goal(
                            id = g.getString("id"),
                            title = g.getString("title"),
                            startMs = g.getLong("startMs"),
                            endMs = g.getLong("endMs"),
                            createdAt = g.optLong("createdAt"),
                            tasks = (0 until steps.length()).map { j ->
                                val t = steps.getJSONObject(j)
                                GoalTask(t.getString("id"), t.getString("title"), t.optBoolean("done"))
                            }
                        )
                    )
                }
                true
            }
            C_REMINDERS -> {
                val arr = JSONArray(plain)
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    Tasks.restoreReminder(
                        context,
                        Reminder(
                            id = r.getString("id"),
                            title = r.getString("title"),
                            dateMs = r.getLong("dateMs"),
                            createdAt = r.optLong("createdAt"),
                            repeatYearly = r.optBoolean("repeatYearly", false),
                            showBeforeDays = r.optInt("showBeforeDays", 7)
                        )
                    )
                }
                true
            }
            C_RESOURCES -> {
                context.getSharedPreferences("youtube_prefs", Context.MODE_PRIVATE)
                    .edit().putString("allowed_entries", plain).apply()
                true
            }
            C_LINKS -> {
                context.getSharedPreferences("resource_links_prefs", Context.MODE_PRIVATE)
                    .edit().putString("links", plain).apply()
                true
            }
            C_HOME -> {
                val arr = JSONObject(plain).optJSONArray("apps") ?: JSONArray()
                db.setHomeApps((0 until arr.length()).map { arr.getString(it) })
                // The dock is already in the database, so the one-time carry-over from
                // preferences must not run afterwards and overwrite what was just restored.
                db.putSetting("dock_migrated_v1", "1")
                true
            }
            C_WHITELIST -> {
                val arr = JSONArray(plain)
                WhitelistManager.saveWhitelistedPackages(
                    context,
                    (0 until arr.length()).map { arr.getString(it) }.toSet()
                )
                true
            }
            C_STATS -> { FocusStats.import(context, plain); true }
            C_COLLECTIONS -> {
                val arr = JSONArray(plain)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.addCollection(o.getString("id"), o.getString("name"), o.optString("emoji"))
                }
                true
            }
            C_ALARMS -> {
                val arr = JSONArray(plain)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    Alarms.save(
                        context,
                        Alarm(
                            id = o.getLong("id"),
                            hour = o.getInt("hour"),
                            minute = o.getInt("minute"),
                            label = o.optString("label"),
                            days = o.optString("days").split(",").filter { it.isNotBlank() }.map { it.toInt() }.toSet(),
                            enabled = o.optBoolean("enabled", true),
                            special = o.optBoolean("special", false),
                            durationDays = o.optInt("durationDays", 0),
                            createdAt = o.optLong("createdAt"),
                            faceUnlock = o.optBoolean("faceUnlock", false)
                        )
                    )
                }
                true
            }
            C_POMODORO -> { loadPrefs(context, "pomodoro_prefs", plain); true }
            C_SESSION -> { loadPrefs(context, "focus_session_prefs", plain); true }
            C_LINKGATE -> { loadPrefs(context, "link_gate_prefs", plain); true }
            C_WATCH -> { loadPrefs(context, "youtube_progress", plain); true }
            C_LIMITS -> { loadPrefs(context, "app_usage_limits", plain); true }
            else -> false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /**
     * True when this phone already holds something worth keeping.
     *
     * The gate on a restore. Anything here means the local copy wins and the server is brought
     * up to date instead of the other way round.
     */
    fun hasLocalData(context: Context): Boolean = try {
        val db = FocusDatabaseHelper(context)
        db.getAllHabits().isNotEmpty() ||
            Tasks.goals(context).isNotEmpty() ||
            Tasks.reminders(context).isNotEmpty()
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }




    // ---------------------------------------------------------------- the enrolled face

    /** Encrypts the face with the account key and mirrors it to the server. */
    fun uploadFace(context: Context, plainB64: String): Boolean {
        val p = prefs(context)
        val email = p.getString(KEY_EMAIL, null) ?: return false
        val verifier = p.getString(KEY_VERIFIER, null) ?: return false
        val material = p.getString(KEY_MATERIAL, null) ?: return false
        val key = SecretKeySpec(
            android.util.Base64.decode(material, android.util.Base64.NO_WRAP), "AES"
        )
        val sealed = CryptoBox.seal(key, plainB64)
        val body = JSONObject().apply {
            put("email", email)
            put("verifier", verifier)
            put("ciphertext", sealed.ciphertext)
            put("iv", sealed.iv)
        }
        return post("/face/upload", body) is Wire.Ok
    }

    /** Brings the enrolled face back and decrypts it, or null if there is none. */
    fun downloadFace(context: Context): String? {
        val p = prefs(context)
        val email = p.getString(KEY_EMAIL, null) ?: return null
        val verifier = p.getString(KEY_VERIFIER, null) ?: return null
        val material = p.getString(KEY_MATERIAL, null) ?: return null
        val body = JSONObject().apply {
            put("email", email)
            put("verifier", verifier)
        }
        val json = when (val r = post("/face/download", body)) {
            is Wire.Ok -> r.json
            else -> return null
        }
        val face = json.optJSONObject("face") ?: return null
        val key = SecretKeySpec(
            android.util.Base64.decode(material, android.util.Base64.NO_WRAP), "AES"
        )
        return CryptoBox.open(key, face.optString("ciphertext"), face.optString("iv"))
    }

    // ---------------------------------------------------------------- wire

    private sealed class Wire {
        data class Ok(val json: JSONObject) : Wire()
        /** The server answered and said no. */
        data class Refused(val message: String) : Wire()
        /** No answer at all -- offline, DNS, timeout. */
        data class Unreachable(val message: String) : Wire()
    }

    private fun post(path: String, body: JSONObject): Wire {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$BASE$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "FocusLauncher/1.0")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use(BufferedReader::readText)
                .orEmpty()

            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject()
            }

            if (code in 200..299 && json.optBoolean("ok")) Wire.Ok(json)
            else Wire.Refused(json.optString("error").ifBlank { "Server said $code." })
        } catch (e: Exception) {
            e.printStackTrace()
            Wire.Unreachable("Could not reach the server.")
        } finally {
            conn?.disconnect()
        }
    }
}
