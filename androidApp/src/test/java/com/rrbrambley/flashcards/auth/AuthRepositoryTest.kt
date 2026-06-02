package com.rrbrambley.flashcards.auth

import com.rrbrambley.flashcards.data.auth.TokenStore
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AuthRepositoryTest {

    @Test
    fun register_success_persistsTokenAndReturnsSuccess() = runTest {
        val tokenStore = FakeTokenStore()
        val repository = repository(
            tokenStore,
            jsonEngine(HttpStatusCode.OK, """{"accessToken":"tok-123","refreshToken":"ref-123","userId":1}"""),
        )

        val outcome = repository.register("a@b.com", "pw123456")

        assertEquals(AuthOutcome.Success, outcome)
        assertEquals("tok-123", tokenStore.currentToken())
        assertEquals("ref-123", tokenStore.currentRefreshToken())
    }

    @Test
    fun register_conflict_returnsEmailExistsMessage() = runTest {
        val outcome = repository(FakeTokenStore(), jsonEngine(HttpStatusCode.Conflict))
            .register("a@b.com", "pw123456")
        assertEquals(AuthOutcome.Error("An account with that email already exists."), outcome)
    }

    @Test
    fun register_badRequest_returnsInvalidInputMessage() = runTest {
        val outcome = repository(FakeTokenStore(), jsonEngine(HttpStatusCode.BadRequest))
            .register("bad", "x")
        assertEquals(AuthOutcome.Error("Enter a valid email and a password."), outcome)
    }

    @Test
    fun register_doesNotPersistTokenOnFailure() = runTest {
        val tokenStore = FakeTokenStore()
        repository(tokenStore, jsonEngine(HttpStatusCode.Conflict)).register("a@b.com", "pw")
        assertNull(tokenStore.currentToken())
    }

    @Test
    fun login_success_persistsToken() = runTest {
        val tokenStore = FakeTokenStore()
        val outcome = repository(
            tokenStore,
            jsonEngine(HttpStatusCode.OK, """{"accessToken":"abc","refreshToken":"ref","userId":2}"""),
        ).login("a@b.com", "pw123456")
        assertEquals(AuthOutcome.Success, outcome)
        assertEquals("abc", tokenStore.currentToken())
        assertEquals("ref", tokenStore.currentRefreshToken())
    }

    @Test
    fun login_unauthorized_returnsInvalidCredentialsMessage() = runTest {
        val outcome = repository(FakeTokenStore(), jsonEngine(HttpStatusCode.Unauthorized))
            .login("a@b.com", "wrong")
        assertEquals(AuthOutcome.Error("Invalid email or password."), outcome)
    }

    @Test
    fun googleSignIn_serviceUnavailable_returnsNotAvailableMessage() = runTest {
        val outcome = repository(FakeTokenStore(), jsonEngine(HttpStatusCode.ServiceUnavailable))
            .signInWithGoogle("id-token")
        assertEquals(AuthOutcome.Error("Google sign-in isn't available right now."), outcome)
    }

    @Test
    fun googleSignIn_unauthorized_returnsFailedMessage() = runTest {
        val outcome = repository(FakeTokenStore(), jsonEngine(HttpStatusCode.Unauthorized))
            .signInWithGoogle("id-token")
        assertEquals(AuthOutcome.Error("Google sign-in failed. Please try again."), outcome)
    }

    @Test
    fun unexpectedStatus_fallsBackToGenericError() = runTest {
        val outcome = repository(FakeTokenStore(), jsonEngine(HttpStatusCode.InternalServerError))
            .login("a@b.com", "pw")
        assertEquals(AuthOutcome.Error(GENERIC_ERROR), outcome)
    }

    @Test
    fun networkFailure_fallsBackToGenericError() = runTest {
        val offline = MockEngine { throw IOException("offline") }
        val outcome = repository(FakeTokenStore(), offline).login("a@b.com", "pw")
        assertEquals(AuthOutcome.Error(GENERIC_ERROR), outcome)
    }

    @Test
    fun logout_revokesServerSideAndClearsTokens() = runTest {
        val tokenStore = FakeTokenStore()
        tokenStore.setTokens("tok", "ref")
        val requests = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            bodies += (request.body as? io.ktor.http.content.TextContent)?.text.orEmpty()
            respond("", HttpStatusCode.NoContent)
        }

        repository(tokenStore, engine).logout()

        assertNull(tokenStore.currentToken())
        assertNull(tokenStore.currentRefreshToken())
        assertTrue(requests.contains("POST /auth/logout"))
        // The refresh token is the one revoked server-side.
        assertTrue(bodies.any { it.contains("ref") })
    }

    @Test
    fun logout_clearsTokensEvenWhenRevokeFails() = runTest {
        val tokenStore = FakeTokenStore()
        tokenStore.setTokens("tok", "ref")
        val offline = MockEngine { throw IOException("offline") }

        repository(tokenStore, offline).logout()

        assertNull(tokenStore.currentToken())
        assertNull(tokenStore.currentRefreshToken())
    }

    // --- Helpers ---

    private fun repository(tokenStore: TokenStore, engine: MockEngine): AuthRepository =
        DefaultAuthRepository(
            apiClient = FlashcardApiClient(
                client = createFlashcardHttpClient(engine),
                baseUrl = "http://localhost",
                tokenProvider = { tokenStore.currentToken() },
            ),
            tokenStore = tokenStore,
        )

    private fun jsonEngine(status: HttpStatusCode, body: String = "{}") = MockEngine {
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private class FakeTokenStore : TokenStore {
        private val tokens = MutableStateFlow<String?>(null)
        private var refreshToken: String? = null
        override fun tokenFlow(): Flow<String?> = tokens
        override suspend fun currentToken(): String? = tokens.value
        override suspend fun currentRefreshToken(): String? = refreshToken
        override suspend fun setToken(token: String) {
            tokens.value = token
        }
        override suspend fun setTokens(accessToken: String, refreshToken: String) {
            tokens.value = accessToken
            this.refreshToken = refreshToken
        }
        override suspend fun clearToken() {
            tokens.value = null
            refreshToken = null
        }
    }

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Check your connection and try again."
    }
}
