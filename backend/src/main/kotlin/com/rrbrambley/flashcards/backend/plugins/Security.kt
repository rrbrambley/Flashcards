package com.rrbrambley.flashcards.backend.plugins

import com.rrbrambley.flashcards.backend.auth.AuthService
import com.rrbrambley.flashcards.backend.auth.UserPrincipal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer

const val BEARER_AUTH = "auth-bearer"

fun Application.configureSecurity() {
    install(Authentication) {
        bearer(BEARER_AUTH) {
            authenticate { credential ->
                AuthService.resolveUser(credential.token)?.let { UserPrincipal(it) }
            }
        }
    }
}
