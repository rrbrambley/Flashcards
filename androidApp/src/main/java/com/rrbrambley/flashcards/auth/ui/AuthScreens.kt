package com.rrbrambley.flashcards.auth.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialException
import com.rrbrambley.flashcards.BuildConfig
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.auth.AuthFormState
import com.rrbrambley.flashcards.auth.AuthViewModel
import com.rrbrambley.flashcards.auth.GoogleSignIn
import kotlinx.coroutines.launch

private enum class AuthScreen { Login, Register }

@Composable
fun AuthHost(
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    var screen by rememberSaveable { mutableStateOf(AuthScreen.Login) }
    val form by authViewModel.formState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notConfiguredMessage = stringResource(R.string.auth_google_not_configured)
    val cancelledMessage = stringResource(R.string.auth_google_cancelled)
    val failedMessage = stringResource(R.string.auth_google_failed)
    val onGoogle: () -> Unit = onGoogle@{
        val activity = context as? Activity
        if (activity == null || BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            authViewModel.onGoogleError(notConfiguredMessage)
            return@onGoogle
        }
        scope.launch {
            try {
                val idToken = GoogleSignIn.getIdToken(activity, BuildConfig.GOOGLE_WEB_CLIENT_ID)
                authViewModel.onGoogleIdToken(idToken)
            } catch (e: GetCredentialException) {
                authViewModel.onGoogleError(cancelledMessage)
            } catch (e: Exception) {
                authViewModel.onGoogleError(failedMessage)
            }
        }
    }

    when (screen) {
        AuthScreen.Login -> AuthForm(
            title = stringResource(R.string.auth_login_title),
            submitLabel = stringResource(R.string.action_log_in),
            googleLabel = stringResource(R.string.auth_login_google),
            switchPrompt = stringResource(R.string.auth_login_switch_prompt),
            switchAction = stringResource(R.string.auth_login_switch_action),
            form = form,
            onEmailChange = authViewModel::onEmailChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onSubmit = authViewModel::login,
            onGoogle = onGoogle,
            onSwitch = {
                authViewModel.resetForm()
                screen = AuthScreen.Register
            },
            modifier = modifier,
        )

        AuthScreen.Register -> AuthForm(
            title = stringResource(R.string.auth_register_title),
            submitLabel = stringResource(R.string.auth_register_submit),
            googleLabel = stringResource(R.string.auth_register_google),
            switchPrompt = stringResource(R.string.auth_register_switch_prompt),
            switchAction = stringResource(R.string.action_log_in),
            form = form,
            onEmailChange = authViewModel::onEmailChange,
            onPasswordChange = authViewModel::onPasswordChange,
            onSubmit = authViewModel::register,
            onGoogle = onGoogle,
            onSwitch = {
                authViewModel.resetForm()
                screen = AuthScreen.Login
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun AuthForm(
    title: String,
    submitLabel: String,
    googleLabel: String,
    switchPrompt: String,
    switchAction: String,
    form: AuthFormState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogle: () -> Unit,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        OutlinedTextField(
            value = form.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.auth_email_label)) },
            singleLine = true,
            enabled = !form.isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.auth_password_label)) },
            singleLine = true,
            enabled = !form.isSubmitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.errorMessage != null) {
            Text(
                text = form.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !form.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (form.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(submitLabel)
            }
        }

        OrDivider()

        OutlinedButton(
            onClick = onGoogle,
            enabled = !form.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(googleLabel)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(switchPrompt, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onSwitch, enabled = !form.isSubmitting) {
                Text(switchAction)
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(stringResource(R.string.auth_divider_or), style = MaterialTheme.typography.labelMedium)
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
