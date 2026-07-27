package com.rrbrambley.flashcards.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.shared.domain.Notification
import com.rrbrambley.flashcards.shared.domain.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the notifications screen shows (#321): loading, then the (possibly empty) cached list. */
sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState

    data class Loaded(val notifications: List<Notification>) : NotificationsUiState
}

/**
 * Backs the notifications center + the top-bar badge (#321), reading the offline-first shared
 * [NotificationRepository]. Also resolves the `notifications` feature flag (fail-open, like the
 * practice-mode flags) so the bell shows unless it's been explicitly turned off.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
    featureFlagRepository: FeatureFlagRepository,
) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> = repository.observeNotifications()
        .map<List<Notification>, NotificationsUiState> { NotificationsUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState.Loading)

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // Fail-open: default true until the flag resolves, and stays visible unless explicitly off.
    val notificationsEnabled: StateFlow<Boolean> = flow {
        emit(featureFlagRepository.flags()[FeatureFlags.NOTIFICATIONS] != false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun markRead(id: Long) {
        viewModelScope.launch { runCatching { repository.markRead(id) } }
    }

    fun markAllRead() {
        viewModelScope.launch { runCatching { repository.markAllRead() } }
    }
}
