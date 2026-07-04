package com.rrbrambley.flashcards.shared.domain

/**
 * A selectable practice mode and its persisted [key] — stored on the session and sent to the backend
 * as `PracticeSessions.mode`. Shared by Android + iOS (FLA-195); the chooser labels/descriptions stay
 * per-platform (they're localized). The runner dispatches the per-card UI on the session's mode key —
 * [fromKey] resolves an unknown/legacy key to [Classic].
 *
 * Entries are PascalCase so they bridge to Swift least-ugly (see the FLA-191 convention).
 */
enum class PracticeMode(val key: String) {
    Classic("flashcards"),
    Test("test"),
    MultipleChoice("multiple_choice"),
    ;

    companion object {
        /** Resolves a persisted/backend [key] to a mode; unknown keys fall back to [Classic]. */
        fun fromKey(key: String): PracticeMode = entries.firstOrNull { it.key == key } ?: Classic
    }
}
