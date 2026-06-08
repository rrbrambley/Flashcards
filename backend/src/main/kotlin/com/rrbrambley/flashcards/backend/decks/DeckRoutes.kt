package com.rrbrambley.flashcards.backend.decks

import com.rrbrambley.flashcards.backend.auth.Permission
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.routes.hasPermission
import com.rrbrambley.flashcards.backend.routes.pageCursor
import com.rrbrambley.flashcards.backend.routes.pageLimit
import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.requirePermission
import com.rrbrambley.flashcards.backend.routes.userId
import com.rrbrambley.flashcards.backend.validation.Validation
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
            val canManageGlobal = call.hasPermission(Permission.MANAGE_GLOBAL_DECKS)
            call.respond(
                DeckRepository.listDecksForUser(call.userId(), canManageGlobal, call.pageLimit(), call.pageCursor()),
            )
        }
        get("/{id}") {
            val deckId = call.pathLong("id")
            val canManageGlobal = call.hasPermission(Permission.MANAGE_GLOBAL_DECKS)
            val deck = DeckRepository.getDeck(call.userId(), canManageGlobal, deckId)
                ?: throw NotFoundException("Deck $deckId not found")
            call.respond(deck)
        }
        post {
            val request = call.receive<CreateDeckRequest>().let { it.copy(title = it.title.trim()) }
            Validation.validateDeck(request)
            call.respond(HttpStatusCode.Created, DeckRepository.createDeck(call.userId(), request))
        }
        // The global (ownerless) catalog — admins only (the web "Manage global decks" view).
        get("/global") {
            call.requirePermission(Permission.MANAGE_GLOBAL_DECKS)
            call.respond(DeckRepository.listGlobalDecks(call.userId(), call.pageLimit(), call.pageCursor()))
        }
        // Create a global (ownerless) catalog deck — admins only.
        post("/global") {
            call.requirePermission(Permission.MANAGE_GLOBAL_DECKS)
            val request = call.receive<CreateDeckRequest>().let { it.copy(title = it.title.trim()) }
            Validation.validateDeck(request)
            call.respond(HttpStatusCode.Created, DeckRepository.createGlobalDeck(request))
        }
        put("/{id}") {
            val request = call.receive<CreateDeckRequest>().let { it.copy(title = it.title.trim()) }
            Validation.validateDeck(request)
            val canManageGlobal = call.hasPermission(Permission.MANAGE_GLOBAL_DECKS)
            call.respond(DeckRepository.updateDeck(call.userId(), call.pathLong("id"), request, canManageGlobal))
        }
        delete("/{id}") {
            val canManageGlobal = call.hasPermission(Permission.MANAGE_GLOBAL_DECKS)
            DeckRepository.deleteDeck(call.userId(), call.pathLong("id"), canManageGlobal)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
