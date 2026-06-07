package com.rrbrambley.flashcards.shared.domain

/**
 * Clears all locally-cached, user-scoped data (the offline-first Room database). Called on logout so
 * the next account that signs in on the device starts from a clean cache rather than seeing the
 * previous user's decks, cards, and practice sessions.
 */
interface LocalDataStore {
    suspend fun clearAll()
}
