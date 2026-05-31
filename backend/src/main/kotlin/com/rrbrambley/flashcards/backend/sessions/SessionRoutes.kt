package com.rrbrambley.flashcards.backend.sessions

import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.userId
import com.rrbrambley.flashcards.shared.api.CreateSessionRequest
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.sessionRoutes() {
    route("/sessions") {
        // Create = start-or-resume the deck's active session.
        post {
            val request = call.receive<CreateSessionRequest>()
            call.respond(SessionRepository.startOrResume(call.userId(), request.deckId))
        }
        get {
            val activeOnly = call.request.queryParameters["active"]?.toBooleanStrictOrNull() ?: false
            call.respond(SessionRepository.listSessions(call.userId(), activeOnly))
        }
        get("/{id}") {
            call.respond(SessionRepository.getSession(call.userId(), call.pathLong("id")))
        }
        patch("/{id}") {
            val request = call.receive<UpdateProgressRequest>()
            call.respond(SessionRepository.updateProgress(call.userId(), call.pathLong("id"), request))
        }
        post("/{id}/complete") {
            call.respond(SessionRepository.complete(call.userId(), call.pathLong("id")))
        }
    }
}
