package com.rrbrambley.flashcards.shared.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface PracticeSessionRepository {
    suspend fun startOrResumeSession(deckId: Long): Long
    fun observeActiveSessions(): Flow<List<PracticeSession>>
    fun observeSession(sessionId: Long): Flow<PracticeSession?>

    /** deckId -> the deck's most recent practice time (millis), for "recently practiced" sorting. */
    fun observeLastPracticedByDeck(): Flow<Map<Long, Long>> = flowOf(emptyMap())
    suspend fun updateProgress(sessionId: Long, currentCardIndex: Int, numCorrect: Int, numIncorrect: Int)
    suspend fun completeSession(sessionId: Long)
}
