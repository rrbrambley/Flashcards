package com.rrbrambley.flashcards.practice.suggestions

import com.rrbrambley.flashcards.shared.domain.ActionError

import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.domain.LocalDataStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
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
class SuggestionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun suggest_submitsTheAnswer_whenSignedIn() = runTest(testDispatcher) {
        val recorded = mutableListOf<String>()
        val viewModel = SuggestionViewModel(suggestClient(recorded = recorded), authService())

        viewModel.suggest(CARD_UID, "  Lisbon ", isGuest = false)

        val state = viewModel.uiState.first { it.submitted }
        assertFalse(state.submitting)
        assertEquals(1, recorded.size)
        assertTrue(recorded.first().contains("Lisbon"))
    }

    @Test
    fun guestSuggest_opensTheAuthPrompt_withoutSubmitting() = runTest(testDispatcher) {
        val recorded = mutableListOf<String>()
        val viewModel = SuggestionViewModel(suggestClient(recorded = recorded), authService())

        viewModel.suggest(CARD_UID, "Lisbon", isGuest = true)

        assertTrue(viewModel.uiState.value.authPrompt)
        assertFalse(viewModel.uiState.value.submitted)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun guestConversion_registersThenSubmits() = runTest(testDispatcher) {
        val recorded = mutableListOf<String>()
        val viewModel = SuggestionViewModel(suggestClient(recorded = recorded), authService(registerOk = true))
        viewModel.suggest(CARD_UID, "Lisbon", isGuest = true)

        viewModel.authenticateAndSuggest(register = true, email = "new@user.com", password = "password1")

        val state = viewModel.uiState.first { it.submitted }
        assertFalse(state.authPrompt)
        assertEquals(1, recorded.size)
    }

    @Test
    fun suggest_mapsRateLimitTo429Error() = runTest(testDispatcher) {
        val viewModel = SuggestionViewModel(
            suggestClient(status = HttpStatusCode.TooManyRequests),
            authService(),
        )

        viewModel.suggest(CARD_UID, "Lisbon", isGuest = false)

        val state = viewModel.uiState.first { it.error != null }
        assertEquals(ActionError.RateLimit, state.error)
        assertFalse(state.submitted)
    }

    @Test
    fun reset_clearsStateForTheNextCard() = runTest(testDispatcher) {
        val viewModel = SuggestionViewModel(suggestClient(), authService())
        viewModel.suggest(CARD_UID, "Lisbon", isGuest = false)
        viewModel.uiState.first { it.submitted }

        viewModel.reset()

        assertFalse(viewModel.uiState.value.submitted)
    }

    // --- Helpers ---

    private fun suggestClient(
        status: HttpStatusCode = HttpStatusCode.Created,
        recorded: MutableList<String> = mutableListOf(),
    ): FlashcardApiClient {
        val engine = MockEngine { request ->
            recorded += (request.body as? TextContent)?.text ?: ""
            respond("", status, jsonHeaders)
        }
        return FlashcardApiClient(createFlashcardHttpClient(engine), "http://localhost", { "token" })
    }

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

    private class FakeTokenStore : TokenStore {
        private val token = MutableStateFlow<String?>(null)
        override fun tokenFlow(): Flow<String?> = token
        override suspend fun currentToken(): String? = token.value
        override suspend fun currentRefreshToken(): String? = null
        override suspend fun setToken(token: String) { this.token.value = token }
        override suspend fun setTokens(accessToken: String, refreshToken: String) { token.value = accessToken }
        override suspend fun clearToken() { token.value = null }
    }

    private class FakeLocalDataStore : LocalDataStore {
        override suspend fun clearAll() = Unit
    }

    private companion object {
        const val CARD_UID = "card-1"
    }
}
