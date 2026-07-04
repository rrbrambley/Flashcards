package com.rrbrambley.flashcards.practice.discussions

import com.rrbrambley.flashcards.shared.api.DiscussionMessageDto
import com.rrbrambley.flashcards.shared.domain.ActionError

/**
 * State of the per-card discussion thread (FLA-122). Messages are oldest-first; one level of replies
 * is grouped by [DiscussionMessageDto.parentMessageId] in the UI. [postedTick] increments on each
 * successful post so the composing field can clear itself.
 */
data class DiscussionUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val messages: List<DiscussionMessageDto> = emptyList(),
    val isLocked: Boolean = false,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    /** Guests can read but must sign in to post (the conversion prompt). */
    val isGuest: Boolean = false,
    val posting: Boolean = false,
    val postError: ActionError? = null,
    /** Whether the guest sign-in/up conversion prompt is showing. */
    val authPrompt: Boolean = false,
    val authSubmitting: Boolean = false,
    val authError: String? = null,
    val postedTick: Int = 0,
    /** Ids of messages this user has reported this session — drives the "Reported" state (FLA-128). */
    val reportedIds: Set<Long> = emptySet(),
    /** Whether the caller can moderate (manage_discussions) — gates the lock/unlock control (FLA-124). */
    val canModerate: Boolean = false,
    /** Whether a lock/unlock toggle is in flight, so the control can disable itself. */
    val togglingLock: Boolean = false,
)
