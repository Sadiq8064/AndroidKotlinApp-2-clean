package com.example.androidkotlinapp

import android.content.Context

/**
 * How many face checks a dismissal still owes.
 *
 * A face-checked alarm is not beaten by one selfie. It takes three, five minutes apart: pass
 * one and the alarm goes quiet, then rings again for the next, so getting up cannot be a single
 * half-asleep tap followed by falling back into bed. The count is kept per alarm so two alarms
 * mid-dismissal never tread on each other.
 *
 * All three rounds ask the same thing: is this the right person, live in front of the camera?
 */
object FaceRounds {

    const val REQUIRED = 3

    /** Five minutes between rounds -- long enough that you cannot just wait it out in bed. */
    const val GAP_MILLIS = 5 * 60_000L

    private const val PREFS = "face_rounds"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Rounds already passed for this alarm, 0 to [REQUIRED]. */
    fun passed(context: Context, id: Long): Int =
        prefs(context).getInt(key(id), 0)

    /**
     * A round sequence left unfinished this long is treated as abandoned.
     *
     * The three rounds run over a few minutes; anything older than this is a test or a session
     * the user walked away from, and must not keep the phone locked out of power-off forever.
     */
    private const val STALE_AFTER_MS = 30 * 60_000L

    /** Records a passed round and returns the new count, stamping when it happened. */
    fun recordPass(context: Context, id: Long): Int {
        val next = passed(context, id) + 1
        prefs(context).edit()
            .putInt(key(id), next)
            .putLong(timeKey(id), System.currentTimeMillis())
            .apply()
        return next
    }

    /** Wipes the count, once every round is done or the alarm is abandoned. */
    fun reset(context: Context, id: Long) {
        prefs(context).edit().remove(key(id)).remove(timeKey(id)).apply()
    }

    /**
     * True while any alarm is part-way through its rounds -- at least one passed, but not all.
     *
     * This is what keeps the phone from being powered off during the gaps between rounds, when
     * nothing is actually ringing: the commitment is not met until the third check.
     */
    fun anyInProgress(context: Context): Boolean {
        return try {
            val p = prefs(context)
            val now = System.currentTimeMillis()
            var found = false
            val editor = p.edit()
            for ((k, v) in p.all) {
                if (!k.startsWith("rounds_")) continue
                val count = (v as? Int) ?: continue
                if (count !in 1 until REQUIRED) continue
                val id = k.removePrefix("rounds_")
                val ts = p.getLong("roundts_$id", 0L)
                if (now - ts <= STALE_AFTER_MS) {
                    found = true
                } else {
                    // Abandoned -- clear it so it can never hold power-off hostage again.
                    editor.remove(k).remove("roundts_$id")
                }
            }
            editor.apply()
            found
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun key(id: Long) = "rounds_$id"
    private fun timeKey(id: Long) = "roundts_$id"
}
