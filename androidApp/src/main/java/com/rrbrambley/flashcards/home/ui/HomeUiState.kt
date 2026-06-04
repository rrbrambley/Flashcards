package com.rrbrambley.flashcards.home.ui

import com.rrbrambley.flashcards.shared.domain.HomeData

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object LoadingFailed : HomeUiState
    data class ShowHome(
        val cards: List<HomeData>
    ) : HomeUiState
}

