package com.rrbrambley.flashcards.backend.error

/** Mapped to 404 by StatusPages. */
class NotFoundException(message: String) : RuntimeException(message)

/** Mapped to 409 by StatusPages (e.g. username already taken). */
class ConflictException(message: String) : RuntimeException(message)

/** Mapped to 401 by StatusPages (e.g. bad login credentials). */
class UnauthorizedException(message: String) : RuntimeException(message)
