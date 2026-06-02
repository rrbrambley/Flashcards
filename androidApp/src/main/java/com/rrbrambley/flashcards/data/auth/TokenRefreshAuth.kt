package com.rrbrambley.flashcards.data.auth

import com.rrbrambley.flashcards.shared.api.AuthResponse
import com.rrbrambley.flashcards.shared.api.RefreshRequest
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Installs bearer auth with transparent token refresh: the access token is attached to every
 * request, and on a `401` Ktor calls `refreshTokens` to mint a new access token from the stored
 * refresh token (posting to [refreshUrl]), then retries the original request — so callers never
 * see the expiry. If the refresh token is missing or rejected, the tokens are cleared so the app
 * gates back to sign-in (AuthViewModel observes the cleared access token).
 *
 * Extracted from `NetworkModule` so the refresh-on-401 flow can be unit-tested with a MockEngine.
 */
fun HttpClientConfig<*>.installTokenRefreshAuth(tokenStore: TokenStore, refreshUrl: String) {
    install(Auth) {
        bearer {
            loadTokens {
                val access = tokenStore.currentToken()
                val refresh = tokenStore.currentRefreshToken()
                if (access != null && refresh != null) BearerTokens(access, refresh) else null
            }
            refreshTokens {
                val refresh = tokenStore.currentRefreshToken() ?: return@refreshTokens null
                try {
                    val response: AuthResponse = client.post(refreshUrl) {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequest(refresh))
                    }.body()
                    tokenStore.setTokens(response.accessToken, response.refreshToken)
                    BearerTokens(response.accessToken, response.refreshToken)
                } catch (e: Exception) {
                    tokenStore.clearToken()
                    null
                }
            }
            sendWithoutRequest { true }
        }
    }
}
