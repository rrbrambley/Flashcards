package com.rrbrambley.flashcards.auth

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultFeatureFlagRepositoryTest {

    private val jsonHeaders = headersOf("Content-Type", "application/json")

    // MockEngine serves whatever `meJson` currently holds, so a test can change the "server's"
    // flags between calls to exercise caching vs. re-resolution.
    private var meJson = ""

    private fun apiClient(tokenStore: TokenStore): FlashcardApiClient {
        val engine = MockEngine { respond(meJson, HttpStatusCode.OK, jsonHeaders) }
        return FlashcardApiClient(createFlashcardHttpClient(engine), baseUrl = "http://localhost") {
            tokenStore.currentToken()
        }
    }

    private fun me(flags: String) = """{"userId":1,"email":"a@b.com","flags":{$flags}}"""

    @Test
    fun reportsNoFlags_whenLoggedOut() = runTest {
        val tokenStore = FakeTokenStore()
        meJson = me(""""streak_calendar":true""")
        val repo = DefaultFeatureFlagRepository(apiClient(tokenStore), tokenStore)

        // No token → no fetch, no flags.
        assertFalse(repo.isEnabled("streak_calendar"))
    }

    @Test
    fun reflectsDeliveredFlags_andDefaultsFalseForUnknownKeys() = runTest {
        val tokenStore = FakeTokenStore().apply { setToken("tok-a") }
        meJson = me(""""streak_calendar":true""")
        val repo = DefaultFeatureFlagRepository(apiClient(tokenStore), tokenStore)

        assertTrue(repo.isEnabled("streak_calendar"))
        assertFalse(repo.isEnabled("not_a_flag")) // absent → false
    }

    @Test
    fun cachesPerToken_andReResolvesOnTokenChange() = runTest {
        val tokenStore = FakeTokenStore().apply { setToken("tok-a") }
        meJson = me(""""streak_calendar":true""")
        val repo = DefaultFeatureFlagRepository(apiClient(tokenStore), tokenStore)

        assertTrue(repo.isEnabled("streak_calendar"))

        // The server's value flips, but the token is unchanged → the cached value is still served.
        meJson = me(""""streak_calendar":false""")
        assertTrue(repo.isEnabled("streak_calendar"))

        // A new token (login / user switch) forces a re-fetch → the new value is picked up.
        tokenStore.setToken("tok-b")
        assertFalse(repo.isEnabled("streak_calendar"))
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
}
