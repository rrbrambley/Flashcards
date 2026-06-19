package com.rrbrambley.flashcards.backend.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayNameDefaultTest {

    @Test
    fun usesTheExplicitDisplayNameWhenSet() {
        assertEquals("Rob B", AuthService.displayNameOrDefault("Rob B", "rob@example.com"))
    }

    @Test
    fun fallsBackToTheEmailLocalPartWhenNullOrBlank() {
        assertEquals("rob", AuthService.displayNameOrDefault(null, "rob@example.com"))
        assertEquals("rob", AuthService.displayNameOrDefault("   ", "rob@example.com"))
    }
}
