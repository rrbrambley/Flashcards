package com.rrbrambley.flashcards.shared.api

/**
 * Typed error surfaced by [FlashcardApiClient] so callers can branch on the failure instead of
 * inspecting raw HTTP status codes. Non-2xx responses map to the subtypes below (carrying the
 * backend's error message when present). Transport failures (no response, timeout, connection
 * refused) propagate as the underlying exception and are typically handled generically.
 */
sealed class ApiError(message: String?, cause: Throwable? = null) : Exception(message, cause) {
    /** 400 — invalid input. */
    class Validation(message: String?) : ApiError(message)

    /** 401 — missing/expired/invalid credentials. */
    class Unauthorized(message: String?) : ApiError(message)

    /** 404 — resource not found (or not visible to this user). */
    class NotFound(message: String?) : ApiError(message)

    /** 409 — conflict (e.g. email already registered). */
    class Conflict(message: String?) : ApiError(message)

    /** 413 — payload too large. */
    class PayloadTooLarge(message: String?) : ApiError(message)

    /** 415 — unsupported media type. */
    class UnsupportedMediaType(message: String?) : ApiError(message)

    /** 503 — an optional feature isn't configured/available. */
    class ServiceUnavailable(message: String?) : ApiError(message)

    /** Any other 4xx. */
    class Client(val status: Int, message: String?) : ApiError(message)

    /** Any 5xx (after retries). */
    class Server(val status: Int, message: String?) : ApiError(message)
}
