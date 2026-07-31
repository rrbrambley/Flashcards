package com.rrbrambley.flashcards.edit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.rrbrambley.flashcards.create.ui.CreateDeckContent
import com.rrbrambley.flashcards.create.ui.DeckFlashcardDraft
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for edit-mode rendering. Edit reuses [CreateDeckContent]; its distinguishing
 * behavior is the read-only mode for a non-editable (global catalog) deck (FLA-256).
 */
class EditDeckScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun card(id: Long, term: String, definition: String) =
        DeckFlashcardDraft(id = id, term = term, definition = definition)

    private fun content(editable: Boolean) {
        composeTestRule.setContent {
            CreateDeckContent(
                deckTitle = "Capitals",
                cards = listOf(card(1, "France", "Paris"), card(2, "Spain", "Madrid")),
                showValidationErrors = false,
                onDeckTitleChange = {},
                onTermChange = { _, _ -> },
                onDefinitionChange = { _, _ -> },
                onImageSelected = { _, _ -> },
                onRemoveImage = {},
                editable = editable,
            )
        }
    }

    @Test
    fun readOnlyDeck_showsBanner() {
        content(editable = false)

        // create_deck_readonly
        composeTestRule.onNodeWithText("This deck is read-only and can't be edited.").assertIsDisplayed()
    }

    @Test
    fun readOnlyDeck_hidesRemoveAffordance_evenWithMultipleCards() {
        content(editable = false)

        composeTestRule.onNodeWithContentDescription("Remove card 2").assertDoesNotExist()
    }

    @Test
    fun editableDeck_hasNoReadOnlyBanner() {
        content(editable = true)

        composeTestRule.onNodeWithText("This deck is read-only and can't be edited.").assertDoesNotExist()
        // Editable multi-card deck keeps the remove affordance.
        composeTestRule.onNodeWithContentDescription("Remove card 2").assertIsDisplayed()
    }
}
