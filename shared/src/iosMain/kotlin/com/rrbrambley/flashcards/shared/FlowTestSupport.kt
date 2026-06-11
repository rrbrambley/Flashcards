package com.rrbrambley.flashcards.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Test/preview support: wraps a single value in a [Flow]. Swift-side fakes of the shared repository
 * interfaces (which return `Flow`s) can't build a Kotlin `Flow` directly, so they use this to emit
 * canned data. Ships in the framework like [InMemoryTokenStore]; not used by production code.
 */
fun <T> oneShotFlow(value: T): Flow<T> = flowOf(value)

/** A never-emitting [Flow] for Swift fakes of optional signal streams (e.g. deck-refresh failures). */
fun emptyBooleanFlow(): Flow<Boolean> = emptyFlow()
