package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.data.mapping.toDomain
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The feed comes from the backend (GET /home). It re-fetches whenever the locally-cached
 * active sessions change (which also keeps the offline fallback fresh); if the network is
 * unavailable, it derives the same feed from the cached sessions plus the static items.
 */
class HomeRepositoryImpl(
    private val apiClient: FlashcardApiClient,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val strings: HomeFeedStrings,
) : HomeRepository {

    override fun observeHomeData(): Flow<List<HomeData>> =
        practiceSessionRepository.observeActiveSessions().map { activeSessions ->
            runCatching { apiClient.getHome().map { it.toDomain() } }
                .getOrElse { activeSessions.map { it.toContinueItem() } + staticItems() }
        }

    private fun PracticeSession.toContinueItem(): HomeData = HomeData(
        title = strings.continuePracticeTitle(deckTitle),
        button = HomeButton(
            message = strings.continuePracticeButton,
            action = HomeButtonAction.ContinuePractice(id),
        ),
    )

    /** The default feed items shown when the backend feed is unavailable. */
    private fun staticItems(): List<HomeData> = listOf(
        HomeData(
            title = strings.practiceCountryFlagsTitle,
            button = HomeButton(
                message = strings.practiceCountryFlagsButton,
                action = HomeButtonAction.NavigateToPractice,
            ),
        ),
        HomeData(
            title = strings.createNewSetTitle,
            button = HomeButton(
                message = strings.createNewSetButton,
                action = HomeButtonAction.CreateNewFlashcardSet,
            ),
        ),
    )
}
