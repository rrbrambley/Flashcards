package com.rrbrambley.flashcards.practice.discussions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.api.DiscussionMessageDto

/** The 💬 control each mode shows once a card's answer is revealed, opening its discussion (FLA-122). */
@Composable
fun DiscussButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(stringResource(R.string.discuss_button))
    }
}

/**
 * The per-card discussion thread as a modal bottom sheet (FLA-122): paginated messages with one level
 * of replies, a post box, and a guest sign-in/up conversion. Reads are public; posting needs auth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionSheet(
    cardUid: String,
    isGuest: Boolean,
    onDismiss: () -> Unit,
    viewModel: DiscussionViewModel = hiltViewModel(),
) {
    LaunchedEffect(cardUid, isGuest) { viewModel.load(cardUid, isGuest) }
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var input by remember(cardUid) { mutableStateOf("") }
    var replyTo by remember(cardUid) { mutableStateOf<DiscussionMessageDto?>(null) }

    // Clear the composing field once a post lands (direct or after the guest conversion).
    LaunchedEffect(state.postedTick) {
        if (state.postedTick > 0) {
            input = ""
            replyTo = null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.discussion_title),
                style = MaterialTheme.typography.titleLarge,
            )

            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                state.loadFailed -> Text(
                    text = stringResource(R.string.discussion_load_error),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> MessageList(
                    state = state,
                    locked = state.isLocked,
                    onReply = { replyTo = it },
                    onLoadMore = viewModel::loadMore,
                )
            }

            if (!state.loading && !state.loadFailed) {
                if (state.isLocked) {
                    Text(
                        text = stringResource(R.string.discussion_locked),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    PostForm(
                        input = input,
                        onInputChange = {
                            input = it
                            viewModel.clearPostError()
                        },
                        replyTo = replyTo,
                        onCancelReply = { replyTo = null },
                        isGuest = state.isGuest,
                        posting = state.posting,
                        postError = state.postError,
                        onPost = { viewModel.post(input, replyTo?.id) },
                    )
                }
            }
        }
    }

    if (state.authPrompt) {
        DiscussionAuthPrompt(
            submitting = state.authSubmitting,
            error = state.authError,
            onSubmit = { register, email, password -> viewModel.authenticateAndPost(register, email, password) },
            onDismiss = viewModel::dismissAuthPrompt,
        )
    }
}

@Composable
private fun MessageList(
    state: DiscussionUiState,
    locked: Boolean,
    onReply: (DiscussionMessageDto) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (state.messages.isEmpty()) {
        Text(
            text = stringResource(R.string.discussion_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val topLevel = state.messages.filter { it.parentMessageId == null }
    val repliesByParent = state.messages
        .filter { it.parentMessageId != null }
        .groupBy { it.parentMessageId }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(topLevel, key = { it.id }) { message ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MessageItem(message)
                if (!locked) {
                    TextButton(
                        onClick = { onReply(message) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.discussion_reply))
                    }
                }
                repliesByParent[message.id]?.forEach { reply ->
                    MessageItem(reply, modifier = Modifier.padding(start = 20.dp))
                }
            }
        }
        if (state.hasMore) {
            item {
                TextButton(onClick = onLoadMore, enabled = !state.loadingMore) {
                    Text(stringResource(R.string.discussion_load_more))
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: DiscussionMessageDto, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.authorDisplayName,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = relativeTime(message.createdAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = message.content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PostForm(
    input: String,
    onInputChange: (String) -> Unit,
    replyTo: DiscussionMessageDto?,
    onCancelReply: () -> Unit,
    isGuest: Boolean,
    posting: Boolean,
    postError: DiscussionPostError?,
    onPost: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (replyTo != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.discussion_replying_to, replyTo.authorDisplayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancelReply) {
                    Text(stringResource(R.string.discussion_cancel))
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
            placeholder = {
                Text(
                    stringResource(
                        if (isGuest) R.string.discussion_input_hint_guest else R.string.discussion_input_hint,
                    ),
                )
            },
        )
        postError?.let {
            Text(
                text = postErrorText(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(
            onClick = onPost,
            enabled = !posting && input.isNotBlank(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                stringResource(
                    when {
                        posting -> R.string.discussion_posting
                        isGuest -> R.string.discussion_post_guest
                        else -> R.string.discussion_post
                    },
                ),
            )
        }
    }
}

/**
 * Guest conversion (FLA-122): register or log in inline; the ViewModel posts the captured message on
 * success before flipping to the signed-in state, so the message is saved as the new user.
 */
@Composable
private fun DiscussionAuthPrompt(
    submitting: Boolean,
    error: String?,
    onSubmit: (register: Boolean, email: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var register by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.discussion_auth_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        if (register) {
                            R.string.discussion_auth_message_register
                        } else {
                            R.string.discussion_auth_message_login
                        },
                    ),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.auth_email_label)) },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.auth_password_label)) },
                    singleLine = true,
                    enabled = !submitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(
                    onClick = { register = !register },
                    enabled = !submitting,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        stringResource(
                            if (register) {
                                R.string.discussion_auth_switch_to_login
                            } else {
                                R.string.discussion_auth_switch_to_register
                            },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(register, email.trim(), password) },
                enabled = !submitting && email.isNotBlank() && password.isNotBlank(),
            ) {
                Text(
                    stringResource(
                        if (register) {
                            R.string.discussion_auth_register_submit
                        } else {
                            R.string.discussion_auth_login_submit
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(stringResource(R.string.discussion_cancel))
            }
        },
    )
}

@Composable
private fun postErrorText(error: DiscussionPostError): String = when (error) {
    DiscussionPostError.RateLimit -> stringResource(R.string.discussion_post_error_rate)
    DiscussionPostError.Locked -> stringResource(R.string.discussion_post_error_locked)
    is DiscussionPostError.Rejected ->
        error.message ?: stringResource(R.string.discussion_post_error_rejected)
    DiscussionPostError.Generic -> stringResource(R.string.discussion_post_error_generic)
}

/** Compact relative time: "just now", "5m", "3h", "2d", else an absolute date. */
@Composable
private fun relativeTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val minutes = (diff / 60_000).toInt()
    val hours = minutes / 60
    val days = hours / 24
    return when {
        diff < 60_000 -> stringResource(R.string.discussion_time_just_now)
        minutes < 60 -> stringResource(R.string.discussion_time_minutes, minutes)
        hours < 24 -> stringResource(R.string.discussion_time_hours, hours)
        days < 7 -> stringResource(R.string.discussion_time_days, days)
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))
    }
}
