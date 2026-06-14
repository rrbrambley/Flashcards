package com.rrbrambley.flashcards.backend.decks

import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.routes.pageCursor
import com.rrbrambley.flashcards.backend.routes.pageLimit
import com.rrbrambley.flashcards.backend.routes.pathLong
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * The **public** (unauthenticated) read-only global catalog — guest mode (FLA-101). Registered
 * OUTSIDE `authenticate(BEARER_AUTH)`, so these handlers must never call `call.userId()`. Only
 * ownerless catalog decks are ever exposed; a user-owned deck id yields 404.
 */
fun Route.catalogRoutes() {
    route("/catalog") {
        get {
            call.respond(DeckRepository.listCatalogDecks(call.pageLimit(), call.pageCursor()))
        }
        get("/{id}") {
            val deckId = call.pathLong("id")
            val deck = DeckRepository.getCatalogDeck(deckId)
                ?: throw NotFoundException("Deck $deckId not found")
            call.respond(deck)
        }
    }
}
