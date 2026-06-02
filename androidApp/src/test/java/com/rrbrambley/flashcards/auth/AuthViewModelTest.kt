package com.rrbrambley.flashcards.auth

import com.rrbrambley.flashcards.data.auth.TokenStore
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun login_withBlankFields_showsValidationErrorAndDoesNotCallBackend() = runTest(testDispatcher) {
        val engine = okEngine()
        val viewModel = viewModel(engine)

        viewModel.onEmailChange("")
        viewModel.onPasswordChange("")
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Enter your email and password.", viewModel.formState.value.errorMessage)
        assertTrue(engine.requestHistory.isEmpty())
    }

    @Test
    fun login_isSubmitting_guardsAgainstDoubleSubmit() = runTest(testDispatcher) {
        val engine = okEngine("""{"token":"t","userId":1}""")
        val viewModel = viewModel(engine)
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("pw123456")

        viewModel.login() // launches; sets isSubmitting = true
        viewModel.login() // ignored while the first is in flight
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun login_errorOutcome_surfacesMessageAndClearsSubmitting() = runTest(testDispatcher) {
        val viewModel = viewModel(statusEngine(HttpStatusCode.Unauthorized))
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("wrong-pw")

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Invalid email or password.", viewModel.formState.value.errorMessage)
        assertEquals(false, viewModel.formState.value.isSubmitting)
    }

    @Test
    fun login_success_persistsTokenAndLeavesNoError() = runTest(testDispatcher) {
        val tokenStore = FakeTokenStore()
        val viewModel = viewModel(okEngine("""{"token":"tok","userId":1}"""), tokenStore)
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("pw123456")

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.formState.value.errorMessage)
        assertEquals("tok", tokenStore.currentToken())
    }

    @Test
    fun register_delegatesToBackend() = runTest(testDispatcher) {
        val tokenStore = FakeTokenStore()
        val viewModel = viewModel(okEngine("""{"token":"reg","userId":3}"""), tokenStore)
        viewModel.onEmailChange("new@b.com")
        viewModel.onPasswordChange("pw123456")

        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("reg", tokenStore.currentToken())
    }

    @Test
    fun onGoogleError_setsMessageAndClearsSubmitting() {
        val viewModel = viewModel(okEngine())
        viewModel.onGoogleError("Google sign-in failed.")
        assertEquals("Google sign-in failed.", viewModel.formState.value.errorMessage)
        assertEquals(false, viewModel.formState.value.isSubmitting)
    }

    @Test
    fun resetForm_clearsEmailPasswordAndError() {
        val viewModel = viewModel(okEngine())
        viewModel.onEmailChange("a@b.com")
        viewModel.onGoogleError("oops")

        viewModel.resetForm()

        assertEquals(AuthFormState(), viewModel.formState.value)
    }

    @Test
    fun authState_reflectsTokenPresence() = runTest(testDispatcher) {
        val tokenStore = FakeTokenStore()
        val viewModel = viewModel(okEngine(), tokenStore)
        backgroundScope.launch { viewModel.authState.collect {} } // keep the WhileSubscribed flow active
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AuthState.LoggedOut, viewModel.authState.value)

        tokenStore.setToken("tok")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthState.LoggedIn, viewModel.authState.value)
    }

    // --- Helpers ---

    private fun viewModel(engine: MockEngine, tokenStore: TokenStore = FakeTokenStore()): AuthViewModel {
        val apiClient = FlashcardApiClient(
            client = createFlashcardHttpClient(engine),
            baseUrl = "http://localhost",
            tokenProvider = { tokenStore.currentToken() },
        )
        return AuthViewModel(AuthRepository(apiClient, tokenStore), tokenStore)
    }

    private fun okEngine(body: String = "{}") = statusEngine(HttpStatusCode.OK, body)

    private fun statusEngine(status: HttpStatusCode, body: String = "{}") = MockEngine {
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private class FakeTokenStore : TokenStore {
        private val tokens = MutableStateFlow<String?>(null)
        override fun tokenFlow(): Flow<String?> = tokens
        override suspend fun currentToken(): String? = tokens.value
        override suspend fun setToken(token: String) {
            tokens.value = token
        }
        override suspend fun clearToken() {
            tokens.value = null
        }
    }
}
