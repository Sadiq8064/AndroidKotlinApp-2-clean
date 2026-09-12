package com.example.androidkotlinapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * The one enrolled face, kept where the alarm's check can reach it.
 *
 * A JPEG file for re-reading, and a copy mirrored (encrypted) to the server so a reinstall does
 * not lose the reference and lock the user out of their own alarm. A face changes over time, so
 * the check refreshes this with a recent selfie -- the reference is always roughly how the user
 * looks now.
 */
object FaceStore {

    private const val PREFS = "face_prefs"
    private const val KEY_ENROLLED = "enrolled"
    private const val FILE_NAME = "enrolled_face.jpg"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun isEnrolled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENROLLED, false) && file(context).exists()

    fun enrolledBitmap(context: Context): Bitmap? = try {
        val f = file(context)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    fun enrolledBase64(context: Context): String? =
        enrolledBitmap(context)?.toBase64Jpeg()

    /** Writes the face down, marks the user enrolled, and mirrors it up (best effort). */
    fun save(context: Context, bitmap: Bitmap) {
        try {
            file(context).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENROLLED, true).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            SyncClient.uploadFace(context, bitmap.toBase64Jpeg())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Pulls the face back from the server onto a phone that has none -- a fresh install. */
    fun restoreIfMissing(context: Context) {
        if (isEnrolled(context)) return
        try {
            val b64 = SyncClient.downloadFace(context) ?: return
            val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            file(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENROLLED, true).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
