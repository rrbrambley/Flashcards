package com.rrbrambley.flashcards.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Thin wrapper over Credential Manager's "Sign in with Google" flow. Returns a Google
 * ID token to send to the backend for verification. Requires a configured Web client ID
 * and Google Play Services on the device.
 */
object GoogleSignIn {

    suspend fun getIdToken(activity: Activity, serverClientId: String): String {
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)

        val credential = result.credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "Unexpected credential type: ${credential.type}" }

        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
