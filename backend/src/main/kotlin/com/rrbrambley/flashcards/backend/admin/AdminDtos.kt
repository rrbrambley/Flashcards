package com.rrbrambley.flashcards.backend.admin

import kotlinx.serialization.Serializable

/**
 * Admin-only RBAC DTOs. These belong to the web admin contract, not the cross-platform client SDK,
 * so they live in the backend module (mobile never consumes them; the web hand-mirrors them in TS).
 */

/** A user as seen by the admin UI: identity + the role keys they currently hold. */
@Serializable
data class AdminUserDto(val id: Long, val email: String, val roles: List<String>)

/** A code-defined role from the catalog (read-only on the web): its key, description, and grants. */
@Serializable
data class RoleDto(val key: String, val description: String, val permissions: List<String>)

/** Body of `POST /admin/users/{id}/roles` — the role key to grant. */
@Serializable
data class GrantRoleRequest(val role: String)
