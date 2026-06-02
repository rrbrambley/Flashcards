package com.rrbrambley.flashcards.backend.auth

import com.rrbrambley.flashcards.backend.error.ServiceUnavailableException
import com.rrbrambley.flashcards.backend.error.UnauthorizedException
import com.rrbrambley.flashcards.backend.validation.Validation
import com.rrbrambley.flashcards.shared.api.GoogleAuthRequest
import com.rrbrambley.flashcards.shared.api.LoginRequest
import com.rrbrambley.flashcards.shared.api.LogoutRequest
import com.rrbrambley.flashcards.shared.api.RefreshRequest
import com.rrbrambley.flashcards.shared.api.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes() {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val email = request.email.trim()
            Validation.validateEmail(email)
            Validation.validatePassword(request.password)
            val response = AuthService.register(email, request.password)
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
        // Public: an expired access token can't authenticate, so refresh is exchanged by token alone.
        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            call.respond(AuthService.refresh(request.refreshToken))
        }
    }
}

/** Authenticated: revokes the caller's refresh token so the session can't be refreshed. */
fun Route.logoutRoute() {
    route("/auth") {
        post("/logout") {
            val userId = call.principal<UserPrincipal>()!!.userId
            val request = call.receive<LogoutRequest>()
            AuthService.revokeRefreshToken(request.refreshToken, userId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
