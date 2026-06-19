package com.rrbrambley.flashcards.practice.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = FlashcardDeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["deckId"])],
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val deckId: Long,
    val question: String,
    val answer: String,
    val imageUrl: String? = null,
    /** JSON-encoded List<String> of extra accepted answers (FLA-109); "[]" when none. */
    val alternativeAnswers: String = "[]",
    /** Stable per-card id (FLA-113), cached so mobile edits round-trip it; "" when not yet assigned. */
    val cardUid: String = "",
)
