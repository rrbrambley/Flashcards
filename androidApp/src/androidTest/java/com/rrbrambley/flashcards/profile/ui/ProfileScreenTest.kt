package com.rrbrambley.flashcards.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rrbrambley.flashcards.shared.api.AvatarDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Compose UI tests for the profile body ([ProfileContent]): loaded / failed / picker interactions. */
class ProfileScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val avatars = listOf(
        AvatarDto(key = "cat", url = "https://cdn.example/cat.png"),
        AvatarDto(key = "dog", url = "https://cdn.example/dog.png"),
    )

    private fun content(
        state: ProfileUiState,
        onRetry: () -> Unit = {},
        onClearAvatar: () -> Unit = {},
        onSelectAvatar: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ProfileContent(
                uiState = state,
                onRetry = onRetry,
                onClearAvatar = onClearAvatar,
                onSelectAvatar = onSelectAvatar,
            )
        }
    }

    @Test
    fun loaded_showsAvatarSectionAndOptions() {
        content(ProfileUiState(loading = false, avatars = avatars, email = "sam@x.com"))

        // profile_avatar_section + profile_avatar_hint
        composeTestRule.onNodeWithText("Avatar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pick an avatar shown on your profile and discussion posts.").assertIsDisplayed()
        // profile_cd_avatar_option = "%1$s avatar"
        composeTestRule.onNodeWithContentDescription("cat avatar").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("dog avatar").assertIsDisplayed()
    }

    @Test
    fun loadFailed_showsRetry_firesCallback() {
        var retried = false
        content(ProfileUiState(loading = false, loadFailed = true), onRetry = { retried = true })

        // profile_load_failed
        composeTestRule.onNodeWithText("Couldn't load your profile.").assertIsDisplayed()
        // profile_retry = "Try again"
        composeTestRule.onNodeWithText("Try again").performClick()
        assertTrue(retried)
    }

    @Test
    fun selectingAvatar_firesCallbackWithKey() {
        var selected: String? = null
        content(ProfileUiState(loading = false, avatars = avatars), onSelectAvatar = { selected = it })

        composeTestRule.onNodeWithContentDescription("dog avatar").performClick()

        assertEquals("dog", selected)
    }

    @Test
    fun removeAvatar_visibleWhenSelected_firesCallback() {
        var cleared = false
        content(
            ProfileUiState(loading = false, avatars = avatars, selectedAvatarKey = "cat"),
            onClearAvatar = { cleared = true },
        )

        // profile_remove_avatar
        composeTestRule.onNodeWithText("Remove avatar").performClick()
        assertTrue(cleared)
    }

    @Test
    fun noSelection_hasNoRemoveAffordance() {
        content(ProfileUiState(loading = false, avatars = avatars, selectedAvatarKey = null))

        composeTestRule.onNodeWithText("Remove avatar").assertDoesNotExist()
    }

    @Test
    fun avatarError_showsMessage() {
        content(ProfileUiState(loading = false, avatars = avatars, avatarError = true))

        // profile_avatar_error
        composeTestRule.onNodeWithText("Couldn't update your avatar.").assertIsDisplayed()
    }

    @Test
    fun selectionDisabled_showsNotice() {
        content(ProfileUiState(loading = false, avatarSelectionEnabled = false))

        // profile_avatar_selection_disabled
        composeTestRule.onNodeWithText("Avatar selection isn't available right now.").assertIsDisplayed()
    }
}
