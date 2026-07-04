package com.rrbrambley.flashcards.shared.domain

import com.rrbrambley.flashcards.shared.api.ApiError

/**
 * Why a write action (post a discussion message, suggest an answer) failed, resolved from the shared
 * [ApiError] so every platform branches the same way instead of showing one generic string (FLA-196).
 * Each platform maps a case to its own localized copy; [Locked] is discussion-specific (a suggestion
 * never sees it, and its UI just falls back to the generic message).
 */
sealed class ActionError {
    /** Over the per-user rate limit (429). */
    data object RateLimit : ActionError()

    /** The discussion thread is locked (403). */
    data object Locked : ActionError()

    /** Rejected by the backend with a message (400 / validation / conflict). */
    data class Rejected(val message: String?) : ActionError()

    /** Anything else (network, 5xx, …). */
    data object Generic : ActionError()

    companion object {
        /** Maps a typed [ApiError] to an [ActionError]. */
        fun from(error: ApiError): ActionError = when (error) {
            is ApiError.Validation -> Rejected(error.message)
            is ApiError.Conflict -> Rejected(error.message)
            is ApiError.Client -> when (error.status) {
                429 -> RateLimit
                403 -> Locked
                400 -> Rejected(error.message)
                else -> Generic
            }
            else -> Generic
        }

        /** Maps any caught [Throwable]; a non-[ApiError] (transport) failure falls back to [Generic]. */
        fun fromThrowable(error: Throwable): ActionError = (error as? ApiError)?.let { from(it) } ?: Generic
    }
}
