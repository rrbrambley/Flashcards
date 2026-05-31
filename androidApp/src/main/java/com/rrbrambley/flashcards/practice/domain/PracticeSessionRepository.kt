package com.rrbrambley.flashcards.practice.domain

import kotlinx.coroutines.flow.Flow

interface PracticeSessionRepository {
    suspend fun startOrResumeSession(deckId: Long): Long
    fun observeActiveSessions(): Flow<List<PracticeSession>>
    fun observeSession(sessionId: Long): Flow<PracticeSession?>
    suspend fun updateProgress(
        sessionId: Long,
        currentCardIndex: Int,
        numCorrect: Int,
        numIncorrect: Int,
    )
    suspend fun completeSession(sessionId: Long)
}
