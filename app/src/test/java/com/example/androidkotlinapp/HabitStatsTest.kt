package com.example.androidkotlinapp

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitStatsTest {

    @Test
    fun `shiftDays crosses month and year boundaries`() {
        assertEquals("2026-03-01", HabitStats.shiftDays("2026-02-28", 1))
        assertEquals("2026-01-01", HabitStats.shiftDays("2025-12-31", 1))
        assertEquals("2025-12-31", HabitStats.previousDay("2026-01-01"))
        // 2028 is a leap year, so February has a 29th.
        assertEquals("2028-02-29", HabitStats.shiftDays("2028-02-28", 1))
    }

    @Test
    fun `daysBetween counts whole days in both directions`() {
        assertEquals(0, HabitStats.daysBetween("2026-08-10", "2026-08-10"))
        assertEquals(5, HabitStats.daysBetween("2026-08-10", "2026-08-15"))
        assertEquals(-5, HabitStats.daysBetween("2026-08-15", "2026-08-10"))
        assertEquals(31, HabitStats.daysBetween("2026-07-10", "2026-08-10"))
    }

    @Test
    fun `dateRange is inclusive at both ends`() {
        val range = HabitStats.dateRange("2026-08-08", "2026-08-11")
        assertEquals(listOf("2026-08-08", "2026-08-09", "2026-08-10", "2026-08-11"), range)
    }

    @Test
    fun `currentStreak counts consecutive days ending today`() {
        val completions = setOf("2026-08-08", "2026-08-09", "2026-08-10")
        assertEquals(3, HabitStats.currentStreak(completions, "2026-08-10"))
    }

    @Test
    fun `an unfinished today does not break the run yet`() {
        // Today is not ticked, but yesterday and before are: the run still stands.
        val completions = setOf("2026-08-08", "2026-08-09")
        assertEquals(2, HabitStats.currentStreak(completions, "2026-08-10"))
    }

    @Test
    fun `missing a whole day resets the streak to zero`() {
        // 2026-08-09 was skipped entirely, and today is not done either.
        val completions = setOf("2026-08-06", "2026-08-07", "2026-08-08")
        assertEquals(0, HabitStats.currentStreak(completions, "2026-08-10"))
    }

    @Test
    fun `a completion after a break starts again from one`() {
        val completions = setOf("2026-08-06", "2026-08-07", "2026-08-10")
        assertEquals(1, HabitStats.currentStreak(completions, "2026-08-10"))
    }

    @Test
    fun `no completions means no streak`() {
        assertEquals(0, HabitStats.currentStreak(emptySet(), "2026-08-10"))
    }

    @Test
    fun `longestStreak finds the best run regardless of position`() {
        val completions = setOf(
            "2026-08-01", "2026-08-02", "2026-08-03", "2026-08-04", // run of 4
            "2026-08-07",                                            // run of 1
            "2026-08-09", "2026-08-10"                               // run of 2
        )
        assertEquals(4, HabitStats.longestStreak(completions))
    }

    @Test
    fun `longestStreak spans a month boundary`() {
        val completions = setOf("2026-07-30", "2026-07-31", "2026-08-01", "2026-08-02")
        assertEquals(4, HabitStats.longestStreak(completions))
    }

}
