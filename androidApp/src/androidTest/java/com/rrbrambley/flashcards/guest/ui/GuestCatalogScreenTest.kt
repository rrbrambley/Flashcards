package com.rrbrambley.flashcards.guest.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rrbrambley.flashcards.shared.api.FlashcardDeckDto
import com.rrbrambley.flashcards.shared.api.FlashcardDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Compose UI tests for the guest catalog body ([GuestCatalogContent]): loaded / failed / empty. */
class GuestCatalogScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun deck(id: Long, title: String, tags: List<String> = emptyList(), cards: Int = 3) = FlashcardDeckDto(
        id = id,
        title = title,
        flashcards = List(cards) { FlashcardDto(question = "q$it", answer = "a$it") },
        tags = tags,
        isGlobal = true,
    )

    @Test
    fun loaded_rendersDecksAndSubtitle() {
        composeTestRule.setContent {
            GuestCatalogContent(
                uiState = GuestCatalogUiState.Loaded(listOf(deck(1, "Capitals", tags = listOf("Geography")))),
                onRetry = {},
                onPracticeDeck = {},
            )
        }

        // guest_catalog_subtitle
        composeTestRule.onNodeWithText("Start studying — no account needed.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Capitals").assertIsDisplayed()
        composeTestRule.onNodeWithText("Geography").assertIsDisplayed()
        // deck_card_count plural → "3 cards"
        composeTestRule.onNodeWithText("3 cards").assertIsDisplayed()
    }

    @Test
    fun tappingDeck_firesCallbackWithThatDeck() {
        val capitals = deck(1, "Capitals")
        var practiced: FlashcardDeckDto? = null
        composeTestRule.setContent {
            GuestCatalogContent(
                uiState = GuestCatalogUiState.Loaded(listOf(capitals, deck(2, "Flags"))),
                onRetry = {},
                onPracticeDeck = { practiced = it },
            )
        }

        composeTestRule.onNodeWithText("Capitals").performClick()

        assertEquals(capitals, practiced)
    }

    @Test
    fun empty_showsEmptyMessage() {
        composeTestRule.setContent {
            GuestCatalogContent(
                uiState = GuestCatalogUiState.Loaded(emptyList()),
                onRetry = {},
                onPracticeDeck = {},
            )
        }

        // guest_catalog_empty
        composeTestRule.onNodeWithText("No decks are available yet.").assertIsDisplayed()
    }

    @Test
    fun failed_showsRetry_firesCallback() {
        var retried = false
        composeTestRule.setContent {
            GuestCatalogContent(
                uiState = GuestCatalogUiState.Failed,
                onRetry = { retried = true },
                onPracticeDeck = {},
            )
        }

        // guest_catalog_error
        composeTestRule
            .onNodeWithText("Couldn't load the deck catalog. Check your connection and try again.")
            .assertIsDisplayed()
        // action_retry = "Retry"
        composeTestRule.onNodeWithText("Retry").performClick()
        assertTrue(retried)
    }
}
