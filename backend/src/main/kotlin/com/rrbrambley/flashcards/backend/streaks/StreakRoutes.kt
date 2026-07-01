package com.rrbrambley.flashcards.backend.streaks

import com.rrbrambley.flashcards.backend.routes.userId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.streakRoutes() {
    // The optional `tz` (IANA) anchors "today" and buckets completions lacking a stored zone.
    get("/streaks") {
        call.respond(StreakService.streaks(call.userId(), call.request.queryParameters["tz"]))
    }
    // Days of `month` (YYYY-MM) the user completed a session — for the activity calendar (FLA-170).
    get("/streaks/calendar") {
        val month = call.request.queryParameters["month"]
            ?: throw IllegalArgumentException("Missing 'month' query parameter (YYYY-MM)")
        call.respond(StreakService.calendar(call.userId(), month, call.request.queryParameters["tz"]))
    }
}
