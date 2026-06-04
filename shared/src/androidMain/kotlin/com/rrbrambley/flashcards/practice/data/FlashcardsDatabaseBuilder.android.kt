package com.rrbrambley.flashcards.practice.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Android [FlashcardsDatabase] builder, backed by `flashcards.db` in the app's database directory
 * (the same path the app has always used, so existing offline data is preserved).
 *
 * @param allowDestructiveMigration when true (debug builds), a missing migration wipes + recreates
 *   instead of crashing; release builds pass false so a missing migration fails loudly.
 */
fun flashcardsDatabaseBuilder(
    context: Context,
    allowDestructiveMigration: Boolean,
): RoomDatabase.Builder<FlashcardsDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath("flashcards.db")
    return Room.databaseBuilder<FlashcardsDatabase>(appContext, dbFile.absolutePath)
        .apply { if (allowDestructiveMigration) fallbackToDestructiveMigration(true) }
}
