package com.rrbrambley.flashcards.practice.discussions

import com.rrbrambley.flashcards.auth.Permissions
import com.rrbrambley.flashcards.auth.PermissionsRepository
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.ApiError
import com.rrbrambley.flashcards.shared.api.DiscussionMessageDto
import com.rrbrambley.flashcards.shared.api.DiscussionThreadDto
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.Page
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.domain.ActionError
import com.rrbrambley.flashcards.shared.domain.LocalDataStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscussionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun load_showsThreadAndFirstPage() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(firstPage = page(listOf(message(1, "Hi"))))
        val viewModel = DiscussionViewModel(repo, authService(), FakePermissionsRepository())

        viewModel.load(CARD_UID, isGuest = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf(1L), state.messages.map { it.id })
    }

    @Test
    fun lockedThread_isReflectedInState() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(thread = DiscussionThreadDto(CARD_UID, isLocked = true, messageCount = 0))
        val viewModel = DiscussionViewModel(repo, authService(), FakePermissionsRepository())

        viewModel.load(CARD_UID, isGuest = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLocked)
    }

    @Test
    fun post_appendsTheMessage_whenSignedIn() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(firstPage = page(emptyList()))
        val viewModel = DiscussionViewModel(repo, authService(), FakePermissionsRepository())
        viewModel.load(CARD_UID, isGuest = false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.post("Why Paris?", parentMessageId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Why Paris?"), repo.posted.map { it.first })
        assertEquals(listOf("Why Paris?"), viewModel.uiState.value.messages.map { it.content })
    }

    @Test
    fun guestPost_opensTheAuthPrompt_withoutPosting() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(firstPage = page(emptyList()))
        val viewModel = DiscussionViewModel(repo, authService(), FakePermissionsRepository())
        viewModel.load(CARD_UID, isGuest = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.post("As a guest", parentMessageId = null)

        assertTrue(viewModel.uiState.value.authPrompt)
        assertTrue(repo.posted.isEmpty())
    }

    @Test
    fun guestConversion_registersThenPostsAndSignsIn() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(firstPage = page(emptyList()))
        val viewModel = DiscussionViewModel(repo, authService(registerOk = true), FakePermissionsRepository())
        viewModel.load(CARD_UID, isGuest = true)
        viewModel.uiState.first { !it.loading }
        viewModel.post("Joining in", parentMessageId = null)

        viewModel.authenticateAndPost(register = true, email = "new@user.com", password = "password1")

        val state = viewModel.uiState.first { !it.authPrompt && it.messages.isNotEmpty() }
        assertFalse(state.isGuest)
        assertEquals(listOf("Joining in"), repo.posted.map { it.first })
        assertEquals(listOf("Joining in"), state.messages.map { it.content })
    }

    @Test
    fun loadMore_appendsTheNextPage() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(
            firstPage = page(listOf(message(1, "First")), nextCursor = "c1"),
            secondPage = page(listOf(message(2, "Second"))),
        )
        val viewModel = DiscussionViewModel(repo, authService(), FakePermissionsRepository())
        viewModel.load(CARD_UID, isGuest = false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasMore)

        viewModel.loadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.messages.map { it.id })
        assertFalse(viewModel.uiState.value.hasMore)
    }

    @Test
    fun post_mapsRateLimitTo429Error() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(
            firstPage = page(emptyList()),
            postError = ApiError.Client(429, "slow down"),
        )
        val viewModel = DiscussionViewModel(repo, authService(), FakePermissionsRepository())
        viewModel.load(CARD_UID, isGuest = false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.post("Too fast", parentMessageId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ActionError.RateLimit, viewModel.uiState.value.postError)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun report_marksTheMessageReported_whenSignedIn() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(firstPage = page(listOf(message(1, "Hi"))))
        val viewModel = DiscussionViewModel(repo, authService(), FakePermissionsRepository())
        viewModel.load(CARD_UID, isGuest = false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.report(1, "spam")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(1L to "spam"), repo.reported)
        assertTrue(1L in viewModel.uiState.value.reportedIds)
    }

    @Test
    fun guestReport_opensTheAuthPrompt_withoutReporting() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(firstPage = page(listOf(message(1, "Hi"))))
        val viewModel = DiscussionViewModel(repo, authService(), FakePermissionsRepository())
        viewModel.load(CARD_UID, isGuest = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.report(1, "spam")

        assertTrue(viewModel.uiState.value.authPrompt)
        assertTrue(repo.reported.isEmpty())
    }

    @Test
    fun guestConversion_replaysAPendingReport() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(firstPage = page(listOf(message(1, "Hi"))))
        val viewModel = DiscussionViewModel(repo, authService(registerOk = true), FakePermissionsRepository())
        viewModel.load(CARD_UID, isGuest = true)
        viewModel.uiState.first { !it.loading }
        viewModel.report(1, "spam")

        viewModel.authenticateAndPost(register = true, email = "new@user.com", password = "password1")

        val state = viewModel.uiState.first { !it.authPrompt && it.reportedIds.isNotEmpty() }
        assertFalse(state.isGuest)
        assertEquals(listOf(1L to "spam"), repo.reported)
    }

    @Test
    fun moderator_seesLockControl_andTogglingLocksThread() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(firstPage = page(emptyList()))
        val viewModel = DiscussionViewModel(
            repo,
            authService(),
            FakePermissionsRepository(setOf(Permissions.MANAGE_DISCUSSIONS)),
        )
        viewModel.load(CARD_UID, isGuest = false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canModerate)

        viewModel.toggleLock()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(true), repo.lockCalls)
        assertTrue(viewModel.uiState.value.isLocked)
    }

    @Test
    fun nonModerator_doesNotSeeLockControl_andToggleIsANoOp() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(firstPage = page(emptyList()))
        val viewModel = DiscussionViewModel(repo, authService(), FakePermissionsRepository())
        viewModel.load(CARD_UID, isGuest = false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canModerate)

        viewModel.toggleLock()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(repo.lockCalls.isEmpty())
    }

    @Test
    fun guest_neverSeesLockControl_evenWithThePermission() = runTest(testDispatcher) {
        // Guests aren't asked for permissions (no /auth/me), so the control stays hidden.
        val repo = FakeDiscussionRepository(firstPage = page(emptyList()))
        val viewModel = DiscussionViewModel(
            repo,
            authService(),
            FakePermissionsRepository(setOf(Permissions.MANAGE_DISCUSSIONS)),
        )
        viewModel.load(CARD_UID, isGuest = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canModerate)
    }

    @Test
    fun toggleLock_unlocksAnAlreadyLockedThread() = runTest(testDispatcher) {
        val repo = FakeDiscussionRepository(
            thread = DiscussionThreadDto(CARD_UID, isLocked = true, messageCount = 0),
            firstPage = page(emptyList()),
        )
        val viewModel = DiscussionViewModel(
            repo,
            authService(),
            FakePermissionsRepository(setOf(Permissions.MANAGE_DISCUSSIONS)),
        )
        viewModel.load(CARD_UID, isGuest = false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)

        viewModel.toggleLock()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(false), repo.lockCalls)
        assertFalse(viewModel.uiState.value.isLocked)
    }

    // --- Helpers ---

    private fun authService(registerOk: Boolean = true): AuthService {
        val engine = MockEngine {
            if (registerOk) {
                respond("""{"accessToken":"a","refreshToken":"r","userId":1}""", HttpStatusCode.OK, jsonHeaders)
            } else {
                respond("""{"error":"conflict"}""", HttpStatusCode.Conflict, jsonHeaders)
            }
        }
        val apiClient = FlashcardApiClient(createFlashcardHttpClient(engine), "http://localhost", { null })
        return AuthService(apiClient, FakeTokenStore(), FakeLocalDataStore())
    }

    private val jsonHeaders = headersOf("Content-Type", "application/json")

    private fun message(id: Long, content: String, parentMessageId: Long? = null) =
        DiscussionMessageDto(id, "Quiz Whiz", content, parentMessageId, createdAtMillis = id)

    private fun page(items: List<DiscussionMessageDto>, nextCursor: String? = null) = Page(items, nextCursor)

    private class FakeDiscussionRepository(
        private val thread: DiscussionThreadDto = DiscussionThreadDto(CARD_UID, isLocked = false, messageCount = 0),
        private val firstPage: Page<DiscussionMessageDto> = Page(emptyList(), null),
        private val secondPage: Page<DiscussionMessageDto> = Page(emptyList(), null),
        private val postError: ApiError? = null,
    ) : DiscussionRepository {
        val posted = mutableListOf<Pair<String, Long?>>()
        val reported = mutableListOf<Pair<Long, String?>>()

        override suspend fun thread(cardUid: String): DiscussionThreadDto = thread

        override suspend fun messages(cardUid: String, cursor: String?): Page<DiscussionMessageDto> =
            if (cursor == null) firstPage else secondPage

        override suspend fun post(cardUid: String, content: String, parentMessageId: Long?): DiscussionMessageDto {
            postError?.let { throw it }
            posted += content to parentMessageId
            return DiscussionMessageDto(1000L + posted.size, "Quiz Whiz", content, parentMessageId, createdAtMillis = 0)
        }

        override suspend fun report(messageId: Long, reason: String?) {
            reported += messageId to reason
        }

        override suspend fun setLocked(cardUid: String, locked: Boolean): DiscussionThreadDto {
            lockCalls += locked
            return DiscussionThreadDto(cardUid, isLocked = locked, messageCount = 0)
        }

        val lockCalls = mutableListOf<Boolean>()
    }

    private class FakePermissionsRepository(private val granted: Set<String> = emptySet()) : PermissionsRepository {
        override suspend fun permissions(): Set<String> = granted
    }

    private class FakeTokenStore : TokenStore {
        private val token = MutableStateFlow<String?>(null)
        override fun tokenFlow(): Flow<String?> = token
        override suspend fun currentToken(): String? = token.value
        override suspend fun currentRefreshToken(): String? = null
        override suspend fun setToken(token: String) {
            this.token.value = token
        }
        override suspend fun setTokens(accessToken: String, refreshToken: String) {
            token.value = accessToken
        }
        override suspend fun clearToken() {
            token.value = null
        }
    }

    private class FakeLocalDataStore : LocalDataStore {
        override suspend fun clearAll() = Unit
    }

    private companion object {
        const val CARD_UID = "card-1"
    }
}
