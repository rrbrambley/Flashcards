package com.rrbrambley.flashcards.profile

import com.rrbrambley.flashcards.shared.api.AvatarDto
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.MeResponse
import com.rrbrambley.flashcards.shared.api.UpdateProfileRequest

/**
 * The current user's profile + avatar catalog (FLA-166) over the shared [FlashcardApiClient].
 * Online-only (not cached in Room), mirroring [com.rrbrambley.flashcards.practice.discussions.DiscussionRepository].
 * An interface so [com.rrbrambley.flashcards.profile.ui.ProfileViewModel] can be unit-tested with a fake.
 */
interface ProfileRepository {
    suspend fun me(): MeResponse
    suspend fun avatars(): List<AvatarDto>

    /** Sets the caller's avatar to [key], or clears it when [key] is blank (backend merge semantics). */
    suspend fun updateAvatar(key: String): MeResponse
}

class ProfileRepositoryImpl(private val apiClient: FlashcardApiClient) : ProfileRepository {
    override suspend fun me(): MeResponse = apiClient.getMe()

    override suspend fun avatars(): List<AvatarDto> = apiClient.getAvatars()

    override suspend fun updateAvatar(key: String): MeResponse =
        apiClient.updateProfile(UpdateProfileRequest(avatarKey = key))
}
