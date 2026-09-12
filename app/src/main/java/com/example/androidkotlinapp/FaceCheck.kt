package com.example.androidkotlinapp

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** What the vision model made of a morning selfie: is it the same person, or not. */
sealed class FaceResult {
    /** The model says it is the same person -- the only thing the check asks. */
    object Match : FaceResult()
    /** Not the same person. */
    object NotRecognised : FaceResult()
    data class Error(val message: String) : FaceResult()
}

/**
 * Compares a fresh selfie to the enrolled face with Gemini.
 *
 * A live look, not a fingerprint: the point is that it cannot be beaten by pressing a sleeping
 * person's finger to a sensor. The model has to recognise the actual face in front of the
 * camera, awake and holding the phone.
 *
 * Two keys, tried in turn, so a quota-out first key falls back to the second. The response is a
 * single plain line, streamed, with the model's own reasoning turned off -- all three make it
 * quick, which matters at 5am.
 */
object FaceCheck {

    private val KEYS = listOf(
        "AIzaSyApZP0dm7k6oJnmrYQPNpZFLQC31gCSGTI",
        "AIzaSyAvk-_BhnLBf91rIaj8FzBHPbpJX_6P8zI"
    )

    private const val MODEL = "gemini-2.5-flash"
    private const val TIMEOUT_MS = 30_000

    fun compare(enrolledB64: String, freshB64: String): FaceResult {
        val prompt = buildPrompt()
        var lastError = "Could not reach the vision service."

        for (key in KEYS) {
            when (val r = callOnce(key, prompt, enrolledB64, freshB64)) {
                is FaceResult.Error -> lastError = r.message   // try the next key
                else -> return r
            }
        }
        return FaceResult.Error(lastError)
    }

    private fun buildPrompt(): String {
        return """
            You are the face check for an alarm app. IMAGE 1 is the enrolled owner. IMAGE 2 is
            a selfie just taken. Judge leniently; when unsure, say true.

            Decide only one thing: is IMAGE 2 the same person as IMAGE 1? Glasses, hair, and
            lighting make no difference -- judge the face.

            Reply with EXACTLY this one line and nothing else:
            ispersonsame: true/false
        """.trimIndent()
    }

    private fun callOnce(
        key: String,
        prompt: String,
        enrolledB64: String,
        freshB64: String
    ): FaceResult {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$MODEL:streamGenerateContent?alt=sse&key=$key"
            )
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(imagePart(enrolledB64))
                        put(imagePart(freshB64))
                    })
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0)
                    put("maxOutputTokens", 20)
                    // No chain-of-thought for a one-line answer; this is what makes it quick.
                    put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                })
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            if (code !in 200..299) {
                conn.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                return FaceResult.Error("Vision service said $code.")
            }

            // Stream in: each "data:" line carries a chunk of the answer; stitch them together.
            val builder = StringBuilder()
            conn.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") return@forEachLine
                    try {
                        val part = JSONObject(payload)
                            .optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")
                            ?.optJSONObject(0)?.optString("text")
                        if (part != null) builder.append(part)
                    } catch (e: Exception) {
                        // Non-JSON keepalive lines are ignored.
                    }
                }
            }
            parse(builder.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            FaceResult.Error("Could not reach the vision service.")
        } finally {
            conn?.disconnect()
        }
    }

    private fun imagePart(b64: String): JSONObject = JSONObject().apply {
        put("inline_data", JSONObject().apply {
            put("mime_type", "image/jpeg")
            put("data", b64)
        })
    }

    private fun parse(raw: String): FaceResult {
        val text = raw.lowercase()
        if (text.isBlank()) return FaceResult.Error("The vision service sent nothing.")

        val i = text.indexOf("ispersonsame")
        if (i < 0) return FaceResult.Error("Unreadable answer.")
        val after = text.substring(i + "ispersonsame".length).take(12)
        return when {
            after.contains("true") -> FaceResult.Match
            after.contains("false") -> FaceResult.NotRecognised
            else -> FaceResult.Error("Unreadable answer.")
        }
    }
}
