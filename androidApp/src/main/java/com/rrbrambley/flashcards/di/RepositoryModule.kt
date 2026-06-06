package com.rrbrambley.flashcards.di

import com.rrbrambley.flashcards.home.data.HomeRepositoryImpl
import com.rrbrambley.flashcards.practice.data.FlashcardDao
import com.rrbrambley.flashcards.practice.data.FlashcardRepositoryImpl
import com.rrbrambley.flashcards.practice.data.PracticeSessionDao
import com.rrbrambley.flashcards.practice.data.PracticeSessionRepositoryImpl
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Constructs the shared (commonMain) repository implementations. They can't be `@Inject`-bound like
 * the Android-only repositories because the shared module has no Hilt/javax.inject dependency.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    fun provideAuthService(
        apiClient: FlashcardApiClient,
        tokenStore: TokenStore,
    ): AuthService = AuthService(apiClient, tokenStore)

    @Provides
    fun provideFlashcardRepository(
        apiClient: FlashcardApiClient,
        flashcardDao: FlashcardDao,
    ): FlashcardRepository = FlashcardRepositoryImpl(apiClient, flashcardDao)

    @Provides
    fun providePracticeSessionRepository(
        apiClient: FlashcardApiClient,
        practiceSessionDao: PracticeSessionDao,
        flashcardDao: FlashcardDao,
    ): PracticeSessionRepository = PracticeSessionRepositoryImpl(apiClient, practiceSessionDao, flashcardDao)

    @Provides
    fun provideHomeRepository(
        apiClient: FlashcardApiClient,
        practiceSessionRepository: PracticeSessionRepository,
        homeFeedStrings: HomeFeedStrings,
    ): HomeRepository = HomeRepositoryImpl(apiClient, practiceSessionRepository, homeFeedStrings)
}
