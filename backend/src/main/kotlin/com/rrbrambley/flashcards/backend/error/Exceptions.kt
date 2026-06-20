package com.rrbrambley.flashcards.backend.error

/** Mapped to 404 by StatusPages. */
class NotFoundException(message: String) : RuntimeException(message)

/** Mapped to 409 by StatusPages (e.g. username already taken). */
class ConflictException(message: String) : RuntimeException(message)

/** Mapped to 401 by StatusPages (e.g. bad login credentials). */
class UnauthorizedException(message: String) : RuntimeException(message)

/** Mapped to 403 by StatusPages: the caller is authenticated but lacks a required permission. */
class ForbiddenException(message: String) : RuntimeException(message)

/** Mapped to 503 by StatusPages (e.g. Google auth not configured on the server). */
class ServiceUnavailableException(message: String) : RuntimeException(message)

/** Mapped to 413 by StatusPages (upload exceeds the size limit). */
class PayloadTooLargeException(message: String) : RuntimeException(message)

/** Mapped to 415 by StatusPages (unsupported image content type). */
class UnsupportedMediaTypeException(message: String) : RuntimeException(message)

/** Mapped to 429 by StatusPages (per-user rate limit exceeded, e.g. posting discussion messages). */
class TooManyRequestsException(message: String) : RuntimeException(message)
