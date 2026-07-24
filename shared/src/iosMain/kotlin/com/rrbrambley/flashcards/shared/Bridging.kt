package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.domain.BatchPracticeController
import com.rrbrambley.flashcards.shared.domain.BatchPracticeUiState
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.GuestSaveState
import com.rrbrambley.flashcards.shared.domain.HomeFeed
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.PracticeAnswer
import com.rrbrambley.flashcards.shared.domain.PracticeEntry
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionController
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import com.rrbrambley.flashcards.shared.domain.PracticeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Swift-consumable adapters for the Kotlin Flows the iOS app observes. A raw `Flow<T>` is erased
 * across the Obj-C bridge, so each Flow is wrapped here in a [FlowAdapter] of a **non-null**
 * element type, which Swift turns into a typed `AsyncStream` (see `asyncStream(_:)`). Add one per
 * Flow a screen needs.
 */

/** Login state for launch auth-gating: emits `true` while an access token is stored, else `false`. */
fun TokenStore.loggedInAdapter(): FlowAdapter<Boolean> = FlowAdapter(tokenFlow().map { it != null })

/** The user's decks + the global catalog (offline-first; best-effort re-syncs on subscribe). */
fun FlashcardRepository.flashcardDecksAdapter(): FlowAdapter<List<FlashcardDeck>> = FlowAdapter(observeFlashcardDecks())

/** Emits `true` when a background deck refresh fails, so the Library can warn it's showing cache. */
fun FlashcardRepository.deckRefreshFailuresAdapter(): FlowAdapter<Boolean> = FlowAdapter(observeDeckRefreshFailures())

/** A single deck by id for the edit screen (non-null emissions only; syncs the full deck on subscribe). */
fun FlashcardRepository.flashcardDeckAdapter(deckId: Long): FlowAdapter<FlashcardDeck> =
    FlowAdapter(observeFlashcardDeck(deckId).filterNotNull())

/** deckId → most-recent practice time (millis), for "recently practiced" sorting. */
fun PracticeSessionRepository.lastPracticedAdapter(): FlowAdapter<Map<Long, Long>> =
    FlowAdapter(observeLastPracticedByDeck())

/** A practice session by id (non-null emissions), for restoring progress on the practice screen. */
fun PracticeSessionRepository.sessionAdapter(sessionId: Long): FlowAdapter<PracticeSession> =
    FlowAdapter(observeSession(sessionId).filterNotNull())

/** The session's answer log (FLA-149), for the end-of-session per-card review. */
fun PracticeSessionRepository.answersAdapter(sessionId: Long): FlowAdapter<List<PracticeAnswer>> =
    FlowAdapter(observeAnswers(sessionId))

/**
 * Builds the shared practice runner (FLA-197) for iOS — supplies the main dispatcher (Kotlin default
 * args don't bridge to Swift). The iOS view model observes [stateAdapter]/[saveStateAdapter] and
 * calls [PracticeSessionController.close] on teardown.
 */
fun createPracticeSessionController(
    flashcardRepository: FlashcardRepository,
    sessionRepository: PracticeSessionRepository,
    apiClient: FlashcardApiClient,
    authService: AuthService?,
    entry: PracticeEntry,
): PracticeSessionController =
    PracticeSessionController(flashcardRepository, sessionRepository, apiClient, authService, entry, Dispatchers.Main)

/** The runner's UI state (Loading / ShowCard / Completed / Failed). */
fun PracticeSessionController.stateAdapter(): FlowAdapter<PracticeUiState> = FlowAdapter(state)

/** The guest "save your progress" flow state. */
fun PracticeSessionController.saveStateAdapter(): FlowAdapter<GuestSaveState> = FlowAdapter(saveState)

/** Timed-session countdown (#289): remaining seconds, or -1 when untimed (FlowAdapter needs non-null). */
fun PracticeSessionController.remainingSecondsAdapter(): FlowAdapter<Int> = FlowAdapter(
    remainingSeconds.map {
        it ?: -1
    },
)

/**
 * Builds the shared "grade at the end" batch runner (#293) for iOS — supplies the main dispatcher
 * (Kotlin default args don't bridge). The iOS view model observes [batchStateAdapter], calls [submit]
 * with the per-card answers, and [BatchPracticeController.close] on teardown.
 */
fun createBatchPracticeController(
    flashcardRepository: FlashcardRepository,
    sessionRepository: PracticeSessionRepository,
    apiClient: FlashcardApiClient,
    entry: PracticeEntry,
): BatchPracticeController =
    BatchPracticeController(flashcardRepository, sessionRepository, apiClient, entry, Dispatchers.Main)

/** The batch runner's UI state (Loading / Answering / Completed / Failed). */
fun BatchPracticeController.batchStateAdapter(): FlowAdapter<BatchPracticeUiState> = FlowAdapter(state)

/** Timed batch countdown (#289): remaining seconds, or -1 when untimed. The view submits at 0. */
fun BatchPracticeController.remainingSecondsAdapter(): FlowAdapter<Int> = FlowAdapter(remainingSeconds.map { it ?: -1 })

/** The home feed (backend GET /home, offline fallback from cached sessions + static items). */
fun HomeRepository.homeAdapter(): FlowAdapter<HomeFeed> = FlowAdapter(observeHomeData())
