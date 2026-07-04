package com.rrbrambley.flashcards.profile.ui

import com.rrbrambley.flashcards.shared.api.AvatarDto
import com.rrbrambley.flashcards.shared.domain.Monogram

/**
 * State of the profile screen (FLA-166): the curated avatar catalog, the current selection, and the
 * identity used for the monogram fallback. [avatars] is empty when the CDN is unconfigured (the
 * picker then hides, matching the web's graceful degradation).
 */
data class ProfileUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val avatars: List<AvatarDto> = emptyList(),
    /** The selected avatar key, or null when none is set. */
    val selectedAvatarKey: String? = null,
    /** The resolved CDN URL for the selection, or null → the monogram fallback renders. */
    val avatarUrl: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    /** Whether a select/clear PATCH is in flight, so the picker can disable itself. */
    val saving: Boolean = false,
    /** True after a failed select/clear; the UI shows an inline error (copy resolved there). */
    val avatarError: Boolean = false,
    /** Whether avatar selection is enabled for this user — the `avatar_selection` flag (FLA-181). */
    val avatarSelectionEnabled: Boolean = true,
) {
    /** The name used for the monogram fallback + image alt: display name, else the email local-part. */
    val monogramName: String?
        get() = Monogram.name(displayName, email)
}
