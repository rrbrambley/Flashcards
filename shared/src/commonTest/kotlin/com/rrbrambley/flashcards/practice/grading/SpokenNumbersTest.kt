package com.rrbrambley.flashcards.practice.grading

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spoken numbers written as digits so a correctly spoken numeric answer can grade correct (#390). */
class SpokenNumbersTest {

    @Test
    fun yearsReadAsTwoGroupsConcatenate() {
        // The motivating case: arithmetic would give 99, which is not what anyone said.
        assertContains(spokenNumberVariants("nineteen eighty"), "1980")
        assertContains(spokenNumberVariants("nineteen eighty four"), "1984")
        assertContains(spokenNumberVariants("twenty twenty four"), "2024")
        assertContains(spokenNumberVariants("nineteen oh five"), "1905")
    }

    @Test
    fun plainNumbersUseTheArithmeticReading() {
        assertContains(spokenNumberVariants("twenty three"), "23")
        assertContains(spokenNumberVariants("one hundred"), "100")
        assertContains(spokenNumberVariants("one hundred and five"), "105")
        assertContains(spokenNumberVariants("two thousand"), "2000")
        assertContains(spokenNumberVariants("three million"), "3000000")
    }

    @Test
    fun scaleWordsRuleOutTheConcatenatedReading() {
        // "two thousand" is 2000, never "2" then "1000".
        assertEquals(listOf("2000"), spokenNumberVariants("two thousand"))
    }

    @Test
    fun numbersInsideASentenceKeepTheirSurroundingWords() {
        assertContains(spokenNumberVariants("the year nineteen eighty"), "the year 1980")
        assertContains(spokenNumberVariants("twenty three skidoo"), "23 skidoo")
    }

    @Test
    fun bothReadingsAreOfferedWhenTheyDiffer() {
        val variants = spokenNumberVariants("nineteen eighty")
        assertContains(variants, "1980") // concatenated
        assertContains(variants, "99") // arithmetic
    }

    @Test
    fun transcriptsWithoutNumberWordsProduceNothing() {
        assertTrue(spokenNumberVariants("paris").isEmpty())
        assertTrue(spokenNumberVariants("").isEmpty())
        assertTrue(spokenNumberVariants("the quick brown fox").isEmpty())
    }

    @Test
    fun pickSpokenAnswer_matchesADigitAnswerFromSpokenWords() {
        // The whole point: this is graded wrong today.
        assertEquals("1980", pickSpokenAnswer(listOf("nineteen eighty"), "1980"))
        assertEquals("2024", pickSpokenAnswer(listOf("twenty twenty four"), "2024"))
        assertEquals("23", pickSpokenAnswer(listOf("twenty three"), "23"))
    }

    @Test
    fun pickSpokenAnswer_prefersTheTranscriptAsHeard() {
        // A word answer must still win on the words, and be recorded as spoken — the digit variant
        // ("1 piece") is only ever reached when every original has already failed.
        assertEquals("one piece", pickSpokenAnswer(listOf("one piece"), "One Piece"))
        assertEquals("one", pickSpokenAnswer(listOf("one"), "one"))
    }

    @Test
    fun pickSpokenAnswer_stillFallsBackToTheTopHypothesis() {
        // Nothing matches: record what they most likely said, unchanged.
        assertEquals("banana", pickSpokenAnswer(listOf("banana", "bandana"), "1980"))
        assertEquals("seventeen", pickSpokenAnswer(listOf("seventeen"), "Paris"))
    }

    @Test
    fun pickSpokenAnswer_triesEveryHypothesisAsHeardBeforeAnyDigits() {
        // "nineteen eighty" would match as digits, but a later hypothesis matches as heard — and a
        // real match beats a converted one.
        assertEquals(
            "paris",
            pickSpokenAnswer(listOf("nineteen eighty", "paris"), "Paris"),
        )
    }

    @Test
    fun pickSpokenAnswer_matchesAnAlternativeAnswerToo() {
        assertEquals("1980", pickSpokenAnswer(listOf("nineteen eighty"), "MCMLXXX", listOf("1980")))
    }
}
