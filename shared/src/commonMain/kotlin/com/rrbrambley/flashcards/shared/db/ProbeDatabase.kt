package com.rrbrambley.flashcards.shared.db

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Minimal Room-KMP database that proves the multiplatform persistence setup (build config +
 * generated DB impl + bundled SQLite driver) works on android/jvm/iOS. The real entities, DAOs,
 * and migrations get lifted into this module in FLA-28, which replaces this probe.
 */
@Entity(tableName = "probe")
data class ProbeEntity(@PrimaryKey val id: Long, val value: String)

@Dao
interface ProbeDao {
    @Upsert
    suspend fun upsert(entity: ProbeEntity)

    @Query("SELECT value FROM probe WHERE id = :id")
    suspend fun value(id: Long): String?
}

@Database(entities = [ProbeEntity::class], version = 1, exportSchema = false)
@ConstructedBy(ProbeDatabaseConstructor::class)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): ProbeDao
}

// KSP generates the actual implementation of this constructor for each platform.
@Suppress("KotlinNoActualForExpect")
expect object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
    override fun initialize(): ProbeDatabase
}

/**
 * Finalizes a platform-supplied [RoomDatabase.Builder] with the shared driver + query context.
 * Uses [Dispatchers.Default] (off the main thread) since [Dispatchers.IO] isn't available on native.
 */
fun createProbeDatabase(builder: RoomDatabase.Builder<ProbeDatabase>): ProbeDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
