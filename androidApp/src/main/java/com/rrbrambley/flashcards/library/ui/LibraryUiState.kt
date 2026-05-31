package com.rrbrambley.flashcards.library.ui

import com.rrbrambley.flashcards.domain.FlashcardDeck

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object LoadingFailed : LibraryUiState
    data class ShowDecks(
        val decks: List<FlashcardDeck>,
    ) : LibraryUiState
}
