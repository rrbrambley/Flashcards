package com.rrbrambley.flashcards.practice.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "practice_sessions",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["deckId"]),
        Index(value = ["deckId", "isCompleted"]),
        // Backs observeActiveSessions(): WHERE isCompleted = 0 ORDER BY updatedAtMillis DESC.
        Index(value = ["isCompleted", "updatedAtMillis"]),
    ],
)
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val deckId: Long,
    val currentCardIndex: Int = 0,
    val numCorrect: Int = 0,
    val numIncorrect: Int = 0,
    val isCompleted: Boolean = false,
    // Practice mode (e.g. flashcards / test / multiple_choice). Default keeps existing rows (and the
    // v5→v6 migration's added column) on classic flashcards. See MIGRATION_5_6.
    val mode: String = "flashcards",
    // Whether this session's cards are randomized, and the seed making that order reproducible across
    // restart (FLA-200; applied by SessionOrdering). Defaults keep old rows unshuffled. See MIGRATION_12_13.
    val shuffle: Boolean = false,
    val shuffleSeed: Long = 0L,
    // True when this row has local writes not yet confirmed by the backend (offline create/progress/
    // complete); the sync routine flushes these on reconnect and clears it once the server confirms.
    // Distinct from a negative [id], which means "offline-minted, no server id yet". See MIGRATION_6_7.
    val pendingSync: Boolean = false,
    // True when the user removed this session locally but the backend DELETE hasn't been confirmed yet
    // (FLA-205). A local tombstone: the row is hidden from the active/observe queries immediately and
    // syncPendingSessions flushes the DELETE on reconnect, then hard-removes it. See MIGRATION_13_14.
    val pendingDelete: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
