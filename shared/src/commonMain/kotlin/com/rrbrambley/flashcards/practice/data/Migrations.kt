package com.rrbrambley.flashcards.practice.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Room schema migrations for [FlashcardsDatabase], registered in [createFlashcardsDatabase].
 *
 * Each bump of the database `version` needs a [Migration] here (and the exported schema JSON in
 * `shared/schemas/` committed), so existing offline data is preserved instead of wiped. Add the new
 * migration to [ALL_MIGRATIONS]. See `FlashcardsDatabaseMigrationTest` for the migration test.
 */

/** v3 → v4: add the index backing observeActiveSessions (WHERE isCompleted ORDER BY updatedAtMillis). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_practice_sessions_isCompleted_updatedAtMillis` " +
                "ON `practice_sessions` (`isCompleted`, `updatedAtMillis`)",
        )
    }
}

/** v4 → v5: add the cached `tags` column to flashcard_decks (JSON `List<String>`, default empty). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `flashcard_decks` ADD COLUMN `tags` TEXT NOT NULL DEFAULT '[]'")
    }
}

/** v5 → v6: add the `mode` column to practice_sessions (practice mode; default classic flashcards). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `practice_sessions` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'flashcards'",
        )
    }
}

/** Every migration, in order; passed to the Room builder by [createFlashcardsDatabase]. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
