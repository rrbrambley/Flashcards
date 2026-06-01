package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.data.mapping.toDomain
import com.rrbrambley.flashcards.home.domain.HomeButton
import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import com.rrbrambley.flashcards.home.domain.HomeData
import com.rrbrambley.flashcards.home.domain.HomeRepository
import com.rrbrambley.flashcards.practice.domain.PracticeSession
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The feed comes from the backend (GET /home). It re-fetches whenever the locally-cached
 * active sessions change (which also keeps the offline fallback fresh); if the network is
 * unavailable, it derives the same feed from the cached sessions plus the static items.
 */
class HomeRepositoryImpl @Inject constructor(
    private val apiClient: FlashcardApiClient,
    private val practiceSessionRepository: PracticeSessionRepository,
) : HomeRepository {

    override fun observeHomeData(): Flow<List<HomeData>> =
        practiceSessionRepository.observeActiveSessions().map { activeSessions ->
            runCatching { apiClient.getHome().map { it.toDomain() } }
                .getOrElse { activeSessions.map { it.toContinueItem() } + STATIC_ITEMS }
        }

    private fun PracticeSession.toContinueItem(): HomeData = HomeData(
        title = "Continue $deckTitle practice",
        button = HomeButton(
            message = "Continue practice",
            action = HomeButtonAction.ContinuePractice(id),
        ),
    )

    private companion object {
        val STATIC_ITEMS = listOf(
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
