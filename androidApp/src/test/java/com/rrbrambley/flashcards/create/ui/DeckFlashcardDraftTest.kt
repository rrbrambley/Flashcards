package com.rrbrambley.flashcards.create.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A card is complete with a definition plus either a term or an image (image-only allowed). */
class DeckFlashcardDraftTest {

    private fun draft(term: String = "", definition: String = "", imageUrl: String? = null) =
        DeckFlashcardDraft(id = 1L, term = term, definition = definition, imageUrl = imageUrl)

    @Test
    fun emptyDraft_isNeitherStartedNorComplete() {
        assertFalse(draft().isStarted())
        assertFalse(draft().isComplete())
    }

    @Test
    fun termOnly_isStartedButNotComplete() {
        val card = draft(term = "Hola")
        assertTrue(card.isStarted())
        assertFalse(card.isComplete())
    }

    @Test
    fun definitionOnly_isStartedButNotComplete() {
        val card = draft(definition = "Hello")
        assertTrue(card.isStarted())
        assertFalse(card.isComplete())
    }

    @Test
    fun termAndDefinition_isComplete() {
        assertTrue(draft(term = "Hola", definition = "Hello").isComplete())
    }

    @Test
    fun imageAndDefinition_isComplete_evenWithoutTerm() {
        assertTrue(draft(definition = "Canada", imageUrl = "https://cdn/flag.png").isComplete())
    }

    @Test
    fun imageOnly_isStartedButNotComplete() {
        val card = draft(imageUrl = "https://cdn/flag.png")
        assertTrue(card.isStarted())
        assertFalse(card.isComplete())
    }

    @Test
    fun blankWhitespace_doesNotCountAsContent() {
        val card = draft(term = "   ", definition = "   ")
        assertFalse(card.isStarted())
        assertFalse(card.isComplete())
    }
}
