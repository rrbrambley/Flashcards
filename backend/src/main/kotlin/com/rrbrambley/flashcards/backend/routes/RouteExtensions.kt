package com.rrbrambley.flashcards.backend.routes

import com.rrbrambley.flashcards.backend.auth.Permission
import com.rrbrambley.flashcards.backend.auth.PermissionRepository
import com.rrbrambley.flashcards.backend.auth.UserPrincipal
import com.rrbrambley.flashcards.backend.error.ForbiddenException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

/** The authenticated user id; non-null inside an `authenticate("auth-bearer")` block. */
fun ApplicationCall.userId(): Long = principal<UserPrincipal>()!!.userId

/**
 * Guards an authenticated route on a feature [permission]: throws [ForbiddenException] (→ 403) unless
 * the current user has it. The user's effective permissions are loaded from the DB per call, so a
 * revoked role takes effect immediately. Call inside an `authenticate(BEARER_AUTH)` block.
 */
suspend fun ApplicationCall.requirePermission(permission: Permission) {
    if (permission.key !in PermissionRepository.effectivePermissions(userId())) {
        throw ForbiddenException("Missing required permission: ${permission.key}")
    }
}

/** Reads a required Long path parameter, throwing 400 on a missing/invalid value. */
fun ApplicationCall.pathLong(name: String): Long = parameters[name]?.toLongOrNull()
    ?: throw IllegalArgumentException("Invalid '$name' path parameter")
