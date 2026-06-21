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

/** v6 → v7: add the `pendingSync` flag to practice_sessions (offline writes awaiting backend sync). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `practice_sessions` ADD COLUMN `pendingSync` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/** v7 → v8: add the `alternativeAnswers` column to flashcards (JSON `List<String>`, default empty). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `flashcards` ADD COLUMN `alternativeAnswers` TEXT NOT NULL DEFAULT '[]'")
    }
}

/** v8 → v9: add the `cardUid` column to flashcards (stable per-card id, FLA-113; default empty). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `flashcards` ADD COLUMN `cardUid` TEXT NOT NULL DEFAULT ''")
    }
}

/** v9 → v10: add the `discussionEnabled` flag to flashcard_decks (FLA-122; default off). */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `flashcard_decks` ADD COLUMN `discussionEnabled` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Every migration, in order; passed to the Room builder by [createFlashcardsDatabase]. */
val ALL_MIGRATIONS =
    arrayOf(
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
    )
