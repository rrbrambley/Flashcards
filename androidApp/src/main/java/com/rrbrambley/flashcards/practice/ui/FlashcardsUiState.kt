package com.rrbrambley.flashcards.practice.ui

import com.rrbrambley.flashcards.domain.Flashcard

sealed interface FlashcardsUiState {
    data object Loading : FlashcardsUiState
    data object LoadingFailed : FlashcardsUiState
    data class ShowFlashcard(
        val numIncorrect: Int,
        val numCorrect: Int,
        val flashcard: Flashcard,
        /** False on the first card, so the UI can disable "Previous". */
        val canGoBack: Boolean = false,
    ) : FlashcardsUiState
    data class SessionCompleted(
        val numIncorrect: Int,
        val numCorrect: Int,
    ) : FlashcardsUiState
}
