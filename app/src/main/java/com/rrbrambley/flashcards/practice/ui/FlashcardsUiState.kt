package com.rrbrambley.flashcards.practice.ui

import com.rrbrambley.flashcards.practice.domain.Flashcard

sealed interface FlashcardsUiState {
    data object Loading : FlashcardsUiState
    data object LoadingFailed : FlashcardsUiState
    data class ShowFlashcard(
        val numIncorrect: Int,
        val numCorrect: Int,
        val flashcard: Flashcard,
    ) : FlashcardsUiState
    data class SessionCompleted(
        val numIncorrect: Int,
        val numCorrect: Int,
    ) : FlashcardsUiState
}
