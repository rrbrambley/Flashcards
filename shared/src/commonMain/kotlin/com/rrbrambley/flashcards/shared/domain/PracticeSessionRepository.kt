package com.rrbrambley.flashcards.shared.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface PracticeSessionRepository {
    // @Throws so failures bridge to catchable Swift errors on iOS instead of crashing; no effect on
    // Kotlin (JVM/Android) callers. See FLA-57.
    @Throws(Exception::class)
    suspend fun startOrResumeSession(deckId: Long, mode: String = "flashcards"): Long
    fun observeActiveSessions(): Flow<List<PracticeSession>>
    fun observeSession(sessionId: Long): Flow<PracticeSession?>

    /** deckId -> the deck's most recent practice time (millis), for "recently practiced" sorting. */
    fun observeLastPracticedByDeck(): Flow<Map<Long, Long>> = flowOf(emptyMap())

    @Throws(Exception::class)
    suspend fun updateProgress(sessionId: Long, currentCardIndex: Int, numCorrect: Int, numIncorrect: Int)

    @Throws(Exception::class)
    suspend fun completeSession(sessionId: Long)
}
