package com.rrbrambley.flashcards.backend.auth

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
            require(request.username.isNotBlank()) { "username must not be blank" }
            require(request.password.isNotBlank()) { "password must not be blank" }
            val response = AuthService.register(request.username, request.password)
            call.respond(HttpStatusCode.Created, response)
        }
        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = AuthService.login(request.username, request.password)
            call.respond(response)
        }
    }
}
