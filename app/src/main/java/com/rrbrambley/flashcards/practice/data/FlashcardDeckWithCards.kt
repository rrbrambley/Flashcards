package com.rrbrambley.flashcards.practice.data

import androidx.room.Embedded
import androidx.room.Relation

data class FlashcardDeckWithCards(
    @Embedded val deck: FlashcardDeckEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "deckId",
    )
    val flashcards: List<FlashcardEntity>,
)
