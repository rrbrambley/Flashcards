package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.PracticeAnswer
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Test/preview support: wraps a single value in a [Flow]. Swift-side fakes of the shared repository
 * interfaces (which return `Flow`s) can't build a Kotlin `Flow` directly, so they use this to emit
 * canned data. Ships in the framework like [InMemoryTokenStore]; not used by production code.
 */
fun <T> oneShotFlow(value: T): Flow<T> = flowOf(value)

/**
 * A real (Darwin) [FlashcardApiClient] aimed at an unreachable address, for Swift view-model tests
 * that need a non-null client to construct the view model but don't exercise the network — best-effort
 * calls (e.g. the home streak fetch) simply fail and degrade. Swift can't build a Ktor client itself,
 * so this exposes one. Test support, not production.
 */
fun stubApiClient(): FlashcardApiClient =
    createFlashcardApiClient(Darwin.create(), baseUrl = "http://127.0.0.1:1", tokenStore = InMemoryTokenStore())

/** A never-emitting [Flow] for Swift fakes of optional signal streams (e.g. deck-refresh failures). */
fun emptyBooleanFlow(): Flow<Boolean> = emptyFlow()

/**
 * A Swift-settable hot [Flow] of the practice answer log, for fakes of the review observation
 * (FLA-149). Mirrors the real Room-backed flow: recording an answer re-emits the updated list, so a
 * fake can model the final card's answer "landing" after completion. Test support, not production.
 */
class MutableAnswerLog {
    private val state = MutableStateFlow<List<PracticeAnswer>>(emptyList())

    fun set(answers: List<PracticeAnswer>) {
        state.value = answers
    }

    fun flow(): Flow<List<PracticeAnswer>> = state.asStateFlow()
}
