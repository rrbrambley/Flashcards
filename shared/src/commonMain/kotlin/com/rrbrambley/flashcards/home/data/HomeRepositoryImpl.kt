package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.data.mapping.toDomain
import com.rrbrambley.flashcards.practice.grading.trailingCorrectStreak
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeFeed
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.HomeSessionInfo
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Offline-first home feed (FLA-210). Whenever the cached sessions/decks change it emits the local
 * (Room-derived) feed immediately — so a local change (a removed session, updated progress) surfaces
 * at once — then best-effort refreshes from the backend (GET /home). A backend failure never tears
 * down this flow: it keeps the local feed and flags [HomeFeed.refreshFailed] so the ViewModel can
 * show an unobtrusive "couldn't refresh" banner while local updates keep flowing.
 * The offline "Practice" item points at the cached global catalog deck (resolved by its read-only
 * flag — never a hardcoded title), so it matches whatever the backend seeds.
 */
class HomeRepositoryImpl(
    private val apiClient: FlashcardApiClient,
    private val flashcardRepository: FlashcardRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val strings: HomeFeedStrings,
) : HomeRepository {

    override fun observeHomeData(): Flow<HomeFeed> = channelFlow {
        // channelFlow (not flow): collectLatest runs its body in a child coroutine, so we send()
        // across coroutines rather than emit() (which must stay on the flow's own coroutine).
        combine(
            practiceSessionRepository.observeActiveSessions(),
            flashcardRepository.observeFlashcardDecks(),
        ) { activeSessions, decks -> activeSessions to decks }
            .distinctUntilChanged()
            .collectLatest { (activeSessions, decks) ->
                // In-session streak (FLA-99) per active session, from the cached answer log — the
                // trailing consecutive-correct run — so the offline card matches the backend's value.
                val streaks = activeSessions.associate { session ->
                    val correctByOrder = practiceSessionRepository.observeAnswers(session.id).first()
                        .sortedBy { it.sequence }.map { it.correct }
                    session.id to trailingCorrectStreak(correctByOrder)
                }
                // Offline-first: show the local (Room-derived) feed instantly, so this cache change
                // (e.g. a removed session) is reflected immediately even with the backend unreachable.
                val localFeed =
                    activeSessions.map { it.toContinueItem(decks, streaks[it.id] ?: 0) } + offlineItems(decks)
                send(HomeFeed(cards = localFeed))
                // Best-effort backend refresh. A failure keeps the local feed and just flags the
                // banner — it must NOT terminate this flow, or later cache changes would stop
                // re-emitting (FLA-210). Cancellation (collectLatest restart) still propagates.
                runCatching { apiClient.getHome().map { it.toDomain() } }
                    .onSuccess { send(HomeFeed(cards = it)) }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        send(HomeFeed(cards = localFeed, refreshFailed = true))
                    }
            }
    }

    private fun PracticeSession.toContinueItem(decks: List<FlashcardDeck>, streak: Int): HomeData = HomeData(
        // FLA-96: the card title is just the deck name; "Continue studying" is the section header.
        title = deckTitle,
        section = strings.continueStudyingSection,
        button = HomeButton(
            message = strings.resumeButton,
            action = HomeButtonAction.ContinuePractice(id),
        ),
        // Mirror the backend feed's session detail so online/offline cards match (FLA-93).
        session = HomeSessionInfo(
            mode = mode,
            numCorrect = numCorrect,
            numIncorrect = numIncorrect,
            currentCardIndex = currentCardIndex,
            totalCards = decks.firstOrNull { it.id == deckId }?.flashcards?.size ?: 0,
            streak = streak,
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
                section = strings.studySomethingNewSection,
                button = HomeButton(
                    message = strings.practiceButton,
                    action = HomeButtonAction.NavigateToPractice(deck.id),
                ),
            )
        }
        return listOfNotNull(practiceItem) + HomeData(
            title = strings.createNewSetTitle,
            section = strings.studySomethingNewSection,
            button = HomeButton(
                message = strings.createNewSetButton,
                action = HomeButtonAction.CreateNewFlashcardSet,
            ),
        )
    }
}
