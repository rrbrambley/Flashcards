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
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
