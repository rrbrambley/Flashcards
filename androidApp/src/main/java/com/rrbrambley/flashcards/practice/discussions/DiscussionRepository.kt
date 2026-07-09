package com.rrbrambley.flashcards.practice.discussions

import com.rrbrambley.flashcards.shared.api.DiscussionMessageDto
import com.rrbrambley.flashcards.shared.api.DiscussionThreadDto
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.Page

/**
 * Card discussions (FLA-122) over the shared [FlashcardApiClient]. Online-only — discussions are not
 * cached in Room (mirroring the web, which fetches them directly). An interface so the ViewModel can
 * be unit-tested with a hand-written fake.
 */
interface DiscussionRepository {
    suspend fun thread(cardUid: String): DiscussionThreadDto
    suspend fun messages(cardUid: String, cursor: String?): Page<DiscussionMessageDto>
    suspend fun post(cardUid: String, content: String, parentMessageId: Long?): DiscussionMessageDto
    suspend fun report(messageId: Long, reason: String?)
    suspend fun setLocked(cardUid: String, locked: Boolean): DiscussionThreadDto
}

class DiscussionRepositoryImpl(private val apiClient: FlashcardApiClient) : DiscussionRepository {
    override suspend fun thread(cardUid: String): DiscussionThreadDto = apiClient.getDiscussionThread(cardUid)

    override suspend fun messages(cardUid: String, cursor: String?): Page<DiscussionMessageDto> =
        apiClient.getDiscussionMessages(cardUid, cursor = cursor)

    override suspend fun post(cardUid: String, content: String, parentMessageId: Long?): DiscussionMessageDto =
        apiClient.postDiscussionMessage(cardUid, content, parentMessageId)

    override suspend fun report(messageId: Long, reason: String?) = apiClient.reportMessage(messageId, reason)

    override suspend fun setLocked(cardUid: String, locked: Boolean): DiscussionThreadDto =
        apiClient.lockThread(cardUid, locked)
}
