package com.rrbrambley.flashcards.home.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rrbrambley.flashcards.shared.domain.HomeButton
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.HomeData
import com.rrbrambley.flashcards.shared.domain.HomeSessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Compose UI tests for the home feed body ([HomeScreenContent]), driven by fake [HomeData] (FLA-256). */
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val practiceCard = HomeData(
        title = "Practice your flashcards",
        button = HomeButton(message = "Start practice", action = HomeButtonAction.NavigateToPractice(deckId = 1)),
    )

    @Test
    fun rendersCardTitleAndButton() {
        composeTestRule.setContent {
            HomeScreenContent(cards = listOf(practiceCard), streak = null, onButtonAction = {})
        }

        composeTestRule.onNodeWithText("Practice your flashcards").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start practice").assertIsDisplayed()
    }

    @Test
    fun tappingButton_firesItsAction() {
        var action: HomeButtonAction? = null
        composeTestRule.setContent {
            HomeScreenContent(cards = listOf(practiceCard), streak = null, onButtonAction = { action = it })
        }

        composeTestRule.onNodeWithText("Start practice").performClick()

        assertEquals(HomeButtonAction.NavigateToPractice(deckId = 1), action)
    }

    @Test
    fun streakBadge_showsWhenActive_hidesWhenNull() {
        composeTestRule.setContent {
            HomeScreenContent(cards = listOf(practiceCard), streak = 5, onButtonAction = {})
        }
        // streak_badge = "🔥 %1$d day streak"
        composeTestRule.onNodeWithText("🔥 5 day streak").assertIsDisplayed()
    }

    @Test
    fun streakBadge_hiddenWhenNull() {
        composeTestRule.setContent {
            HomeScreenContent(cards = listOf(practiceCard), streak = null, onButtonAction = {})
        }
        composeTestRule.onNodeWithText("day streak", substring = true).assertDoesNotExist()
    }

    @Test
    fun continueCard_showsRemoveAffordance_andFiresCallback() {
        val continueCard = HomeData(
            title = "Spanish",
            section = "Continue studying",
            session = HomeSessionInfo(
                mode = "flashcards",
                numCorrect = 3,
                numIncorrect = 1,
                currentCardIndex = 4,
                totalCards = 10,
            ),
            button = HomeButton(message = "Resume", action = HomeButtonAction.ContinuePractice(sessionId = 7)),
        )
        var removed: Pair<Long, String>? = null
        composeTestRule.setContent {
            HomeScreenContent(
                cards = listOf(continueCard),
                streak = null,
                onButtonAction = {},
                onRequestRemove = { id, title -> removed = id to title },
            )
        }

        // The session score detail renders on the continue card (home_session_correct = "✓ %1$d").
        composeTestRule.onNodeWithText("✓ 3").assertIsDisplayed()

        // home_remove_session_cd = "Remove practice session"
        composeTestRule.onNodeWithContentDescription("Remove practice session").performClick()
        assertEquals(7L to "Spanish", removed)
    }

    @Test
    fun nonContinueCard_hasNoRemoveAffordance() {
        var removed: Pair<Long, String>? = null
        composeTestRule.setContent {
            HomeScreenContent(
                cards = listOf(practiceCard),
                streak = null,
                onButtonAction = {},
                onRequestRemove = { id, title -> removed = id to title },
            )
        }

        composeTestRule.onNodeWithContentDescription("Remove practice session").assertDoesNotExist()
        assertNull(removed)
    }
}
