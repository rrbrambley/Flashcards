package com.rrbrambley.flashcards.practice.data

import androidx.room.Embedded
import androidx.room.Relation

data class PracticeSessionWithDeck(
    @Embedded val session: PracticeSessionEntity,
    @Relation(
        parentColumn = "deckId",
        entityColumn = "id",
    )
    val deck: FlashcardDeckEntity,
)
