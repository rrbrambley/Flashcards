package com.rrbrambley.flashcards.backend.flags

import com.rrbrambley.flashcards.backend.db.FeatureFlagRoleOverrides
import com.rrbrambley.flashcards.backend.db.FeatureFlagUserOverrides
import com.rrbrambley.flashcards.backend.db.FeatureFlags
import com.rrbrambley.flashcards.backend.db.UserRoles
import com.rrbrambley.flashcards.backend.db.dbQuery
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Resolves a caller's feature flags (FLA-174). Every catalog flag is resolved to a boolean with
 * precedence **per-user override → per-role override → global default**. For multiple role overrides
 * (a user in several roles) the result is enabled-wins (logical OR). Loaded fresh per call — like
 * [com.rrbrambley.flashcards.backend.auth.PermissionRepository] — so an admin toggle takes effect on
 * the caller's next request, no restart or token refresh needed.
 */
object FeatureFlagService {

    /** The resolved flag map for [userId]. */
    suspend fun flagsFor(userId: Long): Map<String, Boolean> = dbQuery { flagsForTx(userId) }

    /** As [flagsFor], but assumes an already-open transaction (callers inside `dbQuery`). */
    fun flagsForTx(userId: Long): Map<String, Boolean> {
        val flags = FeatureFlags.selectAll().associate {
            it[FeatureFlags.id].value to (it[FeatureFlags.key] to it[FeatureFlags.enabled])
        }

        val userOverrides = FeatureFlagUserOverrides
            .selectAll()
            .where { FeatureFlagUserOverrides.userId eq userId }
            .associate { it[FeatureFlagUserOverrides.flagId].value to it[FeatureFlagUserOverrides.enabled] }

        // A user may hold several roles; collapse their overrides per flag with enabled-wins (OR).
        val roleOverrides = mutableMapOf<Long, Boolean>()
        UserRoles
            .join(
                FeatureFlagRoleOverrides,
                JoinType.INNER,
                onColumn = UserRoles.roleId,
                otherColumn = FeatureFlagRoleOverrides.roleId,
            )
            .selectAll()
            .where { UserRoles.userId eq userId }
            .forEach { row ->
                val flagId = row[FeatureFlagRoleOverrides.flagId].value
                roleOverrides[flagId] = (roleOverrides[flagId] ?: false) || row[FeatureFlagRoleOverrides.enabled]
            }

        return flags.entries.associate { (flagId, keyAndDefault) ->
            val (key, globalDefault) = keyAndDefault
            key to (userOverrides[flagId] ?: roleOverrides[flagId] ?: globalDefault)
        }
    }
}
