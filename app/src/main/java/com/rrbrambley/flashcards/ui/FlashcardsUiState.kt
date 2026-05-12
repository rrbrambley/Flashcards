package com.rrbrambley.flashcards.ui

import com.rrbrambley.flashcards.domain.Flashcard

sealed interface FlashcardsUiState {
    data object Loading : FlashcardsUiState
    data object LoadingFailed: FlashcardsUiState
    data class ShowFlashcard(
        val numIncorrect: Int,
        val numCorrect: Int,
        val flashcard: Flashcard
    ): FlashcardsUiState
}
