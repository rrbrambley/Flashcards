package com.rrbrambley.flashcards.practice.grading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextAnswerGradingTest {

    @Test
    fun acceptsAnExactMatch() {
        val grade = gradeTextAnswer("Paris", "Paris")
        assertTrue(grade.correct)
        assertEquals(1.0, grade.similarity)
    }

    @Test
    fun ignoresCaseAndSurroundingOrCollapsingWhitespace() {
        assertTrue(gradeTextAnswer("  pARiS ", "Paris").correct)
        assertTrue(gradeTextAnswer("new   york", "New York").correct)
    }

    @Test
    fun acceptsATypoWithinTolerance() {
        // One transposed/missing letter in a long word stays >= 0.85.
        val grade = gradeTextAnswer("Mississipi", "Mississippi")
        assertTrue(grade.similarity >= TEXT_ANSWER_THRESHOLD)
        assertTrue(grade.correct)
    }

    @Test
    fun rejectsAnAnswerBelowTolerance() {
        assertFalse(gradeTextAnswer("cat", "dog").correct)
        assertFalse(gradeTextAnswer("Berlin", "Paris").correct)
    }

    @Test
    fun rejectsAnEmptyAnswer() {
        val grade = gradeTextAnswer("", "Paris")
        assertFalse(grade.correct)
        assertEquals(0.0, grade.similarity)
    }
}
