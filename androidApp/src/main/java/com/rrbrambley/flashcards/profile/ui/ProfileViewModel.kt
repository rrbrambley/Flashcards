package com.rrbrambley.flashcards.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.profile.ProfileRepository
import com.rrbrambley.flashcards.shared.api.ApiError
import com.rrbrambley.flashcards.shared.api.AvatarDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the profile screen (FLA-166): loads the current profile + avatar catalog, and picks/clears
 * the avatar via `PATCH /auth/me` (a select saves immediately, mirroring the web). The catalog fetch
 * degrades to empty (picker hidden) if the CDN is unconfigured, without failing the whole screen.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val featureFlagRepository: FeatureFlagRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val me = repository.me()
                // A missing/empty catalog (no CDN) must not fail the screen — just hides the picker.
                val avatars = runCatching { repository.avatars() }.getOrDefault(emptyList<AvatarDto>())
                val selectionEnabled = featureFlagRepository.isEnabled(FeatureFlags.AVATAR_SELECTION)
                _uiState.update {
                    it.copy(
                        loading = false,
                        avatars = avatars,
                        selectedAvatarKey = me.avatarKey,
                        avatarUrl = me.avatarUrl,
                        displayName = me.displayName,
                        email = me.email,
                        avatarSelectionEnabled = selectionEnabled,
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    /** Select an avatar by [key]; a blank key clears it (backend merge semantics). */
    fun selectAvatar(key: String) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true, avatarError = false) }
        viewModelScope.launch {
            try {
                val me = repository.updateAvatar(key)
                _uiState.update {
                    it.copy(
                        saving = false,
                        selectedAvatarKey = me.avatarKey,
                        avatarUrl = me.avatarUrl,
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(saving = false, avatarError = true) }
            }
        }
    }

    /** Clear the avatar (falls back to the initials monogram). */
    fun clearAvatar() = selectAvatar("")
}
