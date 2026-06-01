package com.rrbrambley.flashcards.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Typed client for the Flashcards backend, shared across platforms.
 *
 * @param client a configured [HttpClient] (see [createFlashcardHttpClient]).
 * @param baseUrl e.g. "http://10.0.2.2:8080" (no trailing slash required).
 * @param tokenProvider supplies the current bearer token, or null when unauthenticated.
 */
class FlashcardApiClient(
    private val client: HttpClient,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {
    // --- Auth ---
    suspend fun register(request: RegisterRequest): AuthResponse =
        client.post(url("/auth/register")) { jsonBody(request) }.body()

    suspend fun login(request: LoginRequest): AuthResponse =
        client.post(url("/auth/login")) { jsonBody(request) }.body()

    // --- Decks ---
    suspend fun getDecks(): List<FlashcardDeckDto> =
        client.get(url("/decks")) { auth() }.body()

    suspend fun getDeck(deckId: Long): FlashcardDeckDto =
        client.get(url("/decks/$deckId")) { auth() }.body()

    suspend fun createDeck(request: CreateDeckRequest): FlashcardDeckDto =
        client.post(url("/decks")) { jsonBody(request) }.body()

    suspend fun updateDeck(deckId: Long, request: CreateDeckRequest): FlashcardDeckDto =
        client.put(url("/decks/$deckId")) { jsonBody(request) }.body()

    // --- Sessions ---
    suspend fun getSessions(activeOnly: Boolean = true): List<PracticeSessionDto> =
        client.get(url("/sessions")) {
            auth()
            parameter("active", activeOnly)
        }.body()

    suspend fun getSession(sessionId: Long): PracticeSessionDto =
        client.get(url("/sessions/$sessionId")) { auth() }.body()

    suspend fun createSession(deckId: Long): PracticeSessionDto =
        client.post(url("/sessions")) { jsonBody(CreateSessionRequest(deckId)) }.body()

    suspend fun updateProgress(sessionId: Long, request: UpdateProgressRequest): PracticeSessionDto =
        client.patch(url("/sessions/$sessionId")) { jsonBody(request) }.body()

    suspend fun completeSession(sessionId: Long): PracticeSessionDto =
        client.post(url("/sessions/$sessionId/complete")) { auth() }.body()

    // --- Home ---
    suspend fun getHome(): List<HomeDataDto> =
        client.get(url("/home")) { auth() }.body()

    private fun url(path: String): String = "${baseUrl.trimEnd('/')}$path"

    private suspend fun HttpRequestBuilder.auth() {
        tokenProvider()?.let { bearerAuth(it) }
    }

    private suspend fun HttpRequestBuilder.jsonBody(body: Any) {
        auth()
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
