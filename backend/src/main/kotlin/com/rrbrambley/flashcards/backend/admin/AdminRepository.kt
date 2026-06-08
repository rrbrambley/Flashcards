package com.rrbrambley.flashcards.backend.admin

import com.rrbrambley.flashcards.backend.auth.Role
import com.rrbrambley.flashcards.backend.db.Roles
import com.rrbrambley.flashcards.backend.db.UserRoles
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.ConflictException
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.routes.Cursor
import com.rrbrambley.flashcards.shared.api.Page
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll

object AdminRepository {

    /** The code-defined role catalog (read-only on the web): each role with its description + grants. */
    fun roleCatalog(): List<RoleDto> = Role.entries.map { role ->
        RoleDto(key = role.key, description = role.description, permissions = role.permissions.map { it.key })
    }

    /**
     * One page of users (id ascending), each with the role keys they hold; optionally filtered by a
     * case-insensitive email substring [query]. The cursor packs the last id seen, so paging is stable
     * as users are added. Fetches [limit] + 1 rows to tell whether a further page exists.
     */
    suspend fun listUsers(limit: Int, cursor: String?, query: String?): Page<AdminUserDto> = dbQuery {
        val afterId = cursor?.let {
            Cursor.decode(it).toLongOrNull() ?: throw IllegalArgumentException("Invalid pagination cursor")
        }
        val q = query?.trim()?.takeIf { it.isNotEmpty() }

        val rowsQuery = Users.selectAll()
        if (afterId != null) rowsQuery.andWhere { Users.id greater afterId }
        if (q != null) rowsQuery.andWhere { Users.email.lowerCase() like "%${q.lowercase()}%" }
        val rows = rowsQuery.orderBy(Users.id to SortOrder.ASC).limit(limit + 1).toList()

        val pageRows = rows.take(limit)
        // Assignments are few per user; group them all once and look up per row (mirrors the admin CLI).
        val rolesByUser = (UserRoles innerJoin Roles)
            .selectAll()
            .map { it[UserRoles.userId].value to it[Roles.key] }
            .groupBy({ it.first }, { it.second })

        val items = pageRows.map { row ->
            val id = row[Users.id].value
            AdminUserDto(id = id, email = row[Users.email], roles = rolesByUser[id].orEmpty().sorted())
        }
        val nextCursor = if (rows.size > limit) Cursor.encode(pageRows.last()[Users.id].value.toString()) else null
        Page(items = items, nextCursor = nextCursor)
    }

    /** Grants [roleKey] to [userId] (idempotent), returning the user's updated assignments. */
    suspend fun grantRole(userId: Long, roleKey: String): AdminUserDto = dbQuery {
        val email = emailOrThrow(userId)
        val roleId = roleIdOrThrow(roleKey)
        val already = UserRoles.selectAll()
            .where { (UserRoles.userId eq userId) and (UserRoles.roleId eq roleId) }
            .any()
        if (!already) {
            UserRoles.insert {
                it[UserRoles.userId] = userId
                it[UserRoles.roleId] = roleId
            }
        }
        userDto(userId, email)
    }

    /**
     * Revokes [roleKey] from [userId], returning the user's updated assignments. Guards against
     * removing the last `admin` — that would leave the instance with no administrative access.
     */
    suspend fun revokeRole(userId: Long, roleKey: String): AdminUserDto = dbQuery {
        val email = emailOrThrow(userId)
        val roleId = roleIdOrThrow(roleKey)

        if (roleKey == Role.ADMIN.key) {
            val userHasAdmin = UserRoles.selectAll()
                .where { (UserRoles.userId eq userId) and (UserRoles.roleId eq roleId) }
                .any()
            val adminCount = UserRoles.selectAll().where { UserRoles.roleId eq roleId }.count()
            if (userHasAdmin && adminCount <= 1L) {
                throw ConflictException("Cannot revoke the last admin; grant another user the admin role first.")
            }
        }
        UserRoles.deleteWhere { (UserRoles.userId eq userId) and (UserRoles.roleId eq roleId) }
        userDto(userId, email)
    }

    private fun emailOrThrow(userId: Long): String = Users.selectAll()
        .where { Users.id eq userId }
        .firstOrNull()
        ?.get(Users.email)
        ?: throw NotFoundException("No user with id $userId")

    private fun roleIdOrThrow(roleKey: String): Long = Roles.selectAll()
        .where { Roles.key eq roleKey }
        .firstOrNull()
        ?.get(Roles.id)
        ?.value
        ?: throw NotFoundException("Unknown role '$roleKey'")

    private fun userDto(userId: Long, email: String): AdminUserDto {
        val roles = (UserRoles innerJoin Roles)
            .selectAll()
            .where { UserRoles.userId eq userId }
            .map { it[Roles.key] }
            .sorted()
        return AdminUserDto(id = userId, email = email, roles = roles)
    }
}
