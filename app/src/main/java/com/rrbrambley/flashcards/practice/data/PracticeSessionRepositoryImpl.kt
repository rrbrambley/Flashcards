package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.practice.domain.PracticeSession
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PracticeSessionRepositoryImpl @Inject constructor(
    private val practiceSessionDao: PracticeSessionDao,
) : PracticeSessionRepository {
    override suspend fun startOrResumeSession(deckId: Long): Long {
        val activeSession = practiceSessionDao.getActiveSessionForDeck(deckId)
        if (activeSession != null) return activeSession.id

        val now = System.currentTimeMillis()
        return practiceSessionDao.insertSession(
            PracticeSessionEntity(
                deckId = deckId,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    override fun observeActiveSessions(): Flow<List<PracticeSession>> =
        practiceSessionDao.observeActiveSessions().map { sessions ->
            sessions.map { it.toDomain() }
        }

    override fun observeSession(sessionId: Long): Flow<PracticeSession?> =
        practiceSessionDao.observeSession(sessionId).map { it?.toDomain() }

    override suspend fun updateProgress(
        sessionId: Long,
        currentCardIndex: Int,
        numCorrect: Int,
        numIncorrect: Int,
    ) {
        practiceSessionDao.updateProgress(
            sessionId = sessionId,
            currentCardIndex = currentCardIndex,
            numCorrect = numCorrect,
            numIncorrect = numIncorrect,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun completeSession(sessionId: Long) {
        practiceSessionDao.completeSession(
            sessionId = sessionId,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun PracticeSessionWithDeck.toDomain(): PracticeSession = PracticeSession(
        id = session.id,
        deckId = session.deckId,
        deckTitle = deck.title,
        currentCardIndex = session.currentCardIndex,
        numCorrect = session.numCorrect,
        numIncorrect = session.numIncorrect,
        isCompleted = session.isCompleted,
        createdAtMillis = session.createdAtMillis,
        updatedAtMillis = session.updatedAtMillis,
    )
}
