package com.rrbrambley.flashcards.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.seconds

/** Named limiter applied to the auth routes (see [configureRateLimiting] + Routing). */
val AUTH_RATE_LIMIT = RateLimitName("auth")

/**
 * Throttles the auth endpoints per client IP to blunt credential stuffing / bulk signups.
 * Exceeding the limit yields 429 with a Retry-After header. Limits come from `ratelimit.auth`
 * in config (env-overridable); tests bump the limit so normal flows aren't throttled.
 */
fun Application.configureRateLimiting() {
    val config = environment.config.config("ratelimit").config("auth")
    val limit = config.property("limit").getString().toInt()
    val windowSeconds = config.property("windowSeconds").getString().toLong()

    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = limit, refillPeriod = windowSeconds.seconds)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}
