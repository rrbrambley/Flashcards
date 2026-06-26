package com.rrbrambley.flashcards.practice.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeAnswerDao {
    /** The session's answer log, oldest first (play order) — for review + streak derivation. */
    @Query("SELECT * FROM practice_answers WHERE sessionId = :sessionId ORDER BY sequence ASC")
    fun observeAnswers(sessionId: Long): Flow<List<PracticeAnswerEntity>>

    @Query("SELECT * FROM practice_answers WHERE sessionId = :sessionId ORDER BY sequence ASC")
    suspend fun getAnswers(sessionId: Long): List<PracticeAnswerEntity>

    /** Highest play-order recorded for a session, or null when none — to mint the next [sequence]. */
    @Query("SELECT MAX(sequence) FROM practice_answers WHERE sessionId = :sessionId")
    suspend fun maxSequence(sessionId: Long): Int?

    @Upsert
    suspend fun upsert(answer: PracticeAnswerEntity)

    @Upsert
    suspend fun upsertAll(answers: List<PracticeAnswerEntity>)

    /** Rows with local writes not yet flushed to the backend. */
    @Query("SELECT * FROM practice_answers WHERE pendingSync = 1")
    suspend fun getPendingAnswers(): List<PracticeAnswerEntity>

    @Query("UPDATE practice_answers SET pendingSync = 0 WHERE sessionId = :sessionId AND answerUid IN (:answerUids)")
    suspend fun markSynced(sessionId: Long, answerUids: List<String>)

    /**
     * Re-points a session's answers to a new session id. Called during the offline→server remap so a
     * minted (negative-id) session's answers survive deleting the old row (which would CASCADE them).
     */
    @Query("UPDATE practice_answers SET sessionId = :newSessionId WHERE sessionId = :oldSessionId")
    suspend fun reassignAnswers(oldSessionId: Long, newSessionId: Long)

    @Query("DELETE FROM practice_answers WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    /** Replaces a session's cached answers with the server's set (pull for cross-device review). */
    @Transaction
    suspend fun replaceForSession(sessionId: Long, answers: List<PracticeAnswerEntity>) {
        deleteForSession(sessionId)
        upsertAll(answers)
    }

    /** Clears every cached answer (e.g. on logout). */
    @Query("DELETE FROM practice_answers")
    suspend fun deleteAll()
}
