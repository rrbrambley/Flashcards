package com.rrbrambley.flashcards.auth

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Feature-flag keys mirrored from the backend catalog (`flags/FeatureFlags.kt`). */
object FeatureFlags {
    const val STREAK_CALENDAR = "streak_calendar"
}

/**
 * Holds the signed-in user's resolved feature flags (from `GET /auth/me`, FLA-174) so the UI can
 * hide/reveal features. An interface so ViewModels can be unit-tested with a hand-written fake.
 */
interface FeatureFlagRepository {
    /** The caller's resolved flags (key → enabled); empty for guests or when the fetch fails. */
    suspend fun flags(): Map<String, Boolean>

    /** Whether [key] is enabled for the caller (false when unknown/absent). */
    suspend fun isEnabled(key: String): Boolean = flags()[key] == true
}

/**
 * Fetches `/auth/me` lazily and caches the flag map, keyed by the current access token — mirrors
 * [DefaultPermissionsRepository]: a token change (login, user switch) refetches, a missing token
 * (logged out) reports no flags, and only successful fetches are cached (a transient failure is
 * retried rather than stuck empty). So an admin's flag toggle takes effect on the next token
 * rotation / cache miss.
 */
@Singleton
class DefaultFeatureFlagRepository @Inject constructor(
    private val apiClient: FlashcardApiClient,
    private val tokenStore: TokenStore,
) : FeatureFlagRepository {
    private val mutex = Mutex()
    private var cachedForToken: String? = null
    private var cached: Map<String, Boolean> = emptyMap()

    override suspend fun flags(): Map<String, Boolean> = mutex.withLock {
        val token = tokenStore.currentToken()
        if (token == null) {
            cachedForToken = null
            cached = emptyMap()
            return emptyMap()
        }
        if (token == cachedForToken) return cached
        val fetched = runCatching { apiClient.getMe().flags }.getOrNull()
        if (fetched != null) {
            cached = fetched
            cachedForToken = token
        }
        fetched ?: emptyMap()
    }
}
