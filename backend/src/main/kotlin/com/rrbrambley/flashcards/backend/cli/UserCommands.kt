package com.rrbrambley.flashcards.backend.cli

import com.rrbrambley.flashcards.backend.auth.Passwords
import com.rrbrambley.flashcards.backend.db.Roles
import com.rrbrambley.flashcards.backend.db.UserRoles
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.validation.Validation
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import java.security.SecureRandom
import java.util.Base64

/**
 * `user create --email <email> [--password <password>] [--role <key>]...`
 *
 * Creates a password account using the same validation + bcrypt hashing as registration (but without
 * issuing tokens). If `--password` is omitted a strong one is generated and printed once. `--role`
 * may be repeated to grant roles at creation time.
 */
object UserCreateCommand : AdminCommand {
    override val name = "user create"
    override val usage =
        "user create --email <email> [--password <pw>] [--role <key>]...   Create a password user (random pw printed if omitted)."

    override suspend fun run(args: AdminArgs, out: Appendable) {
        val email = args.required("email").trim()
        Validation.validateEmail(email)
        val provided = args.optional("password")
        val password = provided ?: generatePassword()
        Validation.validatePassword(password)
        val roleKeys = args.list("role")

        dbQuery {
            if (Users.selectAll().where { Users.email eq email }.any()) {
                throw AdminError("a user with email '$email' already exists")
            }
            val roleIds = roleKeys.map { key ->
                Roles.selectAll().where { Roles.key eq key }.firstOrNull()?.get(Roles.id)?.value
                    ?: throw AdminError("unknown role '$key'")
            }
            val userId = Users.insertAndGetId {
                it[Users.email] = email
                it[passwordHash] = Passwords.hash(password)
                it[createdAtMillis] = System.currentTimeMillis()
            }.value
            roleIds.forEach { roleId ->
                UserRoles.insert {
                    it[UserRoles.userId] = userId
                    it[UserRoles.roleId] = roleId
                }
            }

            out.appendLine("Created user #$userId  $email")
            if (roleKeys.isNotEmpty()) out.appendLine("  roles: ${roleKeys.joinToString(", ")}")
            if (provided == null) out.appendLine("  generated password: $password")
        }
    }

    private val secureRandom = SecureRandom()

    private fun generatePassword(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/** `user list [--query <substr>]` — lists users (id, email, auth methods, roles). */
object UserListCommand : AdminCommand {
    override val name = "user list"
    override val usage = "user list [--query <substr>]   List users (id, email, auth, roles)."

    override suspend fun run(args: AdminArgs, out: Appendable) {
        val query = args.optional("query")
        dbQuery {
            val rolesByUser = (UserRoles innerJoin Roles)
                .selectAll()
                .map { it[UserRoles.userId].value to it[Roles.key] }
                .groupBy({ it.first }, { it.second })

            val rows = Users
                .selectAll()
                .apply { if (query != null) andWhere { Users.email like "%$query%" } }
                .orderBy(Users.id to SortOrder.ASC)
                .toList()

            if (rows.isEmpty()) {
                out.appendLine(if (query != null) "No users match '$query'." else "No users.")
                return@dbQuery
            }

            out.appendLine("%-6s %-36s %-16s %s".format("ID", "EMAIL", "AUTH", "ROLES"))
            rows.forEach { row ->
                val id = row[Users.id].value
                val auth = buildList {
                    if (row[Users.passwordHash] != null) add("password")
                    if (row[Users.googleSub] != null) add("google")
                }.ifEmpty { listOf("none") }.joinToString("+")
                val roles = rolesByUser[id].orEmpty().joinToString(",").ifEmpty { "-" }
                out.appendLine("%-6d %-36s %-16s %s".format(id, row[Users.email], auth, roles))
            }
        }
    }
}

/**
 * `user delete (--email <email> | --id <id>) [--yes]`
 *
 * Deletes the user row; the FK `ON DELETE CASCADE`s remove their decks, flashcards, practice
 * sessions, refresh tokens, and role assignments. Requires `--yes` (no-op with a warning otherwise).
 */
object UserDeleteCommand : AdminCommand {
    override val name = "user delete"
    override val usage = "user delete (--email <email> | --id <id>) [--yes]   Delete a user and all their data."

    override suspend fun run(args: AdminArgs, out: Appendable) {
        val email = args.optional("email")
        val idArg = args.optional("id")
        if ((email == null) == (idArg == null)) throw AdminError("specify exactly one of --email or --id")

        dbQuery {
            val row = if (email != null) {
                Users.selectAll().where { Users.email eq email }.firstOrNull()
            } else {
                val id = idArg!!.toLongOrNull() ?: throw AdminError("--id must be a number")
                Users.selectAll().where { Users.id eq id }.firstOrNull()
            } ?: throw AdminError("no matching user")

            val id = row[Users.id].value
            val foundEmail = row[Users.email]
            if (!args.has("yes")) {
                out.appendLine("Would delete user #$id  $foundEmail and ALL their decks, sessions, tokens, and roles.")
                out.appendLine("Re-run with --yes to confirm.")
                return@dbQuery
            }
            Users.deleteWhere { Users.id eq id }
            out.appendLine("Deleted user #$id  $foundEmail")
        }
    }
}
