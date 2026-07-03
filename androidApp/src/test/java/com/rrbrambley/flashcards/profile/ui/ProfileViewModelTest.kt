package com.rrbrambley.flashcards.profile.ui

import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.profile.ProfileRepository
import com.rrbrambley.flashcards.shared.api.ApiError
import com.rrbrambley.flashcards.shared.api.AvatarDto
import com.rrbrambley.flashcards.shared.api.MeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun load_populatesCatalogAndCurrentSelection() = runTest(testDispatcher) {
        val repo = FakeProfileRepository(me = me(avatarKey = "dragon", avatarUrl = url("dragon")), avatars = catalog)
        val viewModel = ProfileViewModel(repo, flagRepo)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf("dragon", "yeti"), state.avatars.map { it.key })
        assertEquals("dragon", state.selectedAvatarKey)
        assertEquals(url("dragon"), state.avatarUrl)
    }

    @Test
    fun selectAvatar_sendsKeyAndUpdatesSelection() = runTest(testDispatcher) {
        val repo = FakeProfileRepository(me = me(), avatars = catalog)
        val viewModel = ProfileViewModel(repo, flagRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAvatar("yeti")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("yeti"), repo.updatedKeys)
        val state = viewModel.uiState.value
        assertEquals("yeti", state.selectedAvatarKey)
        assertEquals(url("yeti"), state.avatarUrl)
        assertFalse(state.saving)
    }

    @Test
    fun clearAvatar_sendsBlankKeyAndResetsSelection() = runTest(testDispatcher) {
        val repo = FakeProfileRepository(me = me(avatarKey = "dragon", avatarUrl = url("dragon")), avatars = catalog)
        val viewModel = ProfileViewModel(repo, flagRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearAvatar()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(""), repo.updatedKeys)
        val state = viewModel.uiState.value
        assertNull(state.selectedAvatarKey)
        assertNull(state.avatarUrl)
    }

    @Test
    fun load_emptyCatalog_whenAvatarsFail_stillShowsProfile() = runTest(testDispatcher) {
        val repo = FakeProfileRepository(me = me(), avatars = catalog, failAvatars = true)
        val viewModel = ProfileViewModel(repo, flagRepo)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertFalse(state.loadFailed)
        assertTrue(state.avatars.isEmpty())
    }

    @Test
    fun load_failsWhenMeFails() = runTest(testDispatcher) {
        val repo = FakeProfileRepository(me = me(), avatars = catalog, failMe = true)
        val viewModel = ProfileViewModel(repo, flagRepo)

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loadFailed)
    }

    @Test
    fun selectAvatar_setsErrorOnFailure() = runTest(testDispatcher) {
        val repo = FakeProfileRepository(me = me(), avatars = catalog, failUpdate = true)
        val viewModel = ProfileViewModel(repo, flagRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAvatar("yeti")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.avatarError)
        assertFalse(state.saving)
    }

    @Test
    fun avatarSelectionEnabled_reflectsTheFlag() = runTest(testDispatcher) {
        val repo = FakeProfileRepository(me = me(), avatars = catalog)
        val offFlags = FakeFeatureFlagRepository(mapOf(FeatureFlags.AVATAR_SELECTION to false))

        val viewModel = ProfileViewModel(repo, offFlags)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.avatarSelectionEnabled)

        // ...and on when the flag is enabled (the shared flagRepo).
        val onViewModel = ProfileViewModel(FakeProfileRepository(me = me(), avatars = catalog), flagRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(onViewModel.uiState.value.avatarSelectionEnabled)
    }

    // Avatar selection on by default; the gating test builds its own repo with it off.
    private val flagRepo = FakeFeatureFlagRepository(mapOf(FeatureFlags.AVATAR_SELECTION to true))

    private fun me(avatarKey: String? = null, avatarUrl: String? = null) = MeResponse(
        userId = 1,
        email = "rob@example.com",
        displayName = "Rob B",
        avatarKey = avatarKey,
        avatarUrl = avatarUrl,
    )

    private fun url(key: String) = "https://cdn.test/avatars/$key.png"

    private val catalog = listOf(
        AvatarDto("dragon", url("dragon")),
        AvatarDto("yeti", url("yeti")),
    )

    private inner class FakeProfileRepository(
        private var me: MeResponse,
        private val avatars: List<AvatarDto>,
        private val failMe: Boolean = false,
        private val failAvatars: Boolean = false,
        private val failUpdate: Boolean = false,
    ) : ProfileRepository {
        val updatedKeys = mutableListOf<String>()

        override suspend fun me(): MeResponse {
            if (failMe) throw ApiError.Server(500, "boom")
            return me
        }

        override suspend fun avatars(): List<AvatarDto> {
            if (failAvatars) throw ApiError.ServiceUnavailable("no cdn")
            return avatars
        }

        override suspend fun updateAvatar(key: String): MeResponse {
            if (failUpdate) throw ApiError.Validation("bad key")
            updatedKeys += key
            me = me.copy(
                avatarKey = key.ifBlank { null },
                avatarUrl = if (key.isBlank()) null else url(key),
            )
            return me
        }
    }

    private class FakeFeatureFlagRepository(private val flags: Map<String, Boolean> = emptyMap()) : FeatureFlagRepository {
        override suspend fun flags(): Map<String, Boolean> = flags
    }
}
