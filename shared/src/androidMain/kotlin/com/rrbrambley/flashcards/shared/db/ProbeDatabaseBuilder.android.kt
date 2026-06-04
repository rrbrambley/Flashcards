package com.rrbrambley.flashcards.shared.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/** Android [ProbeDatabase] builder, backed by a file in the app's database directory. */
fun probeDatabaseBuilder(context: Context): RoomDatabase.Builder<ProbeDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath("probe.db")
    return Room.databaseBuilder<ProbeDatabase>(appContext, dbFile.absolutePath)
}
