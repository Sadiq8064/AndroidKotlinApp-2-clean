package com.example.androidkotlinapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One run of the phone's clock timer, from the moment it started to the moment it stopped. */
data class TimerRun(
    val startedAt: Long,
    val endedAt: Long,
    val seconds: Int,
    /** False when the user cancelled it early rather than letting it ring. */
    val completed: Boolean
)

/** What a day's timers added up to. */
data class TimerDay(
    val runs: Int,
    val totalSeconds: Int,
    val completedRuns: Int
)

/**
 * A record of the timers the user ran in their own clock app.
 *
 * Android exposes no way to read another app's timer, so the times here are measured rather
 * than reported: the clock posts an ongoing notification while a timer runs and takes it down
 * when the timer stops, and the gap between those two moments is the run. That is sturdier
 * than reading the countdown text, which every manufacturer words differently.
 */
object TimerLog {

    private const val PREFS = "timer_log_prefs"
    private const val KEY_RUNS = "runs"
    private const val KEY_OPEN_START = "open_start"

    /** Anything shorter than this is a stray notification, not a timer someone meant to set. */
    private const val MIN_RUN_SECONDS = 5

    private fun dayKey(atMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(atMs))

    // ---------------------------------------------------------------- recording

    /** Called when the clock's timer notification appears. */
    fun onTimerStarted(context: Context, atMs: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // A timer already being tracked keeps its original start: the clock updates its
        // notification every second, and each update must not look like a new timer.
        if (prefs.getLong(KEY_OPEN_START, 0L) > 0L) return
        prefs.edit().putLong(KEY_OPEN_START, atMs).apply()
    }

    /** Called when it disappears, whether it rang or the user cancelled it. */
    fun onTimerEnded(context: Context, atMs: Long, completed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getLong(KEY_OPEN_START, 0L)
        if (start <= 0L) return
        prefs.edit().remove(KEY_OPEN_START).apply()

        val seconds = ((atMs - start) / 1000).toInt()
        if (seconds < MIN_RUN_SECONDS) return

        val runs = all(context) + TimerRun(start, atMs, seconds, completed)
        // A month of history is plenty for a daily view and keeps the file small.
        val cutoff = atMs - 31L * 24 * 60 * 60 * 1000
        save(context, runs.filter { it.startedAt >= cutoff })
    }

    /** True while a timer is being tracked, so the UI can say one is running. */
    fun isRunning(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_OPEN_START, 0L) > 0L

    // ---------------------------------------------------------------- reading

    fun all(context: Context): List<TimerRun> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RUNS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TimerRun(
                    o.optLong("startedAt"),
                    o.optLong("endedAt"),
                    o.optInt("seconds"),
                    o.optBoolean("completed", true)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun save(context: Context, runs: List<TimerRun>) {
        val arr = JSONArray()
        runs.forEach {
            arr.put(
                JSONObject()
                    .put("startedAt", it.startedAt)
                    .put("endedAt", it.endedAt)
                    .put("seconds", it.seconds)
                    .put("completed", it.completed)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_RUNS, arr.toString()).apply()
    }

    /** Today's tally: how many timers, how long in total, how many ran to the end. */
    fun today(context: Context): TimerDay = forDay(context, System.currentTimeMillis())

    fun forDay(context: Context, atMs: Long): TimerDay {
        val key = dayKey(atMs)
        val runs = all(context).filter { dayKey(it.startedAt) == key }
        return TimerDay(
            runs = runs.size,
            totalSeconds = runs.sumOf { it.seconds },
            completedRuns = runs.count { it.completed }
        )
    }

    /** The last seven days, oldest first, for a small bar chart. */
    fun lastWeek(context: Context): List<Pair<String, TimerDay>> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val label = SimpleDateFormat("EEE", Locale.getDefault())
        return (0 until 7).map {
            val ms = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            label.format(java.util.Date(ms)) to forDay(context, ms)
        }
    }

    /** "1h 25m", "25m", "40s" -- whichever units actually carry information. */
    fun pretty(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }
}
