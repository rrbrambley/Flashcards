package com.rrbrambley.flashcards.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DeckLibraryTest {

    private fun deck(id: Long, title: String, tags: List<String> = emptyList()) =
        FlashcardDeck(id = id, title = title, flashcards = emptyList(), tags = tags)

    private val decks = listOf(
        deck(1, "World Capitals", tags = listOf("Geography")),
        deck(2, "Spanish Verbs", tags = listOf("Language")),
        deck(3, "capital investment", tags = emptyList()),
    )

    // ---- filter ----

    @Test
    fun filter_blankQuery_returnsAll() {
        assertEquals(decks, DeckLibrary.filter(decks, "   "))
    }

    @Test
    fun filter_matchesTitleCaseInsensitively() {
        val result = DeckLibrary.filter(decks, "capital")
        assertEquals(listOf(1L, 3L), result.map { it.id })
    }

    @Test
    fun filter_matchesAnyTag() {
        val result = DeckLibrary.filter(decks, "language")
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun filter_noMatch_returnsEmpty() {
        assertEquals(emptyList(), DeckLibrary.filter(decks, "zzz"))
    }

    // ---- sort ----

    @Test
    fun sort_alphabetical_isCaseInsensitive() {
        val result = DeckLibrary.sort(decks, DeckSortOrder.Alphabetical, emptyMap())
        // "capital investment" sorts with the C's, not after the lowercase letters.
        assertEquals(listOf("capital investment", "Spanish Verbs", "World Capitals"), result.map { it.title })
    }

    @Test
    fun sort_recentlyPracticed_ordersByLastPracticedThenIdDesc() {
        val lastPracticed = mapOf(1L to 100L, 2L to 300L)
        val result = DeckLibrary.sort(decks, DeckSortOrder.RecentlyPracticed, lastPracticed)
        // 2 (300) then 1 (100) then the never-practiced 3 (falls back, tie-broken by id desc).
        assertEquals(listOf(2L, 1L, 3L), result.map { it.id })
    }

    @Test
    fun sort_recentlyPracticed_neverPracticed_fallsBackToIdDesc() {
        val result = DeckLibrary.sort(decks, DeckSortOrder.RecentlyPracticed, emptyMap())
        assertEquals(listOf(3L, 2L, 1L), result.map { it.id })
    }

    // ---- query (filter + sort) ----

    @Test
    fun query_filtersThenSorts() {
        val result = DeckLibrary.query(decks, "capital", DeckSortOrder.Alphabetical, emptyMap())
        assertEquals(listOf("capital investment", "World Capitals"), result.map { it.title })
    }
}
