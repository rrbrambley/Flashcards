package com.rrbrambley.flashcards.auth

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Feature-permission keys mirrored from the backend RBAC catalog (`auth/Rbac.kt`). */
object Permissions {
    const val MANAGE_DISCUSSIONS = "manage_discussions"
}

/**
 * Holds the signed-in user's effective permissions (from `GET /auth/me`) so the UI can gate admin
 * affordances — e.g. the discussion lock/unlock control (FLA-124). An interface so ViewModels can be
 * unit-tested with a hand-written fake.
 */
interface PermissionsRepository {
    /** The caller's effective permissions, empty for guests or when the fetch fails. */
    suspend fun permissions(): Set<String>

    /** Whether the caller holds [permission]. */
    suspend fun has(permission: String): Boolean = permission in permissions()
}

/**
 * Fetches `/auth/me` lazily and caches the result, keyed by the current access token: a token change
 * (login, user switch) refetches, and a missing token (logged out) reports no permissions — so a
 * revoked role takes effect on the next token rotation without any explicit cache invalidation. Only
 * successful fetches are cached, so a transient failure is retried rather than stuck empty.
 */
@Singleton
class DefaultPermissionsRepository @Inject constructor(
    private val apiClient: FlashcardApiClient,
    private val tokenStore: TokenStore,
) : PermissionsRepository {
    private val mutex = Mutex()
    private var cachedForToken: String? = null
    private var cached: Set<String> = emptySet()

    override suspend fun permissions(): Set<String> = mutex.withLock {
        val token = tokenStore.currentToken()
        if (token == null) {
            cachedForToken = null
            cached = emptySet()
            return emptySet()
        }
        if (token == cachedForToken) return cached
        val fetched = runCatching { apiClient.getMe().permissions.toSet() }.getOrNull()
        if (fetched != null) {
            cached = fetched
            cachedForToken = token
        }
        fetched ?: emptySet()
    }
}
