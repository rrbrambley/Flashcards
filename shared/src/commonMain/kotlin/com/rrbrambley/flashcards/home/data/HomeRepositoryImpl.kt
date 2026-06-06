package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.data.mapping.toDomain
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The feed comes from the backend (GET /home). It re-fetches whenever the locally-cached active
 * sessions or decks change (which also keeps the offline fallback fresh); if the network is
 * unavailable, it derives the same feed from cache. The offline "Practice" item points at the
 * cached global catalog deck (resolved by its read-only flag — never a hardcoded title), so it
 * matches whatever the backend seeds.
 */
class HomeRepositoryImpl(
    private val apiClient: FlashcardApiClient,
    private val flashcardRepository: FlashcardRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val strings: HomeFeedStrings,
) : HomeRepository {

    override fun observeHomeData(): Flow<List<HomeData>> = combine(
        practiceSessionRepository.observeActiveSessions(),
        flashcardRepository.observeFlashcardDecks(),
    ) { activeSessions, decks ->
        runCatching { apiClient.getHome().map { it.toDomain() } }
            .getOrElse { activeSessions.map { it.toContinueItem() } + offlineItems(decks) }
    }

    private fun PracticeSession.toContinueItem(): HomeData = HomeData(
        title = strings.continuePracticeTitle(deckTitle),
        button = HomeButton(
            message = strings.continuePracticeButton,
            action = HomeButtonAction.ContinuePractice(id),
        ),
    )

    /**
     * The fallback feed when the backend is unavailable: a "Practice" item for the cached global
     * catalog deck (the first read-only deck — omitted if none is cached yet) + "Create a new set".
     */
    private fun offlineItems(decks: List<FlashcardDeck>): List<HomeData> {
        val globalDeck = decks.firstOrNull { !it.isEditable }
        val practiceItem = globalDeck?.let { deck ->
            HomeData(
                title = strings.practiceDeckTitle(deck.title),
                button = HomeButton(
                    message = strings.practiceButton,
                    action = HomeButtonAction.NavigateToPractice(deck.id),
                ),
            )
        }
        return listOfNotNull(practiceItem) + HomeData(
            title = strings.createNewSetTitle,
            button = HomeButton(
                message = strings.createNewSetButton,
                action = HomeButtonAction.CreateNewFlashcardSet,
            ),
        )
    }
}
