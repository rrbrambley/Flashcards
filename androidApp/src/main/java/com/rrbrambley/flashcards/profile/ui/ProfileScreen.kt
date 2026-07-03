package com.rrbrambley.flashcards.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.api.AvatarDto
import com.rrbrambley.flashcards.ui.Avatar

/**
 * The profile screen body (FLA-166): a large preview of the current avatar + the picker grid from
 * `GET /avatars`. Tapping an option saves it immediately; "Remove avatar" clears it. The picker is
 * hidden (with a note) when the catalog is empty — no CDN configured.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.loading) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState.loadFailed) {
            Text(
                text = stringResource(R.string.profile_load_failed),
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = viewModel::load) {
                Text(stringResource(R.string.profile_retry))
            }
            return@Column
        }

        // Avatar selection can be turned off via the `avatar_selection` feature flag (FLA-181).
        if (!uiState.avatarSelectionEnabled) {
            Text(
                text = stringResource(R.string.profile_avatar_selection_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            text = stringResource(R.string.profile_avatar_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(url = uiState.avatarUrl, name = uiState.monogramName, size = 72.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (uiState.avatars.isNotEmpty()) {
                        stringResource(R.string.profile_avatar_hint)
                    } else {
                        stringResource(R.string.profile_avatar_unavailable)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.selectedAvatarKey != null) {
                    TextButton(
                        onClick = viewModel::clearAvatar,
                        enabled = !uiState.saving,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.profile_remove_avatar))
                    }
                }
            }
        }

        if (uiState.avatars.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.avatars.forEach { option ->
                    AvatarOption(
                        option = option,
                        selected = option.key == uiState.selectedAvatarKey,
                        enabled = !uiState.saving,
                        onSelect = { viewModel.selectAvatar(option.key) },
                    )
                }
            }
        }

        if (uiState.avatarError) {
            Text(
                text = stringResource(R.string.profile_avatar_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** One tappable avatar in the picker grid, ringed when it's the current selection. */
@Composable
private fun AvatarOption(
    option: AvatarDto,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val ring = if (selected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .then(ring)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(3.dp),
    ) {
        Avatar(
            url = option.url,
            name = option.key,
            size = 56.dp,
            contentDescription = stringResource(R.string.profile_cd_avatar_option, option.key),
        )
    }
}
