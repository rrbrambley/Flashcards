package com.rrbrambley.flashcards.backend.flags

import kotlinx.serialization.Serializable

/**
 * Admin feature-flag DTOs (FLA-175) — a backend⇄web contract (the web hand-mirrors them in TS), so
 * they live in the backend module, not `:shared:api`. Mobile consumes only the resolved flag map on
 * `MeResponse` / `GET /flags`, never these management shapes.
 */
@Serializable
data class AdminFlagDto(
    val key: String,
    val description: String,
    /** The global default state (applied when a user has no user/role override). */
    val enabled: Boolean,
    val userOverrides: List<FlagUserOverrideDto>,
    val roleOverrides: List<FlagRoleOverrideDto>,
)

@Serializable
data class FlagUserOverrideDto(val userId: Long, val email: String, val enabled: Boolean)

@Serializable
data class FlagRoleOverrideDto(val roleKey: String, val enabled: Boolean)

/** Body for PATCH /admin/flags/{key} — set the global default state. */
@Serializable
data class SetFlagEnabledRequest(val enabled: Boolean)

/** Body for PUT /admin/flags/{key}/users/{id} and /roles/{key} — set an override. */
@Serializable
data class SetFlagOverrideRequest(val enabled: Boolean)
