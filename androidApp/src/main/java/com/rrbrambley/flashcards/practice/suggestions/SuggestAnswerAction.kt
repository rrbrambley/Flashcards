package com.rrbrambley.flashcards.practice.suggestions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.domain.ActionError

/**
 * The Test-mode "this should be correct" action (FLA-134), shown on a global-deck card after the
 * learner's answer is graded wrong: it suggests their typed answer as an acceptable alternative for an
 * admin to review. Posting needs an account, so a guest tap opens an inline sign-in/up conversion.
 * State is per-card — it resets whenever [cardUid] changes.
 */
@Composable
fun SuggestAnswerAction(
    cardUid: String,
    suggestedAnswer: String,
    isGuest: Boolean,
    modifier: Modifier = Modifier,
    viewModel: SuggestionViewModel = hiltViewModel(),
) {
    LaunchedEffect(cardUid) { viewModel.reset() }
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.submitted) {
            Text(
                text = stringResource(R.string.suggest_submitted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            OutlinedButton(
                onClick = { viewModel.suggest(cardUid, suggestedAnswer, isGuest) },
                enabled = !state.submitting,
            ) {
                Text(
                    stringResource(
                        if (state.submitting) R.string.suggest_submitting else R.string.suggest_action,
                    ),
                )
            }
            state.error?.let { error ->
                Text(
                    text = errorText(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (state.authPrompt) {
        SuggestionAuthPrompt(
            submitting = state.authSubmitting,
            error = state.authError,
            onSubmit = { register, email, password ->
                viewModel.authenticateAndSuggest(register, email, password)
            },
            onDismiss = viewModel::dismissAuthPrompt,
        )
    }
}

/**
 * Guest conversion (FLA-134): register or log in inline; the ViewModel replays the captured suggestion
 * on success before flipping to the signed-in state, so it's attributed to the new user.
 */
@Composable
private fun SuggestionAuthPrompt(
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
        title = { Text(stringResource(R.string.suggest_auth_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        if (register) R.string.suggest_auth_message_register else R.string.suggest_auth_message_login,
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
                    contentPadding = PaddingValues(0.dp),
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
                        if (register) R.string.suggest_auth_register_submit else R.string.suggest_auth_login_submit,
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
private fun errorText(error: ActionError): String = when (error) {
    ActionError.RateLimit -> stringResource(R.string.suggest_error_rate)
    is ActionError.Rejected -> error.message ?: stringResource(R.string.suggest_error_rejected)
    // A suggestion never gets a locked thread; fall back to the generic message.
    ActionError.Locked, ActionError.Generic -> stringResource(R.string.suggest_error_generic)
}
