package com.rrbrambley.flashcards.practice.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FlashcardDeckEntity::class,
        FlashcardEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class FlashcardsDatabase : RoomDatabase() {
    abstract fun flashcardDao(): FlashcardDao
}
