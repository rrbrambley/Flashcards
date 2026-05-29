package com.rrbrambley.flashcards.practice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionDao {
    @Query("SELECT * FROM practice_sessions WHERE deckId = :deckId AND isCompleted = 0 LIMIT 1")
    suspend fun getActiveSessionForDeck(deckId: Long): PracticeSessionEntity?

    @Insert
    suspend fun insertSession(session: PracticeSessionEntity): Long

    @Transaction
    @Query("SELECT * FROM practice_sessions WHERE isCompleted = 0 ORDER BY updatedAtMillis DESC")
    fun observeActiveSessions(): Flow<List<PracticeSessionWithDeck>>

    @Transaction
    @Query("SELECT * FROM practice_sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<PracticeSessionWithDeck?>

    @Query(
        """
        UPDATE practice_sessions
        SET currentCardIndex = :currentCardIndex,
            numCorrect = :numCorrect,
            numIncorrect = :numIncorrect,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :sessionId
        """,
    )
    suspend fun updateProgress(
        sessionId: Long,
        currentCardIndex: Int,
        numCorrect: Int,
        numIncorrect: Int,
        updatedAtMillis: Long,
    )

    @Query("UPDATE practice_sessions SET isCompleted = 1, updatedAtMillis = :updatedAtMillis WHERE id = :sessionId")
    suspend fun completeSession(sessionId: Long, updatedAtMillis: Long)
}
