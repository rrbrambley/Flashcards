package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings

/**
 * English [HomeFeedStrings] used by platforms without their own localized resources (the iOS SDK
 * defaults to this). Android overrides it with a resource-backed `AndroidHomeFeedStrings`; iOS can
 * later swap in a localized implementation. Copy mirrors `androidApp`'s `home_*` string resources.
 */
object DefaultHomeFeedStrings : HomeFeedStrings {
    override fun continuePracticeTitle(deckTitle: String): String = "Continue $deckTitle practice"
    override val continuePracticeButton: String = "Continue practice"
    override val practiceCountryFlagsTitle: String = "Practice the flags of the world"
    override val practiceCountryFlagsButton: String = "Practice"
    override val createNewSetTitle: String = "Create a new flashcard set"
    override val createNewSetButton: String = "Create"
}
