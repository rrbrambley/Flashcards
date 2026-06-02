package com.rrbrambley.flashcards.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

/**
 * Signs and verifies short-lived access-token JWTs (HMAC256). Configured once at startup
 * (see [configure], called from `configureSecurity`). The subject claim carries the user id,
 * so request authentication is signature + `exp` only — no per-request DB lookup.
 *
 * Refresh tokens are NOT JWTs: they are opaque, DB-backed, and revocable (see [AuthService]).
 */
object TokenService {
    private data class Config(
        val secret: String,
        val issuer: String,
        val audience: String,
        val accessTtlMillis: Long,
        val refreshTtlMillis: Long,
    )

    @Volatile
    private var config: Config? = null

    @Volatile
    private var cachedVerifier: JWTVerifier? = null

    fun configure(secret: String, issuer: String, audience: String, accessTtlMillis: Long, refreshTtlMillis: Long) {
        config = Config(secret, issuer, audience, accessTtlMillis, refreshTtlMillis)
        cachedVerifier = JWT.require(Algorithm.HMAC256(secret))
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
    }

    private fun requireConfig(): Config = config ?: error("TokenService not configured; call configure() at startup")

    val issuer: String get() = requireConfig().issuer
    val audience: String get() = requireConfig().audience
    val refreshTtlMillis: Long get() = requireConfig().refreshTtlMillis

    /** The verifier used by the JWT auth provider to validate incoming access tokens. */
    fun verifier(): JWTVerifier = cachedVerifier ?: error("TokenService not configured")

    /**
     * Mints an access-token JWT for [userId]. [ttlMillis] defaults to the configured access TTL;
     * pass a custom (or negative) value in tests to exercise expiry.
     */
    fun generateAccessToken(userId: Long, ttlMillis: Long = requireConfig().accessTtlMillis): String {
        val cfg = requireConfig()
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(cfg.issuer)
            .withAudience(cfg.audience)
            .withSubject(userId.toString())
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + ttlMillis))
            .sign(Algorithm.HMAC256(cfg.secret))
    }
}
