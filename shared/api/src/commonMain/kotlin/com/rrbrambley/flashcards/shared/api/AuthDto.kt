package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

/** Body for POST /auth/google: a Google ID token obtained on the client. */
@Serializable
data class GoogleAuthRequest(val idToken: String)

/** Body for POST /auth/refresh: exchange a refresh token for a fresh access token. */
@Serializable
data class RefreshRequest(val refreshToken: String)

/** Body for POST /auth/logout: the refresh token to revoke (ends the session server-side). */
@Serializable
data class LogoutRequest(val refreshToken: String)

/**
 * Issued by register/login/google and refresh. [accessToken] is a short-lived JWT sent as the
 * bearer on every request; [refreshToken] is an opaque, long-lived token exchanged at
 * /auth/refresh and revoked at /auth/logout. [permissions] are the user's effective feature
 * permissions (defaulted so older clients that ignore the field keep working).
 */
@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val permissions: List<String> = emptyList(),
)

/** GET /auth/me — the current user's identity, roles, and effective permissions. */
@Serializable
data class MeResponse(
    val userId: Long,
    val email: String,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    /**
     * The user's explicit public display name, or null when unset (then attribution falls back to the
     * email local-part). Defaulted so older clients keep working (FLA-114).
     */
    val displayName: String? = null,
)

/** Body for PATCH /auth/me — update the caller's profile. A blank/absent [displayName] clears it. */
@Serializable
data class UpdateProfileRequest(val displayName: String? = null)
