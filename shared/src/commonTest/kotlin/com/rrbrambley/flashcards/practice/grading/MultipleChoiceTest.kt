package com.rrbrambley.flashcards.practice.grading

import com.rrbrambley.flashcards.shared.domain.Flashcard
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MultipleChoiceTest {

    private val deck = listOf(
        Flashcard("France", "Paris"),
        Flashcard("Japan", "Tokyo"),
        Flashcard("Italy", "Rome"),
        Flashcard("Spain", "Madrid"),
        Flashcard("Germany", "Berlin"),
    )

    // A fresh seeded RNG per call so the shuffle is deterministic.
    private fun seeded() = Random(42)

    @Test
    fun returnsUpToFourOptionsIncludingTheCorrectAnswer() {
        val choices = buildChoices(deck[0], deck, count = 4, random = seeded())
        assertEquals(4, choices.size)
        assertContains(choices, "Paris")
    }

    @Test
    fun drawsUniqueDistractorsFromOtherCardsNeverTheCorrectAnswer() {
        val choices = buildChoices(deck[0], deck, count = 4, random = seeded())
        val distractors = choices.filter { it != "Paris" }
        assertEquals(3, distractors.size)
        distractors.forEach { assertContains(listOf("Tokyo", "Rome", "Madrid", "Berlin"), it) }
        assertEquals(choices.size, choices.toSet().size) // no duplicates
    }

    @Test
    fun deduplicatesCaseInsensitivelyAndSkipsBlankAnswers() {
        val dupes = listOf(
            Flashcard("France", "Paris"),
            Flashcard("Capital of France", "paris"), // case-dup of the correct answer → dropped
            Flashcard("Japan", "Tokyo"),
            Flashcard("Mystery", ""), // blank → dropped
        )
        val choices = buildChoices(dupes[0], dupes, count = 4, random = seeded())
        assertContains(choices, "Paris")
        assertEquals(listOf("Tokyo"), choices.filter { it != "Paris" })
    }

    @Test
    fun yieldsFewerThanCountForASmallDeck() {
        val small = listOf(Flashcard("France", "Paris"), Flashcard("Japan", "Tokyo"))
        assertEquals(listOf("Paris", "Tokyo"), buildChoices(small[0], small, count = 4, random = seeded()).sorted())
    }

    @Test
    fun isDeterministicWithASeededRng() {
        assertEquals(
            buildChoices(deck[0], deck, count = 4, random = Random(7)),
            buildChoices(deck[0], deck, count = 4, random = Random(7)),
        )
    }
}
