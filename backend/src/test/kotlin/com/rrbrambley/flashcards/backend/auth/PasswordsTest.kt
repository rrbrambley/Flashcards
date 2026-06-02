package com.rrbrambley.flashcards.backend.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordsTest {

    @Test
    fun hash_doesNotReturnTheRawPassword() {
        val raw = "s3cret-pw"
        assertNotEquals(raw, Passwords.hash(raw))
    }

    @Test
    fun verify_acceptsTheCorrectPassword() {
        val hash = Passwords.hash("correct horse")
        assertTrue(Passwords.verify("correct horse", hash))
    }

    @Test
    fun verify_rejectsAWrongPassword() {
        val hash = Passwords.hash("correct horse")
        assertFalse(Passwords.verify("wrong horse", hash))
    }

    @Test
    fun verify_rejectsATamperedHash() {
        val hash = Passwords.hash("password")
        // Flip a character mid-hash. (Not the final base64 char: bcrypt leaves a few
        // unused low bits there, so tampering only it can still verify.)
        val idx = hash.length / 2
        val tampered = hash.replaceRange(idx, idx + 1, if (hash[idx] == 'a') "b" else "a")
        assertFalse(Passwords.verify("password", tampered))
    }

    @Test
    fun hash_isSaltedSoRepeatedHashesDiffer() {
        assertNotEquals(Passwords.hash("same"), Passwords.hash("same"))
    }
}
