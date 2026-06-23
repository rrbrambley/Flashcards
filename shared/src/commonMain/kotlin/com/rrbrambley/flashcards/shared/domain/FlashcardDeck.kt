package com.rrbrambley.flashcards.shared.domain

data class FlashcardDeck(
    val id: Long = 0L,
    val title: String,
    val flashcards: List<Flashcard>,
    /** Whether the current user may edit this deck. False for the read-only global deck. */
    val isEditable: Boolean = true,
    /** User-facing tags / categories (for future grouping, sorting, and search). */
    val tags: List<String> = emptyList(),
    /** Whether per-card discussions are available on this deck (FLA-122); gates the 💬 affordance. */
    val discussionsEnabled: Boolean = false,
    /** Whether this is a global (catalog) deck (FLA-120); gates Test-mode answer suggestions (FLA-134). */
    val isGlobal: Boolean = false,
)
