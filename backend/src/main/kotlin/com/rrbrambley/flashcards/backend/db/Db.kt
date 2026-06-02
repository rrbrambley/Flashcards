package com.rrbrambley.flashcards.backend.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/** Runs Exposed DB work off the request thread; use in all suspend route handlers. */
suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

/** Lightweight connectivity check for the health endpoint: true if a trivial query succeeds. */
suspend fun pingDatabase(): Boolean =
    runCatching { newSuspendedTransaction(Dispatchers.IO) { exec("SELECT 1") } }.isSuccess
