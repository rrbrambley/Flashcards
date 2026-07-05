package com.rrbrambley.flashcards.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationHelpersTest {

    @Test
    fun inSessionStreak_thresholds() {
        assertFalse(InSessionStreak.showsBadge(1))
        assertTrue(InSessionStreak.showsBadge(2))
        assertFalse(InSessionStreak.isHot(4))
        assertTrue(InSessionStreak.isHot(5))
    }

    @Test
    fun sessionProgress_positionIsCapped_andFractionClamped() {
        assertEquals(1, SessionProgress.position(currentCardIndex = 0, total = 3))
        assertEquals(3, SessionProgress.position(currentCardIndex = 5, total = 3)) // capped at total
        assertEquals(0f, SessionProgress.fraction(currentCardIndex = 0, total = 0)) // no divide-by-zero
        assertEquals(0.5f, SessionProgress.fraction(currentCardIndex = 1, total = 2))
        assertEquals(1f, SessionProgress.fraction(currentCardIndex = 9, total = 2)) // clamped
    }

    @Test
    fun credentialsProvided_needsBothFields() {
        assertTrue(credentialsProvided("a@b.com", "pw"))
        assertFalse(credentialsProvided("  ", "pw"))
        assertFalse(credentialsProvided("a@b.com", ""))
    }

    @Test
    fun groupHomeBySection_groupsConsecutiveSameSection_preservingOrder() {
        val items = listOf(
            HomeData(title = "A", section = "Continue"),
            HomeData(title = "B", section = "Continue"),
            HomeData(title = "C", section = "New"),
            HomeData(title = "D", section = null),
        )
        val groups = groupHomeBySection(items)
        assertEquals(listOf("Continue" to 2, "New" to 1, null to 1), groups.map { it.section to it.items.size })
    }
}
