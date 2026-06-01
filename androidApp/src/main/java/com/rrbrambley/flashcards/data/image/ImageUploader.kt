package com.rrbrambley.flashcards.data.image

import android.net.Uri

/**
 * Reads a picked image and uploads it, returning its public (CDN) URL to store as a
 * flashcard's imageUrl. Abstracting this off the ViewModels keeps the Android I/O
 * (ContentResolver) and HTTP client out of them, so they stay unit-testable on the JVM.
 */
fun interface ImageUploader {
    suspend fun upload(uri: Uri): String
}
