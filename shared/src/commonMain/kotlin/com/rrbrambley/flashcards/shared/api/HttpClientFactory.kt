package com.rrbrambley.flashcards.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds an [HttpClient] with the JSON content negotiation the API expects, request timeouts,
 * retry-with-backoff for transient failures, and typed [ApiError] mapping.
 * Each platform passes its own engine (OkHttp on Android/JVM, Darwin on iOS),
 * keeping the configuration shared while the transport stays native.
 *
 * [configure] lets a platform add extra plugins (e.g. Android installs bearer Auth for
 * transparent token refresh) without duplicating the shared setup.
 */
fun createFlashcardHttpClient(engine: HttpClientEngine, configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient =
    HttpClient(engine) {
        // Non-2xx throws (mapped to ApiError by the validator below).
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            // Fail fast when there's no route to the server (offline / server down) instead of
            // waiting out a long connect timeout on every attempt.
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 30_000
        }
        // Retry only failures a retry can plausibly fix: transient 5xx, and read/request timeouts
        // (server reachable but slow). Connection failures — refused, no route, connect timeout —
        // are NOT retried, so an offline request errors after one quick attempt instead of burning
        // ~connect-timeout × retries (it used to hang up to ~45s). 4xx are never retried.
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            retryOnExceptionIf(maxRetries = 2) { _, cause ->
                cause is HttpRequestTimeoutException || cause is SocketTimeoutException
            }
            exponentialDelay()
        }
        // Surface non-2xx responses as a typed ApiError instead of a raw Ktor ResponseException.
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                if (cause is ResponseException) throw cause.toApiError()
            }
        }
        configure()
    }

private suspend fun ResponseException.toApiError(): ApiError {
    val status = response.status.value
    val message = runCatching { response.body<ErrorResponse>().message }.getOrNull()
    return when (status) {
        400 -> ApiError.Validation(message)
        401 -> ApiError.Unauthorized(message)
        404 -> ApiError.NotFound(message)
        409 -> ApiError.Conflict(message)
        413 -> ApiError.PayloadTooLarge(message)
        415 -> ApiError.UnsupportedMediaType(message)
        503 -> ApiError.ServiceUnavailable(message)
        in 500..599 -> ApiError.Server(status, message)
        else -> ApiError.Client(status, message)
    }
}
