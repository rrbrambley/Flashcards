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

    /** Caches a backend session under its backend id (in-place update, no cascade). */
    @Upsert
    suspend fun upsertSession(session: PracticeSessionEntity)
}
