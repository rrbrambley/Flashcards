package com.rrbrambley.flashcards.backend.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [Profanity]. This is a thin wrapper over the `profanity-filter` library, so the
 * intent isn't to re-test the dictionary exhaustively — just to pin the contract we depend on:
 * obvious profanity is caught, ordinary educational discussion is not, and a few substring
 * "Scunthorpe problem" cases don't false-positive.
 */
class ProfanityTest {

    @Test
    fun flags_obvious_profanity() {
        assertTrue(Profanity.isProfane("this is fucking ridiculous"))
        assertTrue(Profanity.isProfane("what a piece of shit"))
    }

    @Test
    fun allows_ordinary_discussion_content() {
        for (clean in listOf(
            "The capital of France is Paris.",
            "I always mix up affect and effect — any tips?",
            "The mitochondria is the powerhouse of the cell",
            "Great deck, this really helped me study for the exam!",
        )) {
            assertFalse(Profanity.isProfane(clean), "expected clean: \"$clean\"")
        }
    }

    @Test
    fun does_not_false_positive_on_innocent_substrings() {
        // Words that merely *contain* the letters of a rude word must not be flagged.
        for (innocent in listOf("class", "assignment", "assessment", "grassland", "Scunthorpe")) {
            assertFalse(Profanity.isProfane(innocent), "expected not profane: \"$innocent\"")
        }
    }

    @Test
    fun empty_and_whitespace_are_not_profane() {
        assertFalse(Profanity.isProfane(""))
        assertFalse(Profanity.isProfane("   "))
    }
}
