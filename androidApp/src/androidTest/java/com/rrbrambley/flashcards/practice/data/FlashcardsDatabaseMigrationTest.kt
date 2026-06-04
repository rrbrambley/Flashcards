package com.rrbrambley.flashcards.practice.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [FlashcardsDatabase] migrations preserve existing rows. Drives the real SQLite engine via
 * Room's [MigrationTestHelper], using the schemas exported to `androidApp/schemas/`.
 */
@RunWith(AndroidJUnit4::class)
class FlashcardsDatabaseMigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FlashcardsDatabase::class.java,
    )

    @Test
    fun migrate3To4_preservesRowsAndAddsIndex() {
        // Seed a v3 database with a deck and a practice session.
        helper.createDatabase(testDb, 3).apply {
            execSQL("INSERT INTO flashcard_decks (id, title, editable) VALUES (1, 'Spanish basics', 1)")
            execSQL(
                "INSERT INTO practice_sessions " +
                    "(id, deckId, currentCardIndex, numCorrect, numIncorrect, isCompleted, " +
                    "createdAtMillis, updatedAtMillis) VALUES (10, 1, 2, 3, 1, 0, 100, 200)",
            )
            close()
        }

        // Run MIGRATION_3_4 and validate the result matches the exported v4 schema.
        val db = helper.runMigrationsAndValidate(testDb, 4, true, MIGRATION_3_4)

        // The deck row survived intact.
        db.query("SELECT title FROM flashcard_decks WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Spanish basics", cursor.getString(0))
        }
        // The session row survived intact.
        db.query("SELECT numCorrect FROM practice_sessions WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
        }
        // The new index was created by the migration.
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND " +
                "name = 'index_practice_sessions_isCompleted_updatedAtMillis'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
    }
}
