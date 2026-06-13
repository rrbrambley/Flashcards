package com.rrbrambley.flashcards.practice.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        FlashcardDeckEntity::class,
        FlashcardEntity::class,
        PracticeSessionEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@ConstructedBy(FlashcardsDatabaseConstructor::class)
abstract class FlashcardsDatabase : RoomDatabase() {
    abstract fun flashcardDao(): FlashcardDao
    abstract fun practiceSessionDao(): PracticeSessionDao
}

// KSP generates the actual implementation of this constructor for each platform.
@Suppress("KotlinNoActualForExpect")
expect object FlashcardsDatabaseConstructor : RoomDatabaseConstructor<FlashcardsDatabase> {
    override fun initialize(): FlashcardsDatabase
}

/**
 * Finalizes a platform-supplied [RoomDatabase.Builder] (Android `Context` / iOS file path) with the
 * shared driver, migrations, and a background query context. Uses [Dispatchers.Default] since
 * [Dispatchers.IO] isn't available on native.
 */
fun createFlashcardsDatabase(builder: RoomDatabase.Builder<FlashcardsDatabase>): FlashcardsDatabase = builder
    .addMigrations(*ALL_MIGRATIONS)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
