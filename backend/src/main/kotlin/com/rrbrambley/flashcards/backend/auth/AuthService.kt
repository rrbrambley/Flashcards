package com.rrbrambley.flashcards.backend.auth

import com.rrbrambley.flashcards.backend.db.RefreshTokens
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.ConflictException
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.error.UnauthorizedException
import com.rrbrambley.flashcards.backend.flags.FeatureFlagService
import com.rrbrambley.flashcards.backend.validation.Validation
import com.rrbrambley.flashcards.shared.api.AuthResponse
import com.rrbrambley.flashcards.shared.api.MeResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom
import java.util.Base64

object AuthService {
    private val secureRandom = SecureRandom()

    suspend fun register(email: String, password: String): AuthResponse = dbQuery {
        val taken = Users.selectAll().where { Users.email eq email }.any()
        if (taken) throw ConflictException("An account with email '$email' already exists")

        val now = System.currentTimeMillis()
        val userId = Users.insertAndGetId {
            it[Users.email] = email
            it[passwordHash] = Passwords.hash(password)
            it[createdAtMillis] = now
        }.value
        issueTokens(userId, now)
    }

    suspend fun login(email: String, password: String): AuthResponse = dbQuery {
        val row = Users.selectAll().where { Users.email eq email }.firstOrNull()
            ?: throw UnauthorizedException("Invalid email or password")
        val hash = row[Users.passwordHash]
            ?: throw UnauthorizedException("Invalid email or password") // Google-only account
        if (!Passwords.verify(password, hash)) {
            throw UnauthorizedException("Invalid email or password")
        }
        issueTokens(row[Users.id].value, System.currentTimeMillis())
    }

    /** Find-or-create by verified Google email (links googleSub onto an existing account). */
    suspend fun signInWithGoogle(email: String, googleSub: String): AuthResponse = dbQuery {
        val now = System.currentTimeMillis()
        val existing = Users.selectAll().where { Users.email eq email }.firstOrNull()
        val userId = if (existing != null) {
            val id = existing[Users.id].value
            if (existing[Users.googleSub] == null) {
                Users.update({ Users.id eq id }) { it[Users.googleSub] = googleSub }
            }
            id
        } else {
            Users.insertAndGetId {
                it[Users.email] = email
                it[Users.googleSub] = googleSub
                it[createdAtMillis] = now
            }.value
        }
        issueTokens(userId, now)
    }

    /**
     * Exchanges a valid, non-revoked, non-expired refresh token for a fresh access token AND a
     * fresh refresh token (rotation): the presented token is retired and can't be used again.
     * Replaying an already-rotated token signals theft, so the whole session is revoked.
     * Throws [UnauthorizedException] if the refresh token is unknown, expired, or reused.
     */
    private sealed interface RefreshResult {
        data class Rotated(val response: AuthResponse) : RefreshResult
        data class Reuse(val userId: Long) : RefreshResult
        data object Invalid : RefreshResult
    }

    suspend fun refresh(refreshToken: String): AuthResponse {
        val now = System.currentTimeMillis()
        val result = dbQuery {
            val row = RefreshTokens.selectAll()
                .where { RefreshTokens.token eq refreshToken }
                .firstOrNull()
                ?: return@dbQuery RefreshResult.Invalid
            val userId = row[RefreshTokens.userId].value
            when {
                // Already exchanged → reuse/theft (handled below, in its own committed transaction).
                row[RefreshTokens.rotatedAtMillis] != null -> RefreshResult.Reuse(userId)
                row[RefreshTokens.expiresAtMillis] <= now -> RefreshResult.Invalid
                else -> {
                    // Rotate: retire the presented token and mint a replacement for the same user.
                    RefreshTokens.update({ RefreshTokens.token eq refreshToken }) { it[rotatedAtMillis] = now }
                    val newRefresh = generateOpaqueToken()
                    RefreshTokens.insert {
                        it[token] = newRefresh
                        it[RefreshTokens.userId] = userId
                        it[createdAtMillis] = now
                        it[expiresAtMillis] = now + TokenService.refreshTtlMillis
                    }
                    RefreshResult.Rotated(
                        AuthResponse(
                            accessToken = TokenService.generateAccessToken(userId),
                            refreshToken = newRefresh,
                            userId = userId,
                        ),
                    )
                }
            }
        }
        return when (result) {
            is RefreshResult.Rotated -> result.response
            is RefreshResult.Reuse -> {
                // Revoke the whole session in its own committed transaction (throwing inside the
                // read transaction would roll the delete back), then reject.
                dbQuery { RefreshTokens.deleteWhere { RefreshTokens.userId eq result.userId } }
                throw UnauthorizedException("Refresh token reuse detected; please sign in again")
            }
            RefreshResult.Invalid -> throw UnauthorizedException("Invalid or expired refresh token")
        }
    }

    /**
     * Revokes a refresh token so the session can no longer be refreshed (logout).
     * Scoped to [userId] so a caller can only revoke its own session.
     */
    suspend fun revokeRefreshToken(refreshToken: String, userId: Long) {
        dbQuery {
            RefreshTokens.deleteWhere { (token eq refreshToken) and (RefreshTokens.userId eq userId) }
        }
    }

    /** Deletes expired refresh-token rows. Safe to call periodically; returns the number removed. */
    suspend fun pruneExpiredRefreshTokens(now: Long = System.currentTimeMillis()): Int = dbQuery {
        RefreshTokens.deleteWhere { expiresAtMillis less now }
    }

    /** Identity + roles + effective permissions + profile for the authenticated user (GET /auth/me). */
    suspend fun me(userId: Long): MeResponse = dbQuery {
        val row = Users.selectAll().where { Users.id eq userId }.firstOrNull()
            ?: throw NotFoundException("User $userId not found")
        val avatarKey = row[Users.avatarKey]
        MeResponse(
            userId = userId,
            email = row[Users.email],
            roles = PermissionRepository.rolesTx(userId).toList(),
            permissions = PermissionRepository.effectivePermissionsTx(userId).toList(),
            displayName = row[Users.displayName],
            avatarKey = avatarKey,
            avatarUrl = Avatars.urlFor(avatarKey),
            flags = FeatureFlagService.flagsForTx(userId),
        )
    }

    /**
     * Updates the caller's profile and returns the refreshed [MeResponse] (FLA-114 / FLA-162).
     * **Merge semantics per field:** a null/absent field is left unchanged, a blank string clears it,
     * a value sets it. [avatarKey] must be one of [Avatars.keys] (else 400 via [IllegalArgumentException]).
     */
    suspend fun updateProfile(userId: Long, displayName: String?, avatarKey: String?): MeResponse {
        // Resolve "set vs clear vs leave" outside the transaction (validation throws before any write).
        val normalizedName = displayName?.let { Validation.normalizeDisplayName(it) } // blank -> null (clear)
        val normalizedAvatar = avatarKey?.takeIf { it.isNotBlank() } // blank -> null (clear)
        if (normalizedAvatar != null) {
            require(Avatars.isValid(normalizedAvatar)) { "'$normalizedAvatar' is not a valid avatar" }
        }
        dbQuery {
            val updated = Users.update({ Users.id eq userId }) {
                if (displayName != null) it[Users.displayName] = normalizedName
                if (avatarKey != null) it[Users.avatarKey] = normalizedAvatar
            }
            if (updated == 0) throw NotFoundException("User $userId not found")
        }
        return me(userId)
    }

    /** Public attribution name for [email]: the explicit [displayName] if set, else the email local-part. */
    fun displayNameOrDefault(displayName: String?, email: String): String =
        displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")

    /** Mints an access-token JWT plus a stored, opaque refresh token. Runs inside a transaction. */
    private fun issueTokens(userId: Long, now: Long): AuthResponse {
        val refreshToken = generateOpaqueToken()
        RefreshTokens.insert {
            it[token] = refreshToken
            it[RefreshTokens.userId] = userId
            it[createdAtMillis] = now
            it[expiresAtMillis] = now + TokenService.refreshTtlMillis
        }
        return AuthResponse(
            accessToken = TokenService.generateAccessToken(userId),
            refreshToken = refreshToken,
            userId = userId,
            permissions = PermissionRepository.effectivePermissionsTx(userId).toList(),
        )
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
