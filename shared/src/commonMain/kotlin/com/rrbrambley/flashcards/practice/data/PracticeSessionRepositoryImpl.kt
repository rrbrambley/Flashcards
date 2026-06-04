package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Sessions are owned by the backend. Writes (start/resume, progress, complete) hit the
 * server first and cache the returned state; reads refresh from the server then serve Room.
 */
class PracticeSessionRepositoryImpl(
    private val apiClient: FlashcardApiClient,
    private val practiceSessionDao: PracticeSessionDao,
    private val flashcardDao: FlashcardDao,
) : PracticeSessionRepository {

    override suspend fun startOrResumeSession(deckId: Long): Long {
        val session = apiClient.createSession(deckId)
        cache(session)
        return session.id
    }

    override fun observeActiveSessions(): Flow<List<PracticeSession>> = flow {
        runCatching { apiClient.getAllSessions(activeOnly = true).forEach { cache(it) } }
        emitAll(practiceSessionDao.observeActiveSessions().map { sessions -> sessions.map { it.toDomain() } })
    }

    override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flow {
        runCatching { cache(apiClient.getSession(sessionId)) }
        emitAll(practiceSessionDao.observeSession(sessionId).map { it?.toDomain() })
    }

    override fun observeLastPracticedByDeck(): Flow<Map<Long, Long>> =
        practiceSessionDao.observeLastPracticedByDeck().map { rows ->
            rows.associate { it.deckId to it.lastPracticedAtMillis }
        }

    override suspend fun updateProgress(sessionId: Long, currentCardIndex: Int, numCorrect: Int, numIncorrect: Int) {
        cache(
            apiClient.updateProgress(
                sessionId,
                UpdateProgressRequest(currentCardIndex, numCorrect, numIncorrect),
            ),
        )
    }

    override suspend fun completeSession(sessionId: Long) {
        cache(apiClient.completeSession(sessionId))
    }

    private suspend fun cache(session: PracticeSessionDto) {
        // Ensure the session's deck exists locally (FK + relation) before caching the session.
        flashcardDao.insertDeckIfAbsent(session.toDeckStubEntity())
        practiceSessionDao.upsertSession(session.toEntity())
    }
}
