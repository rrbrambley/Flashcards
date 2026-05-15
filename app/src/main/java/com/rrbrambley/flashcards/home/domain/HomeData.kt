package com.rrbrambley.flashcards.home.domain

data class HomeData(
    val title: String,
    val button: HomeButton? = null,
)

sealed interface HomeButtonAction {
    object NavigateToPractice : HomeButtonAction
    object CreateNewFlashcardSet : HomeButtonAction
}

data class HomeButton(
    val message: String,
    val action: HomeButtonAction = HomeButtonAction.NavigateToPractice,
)
