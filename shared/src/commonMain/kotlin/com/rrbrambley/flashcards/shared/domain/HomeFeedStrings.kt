package com.rrbrambley.flashcards.shared.domain

/**
 * User-facing copy for the home feed's offline fallback (shown when GET /home is unavailable).
 * Provided per platform so the strings stay localizable — Android via string resources, iOS via its
 * own localization.
 */
interface HomeFeedStrings {
    /** Section header above the "continue studying" cards (FLA-96). */
    val continueStudyingSection: String

    /** Section header above the "study something new" cards — featured deck + create (FLA-96). */
    val studySomethingNewSection: String

    /** Button on a continue-practice card (the card title is just the deck name). */
    val resumeButton: String
    fun practiceDeckTitle(deckTitle: String): String
    val practiceButton: String
    val createNewSetTitle: String
    val createNewSetButton: String
}
