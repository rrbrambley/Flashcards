package com.rrbrambley.flashcards.backend.plugins

import com.rrbrambley.flashcards.backend.auth.TokenService
import com.rrbrambley.flashcards.backend.auth.UserPrincipal
import com.rrbrambley.flashcards.shared.api.ErrorResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.header
import io.ktor.server.response.respond

const val BEARER_AUTH = "auth-bearer"

fun Application.configureSecurity() {
    val jwt = environment.config.config("jwt")
    TokenService.configure(
        secret = jwt.property("secret").getString(),
        issuer = jwt.property("issuer").getString(),
        audience = jwt.property("audience").getString(),
        accessTtlMillis = jwt.property("accessTtlSeconds").getString().toLong() * 1000,
        refreshTtlMillis = jwt.property("refreshTtlSeconds").getString().toLong() * 1000,
    )

    install(Authentication) {
        // Access tokens are stateless JWTs: validated by signature + exp, no per-request DB lookup.
        jwt(BEARER_AUTH) {
            realm = "flashcards"
            verifier(TokenService.verifier())
            validate { credential ->
                credential.payload.subject?.toLongOrNull()?.let { UserPrincipal(it) }
            }
            challenge { _, realm ->
                // RFC 6750: signal a bearer challenge so clients (Android's Ktor Auth plugin) know
                // to refresh the access token and retry, rather than surfacing a bare 401.
                call.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"$realm\"")
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Missing or invalid access token"),
                )
            }
        }
    }
}
