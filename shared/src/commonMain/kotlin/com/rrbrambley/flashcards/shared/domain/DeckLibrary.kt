package com.rrbrambley.flashcards.shared.domain

/**
 * How the library list is ordered. Shared by Android + iOS (FLA-193).
 *
 * Entries are PascalCase (not UPPER_SNAKE) on purpose: Kotlin/Native lowercases only the first letter
 * for Swift, so `Alphabetical` → `.alphabetical` (clean) whereas `ALPHABETICAL` → `.aLPHABETICAL`.
 */
enum class DeckSortOrder {
    Alphabetical,
    RecentlyPracticed,
}

/**
 * Pure library-list transforms shared across platforms (FLA-193) so Android + iOS apply identical
 * search + sort rules instead of hand-reimplementing them per client. No platform or UI dependencies.
 */
object DeckLibrary {

    /**
     * Filters [decks] by [query], matching the deck title OR any of its tags (the category surfaced
     * in the UI), case-insensitively. A blank/whitespace [query] returns [decks] unchanged.
     */
    fun filter(decks: List<FlashcardDeck>, query: String): List<FlashcardDeck> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return decks
        return decks.filter { deck ->
            deck.title.contains(trimmed, ignoreCase = true) ||
                deck.tags.any { it.contains(trimmed, ignoreCase = true) }
        }
    }

    /**
     * Sorts [decks] by [order]:
     * - [DeckSortOrder.Alphabetical] — A–Z by lowercased title.
     * - [DeckSortOrder.RecentlyPracticed] — most-recently-practiced first, using [lastPracticedMillis]
     *   (deck id → last-practiced epoch millis); never-practiced decks fall below practiced ones,
     *   tie-broken by id descending (newest-created first).
     */
    fun sort(
        decks: List<FlashcardDeck>,
        order: DeckSortOrder,
        lastPracticedMillis: Map<Long, Long>,
    ): List<FlashcardDeck> = when (order) {
        DeckSortOrder.Alphabetical -> decks.sortedBy { it.title.lowercase() }
        DeckSortOrder.RecentlyPracticed -> decks.sortedWith(
            compareByDescending<FlashcardDeck> { lastPracticedMillis[it.id] ?: 0L }.thenByDescending { it.id },
        )
    }

    /** Convenience: [filter] by [query] then [sort] by [order]. */
    fun query(
        decks: List<FlashcardDeck>,
        query: String,
        order: DeckSortOrder,
        lastPracticedMillis: Map<Long, Long>,
    ): List<FlashcardDeck> = sort(filter(decks, query), order, lastPracticedMillis)
}
