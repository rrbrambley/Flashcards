package com.rrbrambley.flashcards.di

import com.rrbrambley.flashcards.home.data.HomeRepositoryImpl
import com.rrbrambley.flashcards.practice.data.FlashcardDao
import com.rrbrambley.flashcards.practice.data.FlashcardRepositoryImpl
import com.rrbrambley.flashcards.practice.discussions.DiscussionRepository
import com.rrbrambley.flashcards.practice.discussions.DiscussionRepositoryImpl
import com.rrbrambley.flashcards.profile.ProfileRepository
import com.rrbrambley.flashcards.profile.ProfileRepositoryImpl
import com.rrbrambley.flashcards.practice.data.FlashcardsDatabase
import com.rrbrambley.flashcards.practice.data.PracticeAnswerDao
import com.rrbrambley.flashcards.practice.data.PracticeSessionDao
import com.rrbrambley.flashcards.practice.data.PracticeSessionRepositoryImpl
import com.rrbrambley.flashcards.practice.data.RoomLocalDataStore
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import com.rrbrambley.flashcards.shared.domain.HomeRepository
import com.rrbrambley.flashcards.shared.domain.LocalDataStore
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSessionSyncer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Constructs the shared (commonMain) repository implementations. They can't be `@Inject`-bound like
 * the Android-only repositories because the shared module has no Hilt/javax.inject dependency.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    fun provideLocalDataStore(database: FlashcardsDatabase): LocalDataStore = RoomLocalDataStore(database)

    @Provides
    fun provideAuthService(
        apiClient: FlashcardApiClient,
        tokenStore: TokenStore,
        localDataStore: LocalDataStore,
    ): AuthService = AuthService(apiClient, tokenStore, localDataStore)

    @Provides
    fun provideFlashcardRepository(
        apiClient: FlashcardApiClient,
        flashcardDao: FlashcardDao,
    ): FlashcardRepository = FlashcardRepositoryImpl(apiClient, flashcardDao)

    // Card discussions are online-only (not cached in Room), so this just wraps the shared client.
    @Provides
    fun provideDiscussionRepository(apiClient: FlashcardApiClient): DiscussionRepository =
        DiscussionRepositoryImpl(apiClient)

    // Profile + avatar catalog (FLA-166) — online-only, wraps the shared client.
    @Provides
    fun provideProfileRepository(apiClient: FlashcardApiClient): ProfileRepository =
        ProfileRepositoryImpl(apiClient)

    // One impl instance backs both the repository and the offline-session syncer (FLA-91), so the
    // sync loop and the UI share the same Room writes (and the single-flight mutex).
    @Provides
    @Singleton
    fun providePracticeSessionRepositoryImpl(
        apiClient: FlashcardApiClient,
        practiceSessionDao: PracticeSessionDao,
        flashcardDao: FlashcardDao,
        practiceAnswerDao: PracticeAnswerDao,
    ): PracticeSessionRepositoryImpl =
        PracticeSessionRepositoryImpl(apiClient, practiceSessionDao, flashcardDao, practiceAnswerDao)

    @Provides
    fun providePracticeSessionRepository(impl: PracticeSessionRepositoryImpl): PracticeSessionRepository = impl

    @Provides
    fun providePracticeSessionSyncer(impl: PracticeSessionRepositoryImpl): PracticeSessionSyncer = impl

    @Provides
    fun provideHomeRepository(
        apiClient: FlashcardApiClient,
        flashcardRepository: FlashcardRepository,
        practiceSessionRepository: PracticeSessionRepository,
        homeFeedStrings: HomeFeedStrings,
    ): HomeRepository = HomeRepositoryImpl(apiClient, flashcardRepository, practiceSessionRepository, homeFeedStrings)
}
