package com.rrbrambley.flashcards.shared.api

import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Installs bearer auth with transparent token refresh: the access token is attached to every
 * request, and on a `401` (with the server's `WWW-Authenticate: Bearer` challenge) Ktor calls
 * `refreshTokens` to mint a new access token from the stored refresh token (posting to
 * `$baseUrl/auth/refresh`), then retries the original request — so callers never see the expiry.
 * Ktor coalesces concurrent refreshes into a single flight. The tokens are cleared — so the app
 * gates back to sign-in (the auth UI observes the cleared access token via [TokenStore.tokenFlow]) —
 * only when the refresh token is missing or genuinely *rejected* (401/invalid_grant). A transient
 * failure (5xx, timeout, connection blip) leaves the stored tokens intact so a later request can
 * refresh again; only the in-flight request fails (FLA-138).
 *
 * Lives in `shared` (commonMain) so Android and iOS install the exact same flow through the
 * client factory's `configure` hook — each platform supplies only its native [TokenStore].
 */
fun HttpClientConfig<*>.installTokenRefreshAuth(tokenStore: TokenStore, baseUrl: String) {
    val refreshUrl = "${baseUrl.trimEnd('/')}/auth/refresh"
    install(Auth) {
        bearer {
            loadTokens {
                val access = tokenStore.currentToken()
                val refresh = tokenStore.currentRefreshToken()
                if (access != null && refresh != null) BearerTokens(access, refresh) else null
            }
            refreshTokens {
                // No refresh token to recover with: clear any stale access token so the app
                // gates back to sign-in instead of silently 401-looping with no feedback.
                val refresh = tokenStore.currentRefreshToken() ?: run {
                    tokenStore.clearToken()
                    return@refreshTokens null
                }
                try {
                    val response: AuthResponse = client.post(refreshUrl) {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequest(refresh))
                    }.body()
                    tokenStore.setTokens(response.accessToken, response.refreshToken)
                    BearerTokens(response.accessToken, response.refreshToken)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiError.Unauthorized) {
                    // The refresh token was genuinely rejected (expired / revoked / reuse-detected):
                    // end the session so the app gates back to sign-in.
                    tokenStore.clearToken()
                    null
                } catch (e: ApiError.Validation) {
                    // 400 invalid_grant — also a definitive rejection of the refresh token.
                    tokenStore.clearToken()
                    null
                } catch (e: Exception) {
                    // Transient failure (5xx, timeout, connection blip): keep the tokens so a later
                    // request can refresh again — only the in-flight request fails (FLA-138).
                    null
                }
            }
            sendWithoutRequest { true }
        }
    }
}
