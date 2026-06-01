package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

/** Response from POST /images: the public (CDN) URL of the uploaded image. */
@Serializable
data class ImageUploadResponse(
    val url: String,
)
