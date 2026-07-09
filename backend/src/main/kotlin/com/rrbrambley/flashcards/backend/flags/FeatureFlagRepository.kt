package com.rrbrambley.flashcards.backend.flags

import com.rrbrambley.flashcards.backend.db.FeatureFlagRoleOverrides
import com.rrbrambley.flashcards.backend.db.FeatureFlagUserOverrides
import com.rrbrambley.flashcards.backend.db.FeatureFlags
import com.rrbrambley.flashcards.backend.db.Roles
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Admin management of feature flags (FLA-175): list the catalog with each flag's global state +
 * overrides, toggle the global state, and set/clear per-user and per-role overrides. All writes are
 * idempotent (upsert / delete-if-present). Gated at the route layer on
 * [com.rrbrambley.flashcards.backend.auth.Permission.MANAGE_FEATURE_FLAGS].
 */
object FeatureFlagRepository {

    suspend fun listFlags(): List<AdminFlagDto> = dbQuery {
        FeatureFlags.selectAll().orderBy(FeatureFlags.key).map { row -> row.toAdminFlagDto() }
    }

    suspend fun setGlobal(key: String, enabled: Boolean): AdminFlagDto = dbQuery {
        val flagId = flagIdByKey(key)
        FeatureFlags.update({ FeatureFlags.id eq flagId }) { it[FeatureFlags.enabled] = enabled }
        adminFlagDto(flagId)
    }

    suspend fun setUserOverride(key: String, userId: Long, enabled: Boolean): AdminFlagDto = dbQuery {
        val flagId = flagIdByKey(key)
        if (!Users.selectAll().where { Users.id eq userId }.any()) {
            throw NotFoundException("User $userId not found")
        }
        val updated = FeatureFlagUserOverrides.update({
            (FeatureFlagUserOverrides.flagId eq flagId) and (FeatureFlagUserOverrides.userId eq userId)
        }) { it[FeatureFlagUserOverrides.enabled] = enabled }
        if (updated == 0) {
            FeatureFlagUserOverrides.insert {
                it[FeatureFlagUserOverrides.flagId] = flagId
                it[FeatureFlagUserOverrides.userId] = userId
                it[FeatureFlagUserOverrides.enabled] = enabled
            }
        }
        adminFlagDto(flagId)
    }

    suspend fun clearUserOverride(key: String, userId: Long): AdminFlagDto = dbQuery {
        val flagId = flagIdByKey(key)
        FeatureFlagUserOverrides.deleteWhere {
            (FeatureFlagUserOverrides.flagId eq flagId) and (FeatureFlagUserOverrides.userId eq userId)
        }
        adminFlagDto(flagId)
    }

    suspend fun setRoleOverride(key: String, roleKey: String, enabled: Boolean): AdminFlagDto = dbQuery {
        val flagId = flagIdByKey(key)
        val roleId = roleIdByKey(roleKey)
        val updated = FeatureFlagRoleOverrides.update({
            (FeatureFlagRoleOverrides.flagId eq flagId) and (FeatureFlagRoleOverrides.roleId eq roleId)
        }) { it[FeatureFlagRoleOverrides.enabled] = enabled }
        if (updated == 0) {
            FeatureFlagRoleOverrides.insert {
                it[FeatureFlagRoleOverrides.flagId] = flagId
                it[FeatureFlagRoleOverrides.roleId] = roleId
                it[FeatureFlagRoleOverrides.enabled] = enabled
            }
        }
        adminFlagDto(flagId)
    }

    suspend fun clearRoleOverride(key: String, roleKey: String): AdminFlagDto = dbQuery {
        val flagId = flagIdByKey(key)
        val roleId = roleIdByKey(roleKey)
        FeatureFlagRoleOverrides.deleteWhere {
            (FeatureFlagRoleOverrides.flagId eq flagId) and (FeatureFlagRoleOverrides.roleId eq roleId)
        }
        adminFlagDto(flagId)
    }

    /** Builds the DTO for a single flag by id (assumes an open transaction). */
    private fun adminFlagDto(flagId: Long): AdminFlagDto =
        FeatureFlags.selectAll().where { FeatureFlags.id eq flagId }.first().toAdminFlagDto()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toAdminFlagDto(): AdminFlagDto {
        val flagId = this[FeatureFlags.id].value
        val userOverrides = (FeatureFlagUserOverrides innerJoin Users)
            .selectAll()
            .where { FeatureFlagUserOverrides.flagId eq flagId }
            .map {
                FlagUserOverrideDto(
                    it[Users.id].value,
                    it[Users.email],
                    it[FeatureFlagUserOverrides.enabled],
                )
            }
        val roleOverrides = (FeatureFlagRoleOverrides innerJoin Roles)
            .selectAll()
            .where { FeatureFlagRoleOverrides.flagId eq flagId }
            .map { FlagRoleOverrideDto(it[Roles.key], it[FeatureFlagRoleOverrides.enabled]) }
        return AdminFlagDto(
            key = this[FeatureFlags.key],
            description = this[FeatureFlags.description],
            enabled = this[FeatureFlags.enabled],
            userOverrides = userOverrides,
            roleOverrides = roleOverrides,
        )
    }

    private fun flagIdByKey(key: String): Long =
        FeatureFlags.selectAll().where { FeatureFlags.key eq key }.firstOrNull()?.get(FeatureFlags.id)?.value
            ?: throw NotFoundException("Unknown feature flag '$key'")

    private fun roleIdByKey(roleKey: String): Long =
        Roles.selectAll().where { Roles.key eq roleKey }.firstOrNull()?.get(Roles.id)?.value
            ?: throw NotFoundException("Unknown role '$roleKey'")
}
