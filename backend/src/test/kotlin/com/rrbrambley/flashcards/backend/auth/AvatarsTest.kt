package com.rrbrambley.flashcards.backend.auth

import com.rrbrambley.flashcards.shared.api.AvatarDto
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarsTest {
    // Avatars is a process-global singleton; reset it so a configured base doesn't leak to other tests.
    @AfterTest fun reset() = Avatars.configure(null)

    @Test
    fun resolvesUrlsAndCatalogWhenConfigured() {
        Avatars.configure("https://cdn.example.com/") // trailing slash trimmed
        assertEquals("https://cdn.example.com/avatars/dragon.png", Avatars.urlFor("dragon"))
        assertNull(Avatars.urlFor("not-a-beast")) // unknown key
        assertNull(Avatars.urlFor(null))
        val catalog = Avatars.catalog()
        assertEquals(Avatars.keys.size, catalog.size)
        assertEquals(AvatarDto("dragon", "https://cdn.example.com/avatars/dragon.png"), catalog.first())
    }

    @Test
    fun degradesWhenCdnUnconfigured() {
        Avatars.configure(null)
        assertNull(Avatars.urlFor("dragon"))
        assertTrue(Avatars.catalog().isEmpty())
    }

    @Test
    fun validatesKeys() {
        assertTrue(Avatars.isValid("dragon"))
        assertTrue(Avatars.isValid("pegasus"))
        assertTrue(Avatars.isValid("kitsune"))
        assertTrue(Avatars.isValid("basilisk"))
        assertFalse(Avatars.isValid("wyvern"))
        assertEquals(20, Avatars.keys.size)
    }
}
