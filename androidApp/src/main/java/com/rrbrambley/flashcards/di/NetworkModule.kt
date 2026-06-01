package com.rrbrambley.flashcards.di

import com.rrbrambley.flashcards.BuildConfig
import com.rrbrambley.flashcards.data.auth.TokenStore
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = createFlashcardHttpClient(OkHttp.create())

    @Provides
    @Singleton
    fun provideFlashcardApiClient(
        httpClient: HttpClient,
        tokenStore: TokenStore,
    ): FlashcardApiClient = FlashcardApiClient(
        client = httpClient,
        baseUrl = BuildConfig.BACKEND_BASE_URL,
        tokenProvider = { tokenStore.currentToken() },
    )
}
