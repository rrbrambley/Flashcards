package com.rrbrambley.flashcards.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt

/** BCrypt password hashing shared by the seed step and [AuthService]. */
object Passwords {
    private const val COST = 10

    fun hash(rawPassword: String): String = BCrypt.withDefaults().hashToString(COST, rawPassword.toCharArray())

    fun verify(rawPassword: String, hash: String): Boolean =
        BCrypt.verifyer().verify(rawPassword.toCharArray(), hash).verified
}
