package com.rrbrambley.flashcards.backend.plugins

import com.rrbrambley.flashcards.backend.admin.adminRoutes
import com.rrbrambley.flashcards.backend.auth.authRoutes
import com.rrbrambley.flashcards.backend.auth.authenticatedAuthRoutes
import com.rrbrambley.flashcards.backend.decks.catalogRoutes
import com.rrbrambley.flashcards.backend.decks.deckRoutes
import com.rrbrambley.flashcards.backend.health.healthRoutes
import com.rrbrambley.flashcards.backend.home.homeRoutes
import com.rrbrambley.flashcards.backend.images.imageRoutes
import com.rrbrambley.flashcards.backend.sessions.sessionRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        // Public, unauthenticated probe for load balancers / orchestrators.
        healthRoutes()
        // Public, read-only global catalog for guest mode (browse + practice without an account).
        catalogRoutes()
        // Throttle the unauthenticated auth endpoints per IP.
        rateLimit(AUTH_RATE_LIMIT) {
            authRoutes()
        }
        authenticate(BEARER_AUTH) {
            authenticatedAuthRoutes()
            deckRoutes()
            sessionRoutes()
            homeRoutes()
            imageRoutes()
            adminRoutes()
        }
    }
}
