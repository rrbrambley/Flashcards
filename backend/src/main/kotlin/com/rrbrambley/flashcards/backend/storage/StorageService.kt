package com.rrbrambley.flashcards.backend.storage

/** Stores image bytes and returns their public (CDN) URL. */
interface StorageService {
    suspend fun upload(bytes: ByteArray, contentType: String, extension: String): String
}

/**
 * Holds the configured storage service (set once at startup). Null when storage isn't
 * configured, in which case the upload endpoint returns 503. Mirrors GoogleTokenVerifier.
 */
object Storage {
    @Volatile
    var service: StorageService? = null
}
