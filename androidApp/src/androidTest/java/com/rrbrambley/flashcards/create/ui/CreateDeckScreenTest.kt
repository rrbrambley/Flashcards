package com.rrbrambley.flashcards.create.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Compose UI tests for the create/edit form body ([CreateDeckContent]): fields, validation, remove. */
class CreateDeckScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun card(id: Long, term: String = "", definition: String = "") =
        DeckFlashcardDraft(id = id, term = term, definition = definition)

    @Test
    fun rendersFormFieldLabels() {
        composeTestRule.setContent {
            CreateDeckContent(
                deckTitle = "",
                cards = listOf(card(1)),
                showValidationErrors = false,
                onDeckTitleChange = {},
                onTermChange = { _, _ -> },
                onDefinitionChange = { _, _ -> },
                onImageSelected = { _, _ -> },
                onRemoveImage = {},
            )
        }

        composeTestRule.onNodeWithText("Deck title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Category (optional)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Term").assertIsDisplayed()
        composeTestRule.onNodeWithText("Definition").assertIsDisplayed()
    }

    @Test
    fun typingTitle_firesCallback() {
        var changed: String? = null
        composeTestRule.setContent {
            CreateDeckContent(
                deckTitle = "",
                cards = listOf(card(1)),
                showValidationErrors = false,
                onDeckTitleChange = { changed = it },
                onTermChange = { _, _ -> },
                onDefinitionChange = { _, _ -> },
                onImageSelected = { _, _ -> },
                onRemoveImage = {},
            )
        }

        // Target the (empty) title field by its unique label, then type into it.
        composeTestRule.onNodeWithText("Deck title").performTextInput("Deck")

        assertEquals("Deck", changed)
    }

    @Test
    fun typingTerm_firesCallbackForThatCard() {
        var termCardId: Long? = null
        composeTestRule.setContent {
            CreateDeckContent(
                deckTitle = "Deck",
                cards = listOf(card(9, term = "ca")),
                showValidationErrors = false,
                onDeckTitleChange = {},
                onTermChange = { id, _ -> termCardId = id },
                onDefinitionChange = { _, _ -> },
                onImageSelected = { _, _ -> },
                onRemoveImage = {},
            )
        }

        composeTestRule.onNodeWithText("ca").performTextInput("t")

        assertEquals(9L, termCardId)
    }

    @Test
    fun blankTitle_withValidation_showsTitleError() {
        composeTestRule.setContent {
            CreateDeckContent(
                deckTitle = "",
                cards = listOf(card(1)),
                showValidationErrors = true,
                onDeckTitleChange = {},
                onTermChange = { _, _ -> },
                onDefinitionChange = { _, _ -> },
                onImageSelected = { _, _ -> },
                onRemoveImage = {},
            )
        }

        // create_deck_title_error
        composeTestRule.onNodeWithText("Enter a deck title").assertIsDisplayed()
    }

    @Test
    fun noCompleteCard_withValidation_showsCardCountError() {
        composeTestRule.setContent {
            CreateDeckContent(
                deckTitle = "Deck",
                cards = listOf(card(1)), // empty card → 0 complete
                showValidationErrors = true,
                onDeckTitleChange = {},
                onTermChange = { _, _ -> },
                onDefinitionChange = { _, _ -> },
                onImageSelected = { _, _ -> },
                onRemoveImage = {},
            )
        }

        // create_deck_card_count_error
        composeTestRule
            .onNodeWithText("Add at least one card with a definition and a term or image.")
            .assertIsDisplayed()
    }

    @Test
    fun multipleCards_removeAffordance_firesCallback() {
        var removedId: Long? = null
        composeTestRule.setContent {
            CreateDeckContent(
                deckTitle = "Deck",
                cards = listOf(card(1, "a", "b"), card(2, "c", "d")),
                showValidationErrors = false,
                onDeckTitleChange = {},
                onTermChange = { _, _ -> },
                onDefinitionChange = { _, _ -> },
                onImageSelected = { _, _ -> },
                onRemoveImage = {},
                onRemoveCard = { removedId = it },
            )
        }

        // create_deck_cd_remove_card = "Remove card %1$d" (card number, 1-based)
        composeTestRule.onNodeWithContentDescription("Remove card 2").performClick()

        assertEquals(2L, removedId)
    }

    @Test
    fun singleCard_hasNoRemoveAffordance() {
        composeTestRule.setContent {
            CreateDeckContent(
                deckTitle = "Deck",
                cards = listOf(card(1, "a", "b")),
                showValidationErrors = false,
                onDeckTitleChange = {},
                onTermChange = { _, _ -> },
                onDefinitionChange = { _, _ -> },
                onImageSelected = { _, _ -> },
                onRemoveImage = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Remove card 1").assertDoesNotExist()
    }
}
