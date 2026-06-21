package com.rrbrambley.flashcards.backend.discussions

import kotlinx.serialization.Serializable

/**
 * Moderation-queue DTOs (FLA-118). Like the admin RBAC DTOs, these are a backend⇄web contract — the
 * web hand-mirrors them in TypeScript; they are not part of the cross-platform client SDK (mobile
 * only gets the user-facing report endpoint).
 */

/** One open report in the moderation queue: the report + the reported message + who reported it. */
@Serializable
data class ReportedMessageDto(
    val reportId: Long,
    val reason: String?,
    val status: String,
    val reportedAtMillis: Long,
    val reporterDisplayName: String,
    val messageId: Long,
    val cardUid: String,
    val authorDisplayName: String,
    val content: String,
    val deleted: Boolean,
    val messageCreatedAtMillis: Long,
)

/** Body of `PATCH /admin/discussions/reports/{reportId}` — the new status (e.g. "dismissed"). */
@Serializable
data class UpdateReportRequest(val status: String)
