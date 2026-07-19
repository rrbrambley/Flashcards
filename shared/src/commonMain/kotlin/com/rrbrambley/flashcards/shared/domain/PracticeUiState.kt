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

/**
 * What the "grade at the end" batch runner shows (#293), produced by [BatchPracticeController] and
 * shared by Android + iOS. Every card is answered in one scrollable list, then graded together on
 * submit — which lands on the same completion recap the card-by-card runner uses (#298).
 */
sealed class BatchPracticeUiState {
    data object Loading : BatchPracticeUiState()

    /** The answering phase: every card at once. The view owns the per-card entries locally. */
    data class Answering(
        val cards: List<Flashcard>,
        /** The session's mode key — test or multiple_choice (Classic can't defer grading). */
        val mode: String,
    ) : BatchPracticeUiState()

    /** After submit: the graded recap, mirroring [PracticeUiState.Completed]. */
    data class Completed(
        val numCorrect: Int,
        val numIncorrect: Int,
        /** Overall practice streak after this completion (FLA-106); null until read / 0 = none. */
        val streak: Int? = null,
        val review: List<ReviewItem> = emptyList(),
    ) : BatchPracticeUiState()

    data object Failed : BatchPracticeUiState()
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
    /**
     * Start or resume a session for a deck in a given mode. [shuffle] applies only when a *new* session
     * is created (resume keeps the existing session's stored order); defaulted false so the data layer
     * stays backward-compatible — the picker UIs pass the user's choice (default On). See FLA-200.
     */
    data class Deck(
        val deckId: Long,
        val mode: String,
        val shuffle: Boolean = false,
        // A subset of the deck to practice (FLA-219); null = the whole deck. Applies to a new session.
        val questionCount: Int? = null,
        // Grade the whole session at the end (#293) rather than card-by-card. Applies to a new session.
        val gradeAtEnd: Boolean = false,
    ) : PracticeEntry()

    /** Resume an existing session; the mode + shuffle order come from the session. */
    data class Session(val sessionId: Long) : PracticeEntry()

    /** Guest mode (FLA-104): practice a public catalog deck in memory — no session, no persistence. */
    data class GuestDeck(
        val deckId: Long,
        val mode: String,
        val shuffle: Boolean = false,
        val questionCount: Int? = null,
        val gradeAtEnd: Boolean = false,
    ) : PracticeEntry()
}
