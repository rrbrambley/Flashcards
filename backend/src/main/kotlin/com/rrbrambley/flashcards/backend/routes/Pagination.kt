package com.rrbrambley.flashcards.backend.routes

import io.ktor.server.application.ApplicationCall
import java.util.Base64

/** Page size used when the client doesn't specify a `limit`. */
const val DEFAULT_PAGE_SIZE = 20

/** Hard upper bound on `limit`; larger requests are clamped down to this. */
const val MAX_PAGE_SIZE = 100

/**
 * The requested page size: the `limit` query parameter clamped to `1..`[MAX_PAGE_SIZE], defaulting
 * to [DEFAULT_PAGE_SIZE] when absent. A non-numeric `limit` is a 400 (via [IllegalArgumentException]).
 */
fun ApplicationCall.pageLimit(): Int {
    val raw = request.queryParameters["limit"] ?: return DEFAULT_PAGE_SIZE
    val parsed = raw.toIntOrNull() ?: throw IllegalArgumentException("Invalid 'limit' query parameter")
    return parsed.coerceIn(1, MAX_PAGE_SIZE)
}

/** The raw opaque `cursor` query parameter, or null when absent. */
fun ApplicationCall.pageCursor(): String? = request.queryParameters["cursor"]

/**
 * Opaque pagination cursors. The encoded form is URL-safe base64 so clients treat it as a blob;
 * each list endpoint decides what ordering key it packs inside (see the repositories).
 */
object Cursor {
    fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.encodeToByteArray())

    fun decode(token: String): String = try {
        Base64.getUrlDecoder().decode(token).decodeToString()
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid pagination cursor")
    }
}
