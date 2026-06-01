package com.rrbrambley.flashcards.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds an [HttpClient] with the JSON content negotiation the API expects.
 * Each platform passes its own engine (OkHttp on Android/JVM, Darwin on iOS),
 * keeping the configuration shared while the transport stays native.
 */
fun createFlashcardHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        // Throw ClientRequestException / ServerResponseException on non-2xx responses.
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }
    }
