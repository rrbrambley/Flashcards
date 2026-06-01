package com.rrbrambley.flashcards.backend.plugins

import com.rrbrambley.flashcards.backend.error.ConflictException
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.error.PayloadTooLargeException
import com.rrbrambley.flashcards.backend.error.ServiceUnavailableException
import com.rrbrambley.flashcards.backend.error.UnauthorizedException
import com.rrbrambley.flashcards.backend.error.UnsupportedMediaTypeException
import com.rrbrambley.flashcards.shared.api.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", cause.message))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("conflict", cause.message))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", cause.message))
        }
        exception<ServiceUnavailableException> { call, cause ->
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("unavailable", cause.message))
        }
        exception<PayloadTooLargeException> { call, cause ->
            call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", cause.message))
        }
        exception<UnsupportedMediaTypeException> { call, cause ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("unsupported_media_type", cause.message))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message))
        }
        exception<Throwable> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error"))
        }
    }
}
