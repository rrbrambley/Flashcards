package com.rrbrambley.flashcards.shared.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS [ProbeDatabase] builder, backed by a file in the app's Documents directory. */
fun probeDatabaseBuilder(): RoomDatabase.Builder<ProbeDatabase> {
    val dbFilePath = "${documentDirectory()}/probe.db"
    return Room.databaseBuilder<ProbeDatabase>(name = dbFilePath)
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
