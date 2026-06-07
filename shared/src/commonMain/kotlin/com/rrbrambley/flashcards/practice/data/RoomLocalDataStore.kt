package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.shared.domain.LocalDataStore

/**
 * [LocalDataStore] backed by the shared Room database. Wipes every user-scoped table so a logout
 * leaves no trace of the previous account's data for the next sign-in.
 */
class RoomLocalDataStore(private val database: FlashcardsDatabase) : LocalDataStore {
    override suspend fun clearAll() {
        // Deleting every deck cascades to its flashcards and sessions; the explicit session delete
        // also drops any session whose deck row is already gone. Both converge to empty, so no
        // wrapping transaction is needed.
        database.practiceSessionDao().deleteAllSessions()
        database.flashcardDao().deleteAllDecks()
    }
}
