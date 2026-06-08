package com.rrbrambley.flashcards.backend.auth

import com.rrbrambley.flashcards.backend.db.Permissions
import com.rrbrambley.flashcards.backend.db.RolePermissions
import com.rrbrambley.flashcards.backend.db.UserRoles
import com.rrbrambley.flashcards.backend.db.dbQuery
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.selectAll

object PermissionRepository {

    /**
     * The set of permission keys granted to [userId] across all of their roles (empty if the user
     * has no roles or none grant any permission). Loaded fresh per call — gated routes query this via
     * [com.rrbrambley.flashcards.backend.routes.requirePermission], so revoking a role takes effect
     * immediately rather than waiting for the access token to refresh.
     */
    suspend fun effectivePermissions(userId: Long): Set<String> = dbQuery {
        UserRoles
            .join(RolePermissions, JoinType.INNER, onColumn = UserRoles.roleId, otherColumn = RolePermissions.roleId)
            .innerJoin(Permissions)
            .selectAll()
            .where { UserRoles.userId eq userId }
            .map { it[Permissions.key] }
            .toSet()
    }
}
