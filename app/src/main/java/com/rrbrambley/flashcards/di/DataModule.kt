package com.rrbrambley.flashcards.di

import com.rrbrambley.flashcards.data.FlashcardRepositoryImpl
import com.rrbrambley.flashcards.domain.FlashcardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindRepository(repository: FlashcardRepositoryImpl): FlashcardRepository
}
