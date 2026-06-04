package com.rrbrambley.flashcards.di

import com.rrbrambley.flashcards.auth.AuthRepository
import com.rrbrambley.flashcards.auth.DefaultAuthRepository
import com.rrbrambley.flashcards.core.AndroidStringProvider
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.data.auth.DataStoreTokenStore
import com.rrbrambley.flashcards.data.auth.TokenStore
import com.rrbrambley.flashcards.data.image.AndroidImageUploader
import com.rrbrambley.flashcards.data.image.ImageUploader
import com.rrbrambley.flashcards.home.data.HomeRepositoryImpl
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.practice.data.FlashcardRepositoryImpl
import com.rrbrambley.flashcards.practice.data.PracticeSessionRepositoryImpl
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
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

    @Binds
    abstract fun bindImageUploader(uploader: AndroidImageUploader): ImageUploader

    @Binds
    abstract fun bindTokenStore(store: DataStoreTokenStore): TokenStore

    @Binds
    abstract fun bindAuthRepository(repository: DefaultAuthRepository): AuthRepository

    @Binds
    abstract fun bindStringProvider(provider: AndroidStringProvider): StringProvider
}
