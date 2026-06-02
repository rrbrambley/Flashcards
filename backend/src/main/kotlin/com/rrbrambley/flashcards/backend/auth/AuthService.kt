package com.rrbrambley.flashcards.backend.auth

import com.rrbrambley.flashcards.backend.db.RefreshTokens
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.ConflictException
import com.rrbrambley.flashcards.backend.error.UnauthorizedException
import com.rrbrambley.flashcards.shared.api.AuthResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
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
     * Exchanges a valid, non-revoked, non-expired refresh token for a fresh access token.
     * The refresh token itself is returned unchanged (no rotation in this pass — see FLA-6).
     * Throws [UnauthorizedException] if the refresh token is unknown or expired.
     */
    suspend fun refresh(refreshToken: String): AuthResponse = dbQuery {
        val now = System.currentTimeMillis()
        val userId = RefreshTokens
            .selectAll()
            .where { (RefreshTokens.token eq refreshToken) and (RefreshTokens.expiresAtMillis greater now) }
            .firstOrNull()
            ?.get(RefreshTokens.userId)
            ?.value
            ?: throw UnauthorizedException("Invalid or expired refresh token")
        AuthResponse(
            accessToken = TokenService.generateAccessToken(userId),
            refreshToken = refreshToken,
            userId = userId,
        )
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
        )
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
