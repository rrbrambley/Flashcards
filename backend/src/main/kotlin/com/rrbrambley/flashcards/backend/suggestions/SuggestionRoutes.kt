package com.rrbrambley.flashcards.backend.suggestions

import com.rrbrambley.flashcards.backend.auth.Permission
import com.rrbrambley.flashcards.backend.routes.pageCursor
import com.rrbrambley.flashcards.backend.routes.pageLimit
import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.pathString
import com.rrbrambley.flashcards.backend.routes.requirePermission
import com.rrbrambley.flashcards.backend.routes.userId
import com.rrbrambley.flashcards.backend.validation.Validation
import com.rrbrambley.flashcards.shared.api.SuggestAnswerRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Answer-suggestion routes (FLA-130), all authenticated. Submitting is open to any signed-in user;
 * the review queue + accept/dismiss are gated on [Permission.MANAGE_SUGGESTIONS].
 */
fun Route.suggestionRoutes() {
    // Suggest an alternative answer for a card ("this should be correct").
    post("/cards/{cardUid}/answer-suggestions") {
        val request = call.receive<SuggestAnswerRequest>()
        val answer = Validation.normalizeAnswerSuggestion(request.suggestedAnswer)
        SuggestionRepository.suggest(call.userId(), call.pathString("cardUid"), answer)
        call.respond(HttpStatusCode.NoContent)
    }

    route("/admin/answer-suggestions") {
        get {
            call.requirePermission(Permission.MANAGE_SUGGESTIONS)
            call.respond(SuggestionRepository.listOpen(call.pageLimit(), call.pageCursor()))
        }
        post("/{id}/accept") {
            call.requirePermission(Permission.MANAGE_SUGGESTIONS)
            SuggestionRepository.accept(call.userId(), call.pathLong("id"))
            call.respond(HttpStatusCode.NoContent)
        }
        post("/{id}/dismiss") {
            call.requirePermission(Permission.MANAGE_SUGGESTIONS)
            SuggestionRepository.dismiss(call.userId(), call.pathLong("id"))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
