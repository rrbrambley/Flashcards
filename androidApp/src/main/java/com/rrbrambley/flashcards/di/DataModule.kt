package com.rrbrambley.flashcards.di

import com.rrbrambley.flashcards.auth.AuthRepository
import com.rrbrambley.flashcards.auth.DefaultAuthRepository
import com.rrbrambley.flashcards.core.AndroidStringProvider
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.data.auth.DataStoreTokenStore
import com.rrbrambley.flashcards.data.auth.TokenStore
import com.rrbrambley.flashcards.data.image.AndroidImageUploader
import com.rrbrambley.flashcards.data.image.ImageUploader
import com.rrbrambley.flashcards.home.data.AndroidHomeFeedStrings
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // The shared repository impls are constructed in RepositoryModule (@Provides) — they can't be
    // @Inject-bound since the shared module has no Hilt dependency.

    @Binds
    abstract fun bindImageUploader(uploader: AndroidImageUploader): ImageUploader

    @Binds
    abstract fun bindTokenStore(store: DataStoreTokenStore): TokenStore

    @Binds
    abstract fun bindAuthRepository(repository: DefaultAuthRepository): AuthRepository

    @Binds
    abstract fun bindStringProvider(provider: AndroidStringProvider): StringProvider

    @Binds
    abstract fun bindHomeFeedStrings(strings: AndroidHomeFeedStrings): HomeFeedStrings
}
