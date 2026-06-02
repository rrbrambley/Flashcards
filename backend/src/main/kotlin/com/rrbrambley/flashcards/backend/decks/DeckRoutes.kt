package com.rrbrambley.flashcards.backend.decks

import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.userId
import com.rrbrambley.flashcards.shared.api.CreateDeckRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.deckRoutes() {
    route("/decks") {
        get {
            call.respond(DeckRepository.listDecksForUser(call.userId()))
        }
        get("/{id}") {
            val deckId = call.pathLong("id")
            val deck = DeckRepository.getDeck(call.userId(), deckId)
                ?: throw NotFoundException("Deck $deckId not found")
            call.respond(deck)
        }
        post {
            val request = call.receive<CreateDeckRequest>()
            require(request.title.isNotBlank()) { "title must not be blank" }
            call.respond(HttpStatusCode.Created, DeckRepository.createDeck(call.userId(), request))
        }
        put("/{id}") {
            val request = call.receive<CreateDeckRequest>()
            require(request.title.isNotBlank()) { "title must not be blank" }
            call.respond(DeckRepository.updateDeck(call.userId(), call.pathLong("id"), request))
        }
        delete("/{id}") {
            DeckRepository.deleteDeck(call.userId(), call.pathLong("id"))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
