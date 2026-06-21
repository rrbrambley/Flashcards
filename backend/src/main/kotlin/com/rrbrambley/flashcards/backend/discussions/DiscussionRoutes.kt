package com.rrbrambley.flashcards.backend.discussions

import com.rrbrambley.flashcards.backend.auth.Permission
import com.rrbrambley.flashcards.backend.decks.DeckRepository
import com.rrbrambley.flashcards.backend.routes.pageCursor
import com.rrbrambley.flashcards.backend.routes.pageLimit
import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.pathString
import com.rrbrambley.flashcards.backend.routes.requirePermission
import com.rrbrambley.flashcards.backend.routes.userId
import com.rrbrambley.flashcards.shared.api.CreateMessageRequest
import com.rrbrambley.flashcards.shared.api.LockThreadRequest
import com.rrbrambley.flashcards.shared.api.ToggleDiscussionRequest
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Public, unauthenticated discussion reads (FLA-115) — registered OUTSIDE `authenticate`, like the
 *  catalog. Guests can read; only the deck being global + discussion-enabled exposes anything. */
fun Route.discussionPublicRoutes() {
    route("/discussions") {
        get("/{cardUid}") {
            call.respond(DiscussionRepository.threadMeta(call.pathString("cardUid")))
        }
        get("/{cardUid}/messages") {
            call.respond(
                DiscussionRepository.listMessages(call.pathString("cardUid"), call.pageLimit(), call.pageCursor()),
            )
        }
    }
}

/** Authenticated discussion writes (FLA-115): posting (any signed-in user), and admin lock + the
 *  per-deck discussion toggle (gated on manage-discussions). */
fun Route.discussionAuthedRoutes() {
    route("/discussions") {
        post("/{cardUid}/messages") {
            val request = call.receive<CreateMessageRequest>()
            call.respond(
                DiscussionRepository.createMessage(
                    userId = call.userId(),
                    cardUid = call.pathString("cardUid"),
                    content = request.content,
                    parentMessageId = request.parentMessageId,
                ),
            )
        }
        patch("/{cardUid}/lock") {
            call.requirePermission(Permission.MANAGE_DISCUSSIONS)
            val request = call.receive<LockThreadRequest>()
            call.respond(DiscussionRepository.setLocked(call.pathString("cardUid"), request.locked))
        }
    }
    // Admin: enable/disable discussions on a global deck.
    patch("/decks/{deckId}/discussion") {
        call.requirePermission(Permission.MANAGE_DISCUSSIONS)
        val request = call.receive<ToggleDiscussionRequest>()
        call.respond(DeckRepository.setDiscussionEnabled(call.pathLong("deckId"), request.enabled))
    }
}
