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
import com.rrbrambley.flashcards.shared.domain.HomeSessionInfo
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Offline-first home feed. On subscribe it immediately emits a feed derived from the local cache
 * (active sessions + the cached global deck), then replaces it with the authoritative backend feed
 * (GET /home). It re-fetches — without re-flashing the fallback — whenever the cached sessions or
 * decks change. When the backend is unreachable the remote fetch throws, so the collector keeps the
 * already-shown cached feed (the ViewModel can surface an unobtrusive "couldn't refresh" message).
 * The offline "Practice" item points at the cached global catalog deck (resolved by its read-only
 * flag — never a hardcoded title), so it matches whatever the backend seeds.
 */
class HomeRepositoryImpl(
    private val apiClient: FlashcardApiClient,
    private val flashcardRepository: FlashcardRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val strings: HomeFeedStrings,
) : HomeRepository {

    override fun observeHomeData(): Flow<List<HomeData>> = channelFlow {
        // channelFlow (not flow): collectLatest runs its body in a child coroutine, so we send()
        // across coroutines rather than emit() (which must stay on the flow's own coroutine).
        var emittedCachedFeed = false
        combine(
            practiceSessionRepository.observeActiveSessions(),
            flashcardRepository.observeFlashcardDecks(),
        ) { activeSessions, decks -> activeSessions to decks }
            .distinctUntilChanged()
            .collectLatest { (activeSessions, decks) ->
                // Show the cache-derived feed instantly on first subscribe; later cache changes
                // refresh in place without re-flashing the fallback.
                if (!emittedCachedFeed) {
                    emittedCachedFeed = true
                    send(activeSessions.map { it.toContinueItem(decks) } + offlineItems(decks))
                }
                // Throws when the backend is unreachable; the cached feed above stays on screen.
                send(apiClient.getHome().map { it.toDomain() })
            }
    }

    private fun PracticeSession.toContinueItem(decks: List<FlashcardDeck>): HomeData = HomeData(
        title = strings.continuePracticeTitle(deckTitle),
        button = HomeButton(
            message = strings.continuePracticeButton,
            action = HomeButtonAction.ContinuePractice(id),
        ),
        // Mirror the backend feed's session detail so online/offline cards match (FLA-93).
        session = HomeSessionInfo(
            mode = mode,
            numCorrect = numCorrect,
            numIncorrect = numIncorrect,
            currentCardIndex = currentCardIndex,
            totalCards = decks.firstOrNull { it.id == deckId }?.flashcards?.size ?: 0,
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
