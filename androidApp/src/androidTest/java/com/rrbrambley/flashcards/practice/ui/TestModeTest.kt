package com.rrbrambley.flashcards.practice.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.rrbrambley.flashcards.shared.domain.Flashcard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * FLA-190: an accidental empty submit (keyboard Done or Check) must confirm the skip rather than
 * silently grading a blank answer wrong. Compose UI test over the [TestMode] composable.
 */
class TestModeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val card = Flashcard(question = "Capital of France?", answer = "Paris")

    @Test
    fun blankCheck_showsConfirm_andDoesNotGrade() {
        var graded: Pair<Boolean, String?>? = null
        composeTestRule.setContent {
            TestMode(flashcard = card, onGraded = { correct, input -> graded = correct to input }, onAdvance = {})
        }

        composeTestRule.onNodeWithText("Check").performClick()

        composeTestRule.onNodeWithText("skip this one", substring = true).assertIsDisplayed()
        assertNull(graded)
    }

    @Test
    fun confirm_gradesTheBlankAnswerIncorrect() {
        var graded: Pair<Boolean, String?>? = null
        composeTestRule.setContent {
            TestMode(flashcard = card, onGraded = { correct, input -> graded = correct to input }, onAdvance = {})
        }

        composeTestRule.onNodeWithText("Check").performClick()
        composeTestRule.onNodeWithText("Confirm").performClick()

        assertEquals(false, graded?.first)
        assertTrue(graded?.second.isNullOrBlank())
        // The verdict replaces the input row, so "Next" is now shown.
        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test
    fun typing_dismissesTheConfirm() {
        composeTestRule.setContent {
            TestMode(flashcard = card, onGraded = { _, _ -> }, onAdvance = {})
        }

        composeTestRule.onNodeWithText("Check").performClick()
        composeTestRule.onNodeWithText("skip this one", substring = true).assertIsDisplayed()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("paris")

        composeTestRule.onNodeWithText("skip this one", substring = true).assertDoesNotExist()
    }

    @Test
    fun nonBlankCheck_gradesImmediately_withNoConfirm() {
        var graded: Pair<Boolean, String?>? = null
        composeTestRule.setContent {
            TestMode(flashcard = card, onGraded = { correct, input -> graded = correct to input }, onAdvance = {})
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("paris")
        composeTestRule.onNodeWithText("Check").performClick()

        assertEquals(true, graded?.first)
        assertEquals("paris", graded?.second)
        composeTestRule.onNodeWithText("skip this one", substring = true).assertDoesNotExist()
    }
}
