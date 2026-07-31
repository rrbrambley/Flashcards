package com.rrbrambley.flashcards.library.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.rrbrambley.flashcards.shared.domain.DeckSortOrder
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compose UI tests for the library body ([LibraryContent]): deck list, search + sort, empty states. */
class LibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun deck(id: Long, title: String, tags: List<String> = emptyList(), cards: Int = 2) = FlashcardDeck(
        id = id,
        title = title,
        flashcards = List(cards) { Flashcard(question = "q$it", answer = "a$it") },
        tags = tags,
    )

    @Test
    fun rendersDeckTitleCategoryAndCount() {
        composeTestRule.setContent {
            LibraryContent(decks = listOf(deck(1, "Spanish", tags = listOf("Language"), cards = 2)))
        }

        composeTestRule.onNodeWithText("Spanish").assertIsDisplayed()
        composeTestRule.onNodeWithText("Language").assertIsDisplayed()
        // deck_card_count plural → "2 cards"
        composeTestRule.onNodeWithText("2 cards").assertIsDisplayed()
    }

    @Test
    fun emptyLibrary_showsEmptyMessage_andNoSearchField() {
        composeTestRule.setContent {
            LibraryContent(decks = emptyList())
        }

        // library_empty_title
        composeTestRule.onNodeWithText("No saved decks yet").assertIsDisplayed()
        // The search field is hidden when there's nothing to search and no active query.
        composeTestRule.onNodeWithText("Search decks").assertDoesNotExist()
    }

    @Test
    fun searchWithNoMatches_showsNoResultsMessage() {
        composeTestRule.setContent {
            LibraryContent(decks = emptyList(), searchQuery = "xyz")
        }

        // library_no_results = "No decks match \"%1$s\""
        composeTestRule.onNodeWithText("No decks match \"xyz\"").assertIsDisplayed()
    }

    @Test
    fun typingInSearch_firesCallback() {
        var query = ""
        composeTestRule.setContent {
            LibraryContent(decks = listOf(deck(1, "Spanish")), onSearchQueryChange = { query = it })
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("sp")

        assertEquals("sp", query)
    }

    @Test
    fun tappingDeck_firesCallbackWithThatDeck() {
        val spanish = deck(1, "Spanish")
        var clicked: FlashcardDeck? = null
        composeTestRule.setContent {
            LibraryContent(decks = listOf(spanish, deck(2, "French")), onDeckClick = { clicked = it })
        }

        composeTestRule.onNodeWithText("Spanish").performClick()

        assertEquals(spanish, clicked)
    }

    @Test
    fun tappingSortChip_firesCallback() {
        var order: DeckSortOrder? = null
        composeTestRule.setContent {
            LibraryContent(
                decks = listOf(deck(1, "Spanish")),
                sortOrder = DeckSortOrder.Alphabetical,
                onSortOrderChange = { order = it },
            )
        }

        // library_sort_recent = "Recently practiced"
        composeTestRule.onNodeWithText("Recently practiced").performClick()

        assertEquals(DeckSortOrder.RecentlyPracticed, order)
    }
}
