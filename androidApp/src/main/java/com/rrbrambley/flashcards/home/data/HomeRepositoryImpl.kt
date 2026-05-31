package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.home.domain.HomeButton
import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import com.rrbrambley.flashcards.home.domain.HomeData
import com.rrbrambley.flashcards.home.domain.HomeRepository
import com.rrbrambley.flashcards.practice.domain.PracticeSession
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HomeRepositoryImpl @Inject constructor(
    private val practiceSessionRepository: PracticeSessionRepository,
) : HomeRepository {
    override fun observeHomeData(): Flow<List<HomeData>> = practiceSessionRepository.observeActiveSessions().map { sessions ->
        sessions.map { it.toHomeData() } + defaultHomeData
    }

    private fun PracticeSession.toHomeData(): HomeData = HomeData(
        title = "Continue $deckTitle practice",
        button = HomeButton(
            message = "Continue practice",
            action = HomeButtonAction.ContinuePractice(id),
        ),
    )

    private companion object {
        val defaultHomeData = listOf(
            HomeData(
                title = "Practice identifying country flags",
                button = HomeButton(
                    message = "Practice",
                    action = HomeButtonAction.NavigateToPractice,
                ),
            ),
            HomeData(
                title = "Create a new flashcard set",
                button = HomeButton(
                    message = "Create",
                    action = HomeButtonAction.CreateNewFlashcardSet,
                ),
            ),
        )
    }
}
