package com.rrbrambley.flashcards.backend.db

/**
 * Turns a failed database connection into a message that names the *likely cause* rather than the
 * symptom.
 *
 * Worth the file because the raw driver errors actively mislead here (#405). The local Postgres runs
 * in a container that publishes 5433 when 5432 is already taken, so the common failure is "the URL
 * points at the wrong server" — but that arrives as either `Connection refused` or, far more
 * confusingly, `FATAL: role "flashcards" does not exist`. The second one reads as a credentials or
 * seeding problem and sends you off inspecting roles and the container, when the container is
 * healthy and simply isn't the server being talked to. That happens on any machine with a second
 * Postgres (a native/Homebrew install is the usual one) on 5432: the connection *succeeds* and is
 * then rejected by a database that was never ours.
 *
 * [command] is the wrapper that detects the container's published port — `make start` for the
 * server, `make admin` for the CLI.
 */
fun databaseConnectionHelp(jdbcUrl: String, cause: String?, command: String): String {
    val detail = cause?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    // Reached a server, and it turned us away: the port is open but belongs to something else.
    val reachedWrongServer = cause != null &&
        listOf("does not exist", "password authentication", "authentication failed").any { it in cause }
    val diagnosis = if (reachedWrongServer) {
        "Something is listening there, but it isn't this project's database — another Postgres on " +
            "that port will reject the `flashcards` role instead of reporting a wrong address."
    } else {
        "Nothing is accepting connections at that address."
    }
    return "Could not connect to the database at $jdbcUrl$detail. $diagnosis " +
        "Run `$command` (it detects the container's published port), or set DB_JDBC_URL yourself."
}
