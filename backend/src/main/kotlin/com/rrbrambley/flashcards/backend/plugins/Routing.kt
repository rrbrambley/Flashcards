package com.rrbrambley.flashcards.backend.plugins

import com.rrbrambley.flashcards.backend.auth.authRoutes
import com.rrbrambley.flashcards.backend.auth.logoutRoute
import com.rrbrambley.flashcards.backend.decks.deckRoutes
import com.rrbrambley.flashcards.backend.home.homeRoutes
import com.rrbrambley.flashcards.backend.images.imageRoutes
import com.rrbrambley.flashcards.backend.sessions.sessionRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        authRoutes()
        authenticate(BEARER_AUTH) {
            logoutRoute()
            deckRoutes()
            sessionRoutes()
            homeRoutes()
            imageRoutes()
        }
    }
}
