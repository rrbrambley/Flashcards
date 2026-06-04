package com.rrbrambley.flashcards.library.ui

import com.rrbrambley.flashcards.shared.domain.FlashcardDeck

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object LoadingFailed : LibraryUiState
    data class ShowDecks(
        val decks: List<FlashcardDeck>,
    ) : LibraryUiState
}

/** How the library list is ordered. */
enum class DeckSortOrder {
    RecentlyPracticed,
    Alphabetical,
}
