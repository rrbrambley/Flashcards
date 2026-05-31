package com.rrbrambley.flashcards.backend.routes

import com.rrbrambley.flashcards.backend.auth.UserPrincipal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

/** The authenticated user id; non-null inside an `authenticate("auth-bearer")` block. */
fun ApplicationCall.userId(): Long =
    principal<UserPrincipal>()!!.userId

/** Reads a required Long path parameter, throwing 400 on a missing/invalid value. */
fun ApplicationCall.pathLong(name: String): Long =
    parameters[name]?.toLongOrNull()
        ?: throw IllegalArgumentException("Invalid '$name' path parameter")
