package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Swift-facing front-of-card image upload. Swift hands the picked image as [NSData]; we copy it
 * into a Kotlin [ByteArray] and POST it via [FlashcardApiClient.uploadImage], returning the CDN URL
 * to store on the card.
 *
 * Annotated [Throws] so a failed upload (e.g. [com.rrbrambley.flashcards.shared.api.ApiError], or a
 * 503 when S3 isn't configured) bridges to a *catchable* Swift error instead of crashing the app —
 * the caller maps it to a parity error message. (Doing this across the whole API surface is FLA-57.)
 */
@OptIn(ExperimentalForeignApi::class)
@Throws(Exception::class)
suspend fun FlashcardApiClient.uploadImageData(data: NSData, filename: String, contentType: String): String =
    uploadImage(data.toByteArray(), filename, contentType).url

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
