package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

/** Body for POST /auth/google: a Google ID token obtained on the client. */
@Serializable
data class GoogleAuthRequest(val idToken: String)

@Serializable
data class AuthResponse(val token: String, val userId: Long)
