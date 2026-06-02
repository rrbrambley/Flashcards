package com.rrbrambley.flashcards.backend.plugins

import com.rrbrambley.flashcards.backend.error.PayloadTooLargeException
import com.rrbrambley.flashcards.backend.validation.Validation
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call

/**
 * Rejects oversized request bodies up front, based on Content-Length, so a huge payload is
 * refused before it's buffered/parsed. Mapped to 413 by StatusPages.
 */
fun Application.configureRequestLimits() {
    intercept(ApplicationCallPipeline.Plugins) {
        val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (length != null && length > Validation.MAX_REQUEST_BODY_BYTES) {
            throw PayloadTooLargeException("Request body exceeds the ${Validation.MAX_REQUEST_BODY_BYTES}-byte limit")
        }
    }
}
