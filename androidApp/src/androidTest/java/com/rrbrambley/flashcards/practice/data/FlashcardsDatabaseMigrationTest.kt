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
 * Room's [MigrationTestHelper], using the schemas exported to `shared/schemas/` (wired into this
 * source set's assets by androidApp's build.gradle.kts).
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

    @Test
    fun migrate4To5_preservesRowsAndAddsTagsColumn() {
        // Seed a v4 database with a deck (no tags column yet).
        helper.createDatabase(testDb, 4).apply {
            execSQL("INSERT INTO flashcard_decks (id, title, editable) VALUES (1, 'Spanish basics', 1)")
            close()
        }

        // Run MIGRATION_4_5 and validate against the exported v5 schema.
        val db = helper.runMigrationsAndValidate(testDb, 5, true, MIGRATION_4_5)

        // The existing row survived and got the default (empty) tags.
        db.query("SELECT title, tags FROM flashcard_decks WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Spanish basics", cursor.getString(0))
            assertEquals("[]", cursor.getString(1))
        }
    }

    @Test
    fun migrate5To6_preservesRowsAndAddsModeColumn() {
        // Seed a v5 database with a deck + a practice session (no mode column yet).
        helper.createDatabase(testDb, 5).apply {
            execSQL("INSERT INTO flashcard_decks (id, title, editable, tags) VALUES (1, 'Spanish basics', 1, '[]')")
            execSQL(
                "INSERT INTO practice_sessions " +
                    "(id, deckId, currentCardIndex, numCorrect, numIncorrect, isCompleted, " +
                    "createdAtMillis, updatedAtMillis) VALUES (10, 1, 2, 3, 1, 0, 100, 200)",
            )
            close()
        }

        // Run MIGRATION_5_6 and validate against the exported v6 schema.
        val db = helper.runMigrationsAndValidate(testDb, 6, true, MIGRATION_5_6)

        // The session survived and got the default (classic) mode.
        db.query("SELECT numCorrect, mode FROM practice_sessions WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
            assertEquals("flashcards", cursor.getString(1))
        }
    }

    @Test
    fun migrate6To7_preservesRowsAndAddsPendingSyncColumn() {
        // Seed a v6 database with a deck + a practice session (no pendingSync column yet).
        helper.createDatabase(testDb, 6).apply {
            execSQL("INSERT INTO flashcard_decks (id, title, editable, tags) VALUES (1, 'Spanish basics', 1, '[]')")
            execSQL(
                "INSERT INTO practice_sessions " +
                    "(id, deckId, currentCardIndex, numCorrect, numIncorrect, isCompleted, mode, " +
                    "createdAtMillis, updatedAtMillis) VALUES (10, 1, 2, 3, 1, 0, 'flashcards', 100, 200)",
            )
            close()
        }

        // Run MIGRATION_6_7 and validate against the exported v7 schema.
        val db = helper.runMigrationsAndValidate(testDb, 7, true, MIGRATION_6_7)

        // The session survived and got the default (not-pending) sync flag.
        db.query("SELECT numCorrect, pendingSync FROM practice_sessions WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
        }
    }

    @Test
    fun migrate7To8_preservesRowsAndAddsAlternativeAnswersColumn() {
        // Seed a v7 database with a deck + a flashcard (no alternativeAnswers column yet).
        helper.createDatabase(testDb, 7).apply {
            execSQL("INSERT INTO flashcard_decks (id, title, editable, tags) VALUES (1, 'Spanish basics', 1, '[]')")
            execSQL(
                "INSERT INTO flashcards (id, deckId, question, answer, imageUrl) " +
                    "VALUES (5, 1, 'Hola', 'Hello', NULL)",
            )
            close()
        }

        // Run MIGRATION_7_8 and validate against the exported v8 schema.
        val db = helper.runMigrationsAndValidate(testDb, 8, true, MIGRATION_7_8)

        // The card survived and got the default (empty) alternative answers.
        db.query("SELECT answer, alternativeAnswers FROM flashcards WHERE id = 5").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Hello", cursor.getString(0))
            assertEquals("[]", cursor.getString(1))
        }
    }
}
