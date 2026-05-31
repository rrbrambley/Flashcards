package com.rrbrambley.flashcards.backend.home

import com.rrbrambley.flashcards.backend.routes.userId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.homeRoutes() {
    get("/home") {
        call.respond(HomeService.homeFeed(call.userId()))
    }
}
