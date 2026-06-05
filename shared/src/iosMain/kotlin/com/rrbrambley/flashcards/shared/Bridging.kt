package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
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

/** deckId → most-recent practice time (millis), for "recently practiced" sorting. */
fun PracticeSessionRepository.lastPracticedAdapter(): FlowAdapter<Map<Long, Long>> =
    FlowAdapter(observeLastPracticedByDeck())
