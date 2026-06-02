package com.rrbrambley.flashcards.backend.plugins

import com.rrbrambley.flashcards.backend.auth.UserPrincipal
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level
import java.util.UUID

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        // Correlation id per request (rendered via %X{requestId} in logback), plus the user id
        // when the call is authenticated — so the access log and any error line up.
        mdc("requestId") { UUID.randomUUID().toString() }
        mdc("userId") { call -> call.principal<UserPrincipal>()?.userId?.toString() }
    }
}
