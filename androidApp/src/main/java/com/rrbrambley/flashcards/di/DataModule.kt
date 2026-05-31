package com.rrbrambley.flashcards.di

import com.rrbrambley.flashcards.home.data.HomeRepositoryImpl
import com.rrbrambley.flashcards.home.domain.HomeRepository
import com.rrbrambley.flashcards.practice.data.FlashcardRepositoryImpl
import com.rrbrambley.flashcards.practice.data.PracticeSessionRepositoryImpl
import com.rrbrambley.flashcards.domain.FlashcardRepository
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindFlashcardRepository(repository: FlashcardRepositoryImpl): FlashcardRepository

    @Binds
    abstract fun bindPracticeSessionRepository(repository: PracticeSessionRepositoryImpl): PracticeSessionRepository

    @Binds
    abstract fun bindHomeRepository(repository: HomeRepositoryImpl): HomeRepository
}
