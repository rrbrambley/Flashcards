package com.rrbrambley.flashcards.practice.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FlashcardDeckEntity::class,
        FlashcardEntity::class,
        PracticeSessionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class FlashcardsDatabase : RoomDatabase() {
    abstract fun flashcardDao(): FlashcardDao
    abstract fun practiceSessionDao(): PracticeSessionDao
}
