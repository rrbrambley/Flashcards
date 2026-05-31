package com.rrbrambley.flashcards.backend.decks

import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.userId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
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
    }
}
