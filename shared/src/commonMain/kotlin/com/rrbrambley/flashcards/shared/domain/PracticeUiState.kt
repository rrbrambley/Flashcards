package com.rrbrambley.flashcards.shared.domain

/**
 * What the practice screen shows, produced by [PracticeSessionController] and shared by Android + iOS
 * (FLA-197). `discussionsEnabled` is the deck's raw opt-in — the per-user `discussions` feature flag
 * is applied by each platform at the view boundary.
 */
sealed class PracticeUiState {
    data object Loading : PracticeUiState()

    data class ShowCard(
        val card: Flashcard,
        /** 0-based index of the current card. */
        val position: Int,
        val numCorrect: Int,
        val numIncorrect: Int,
        val canGoBack: Boolean,
        /** The session's mode key (flashcards / test / multiple_choice). */
        val mode: String,
        /** The whole deck, so Multiple Choice can draw distractors from other cards. */
        val deck: List<Flashcard>,
        /** The deck's raw discussions opt-in (FLA-122); the platform ANDs the `discussions` flag. */
        val discussionsEnabled: Boolean,
        /** Whether this is a global (catalog) deck — gates the Test-mode suggest action (FLA-134). */
        val isGlobal: Boolean,
        /** Current consecutive-correct run within this session (FLA-99). */
        val streak: Int,
    ) : PracticeUiState()

    data class Completed(
        val numCorrect: Int,
        val numIncorrect: Int,
        /** Overall practice streak after this completion (FLA-106); null until read / 0 = none. */
        val streak: Int? = null,
        /** Per-card recap of the run (FLA-149); empty until the answer log loads / for guests. */
        val review: List<ReviewItem> = emptyList(),
    ) : PracticeUiState()

    data object Failed : PracticeUiState()
}

/** One graded card in the end-of-session recap (FLA-149) — an answer joined to its deck card. */
data class ReviewItem(
    val answerUid: String,
    val cardUid: String,
    val question: String,
    val answer: String,
    val imageUrl: String?,
    val correct: Boolean,
    val submittedText: String?,
)

/** State of the guest "create an account to save your progress" flow (FLA-103/104). */
sealed class GuestSaveState {
    data object Idle : GuestSaveState()
    data object Saving : GuestSaveState()
    data class Error(val message: String) : GuestSaveState()
    data object Saved : GuestSaveState()
}

/** How a practice run is launched. */
sealed class PracticeEntry {
    /** Start or resume a session for a deck in a given mode. */
    data class Deck(val deckId: Long, val mode: String) : PracticeEntry()

    /** Resume an existing session; the mode comes from the session. */
    data class Session(val sessionId: Long) : PracticeEntry()

    /** Guest mode (FLA-104): practice a public catalog deck in memory — no session, no persistence. */
    data class GuestDeck(val deckId: Long, val mode: String) : PracticeEntry()
}
