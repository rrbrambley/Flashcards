package com.rrbrambley.flashcards.di

import android.content.Context
import com.rrbrambley.flashcards.BuildConfig
import com.rrbrambley.flashcards.practice.data.FlashcardDao
import com.rrbrambley.flashcards.practice.data.FlashcardsDatabase
import com.rrbrambley.flashcards.practice.data.PracticeAnswerDao
import com.rrbrambley.flashcards.practice.data.PracticeSessionDao
import com.rrbrambley.flashcards.practice.data.createFlashcardsDatabase
import com.rrbrambley.flashcards.practice.data.flashcardsDatabaseBuilder
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
    fun provideFlashcardsDatabase(@ApplicationContext context: Context): FlashcardsDatabase = createFlashcardsDatabase(
        // Debug builds keep the destructive fallback while iterating on the schema; release builds
        // must migrate (a missing migration should fail loudly, not wipe user data).
        flashcardsDatabaseBuilder(context, allowDestructiveMigration = BuildConfig.DEBUG),
    )

    @Provides
    fun provideFlashcardDao(database: FlashcardsDatabase): FlashcardDao = database.flashcardDao()

    @Provides
    fun providePracticeSessionDao(database: FlashcardsDatabase): PracticeSessionDao = database.practiceSessionDao()

    @Provides
    fun providePracticeAnswerDao(database: FlashcardsDatabase): PracticeAnswerDao = database.practiceAnswerDao()
}
