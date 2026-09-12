package com.example.androidkotlinapp

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Calendar arithmetic over `yyyy-MM-dd` IST day labels.
 *
 * Everything works on `yyyy-MM-dd` label strings rather than on timestamps, so comparing and
 * stepping between days never has to think about hours, offsets or daylight saving.
 */
object HabitStats {

    private const val DATE_PATTERN = "yyyy-MM-dd"

    /**
     * Today's calendar day, from the device clock in its own timezone.
     *
     * The device clock is trusted here on purpose: once a session is running the blocker
     * keeps Settings out of reach, so there is no route to the date picker to game a streak
     * or dodge a penalty from inside a session.
     */
    fun today(): String = SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date())

    private fun formatter() = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    private fun parse(date: String): Date? = try {
        formatter().parse(date)
    } catch (e: Exception) {
        null
    }

    fun shiftDays(date: String, days: Int): String {
        val parsed = parse(date) ?: return date
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, days)
        }
        return formatter().format(calendar.time)
    }

    fun previousDay(date: String): String = shiftDays(date, -1)

    /** Whole days from [from] to [to]; negative when [to] is earlier. */
    fun daysBetween(from: String, to: String): Int {
        val start = parse(from) ?: return 0
        val end = parse(to) ?: return 0
        return ((end.time - start.time) / 86_400_000L).toInt()
    }

    /** Inclusive list of every day label from [from] to [to]. */
    fun dateRange(from: String, to: String): List<String> {
        val span = daysBetween(from, to)
        if (span < 0) return emptyList()
        val dates = ArrayList<String>(span + 1)
        var cursor = from
        repeat(span + 1) {
            dates.add(cursor)
            cursor = shiftDays(cursor, 1)
        }
        return dates
    }

    /**
     * Current streak: the run of consecutive days performed, ending today.
     *
     * A day that goes unmarked breaks the run outright -- the next completion starts again
     * from one. Today is treated as still open: if it has not been ticked yet the streak is
     * measured up to yesterday, so an unfinished day does not wipe the run before it ends.
     */
    fun currentStreak(completions: Set<String>, today: String): Int {
        if (completions.isEmpty()) return 0
        var cursor = if (completions.contains(today)) today else previousDay(today)
        var streak = 0
        while (completions.contains(cursor)) {
            streak++
            cursor = previousDay(cursor)
        }
        return streak
    }

    /** Longest run of consecutive completed days ever recorded. */
    fun longestStreak(completions: Set<String>): Int {
        if (completions.isEmpty()) return 0
        var longest = 0
        for (date in completions) {
            // Only count from the start of a run to avoid re-walking the same one.
            if (completions.contains(previousDay(date))) continue
            var run = 0
            var cursor = date
            while (completions.contains(cursor)) {
                run++
                cursor = shiftDays(cursor, 1)
            }
            if (run > longest) longest = run
        }
        return longest
    }
}
