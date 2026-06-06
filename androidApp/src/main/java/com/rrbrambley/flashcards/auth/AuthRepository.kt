package com.rrbrambley.flashcards.auth

import com.rrbrambley.flashcards.shared.AuthResult
import com.rrbrambley.flashcards.shared.AuthService
import javax.inject.Inject

sealed interface AuthOutcome {
    data object Success : AuthOutcome
    data class Error(val message: String) : AuthOutcome
}

/**
 * Performs auth calls and persists the returned bearer token on success. Behind an
 * interface ([DefaultAuthRepository] is the impl) so the ViewModel can be unit-tested
 * with a synchronous fake.
 */
interface AuthRepository {
    suspend fun register(email: String, password: String): AuthOutcome
    suspend fun login(email: String, password: String): AuthOutcome
    suspend fun signInWithGoogle(idToken: String): AuthOutcome
    suspend fun logout()
}

/**
 * Thin adapter over the shared [AuthService] (login/register/Google/logout over the shared
 * `FlashcardApiClient` + `TokenStore`, persisting tokens on success, with parity error copy — the
 * same logic iOS uses). Kept behind [AuthRepository] so the ViewModel stays unit-testable with a
 * synchronous fake; maps the shared sealed [AuthResult] to the Android [AuthOutcome].
 */
class DefaultAuthRepository @Inject constructor(
    private val authService: AuthService,
) : AuthRepository {
    override suspend fun register(email: String, password: String): AuthOutcome =
        authService.register(email, password).toOutcome()

    override suspend fun login(email: String, password: String): AuthOutcome =
        authService.login(email, password).toOutcome()

    override suspend fun signInWithGoogle(idToken: String): AuthOutcome =
        authService.signInWithGoogle(idToken).toOutcome()

    override suspend fun logout() = authService.logout()

    private fun AuthResult.toOutcome(): AuthOutcome = when (this) {
        is AuthResult.Success -> AuthOutcome.Success
        is AuthResult.Failure -> AuthOutcome.Error(message)
    }
}
