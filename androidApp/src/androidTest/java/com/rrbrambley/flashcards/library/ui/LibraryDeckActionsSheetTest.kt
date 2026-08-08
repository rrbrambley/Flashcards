package com.rrbrambley.flashcards.library.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.PracticeMode
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme
import org.junit.Rule
import org.junit.Test

/**
 * The practice options sheet's configure step outgrows the sheet once every setting is on (#367).
 * In a non-scrolling Column that squeezed the trailing "Start practice" button into whatever space
 * was left, clipping its label into an illegible sliver. The body scrolls now and the button is
 * pinned outside it, so it keeps its full height however tall the settings get.
 */
class LibraryDeckActionsSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun startPracticeButton_keepsItsHeight_whenTheSettingsOutgrowTheSheet() {
        showSheet()

        composeTestRule.onNodeWithText("Practice").performClick()
        // A gradeable mode, so the grade-at-the-end row is live too.
        composeTestRule.onNodeWithText("Test").performClick()

        // Material's minimum button height. Pre-fix this collapsed to the leftover sliver — the height
        // is asserted rather than mere visibility, since a squeezed button is still "displayed".
        composeTestRule.onNodeWithText("Start practice").assertHeightIsAtLeast(40.dp)
    }

    /**
     * Renders the sheet with every setting flag on, at double the font scale — the reliable way to
     * overflow it on any test device, standing in for the small screen, large accessibility font, or
     * one-more-setting-row that would do the same in the wild. Only the font scale is raised, so the
     * dp↔px conversion the assertion relies on is unchanged.
     */
    private fun showSheet() {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 2f),
            ) {
                FlashcardsTheme {
                    LibraryDeckActionsSheet(
                        deck = FlashcardDeck(
                            id = 1,
                            title = "Flags of the World",
                            flashcards = List(252) { Flashcard(question = "q$it", answer = "a$it") },
                        ),
                        availableModes = listOf(
                            PracticeMode.Classic,
                            PracticeMode.Test,
                            PracticeMode.MultipleChoice,
                        ),
                        questionCountEnabled = true,
                        gradeAtEndEnabled = true,
                        timerEnabled = true,
                        onDismissRequest = {},
                        onPracticeWithMode = { _, _, _, _, _ -> },
                        onEditClick = {},
                        onDeleteClick = {},
                    )
                }
            }
        }
    }
}
