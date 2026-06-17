package com.rrbrambley.flashcards.backend.streaks

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakComputeTest {

    private val today = LocalDate.of(2026, 6, 16)
    private fun days(vararg offsets: Long): Set<LocalDate> = offsets.map { today.minusDays(it) }.toSet()

    @Test
    fun noDates_isZero() {
        val streak = StreakService.computeStreak(emptySet(), today)
        assertEquals(0, streak.current)
        assertEquals(0, streak.longest)
    }

    @Test
    fun singleDayToday_currentAndLongestAreOne() {
        val streak = StreakService.computeStreak(days(0), today)
        assertEquals(1, streak.current)
        assertEquals(1, streak.longest)
    }

    @Test
    fun consecutiveTodayAndYesterday_currentIsTwo() {
        val streak = StreakService.computeStreak(days(0, 1), today)
        assertEquals(2, streak.current)
        assertEquals(2, streak.longest)
    }

    @Test
    fun yesterdayOnly_graceKeepsCurrentAlive() {
        // Hasn't practiced today yet, but yesterday counts — one grace day before the streak breaks.
        val streak = StreakService.computeStreak(days(1), today)
        assertEquals(1, streak.current)
        assertEquals(1, streak.longest)
    }

    @Test
    fun gapBeforeToday_currentResetsButLongestPreserved() {
        // A 3-day run ending 4 days ago; nothing today/yesterday → current 0, longest 3.
        val streak = StreakService.computeStreak(days(4, 5, 6), today)
        assertEquals(0, streak.current)
        assertEquals(3, streak.longest)
    }

    @Test
    fun multipleRuns_longestIsTheMaxRun() {
        // Current run of 2 (today + yesterday); an older run of 4.
        val streak = StreakService.computeStreak(days(0, 1, 5, 6, 7, 8), today)
        assertEquals(2, streak.current)
        assertEquals(4, streak.longest)
    }
}
