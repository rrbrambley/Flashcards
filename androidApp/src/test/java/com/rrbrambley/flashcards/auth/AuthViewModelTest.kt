package com.rrbrambley.flashcards.auth

import com.rrbrambley.flashcards.data.auth.TokenStore
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
    fun login_withBlankFields_showsValidationErrorAndDoesNotCallRepository() = runTest(testDispatcher) {
        val repository = FakeAuthRepository(FakeTokenStore())
        val viewModel = AuthViewModel(repository, FakeTokenStore())

        viewModel.onEmailChange("")
        viewModel.onPasswordChange("")
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Enter your email and password.", viewModel.formState.value.errorMessage)
        assertEquals(0, repository.calls)
    }

    @Test
    fun login_isSubmitting_guardsAgainstDoubleSubmit() = runTest(testDispatcher) {
        val repository = FakeAuthRepository(FakeTokenStore())
        val viewModel = AuthViewModel(repository, FakeTokenStore())
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("pw123456")

        viewModel.login() // sets isSubmitting = true, then launches
        viewModel.login() // ignored while the first is in flight
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.calls)
    }

    @Test
    fun login_errorOutcome_surfacesMessageAndClearsSubmitting() = runTest(testDispatcher) {
        val repository = FakeAuthRepository(FakeTokenStore(), outcome = AuthOutcome.Error("Invalid email or password."))
        val viewModel = AuthViewModel(repository, FakeTokenStore())
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
        val repository = FakeAuthRepository(tokenStore, tokenOnSuccess = "tok")
        val viewModel = AuthViewModel(repository, tokenStore)
        viewModel.onEmailChange("a@b.com")
        viewModel.onPasswordChange("pw123456")

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.formState.value.errorMessage)
        assertEquals("tok", tokenStore.currentToken())
    }

    @Test
    fun register_delegatesToRepository() = runTest(testDispatcher) {
        val tokenStore = FakeTokenStore()
        val repository = FakeAuthRepository(tokenStore, tokenOnSuccess = "reg")
        val viewModel = AuthViewModel(repository, tokenStore)
        viewModel.onEmailChange("new@b.com")
        viewModel.onPasswordChange("pw123456")

        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.calls)
        assertEquals("reg", tokenStore.currentToken())
    }

    @Test
    fun onGoogleError_setsMessageAndClearsSubmitting() {
        val viewModel = AuthViewModel(FakeAuthRepository(FakeTokenStore()), FakeTokenStore())
        viewModel.onGoogleError("Google sign-in failed.")
        assertEquals("Google sign-in failed.", viewModel.formState.value.errorMessage)
        assertEquals(false, viewModel.formState.value.isSubmitting)
    }

    @Test
    fun resetForm_clearsEmailPasswordAndError() {
        val viewModel = AuthViewModel(FakeAuthRepository(FakeTokenStore()), FakeTokenStore())
        viewModel.onEmailChange("a@b.com")
        viewModel.onGoogleError("oops")

        viewModel.resetForm()

        assertEquals(AuthFormState(), viewModel.formState.value)
    }

    @Test
    fun authState_reflectsTokenPresence() = runTest(testDispatcher) {
        val tokenStore = FakeTokenStore()
        val viewModel = AuthViewModel(FakeAuthRepository(tokenStore), tokenStore)
        backgroundScope.launch { viewModel.authState.collect {} } // keep the WhileSubscribed flow active
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AuthState.LoggedOut, viewModel.authState.value)

        tokenStore.setToken("tok")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthState.LoggedIn, viewModel.authState.value)
    }

    @Test
    fun logout_clearsTokenAndReturnsToLoggedOut() = runTest(testDispatcher) {
        val tokenStore = FakeTokenStore()
        tokenStore.setToken("tok")
        val viewModel = AuthViewModel(FakeAuthRepository(tokenStore), tokenStore)
        backgroundScope.launch { viewModel.authState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AuthState.LoggedIn, viewModel.authState.value)

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(tokenStore.currentToken())
        assertEquals(AuthState.LoggedOut, viewModel.authState.value)
    }

    // --- Fakes ---

    private class FakeAuthRepository(
        private val tokenStore: TokenStore,
        private val outcome: AuthOutcome = AuthOutcome.Success,
        private val tokenOnSuccess: String = "tok",
    ) : AuthRepository {
        var calls = 0
            private set

        override suspend fun register(email: String, password: String) = handle()
        override suspend fun login(email: String, password: String) = handle()
        override suspend fun signInWithGoogle(idToken: String) = handle()
        override suspend fun logout() {
            tokenStore.clearToken()
        }

        private suspend fun handle(): AuthOutcome {
            calls++
            if (outcome is AuthOutcome.Success) tokenStore.setToken(tokenOnSuccess)
            return outcome
        }
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
