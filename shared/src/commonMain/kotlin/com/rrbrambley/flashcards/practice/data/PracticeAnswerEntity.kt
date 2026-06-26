package com.rrbrambley.flashcards.practice.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The append-only answer log for a practice session (FLA-99), cached offline-first. One immutable row
 * per answer, in [sequence] (play) order. CASCADE-deletes with its session; the offline→server remap
 * re-points [sessionId] before the old (negative-id) session row is dropped, so answers aren't lost.
 *
 * [answerUid] is a client-minted UUID (idempotent backend sync); [pendingSync] flags a local write
 * not yet flushed to the backend. [cardUid] is the stable per-card id (FLA-113) for review.
 */
@Entity(
    tableName = "practice_answers",
    foreignKeys = [
        ForeignKey(
            entity = PracticeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Ordered retrieval (review + streak) and the FK/lookup by session.
        Index(value = ["sessionId", "sequence"]),
    ],
)
data class PracticeAnswerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val answerUid: String,
    val cardUid: String,
    val correct: Boolean,
    val sequence: Int,
    val answeredAtMillis: Long,
    val submittedText: String? = null,
    val pendingSync: Boolean = false,
)
