package com.rrbrambley.flashcards.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeModeTest {

    @Test
    fun keys_matchTheBackendContract() {
        assertEquals("flashcards", PracticeMode.Classic.key)
        assertEquals("test", PracticeMode.Test.key)
        assertEquals("multiple_choice", PracticeMode.MultipleChoice.key)
    }

    @Test
    fun fromKey_resolvesEachKnownKey() {
        assertEquals(PracticeMode.Classic, PracticeMode.fromKey("flashcards"))
        assertEquals(PracticeMode.Test, PracticeMode.fromKey("test"))
        assertEquals(PracticeMode.MultipleChoice, PracticeMode.fromKey("multiple_choice"))
    }

    @Test
    fun fromKey_unknownOrEmpty_fallsBackToClassic() {
        assertEquals(PracticeMode.Classic, PracticeMode.fromKey("legacy_mode"))
        assertEquals(PracticeMode.Classic, PracticeMode.fromKey(""))
    }

    /**
     * The practice runner enables voice and the home feed advertises it (#434); both read this, so
     * a card can never promise voice for a run that then can't use it.
     */
    @Test
    fun supportsVoice_testAndMultipleChoiceOnly() {
        assertTrue(PracticeMode.Test.supportsVoice)
        assertTrue(PracticeMode.MultipleChoice.supportsVoice)
        // Classic is flip-and-swipe: no answer to say, so voice has nothing to grade.
        assertFalse(PracticeMode.Classic.supportsVoice)
    }

    @Test
    fun supportsVoice_resolvesFromAPersistedKey() {
        assertTrue(PracticeMode.supportsVoice("test"))
        assertTrue(PracticeMode.supportsVoice("multiple_choice"))
        assertFalse(PracticeMode.supportsVoice("flashcards"))
        // Unknown keys fall back to Classic, so they never claim voice support.
        assertFalse(PracticeMode.supportsVoice("something_new"))
    }
}
