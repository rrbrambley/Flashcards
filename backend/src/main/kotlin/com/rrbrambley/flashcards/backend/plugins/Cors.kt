package com.rrbrambley.flashcards.backend.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Allows the browser web app (a different origin) to call the API. Origins come from
 * `cors.allowedOrigins` (comma-separated, e.g. "http://localhost:5173"); the bearer token
 * is sent as a header (not a cookie), so credentials/cookies aren't needed.
 */
fun Application.configureCors() {
    val origins = environment.config.propertyOrNull("cors.allowedOrigins")?.getString()
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf("http://localhost:5173")

    install(CORS) {
        origins.forEach { origin ->
            val parts = origin.split("://")
            val scheme = parts.getOrElse(0) { "http" }
            val hostPort = parts.getOrElse(1) { "" }
            if (hostPort.isNotEmpty()) {
                allowHost(hostPort, schemes = listOf(scheme))
            }
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
}
