package com.rrbrambley.flashcards.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SessionOrderingTest {

    @Test
    fun shuffleOff_keepsSavedOrder() {
        val items = (0 until 10).toList()
        assertEquals(items, SessionOrdering.order(items, shuffle = false, seed = 12345))
    }

    @Test
    fun sameSeed_producesSameOrder() {
        val a = SessionOrdering.order(size = 30, shuffle = true, seed = 777)
        val b = SessionOrdering.order(size = 30, shuffle = true, seed = 777)
        assertEquals(a, b, "a seeded shuffle must be deterministic (stable across resume/re-render)")
    }

    @Test
    fun differentSeeds_generallyDiffer() {
        val a = SessionOrdering.order(size = 30, shuffle = true, seed = 1)
        val b = SessionOrdering.order(size = 30, shuffle = true, seed = 2)
        assertNotEquals(a, b)
    }

    @Test
    fun shuffle_isAPermutation_losingNoCards() {
        val order = SessionOrdering.order(size = 25, shuffle = true, seed = 999)
        assertEquals((0 until 25).toList(), order.sorted(), "every card must appear exactly once")
    }

    @Test
    fun tinyDecks_areUnchanged() {
        // Nothing to shuffle with 0 or 1 cards — return as-is regardless of seed.
        assertEquals(emptyList(), SessionOrdering.order(emptyList<Int>(), shuffle = true, seed = 5))
        assertEquals(listOf(7), SessionOrdering.order(listOf(7), shuffle = true, seed = 5))
    }

    @Test
    fun shuffle_actuallyReorders() {
        // For a reasonable deck a shuffle should not coincidentally equal the identity order.
        val order = SessionOrdering.order(size = 20, shuffle = true, seed = 42)
        assertTrue(order != (0 until 20).toList(), "expected a reordering, not the identity")
    }
}
