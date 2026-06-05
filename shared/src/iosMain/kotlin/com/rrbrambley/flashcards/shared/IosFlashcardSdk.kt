package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.home.data.DefaultHomeFeedStrings
import com.rrbrambley.flashcards.practice.data.createFlashcardsDatabase
import com.rrbrambley.flashcards.practice.data.flashcardsDatabaseBuilder
import com.rrbrambley.flashcards.shared.api.TokenStore
import com.rrbrambley.flashcards.shared.api.createFlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.HomeFeedStrings
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS entry point: one call from Swift yields a fully configured [FlashcardSdk] over the Darwin
 * (NSURLSession) engine, with transparent token refresh and the offline-first Room database wired
 * in. Swift supplies a Keychain-backed [TokenStore]; [homeFeedStrings] defaults to English copy
 * until iOS adds localized strings.
 */
fun createIosFlashcardSdk(
    baseUrl: String,
    tokenStore: TokenStore,
    homeFeedStrings: HomeFeedStrings = DefaultHomeFeedStrings,
): FlashcardSdk {
    val apiClient = createFlashcardApiClient(Darwin.create(), baseUrl, tokenStore)
    val database = createFlashcardsDatabase(flashcardsDatabaseBuilder())
    return buildFlashcardSdk(apiClient, database, homeFeedStrings)
}
