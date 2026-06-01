package com.rrbrambley.flashcards.backend.images

import com.rrbrambley.flashcards.backend.error.PayloadTooLargeException
import com.rrbrambley.flashcards.backend.error.ServiceUnavailableException
import com.rrbrambley.flashcards.backend.error.UnsupportedMediaTypeException
import com.rrbrambley.flashcards.backend.storage.Storage
import com.rrbrambley.flashcards.shared.api.ImageUploadResponse
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

private const val MAX_BYTES = 5 * 1024 * 1024 // 5 MB

// Content type -> file extension. Only formats that render on Android, iOS, and web.
private val ALLOWED_TYPES = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
    "image/gif" to "gif",
)

fun Route.imageRoutes() {
    post("/images") {
        val storage = Storage.service
            ?: throw ServiceUnavailableException("Image upload is not configured on this server")

        val multipart = call.receiveMultipart()
        var response: ImageUploadResponse? = null

        multipart.forEachPart { part ->
            if (response == null && part is PartData.FileItem) {
                val contentType = part.contentType?.let { "${it.contentType}/${it.contentSubtype}" }
                val extension = ALLOWED_TYPES[contentType]
                    ?: throw UnsupportedMediaTypeException("Unsupported image type. Allowed: ${ALLOWED_TYPES.keys}")

                // Read at most MAX_BYTES + 1 so an oversized file is rejected without buffering it fully.
                val bytes = part.provider().readRemaining((MAX_BYTES + 1).toLong()).readByteArray()
                if (bytes.size > MAX_BYTES) {
                    throw PayloadTooLargeException("Image exceeds the ${MAX_BYTES / (1024 * 1024)} MB limit")
                }
                response = ImageUploadResponse(storage.upload(bytes, contentType!!, extension))
            }
            part.dispose()
        }

        response?.let { call.respond(it) }
            ?: throw IllegalArgumentException("No image file provided")
    }
}
