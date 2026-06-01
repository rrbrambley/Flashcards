package com.rrbrambley.flashcards.backend.auth

import com.rrbrambley.flashcards.backend.error.ServiceUnavailableException
import com.rrbrambley.flashcards.backend.error.UnauthorizedException
import com.rrbrambley.flashcards.shared.api.GoogleAuthRequest
import com.rrbrambley.flashcards.shared.api.LoginRequest
import com.rrbrambley.flashcards.shared.api.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes() {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            require(request.email.contains("@")) { "a valid email is required" }
            require(request.password.isNotBlank()) { "password must not be blank" }
            val response = AuthService.register(request.email.trim(), request.password)
            call.respond(HttpStatusCode.Created, response)
        }
        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = AuthService.login(request.email.trim(), request.password)
            call.respond(response)
        }
        post("/google") {
            if (!GoogleTokenVerifier.isConfigured) {
                throw ServiceUnavailableException("Google sign-in is not configured on this server")
            }
            val request = call.receive<GoogleAuthRequest>()
            val identity = GoogleTokenVerifier.verify(request.idToken)
                ?: throw UnauthorizedException("Invalid Google ID token")
            if (!identity.emailVerified) {
                throw UnauthorizedException("Google account email is not verified")
            }
            call.respond(AuthService.signInWithGoogle(identity.email, identity.sub))
        }
    }
}
