package com.rrbrambley.flashcards.backend.sessions

import com.rrbrambley.flashcards.backend.routes.pageCursor
import com.rrbrambley.flashcards.backend.routes.pageLimit
import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.userId
import com.rrbrambley.flashcards.shared.api.CompleteSessionRequest
import com.rrbrambley.flashcards.shared.api.CreateSessionRequest
import com.rrbrambley.flashcards.shared.api.RecordAnswersRequest
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.sessionRoutes() {
    route("/sessions") {
        // Create = start-or-resume the deck's active session for the requested mode.
        post {
            val request = call.receive<CreateSessionRequest>()
            call.respond(SessionRepository.startOrResume(call.userId(), request.deckId, request.mode, request.shuffle))
        }
        get {
            val activeOnly = call.request.queryParameters["active"]?.toBooleanStrictOrNull() ?: false
            call.respond(
                SessionRepository.listSessions(call.userId(), activeOnly, call.pageLimit(), call.pageCursor()),
            )
        }
        get("/{id}") {
            call.respond(SessionRepository.getSession(call.userId(), call.pathLong("id")))
        }
        // Discard an in-progress session (the home "×" action, FLA-205). Hard delete; cascades answers.
        delete("/{id}") {
            SessionRepository.delete(call.userId(), call.pathLong("id"))
            call.respond(HttpStatusCode.NoContent)
        }
        patch("/{id}") {
            val request = call.receive<UpdateProgressRequest>()
            call.respond(SessionRepository.updateProgress(call.userId(), call.pathLong("id"), request))
        }
        post("/{id}/complete") {
            // Optional body carrying the device tz (FLA-105); tolerate older clients that send none.
            val timeZone = runCatching { call.receiveNullable<CompleteSessionRequest>()?.timeZone }.getOrNull()
            call.respond(SessionRepository.complete(call.userId(), call.pathLong("id"), timeZone))
        }
        // Append to the session's answer log (FLA-99); returns the session with recomputed counts.
        post("/{id}/answers") {
            val request = call.receive<RecordAnswersRequest>()
            call.respond(SessionRepository.recordAnswers(call.userId(), call.pathLong("id"), request))
        }
        // The session's answer log, in play order — for an end-of-session review.
        get("/{id}/answers") {
            call.respond(SessionRepository.listAnswers(call.userId(), call.pathLong("id")))
        }
    }
}
