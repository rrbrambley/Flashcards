package com.rrbrambley.flashcards.practice.data

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS [FlashcardsDatabase] builder, backed by `flashcards.db` in the app's Documents directory. */
fun flashcardsDatabaseBuilder(): RoomDatabase.Builder<FlashcardsDatabase> {
    val dbFilePath = "${documentDirectory()}/flashcards.db"
    return Room.databaseBuilder<FlashcardsDatabase>(name = dbFilePath)
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
