package com.rrbrambley.flashcards.shared.api

import io.ktor.client.engine.HttpClientEngine

/**
 * Builds a [FlashcardApiClient] over [engine] with transparent token refresh wired in. The bearer
 * header is owned by the `Auth` plugin ([installTokenRefreshAuth]) — which loads the access token
 * from [tokenStore] and refreshes it on `401` — so the client itself attaches none (its
 * `tokenProvider` returns null).
 *
 * Shared so every platform composes the client identically; each passes its own engine (OkHttp on
 * Android, Darwin on iOS via [createIosFlashcardSdk]).
 */
fun createFlashcardApiClient(engine: HttpClientEngine, baseUrl: String, tokenStore: TokenStore): FlashcardApiClient {
    val httpClient = createFlashcardHttpClient(engine) {
        installTokenRefreshAuth(tokenStore, baseUrl)
    }
    return FlashcardApiClient(httpClient, baseUrl, tokenProvider = { null })
}
