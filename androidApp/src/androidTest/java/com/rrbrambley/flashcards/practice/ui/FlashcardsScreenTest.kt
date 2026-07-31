package com.rrbrambley.flashcards.practice.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rrbrambley.flashcards.shared.domain.BatchPracticeUiState
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.PracticeMode
import com.rrbrambley.flashcards.shared.domain.ReviewItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for the practice runner's stateless surfaces: the grade-at-the-end
 * [BatchPracticeScreen] (answering / completed / failed) and the shared completion recap
 * [FlashcardsCompletionContent] (FLA-256).
 */
class FlashcardsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun review(question: String, answer: String, correct: Boolean, submitted: String?) = ReviewItem(
        answerUid = "u-$question",
        cardUid = "c-$question",
        question = question,
        answer = answer,
        imageUrl = null,
        correct = correct,
        submittedText = submitted,
    )

    @Test
    fun batchAnswering_rendersCardsAndSubmitBar() {
        val state = BatchPracticeUiState.Answering(
            cards = listOf(
                Flashcard(question = "Capital of France?", answer = "Paris"),
                Flashcard(question = "Capital of Spain?", answer = "Madrid"),
            ),
            mode = PracticeMode.Test.key,
        )
        composeTestRule.setContent {
            BatchPracticeScreen(
                state = state,
                remainingSeconds = null,
                onSubmit = {},
                sharedDeck = { null },
                isGuest = false,
                onBack = {},
            )
        }

        composeTestRule.onNodeWithText("Capital of France?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Capital of Spain?").assertIsDisplayed()
        // practice_batch_submit = "Submit (%1$d/%2$d)" — nothing answered yet.
        composeTestRule.onNodeWithText("Submit (0/2)").assertIsDisplayed()
    }

    @Test
    fun batchCompleted_showsRecap() {
        val state = BatchPracticeUiState.Completed(
            numCorrect = 1,
            numIncorrect = 1,
            review = listOf(
                review("Capital of France?", "Paris", correct = true, submitted = "Paris"),
                review("Capital of Spain?", "Madrid", correct = false, submitted = "Barcelona"),
            ),
        )
        composeTestRule.setContent {
            BatchPracticeScreen(
                state = state,
                remainingSeconds = null,
                onSubmit = {},
                sharedDeck = { null },
                isGuest = false,
                onBack = {},
            )
        }

        // practice_complete_title + practice_review_heading
        composeTestRule.onNodeWithText("Practice complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review").assertIsDisplayed()
    }

    @Test
    fun batchCompleted_closeFiresOnBack() {
        var backed = false
        composeTestRule.setContent {
            BatchPracticeScreen(
                state = BatchPracticeUiState.Completed(numCorrect = 2, numIncorrect = 0),
                remainingSeconds = null,
                onSubmit = {},
                sharedDeck = { null },
                isGuest = false,
                onBack = { backed = true },
            )
        }

        // practice_cd_back = "Back" — the close affordance is shown once the run is complete.
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backed)
    }

    @Test
    fun batchFailed_showsNoRecap() {
        composeTestRule.setContent {
            BatchPracticeScreen(
                state = BatchPracticeUiState.Failed,
                remainingSeconds = null,
                onSubmit = {},
                sharedDeck = { null },
                isGuest = false,
                onBack = {},
            )
        }

        composeTestRule.onNodeWithText("Practice complete").assertDoesNotExist()
    }

    @Test
    fun completionContent_rendersTitleAndReviewRows() {
        composeTestRule.setContent {
            FlashcardsCompletionContent(
                streak = 4,
                review = listOf(
                    review("Capital of France?", "Paris", correct = true, submitted = "Paris"),
                    review("Capital of Spain?", "Madrid", correct = false, submitted = "Barcelona"),
                ),
            )
        }

        composeTestRule.onNodeWithText("Practice complete").assertIsDisplayed()
        // practice_complete_subtitle
        composeTestRule.onNodeWithText("Nice work reviewing this deck.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review").assertIsDisplayed()
        // A review row surfaces its question + correct answer.
        composeTestRule.onNodeWithText("Capital of Spain?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Madrid").assertIsDisplayed()
    }

    @Test
    fun completionContent_withNoReview_showsNoReviewHeading() {
        composeTestRule.setContent {
            FlashcardsCompletionContent(streak = null, review = emptyList())
        }

        composeTestRule.onNodeWithText("Practice complete").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review").assertDoesNotExist()
    }
}
