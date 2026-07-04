package com.rrbrambley.flashcards.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MonogramTest {

    // ---- initials ----

    @Test
    fun initials_firstAndLastWord_uppercased() {
        assertEquals("RB", Monogram.initials("Rob Brambley"))
        assertEquals("RB", Monogram.initials("rob van der brambley")) // first + last word only
    }

    @Test
    fun initials_singleWord_isOneLetter() {
        assertEquals("M", Monogram.initials("madonna"))
    }

    @Test
    fun initials_blankOrNull_isQuestionMark() {
        assertEquals("?", Monogram.initials("   "))
        assertEquals("?", Monogram.initials(null))
    }

    // ---- hue ----

    @Test
    fun hue_isStableAndInRange() {
        // Deterministic: hash*31+code % 360 over "Rob" → 261.
        assertEquals(261, Monogram.hue("Rob"))
        assertEquals(Monogram.hue("Rob"), Monogram.hue("  Rob  ")) // trimmed → same seed
    }

    @Test
    fun hue_blankOrNull_isZero() {
        assertEquals(0, Monogram.hue(null))
        assertEquals(0, Monogram.hue("   "))
    }

    // ---- monogram name ----

    @Test
    fun name_prefersTrimmedDisplayName() {
        assertEquals("Rob B", Monogram.name(displayName = "  Rob B ", email = "rob@example.com"))
    }

    @Test
    fun name_fallsBackToEmailLocalPart() {
        assertEquals("rob", Monogram.name(displayName = "   ", email = "rob@example.com"))
        assertEquals("rob", Monogram.name(displayName = null, email = "rob@example.com"))
        assertEquals(null, Monogram.name(displayName = null, email = null))
    }
}
