package com.rrbrambley.flashcards.practice.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migrations for [FlashcardsDatabase], registered in `di/DatabaseModule`.
 *
 * Each bump of the database `version` needs a [Migration] here (and the exported schema JSON in
 * `androidApp/schemas/` committed), so existing offline data is preserved instead of wiped. Add the
 * new migration to [ALL_MIGRATIONS]. See `FlashcardsDatabaseMigrationTest` for the migration test.
 */

/** v3 → v4: add the index backing observeActiveSessions (WHERE isCompleted ORDER BY updatedAtMillis). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_practice_sessions_isCompleted_updatedAtMillis` " +
                "ON `practice_sessions` (`isCompleted`, `updatedAtMillis`)",
        )
    }
}

/** Every migration, in order; passed to the Room builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_3_4)
