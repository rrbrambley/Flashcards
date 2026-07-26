package com.rrbrambley.flashcards.backend.notifications

import com.rrbrambley.flashcards.backend.routes.pageCursor
import com.rrbrambley.flashcards.backend.routes.pageLimit
import com.rrbrambley.flashcards.backend.routes.pathLong
import com.rrbrambley.flashcards.backend.routes.userId
import com.rrbrambley.flashcards.shared.api.UnreadCountDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * The caller's in-app notifications (#321). All routes are the caller's own — a user only ever reads
 * or marks their own. Notifications are produced internally (no create endpoint). Register inside an
 * `authenticate(BEARER_AUTH)` block.
 */
fun Route.notificationRoutes() {
    route("/notifications") {
        // The caller's notifications, newest first, cursor-paginated (Page<NotificationDto>).
        get {
            call.respond(NotificationRepository.list(call.userId(), call.pageLimit(), call.pageCursor()))
        }
        // Unread count for the badge.
        get("/unread-count") {
            call.respond(UnreadCountDto(NotificationRepository.unreadCount(call.userId())))
        }
        // Mark all read.
        post("/read") {
            NotificationRepository.markAllRead(call.userId())
            call.respond(HttpStatusCode.NoContent)
        }
        // Mark one read (idempotent).
        post("/{id}/read") {
            NotificationRepository.markRead(call.userId(), call.pathLong("id"))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
