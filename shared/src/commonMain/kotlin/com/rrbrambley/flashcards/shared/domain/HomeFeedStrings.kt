package com.rrbrambley.flashcards.shared.domain

/**
 * User-facing copy for the home feed's offline fallback (shown when GET /home is unavailable).
 * Provided per platform so the strings stay localizable — Android via string resources, iOS via its
 * own localization.
 */
interface HomeFeedStrings {
    fun continuePracticeTitle(deckTitle: String): String
    val continuePracticeButton: String
    val practiceCountryFlagsTitle: String
    val practiceCountryFlagsButton: String
    val createNewSetTitle: String
    val createNewSetButton: String
}
