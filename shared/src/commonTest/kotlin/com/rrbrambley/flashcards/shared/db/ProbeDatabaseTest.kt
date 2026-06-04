package com.rrbrambley.flashcards.shared.db

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Proves the Room-KMP setup works on every platform this test runs on (jvmTest + the iOS simulator
 * test): an in-memory database round-trips a row through the bundled SQLite driver.
 */
class ProbeDatabaseTest {

    @Test
    fun upsertAndReadBack() = runTest {
        val db = createProbeDatabase(Room.inMemoryDatabaseBuilder<ProbeDatabase>())
        val dao = db.probeDao()

        assertNull(dao.value(1))
        dao.upsert(ProbeEntity(id = 1, value = "hello"))
        assertEquals("hello", dao.value(1))

        db.close()
    }
}
