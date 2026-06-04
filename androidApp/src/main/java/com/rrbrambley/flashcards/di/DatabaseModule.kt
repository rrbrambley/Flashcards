package com.rrbrambley.flashcards.di

import android.content.Context
import androidx.room.Room
import com.rrbrambley.flashcards.BuildConfig
import com.rrbrambley.flashcards.practice.data.ALL_MIGRATIONS
import com.rrbrambley.flashcards.practice.data.FlashcardDao
import com.rrbrambley.flashcards.practice.data.FlashcardsDatabase
import com.rrbrambley.flashcards.practice.data.PracticeSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Provides
    @Singleton
    fun provideFlashcardsDatabase(
        @ApplicationContext context: Context,
    ): FlashcardsDatabase = Room.databaseBuilder(
        context,
        FlashcardsDatabase::class.java,
        "flashcards.db",
    )
        .addMigrations(*ALL_MIGRATIONS)
        // Release builds must migrate (a missing migration should fail loudly, not wipe user data);
        // debug builds keep the destructive fallback as a convenience while iterating on the schema.
        .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration(true) }
        .build()

    @Provides
    fun provideFlashcardDao(database: FlashcardsDatabase): FlashcardDao = database.flashcardDao()

    @Provides
    fun providePracticeSessionDao(database: FlashcardsDatabase): PracticeSessionDao = database.practiceSessionDao()
}
