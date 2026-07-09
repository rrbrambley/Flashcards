package com.rrbrambley.flashcards.di

import com.rrbrambley.flashcards.BuildConfig
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import com.rrbrambley.flashcards.shared.api.installTokenRefreshAuth
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
    fun provideHttpClient(tokenStore: TokenStore): HttpClient = createFlashcardHttpClient(OkHttp.create()) {
        installTokenRefreshAuth(tokenStore, BuildConfig.BACKEND_BASE_URL)
    }

    @Provides
    @Singleton
    fun provideFlashcardApiClient(httpClient: HttpClient): FlashcardApiClient = FlashcardApiClient(
        client = httpClient,
        baseUrl = BuildConfig.BACKEND_BASE_URL,
        // The Auth plugin (above) owns the bearer header, so the client adds none itself.
        tokenProvider = { null },
    )
}
