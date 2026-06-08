package com.rrbrambley.flashcards.backend.auth

/**
 * The code-defined catalog of RBAC roles and feature permissions. These keys are the stable source of
 * truth: `DatabaseFactory` seeds them — and each role's default grants — into the
 * roles/permissions/role_permissions tables, so the database can hold the runtime assignments (which
 * user holds which role). Gate an endpoint with `ApplicationCall.requirePermission(Permission.X)`.
 *
 * Adding a permission = add an enum constant (+ grant it to a role) and reseed; the new row is created
 * on the next boot.
 */
enum class Permission(val key: String, val description: String) {
    MANAGE_GLOBAL_DECKS("manage_global_decks", "Create, edit, and delete global (catalog) flashcard decks"),
}

enum class Role(val key: String, val description: String, val permissions: Set<Permission>) {
    ADMIN("admin", "Full administrative access", setOf(Permission.MANAGE_GLOBAL_DECKS)),
    USER("user", "A standard, non-privileged user", emptySet()),
}
