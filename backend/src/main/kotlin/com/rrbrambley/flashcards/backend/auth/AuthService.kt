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
import java.security.SecureRandom
import java.util.Base64

object AuthService {
    private val secureRandom = SecureRandom()

    suspend fun register(username: String, password: String): AuthResponse = dbQuery {
        val taken = Users.selectAll().where { Users.username eq username }.any()
        if (taken) throw ConflictException("Username '$username' is already taken")

        val now = System.currentTimeMillis()
        val userId = Users.insertAndGetId {
            it[Users.username] = username
            it[passwordHash] = Passwords.hash(password)
            it[createdAtMillis] = now
        }.value
        AuthResponse(token = mintToken(userId, now), userId = userId)
    }

    suspend fun login(username: String, password: String): AuthResponse = dbQuery {
        val row = Users.selectAll().where { Users.username eq username }.firstOrNull()
            ?: throw UnauthorizedException("Invalid username or password")
        if (!Passwords.verify(password, row[Users.passwordHash])) {
            throw UnauthorizedException("Invalid username or password")
        }
        val userId = row[Users.id].value
        AuthResponse(token = mintToken(userId, System.currentTimeMillis()), userId = userId)
    }

    /** Resolves a bearer token to its user id, or null if unknown. */
    suspend fun resolveUser(token: String): Long? = dbQuery {
        AuthTokens.selectAll()
            .where { AuthTokens.token eq token }
            .firstOrNull()
            ?.get(AuthTokens.userId)
            ?.value
    }

    /** Must be called inside an active transaction (register/login run within dbQuery). */
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
