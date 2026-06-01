package com.rrbrambley.flashcards.backend.auth

import com.rrbrambley.flashcards.backend.db.AuthTokens
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.ConflictException
import com.rrbrambley.flashcards.backend.error.UnauthorizedException
import com.rrbrambley.flashcards.shared.api.AuthResponse
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
        AuthResponse(token = mintToken(userId, now), userId = userId)
    }

    suspend fun login(email: String, password: String): AuthResponse = dbQuery {
        val row = Users.selectAll().where { Users.email eq email }.firstOrNull()
            ?: throw UnauthorizedException("Invalid email or password")
        val hash = row[Users.passwordHash]
            ?: throw UnauthorizedException("Invalid email or password") // Google-only account
        if (!Passwords.verify(password, hash)) {
            throw UnauthorizedException("Invalid email or password")
        }
        val userId = row[Users.id].value
        AuthResponse(token = mintToken(userId, System.currentTimeMillis()), userId = userId)
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
        AuthResponse(token = mintToken(userId, now), userId = userId)
    }

    /** Resolves a bearer token to its user id, or null if unknown. */
    suspend fun resolveUser(token: String): Long? = dbQuery {
        AuthTokens.selectAll()
            .where { AuthTokens.token eq token }
            .firstOrNull()
            ?.get(AuthTokens.userId)
            ?.value
    }

    /** Must be called inside an active transaction (callers run within dbQuery). */
    private fun mintToken(userId: Long, now: Long): String {
        val token = generateToken()
        AuthTokens.insert {
            it[AuthTokens.token] = token
            it[AuthTokens.userId] = userId
            it[createdAtMillis] = now
        }
        return token
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
