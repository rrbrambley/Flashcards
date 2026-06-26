package com.rrbrambley.flashcards.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PracticeAnswerTest {
    private fun answer(sequence: Int, correct: Boolean) = PracticeAnswer(
        answerUid = "u$sequence",
        cardUid = "c$sequence",
        correct = correct,
        sequence = sequence,
        answeredAtMillis = sequence.toLong(),
    )

    @Test
    fun currentStreak_isTheTrailingRunOfCorrect() {
        val answers = listOf(answer(0, true), answer(1, false), answer(2, true), answer(3, true))
        assertEquals(2, answers.currentStreak())
    }

    @Test
    fun currentStreak_isZeroWhenTheLastAnswerWasWrong() {
        val answers = listOf(answer(0, true), answer(1, true), answer(2, false))
        assertEquals(0, answers.currentStreak())
    }

    @Test
    fun currentStreak_ofEmptyIsZero() {
        assertEquals(0, emptyList<PracticeAnswer>().currentStreak())
    }

    @Test
    fun longestStreak_findsTheMaxRunAnywhere() {
        val answers = listOf(
            answer(0, true),
            answer(1, true),
            answer(2, true),
            answer(3, false),
            answer(4, true),
            answer(5, true),
        )
        assertEquals(3, answers.longestStreak())
        assertEquals(2, answers.currentStreak())
    }

    @Test
    fun streaks_sortByPlayOrderNotListOrder() {
        // Out of order on input; sequence (play order) is what matters.
        val answers = listOf(answer(2, true), answer(0, true), answer(1, true))
        assertEquals(3, answers.currentStreak())
        assertEquals(3, answers.longestStreak())
    }
}
