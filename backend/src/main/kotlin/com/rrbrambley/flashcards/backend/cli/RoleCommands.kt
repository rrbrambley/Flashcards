package com.rrbrambley.flashcards.backend.cli

import com.rrbrambley.flashcards.backend.db.Roles
import com.rrbrambley.flashcards.backend.db.UserRoles
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** `role grant --email <email> --role <key>` — assigns an existing role to a user (e.g. make an admin). */
object RoleGrantCommand : AdminCommand {
    override val name = "role grant"
    override val usage = "role grant --email <email> --role <key>   Grant a role to a user."

    override suspend fun run(args: AdminArgs, out: Appendable) {
        val email = args.required("email").trim()
        val roleKey = args.required("role")
        dbQuery {
            val userId = userIdByEmail(email)
            val roleId = roleIdByKey(roleKey)
            val already = UserRoles
                .selectAll()
                .where { (UserRoles.userId eq userId) and (UserRoles.roleId eq roleId) }
                .any()
            if (already) {
                out.appendLine("$email already has role '$roleKey'.")
                return@dbQuery
            }
            UserRoles.insert {
                it[UserRoles.userId] = userId
                it[UserRoles.roleId] = roleId
            }
            out.appendLine("Granted '$roleKey' to $email.")
        }
    }
}

/** `role revoke --email <email> --role <key>` — removes a role assignment from a user. */
object RoleRevokeCommand : AdminCommand {
    override val name = "role revoke"
    override val usage = "role revoke --email <email> --role <key>   Revoke a role from a user."

    override suspend fun run(args: AdminArgs, out: Appendable) {
        val email = args.required("email").trim()
        val roleKey = args.required("role")
        dbQuery {
            val userId = userIdByEmail(email)
            val roleId = roleIdByKey(roleKey)
            val removed = UserRoles.deleteWhere { (UserRoles.userId eq userId) and (UserRoles.roleId eq roleId) }
            if (removed == 0) {
                out.appendLine("$email did not have role '$roleKey'.")
            } else {
                out.appendLine("Revoked '$roleKey' from $email.")
            }
        }
    }
}

private fun userIdByEmail(email: String): Long =
    Users.selectAll().where { Users.email eq email }.firstOrNull()?.get(Users.id)?.value
        ?: throw AdminError("no user with email '$email'")

private fun roleIdByKey(roleKey: String): Long =
    Roles.selectAll().where { Roles.key eq roleKey }.firstOrNull()?.get(Roles.id)?.value
        ?: throw AdminError("unknown role '$roleKey'")
