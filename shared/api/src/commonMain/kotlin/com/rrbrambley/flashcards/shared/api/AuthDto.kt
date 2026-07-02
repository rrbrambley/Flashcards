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
    /** The user's selected avatar key (e.g. "dragon"), or null. Lets the picker show the current
     *  selection. Defaulted (FLA-162). */
    val avatarKey: String? = null,
    /** The resolved CDN URL for [avatarKey] (`…/avatars/<key>.png`), or null when unset or the CDN
     *  isn't configured. Clients render this and fall back to an initials monogram when null. */
    val avatarUrl: String? = null,
    /** The caller's resolved feature flags — every catalog flag key → its effective value (FLA-174).
     *  Also available via `GET /flags`. Defaulted so older clients keep working. */
    val flags: Map<String, Boolean> = emptyMap(),
)

/**
 * Body for PATCH /auth/me — update the caller's profile. **Merge semantics per field:** a field that
 * is absent/null is left unchanged; a blank string clears it; a value sets it. So a display-name edit
 * needn't resend the avatar, and vice-versa. [avatarKey] must be one of `GET /avatars` (FLA-114 / FLA-162).
 */
@Serializable
data class UpdateProfileRequest(val displayName: String? = null, val avatarKey: String? = null)

/** One selectable profile avatar (FLA-162): its stable [key] and the CDN [url] to render it. */
@Serializable
data class AvatarDto(val key: String, val url: String)
