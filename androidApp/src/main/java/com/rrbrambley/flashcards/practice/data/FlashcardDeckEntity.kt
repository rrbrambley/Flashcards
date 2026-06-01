package com.rrbrambley.flashcards.practice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flashcard_decks")
data class FlashcardDeckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    /** Cached from the backend: whether the current user may edit this deck. */
    val editable: Boolean = true,
)
