package com.rrbrambley.flashcards.practice.ui

import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.PracticeMode
import com.rrbrambley.flashcards.shared.domain.PracticeUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Classic-only "How to practice" help gate (#351). */
class FlashcardsHelpTest {

    private fun showCard(mode: String) = PracticeUiState.ShowCard(
        card = Flashcard(question = "q", answer = "a"),
        position = 0,
        numCorrect = 0,
        numIncorrect = 0,
        canGoBack = false,
        mode = mode,
        deck = emptyList(),
        discussionsEnabled = false,
        isGlobal = false,
        streak = 0,
    )

    @Test
    fun help_is_offered_only_while_showing_a_classic_card() {
        assertTrue(offersClassicHelp(showCard(PracticeMode.Classic.key)))
        assertFalse(offersClassicHelp(showCard(PracticeMode.Test.key)))
        assertFalse(offersClassicHelp(showCard(PracticeMode.MultipleChoice.key)))
    }

    @Test
    fun help_is_not_offered_off_the_card_screen() {
        // The bug (#351): non-ShowCard states defaulted to "classic", so the flip/swipe help showed on
        // the Test/Multiple-Choice completion screen. It must be off for every non-card state.
        assertFalse(offersClassicHelp(PracticeUiState.Completed(numCorrect = 5, numIncorrect = 1)))
        assertFalse(offersClassicHelp(PracticeUiState.Loading))
        assertFalse(offersClassicHelp(PracticeUiState.Failed))
    }
}
