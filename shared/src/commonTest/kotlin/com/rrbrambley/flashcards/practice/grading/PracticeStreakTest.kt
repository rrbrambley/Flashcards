package com.rrbrambley.flashcards.practice.grading

import kotlin.test.Test
import kotlin.test.assertEquals

class PracticeStreakTest {
    @Test
    fun empty_isZero() = assertEquals(0, trailingCorrectStreak(emptyList()))

    @Test
    fun allCorrect_isTheFullCount() = assertEquals(3, trailingCorrectStreak(listOf(true, true, true)))

    @Test
    fun endingOnAMiss_isZero() = assertEquals(0, trailingCorrectStreak(listOf(true, true, false)))

    @Test
    fun countsOnlyTheTrailingRun_notEarlierCorrects() =
        assertEquals(2, trailingCorrectStreak(listOf(true, false, true, true)))

    @Test
    fun singleCorrect_isOne() = assertEquals(1, trailingCorrectStreak(listOf(true)))

    @Test
    fun singleMiss_isZero() = assertEquals(0, trailingCorrectStreak(listOf(false)))
}
