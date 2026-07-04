package com.rrbrambley.flashcards.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
