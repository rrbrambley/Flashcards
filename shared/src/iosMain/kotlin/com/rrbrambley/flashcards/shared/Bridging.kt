package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
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

/** A single deck by id for the edit screen (non-null emissions only; syncs the full deck on subscribe). */
fun FlashcardRepository.flashcardDeckAdapter(deckId: Long): FlowAdapter<FlashcardDeck> =
    FlowAdapter(observeFlashcardDeck(deckId).filterNotNull())

/** deckId → most-recent practice time (millis), for "recently practiced" sorting. */
fun PracticeSessionRepository.lastPracticedAdapter(): FlowAdapter<Map<Long, Long>> =
    FlowAdapter(observeLastPracticedByDeck())

/** A practice session by id (non-null emissions), for restoring progress on the practice screen. */
fun PracticeSessionRepository.sessionAdapter(sessionId: Long): FlowAdapter<PracticeSession> =
    FlowAdapter(observeSession(sessionId).filterNotNull())

/** The home feed (backend GET /home, offline fallback from cached sessions + static items). */
fun HomeRepository.homeAdapter(): FlowAdapter<List<HomeData>> = FlowAdapter(observeHomeData())
