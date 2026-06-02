package com.rrbrambley.flashcards.data.image

import android.content.Context
import android.net.Uri
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Reads the bytes from a content [Uri] via [Context.getContentResolver] and uploads them. */
class AndroidImageUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: FlashcardApiClient,
) : ImageUploader {
    override suspend fun upload(uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        require(mime in ALLOWED_TYPES) { "Unsupported image type" }
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("Could not read the selected image")
        require(bytes.size <= MAX_IMAGE_BYTES) { "Image is too large" }
        val filename = "image.${mime.substringAfterLast('/')}"
        return apiClient.uploadImage(bytes, filename, mime).url
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
        val ALLOWED_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }
}
