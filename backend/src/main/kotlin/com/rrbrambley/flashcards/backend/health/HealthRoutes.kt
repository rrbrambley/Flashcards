package com.rrbrambley.flashcards.backend.health

import com.rrbrambley.flashcards.backend.db.pingDatabase
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val database: String)

/** Public liveness/readiness probe: 200 when the DB is reachable, 503 otherwise. */
fun Route.healthRoutes() {
    get("/health") {
        if (pingDatabase()) {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "ok", database = "up"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse(status = "degraded", database = "down"))
        }
    }
}
