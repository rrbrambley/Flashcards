package com.rrbrambley.flashcards.backend.plugins

import com.rrbrambley.flashcards.backend.admin.adminRoutes
import com.rrbrambley.flashcards.backend.auth.authRoutes
import com.rrbrambley.flashcards.backend.auth.authenticatedAuthRoutes
import com.rrbrambley.flashcards.backend.auth.avatarRoutes
import com.rrbrambley.flashcards.backend.decks.catalogRoutes
import com.rrbrambley.flashcards.backend.decks.deckRoutes
import com.rrbrambley.flashcards.backend.discussions.discussionAuthedRoutes
import com.rrbrambley.flashcards.backend.discussions.discussionPublicRoutes
import com.rrbrambley.flashcards.backend.health.healthRoutes
import com.rrbrambley.flashcards.backend.home.homeRoutes
import com.rrbrambley.flashcards.backend.images.imageRoutes
import com.rrbrambley.flashcards.backend.sessions.sessionRoutes
import com.rrbrambley.flashcards.backend.streaks.streakRoutes
import com.rrbrambley.flashcards.backend.suggestions.suggestionRoutes
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
        // Public, read-only discussion reads (guests can read; posting is authenticated).
        discussionPublicRoutes()
        // Throttle the unauthenticated auth endpoints per IP.
        rateLimit(AUTH_RATE_LIMIT) {
            authRoutes()
        }
        authenticate(BEARER_AUTH) {
            authenticatedAuthRoutes()
            avatarRoutes()
            deckRoutes()
            sessionRoutes()
            streakRoutes()
            homeRoutes()
            imageRoutes()
            adminRoutes()
            discussionAuthedRoutes()
            suggestionRoutes()
        }
    }
}
