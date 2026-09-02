package com.rrbrambley.flashcards.practice.grading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * n-best rescoring (#390/#425). These run on the JVM *and* the iOS simulator, which is what gives
 * the mobile clients real coverage of the rule — the panels themselves need a UI harness.
 */
class VoiceNBestTest {
    private val capitals = listOf("Paris", "Tokyo", "Rome", "Madrid")

    @Test
    fun `takes a lower-ranked choice when the top one names no option`() {
        // "elephant" scores ≈0.13 against every option — under the floor, so the walk has to reach
        // the second hypothesis to find Paris. The reported failure: the answer is in the list, just
        // not ranked first, because the recogniser's language model prefers everyday words.
        assertEquals(0, matchSpokenChoiceAmong(listOf("elephant", "paris"), capitals))
    }

    @Test
    fun `keeps the recogniser's ranking when the top hypothesis already names an option`() {
        assertEquals(1, matchSpokenChoiceAmong(listOf("tokyo", "paris"), capitals))
    }

    @Test
    fun `re-prompts rather than guessing when no hypothesis names an option`() {
        assertNull(matchSpokenChoiceAmong(listOf("banana", "bandana", "bahama"), capitals))
    }

    @Test
    fun `an empty hypothesis list names nothing`() {
        assertNull(matchSpokenChoiceAmong(emptyList(), capitals))
    }

    @Test
    fun `grades a lower-ranked hypothesis that is correct`() {
        // "pear iss" scores ≈0.63 against "Paris" — nowhere near Test's 0.85 threshold, so the top
        // hypothesis is genuinely wrong and the second has to be reached.
        assertEquals("paris", pickSpokenAnswer(listOf("pear iss", "paris"), "Paris"))
    }

    @Test
    fun `keeps the top hypothesis when it is already correct`() {
        assertEquals("paris", pickSpokenAnswer(listOf("paris", "parris"), "Paris"))
    }

    /**
     * Rescoring must not become "keep looking until something is right". A genuinely wrong answer
     * stays wrong, recorded as the top hypothesis — what they most likely said — so the recap and
     * answer stats show the real utterance.
     */
    @Test
    fun `falls back to the top hypothesis when none is correct`() {
        assertEquals("lyon", pickSpokenAnswer(listOf("lyon", "leon", "lion"), "Paris"))
    }

    @Test
    fun `honours alternative answers when choosing`() {
        assertEquals(
            "nyc",
            pickSpokenAnswer(listOf("bicycle", "nyc"), "New York", listOf("NYC")),
        )
    }

    @Test
    fun `an empty hypothesis list grades nothing`() {
        assertNull(pickSpokenAnswer(emptyList(), "Paris"))
    }
}
