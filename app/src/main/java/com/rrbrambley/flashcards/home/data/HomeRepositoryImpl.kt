package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.home.domain.HomeButton
import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import com.rrbrambley.flashcards.home.domain.HomeData
import com.rrbrambley.flashcards.home.domain.HomeRepository
import javax.inject.Inject

class HomeRepositoryImpl @Inject constructor() : HomeRepository {
    override suspend fun getHomeData(): List<HomeData> = listOf(
        HomeData(
            title = "Practice identifying country flags",
            button = HomeButton(
                message = "Practice",
                action = HomeButtonAction.NavigateToPractice
            )
        ),
        HomeData(
            title = "Create a new flashcard set",
            button = HomeButton(
                message = "Create",
                action = HomeButtonAction.CreateNewFlashcardSet
            )
        )
    )
}
