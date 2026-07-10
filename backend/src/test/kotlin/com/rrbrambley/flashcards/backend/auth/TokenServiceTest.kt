package com.rrbrambley.flashcards.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [TokenService] — access-JWT minting/verification. Runs without a server: each test
 * reconfigures the (global) service with known values, so the round-trip, the expiry check, and the
 * signature check are pinned independently of the full auth flow in ApplicationFlowTest.
 */
class TokenServiceTest {

    private val secret = "test-secret-please-ignore"
    private val issuer = "flashcards-test"
    private val audience = "flashcards-clients"

    @BeforeTest
    fun configure() {
        TokenService.configure(
            secret = secret,
            issuer = issuer,
            audience = audience,
            accessTtlMillis = 60_000,
            refreshTtlMillis = 1_209_600_000, // 14 days
        )
    }

    @Test
    fun generated_token_round_trips_with_the_user_id_as_subject_and_the_configured_claims() {
        val token = TokenService.generateAccessToken(userId = 42)
        val decoded = TokenService.verifier().verify(token)

        assertEquals("42", decoded.subject)
        assertEquals(issuer, decoded.issuer)
        assertTrue(audience in decoded.audience)
    }

    @Test
    fun an_expired_token_fails_verification() {
        // A negative TTL mints an already-expired token (the documented test hook).
        val expired = TokenService.generateAccessToken(userId = 1, ttlMillis = -1_000)
        assertFailsWith<TokenExpiredException> { TokenService.verifier().verify(expired) }
    }

    @Test
    fun a_token_signed_with_a_different_secret_is_rejected() {
        val token = TokenService.generateAccessToken(userId = 7)
        val foreignVerifier = JWT.require(Algorithm.HMAC256("a-different-secret"))
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
        assertFailsWith<JWTVerificationException> { foreignVerifier.verify(token) }
    }

    @Test
    fun a_token_from_a_foreign_issuer_is_rejected() {
        val foreignToken = JWT.create()
            .withIssuer("evil-issuer")
            .withAudience(audience)
            .withSubject("42")
            .sign(Algorithm.HMAC256(secret))
        assertFailsWith<JWTVerificationException> { TokenService.verifier().verify(foreignToken) }
    }

    @Test
    fun configured_metadata_is_exposed() {
        assertEquals(issuer, TokenService.issuer)
        assertEquals(audience, TokenService.audience)
        assertEquals(1_209_600_000, TokenService.refreshTtlMillis)
    }
}
