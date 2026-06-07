package com.rrbrambley.flashcards.practice.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionDao {
    @Transaction
    @Query("SELECT * FROM practice_sessions WHERE isCompleted = 0 ORDER BY updatedAtMillis DESC")
    fun observeActiveSessions(): Flow<List<PracticeSessionWithDeck>>

    @Transaction
    @Query("SELECT * FROM practice_sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<PracticeSessionWithDeck?>

    /** Most recent practice time per deck (across active and completed sessions); for sorting. */
    @Query("SELECT deckId, MAX(updatedAtMillis) AS lastPracticedAtMillis FROM practice_sessions GROUP BY deckId")
    fun observeLastPracticedByDeck(): Flow<List<DeckLastPracticed>>

    /** Caches a backend session under its backend id (in-place update, no cascade). */
    @Upsert
    suspend fun upsertSession(session: PracticeSessionEntity)

    /** Clears every cached practice session (e.g. on logout). */
    @Query("DELETE FROM practice_sessions")
    suspend fun deleteAllSessions()
}
