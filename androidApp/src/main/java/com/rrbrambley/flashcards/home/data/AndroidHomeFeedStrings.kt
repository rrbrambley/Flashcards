package com.rrbrambley.flashcards.home.data

import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import javax.inject.Inject

/** Android [HomeFeedStrings] backed by localizable string resources via [StringProvider]. */
class AndroidHomeFeedStrings @Inject constructor(
    private val stringProvider: StringProvider,
) : HomeFeedStrings {
    override fun continuePracticeTitle(deckTitle: String): String =
        stringProvider.getString(R.string.home_continue_practice_title, deckTitle)

    override val continuePracticeButton: String
        get() = stringProvider.getString(R.string.home_continue_practice_button)

    override val practiceCountryFlagsTitle: String
        get() = stringProvider.getString(R.string.home_country_flags_title)

    override val practiceCountryFlagsButton: String
        get() = stringProvider.getString(R.string.home_country_flags_button)

    override val createNewSetTitle: String
        get() = stringProvider.getString(R.string.home_create_set_title)

    override val createNewSetButton: String
        get() = stringProvider.getString(R.string.home_create_set_button)
}
