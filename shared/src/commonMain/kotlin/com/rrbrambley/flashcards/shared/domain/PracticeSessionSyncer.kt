package com.rrbrambley.flashcards.shared.domain

/**
 * Flushes practice sessions that were started/advanced offline to the backend (FLA-91). Kept
 * separate from [PracticeSessionRepository] so the connectivity-driven sync layer can depend on it
 * without widening the repository interface (and its UI test fakes). Implemented by the repository.
 */
interface PracticeSessionSyncer {
    /** Reconciles all locally-pending sessions with the backend; safe to call repeatedly. */
    suspend fun syncPendingSessions()
}
