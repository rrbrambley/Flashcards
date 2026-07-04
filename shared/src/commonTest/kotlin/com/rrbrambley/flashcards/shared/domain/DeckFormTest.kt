package com.rrbrambley.flashcards.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckFormTest {

    // ---- card completeness ----

    @Test
    fun isCardComplete_needsDefinitionPlusTermOrImage() {
        assertTrue(DeckForm.isCardComplete(term = "Bonjour", definition = "Hello", hasImage = false))
        assertTrue(DeckForm.isCardComplete(term = "", definition = "Hello", hasImage = true)) // image-only
        assertFalse(DeckForm.isCardComplete(term = "Bonjour", definition = " ", hasImage = false)) // no definition
        assertFalse(DeckForm.isCardComplete(term = "", definition = "Hello", hasImage = false)) // no term/image
    }

    @Test
    fun isCardStarted_trueOnceAnyFieldIsFilled() {
        assertFalse(DeckForm.isCardStarted(term = " ", definition = "", hasImage = false))
        assertTrue(DeckForm.isCardStarted(term = "x", definition = "", hasImage = false))
        assertTrue(DeckForm.isCardStarted(term = "", definition = "y", hasImage = false))
        assertTrue(DeckForm.isCardStarted(term = "", definition = "", hasImage = true))
    }

    // ---- save validity ----

    @Test
    fun isDeckSavable_requiresTitleCompleteCardAndNoPartial() {
        assertTrue(DeckForm.isDeckSavable(title = "French", hasCompleteCard = true, hasIncompleteStartedCard = false))
        assertFalse(DeckForm.isDeckSavable(title = " ", hasCompleteCard = true, hasIncompleteStartedCard = false))
        assertFalse(DeckForm.isDeckSavable(title = "French", hasCompleteCard = false, hasIncompleteStartedCard = false))
        assertFalse(DeckForm.isDeckSavable(title = "French", hasCompleteCard = true, hasIncompleteStartedCard = true))
    }

    // ---- alternatives ----

    @Test
    fun parseAlternatives_trimsDropsBlanksAndDedupesPreservingOrder() {
        assertEquals(
            listOf("uno", "dos", "tres"),
            DeckForm.parseAlternatives("uno\n  dos \n\ntres\nuno\n"),
        )
    }

    @Test
    fun alternativesText_isTheInverseJoin() {
        assertEquals("a\nb", DeckForm.alternativesText(listOf("a", "b")))
        assertEquals(listOf("a", "b"), DeckForm.parseAlternatives(DeckForm.alternativesText(listOf("a", "b"))))
    }

    // ---- category ⇄ tags ----

    @Test
    fun categoryTags_singleTrimmedTagOrEmpty() {
        assertEquals(emptyList(), DeckForm.categoryTags("   "))
        assertEquals(listOf("Geography"), DeckForm.categoryTags("  Geography "))
    }

    @Test
    fun categoryOf_isFirstTagOrEmpty() {
        assertEquals("Geography", DeckForm.categoryOf(listOf("Geography", "World")))
        assertEquals("", DeckForm.categoryOf(emptyList()))
    }

    // ---- draft → domain ----

    @Test
    fun toFlashcard_trimsParsesAndPreservesCardUid() {
        val card = DeckForm.toFlashcard(
            term = "  Bonjour ",
            definition = " Hello ",
            imageUrl = "https://cdn/x.png",
            alternativesRaw = "hi\nhey\nhi",
            cardUid = "card-1",
        )
        assertEquals("Bonjour", card.question)
        assertEquals("Hello", card.answer)
        assertEquals("https://cdn/x.png", card.imageUrl)
        assertEquals(listOf("hi", "hey"), card.alternativeAnswers)
        assertEquals("card-1", card.cardUid)
    }
}
