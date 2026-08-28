package com.rrbrambley.flashcards.backend.db

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DbConnectionHelpTest {
    /**
     * The failure that motivated this (#405): a second Postgres on 5432 accepts the connection and
     * then rejects the role, so the driver reports what looks like a credentials or seeding problem.
     * The message has to say "wrong server", or the next hour goes into roles and the container.
     */
    @Test
    fun `a rejected role is explained as the wrong server, not bad credentials`() {
        val help = databaseConnectionHelp(
            jdbcUrl = "jdbc:postgresql://localhost:5432/flashcards",
            cause = """FATAL: role "flashcards" does not exist""",
            command = "make start",
        )

        assertContains(help, "isn't this project's database")
        assertContains(help, "another Postgres on that port")
        assertFalse(
            "Nothing is accepting connections" in help,
            "reaching a server and being turned away is the opposite of nothing listening",
        )
    }

    @Test
    fun `a refused connection says nothing is listening`() {
        val help = databaseConnectionHelp(
            jdbcUrl = "jdbc:postgresql://localhost:59999/flashcards",
            cause = "Connection to localhost:59999 refused.",
            command = "make start",
        )

        assertContains(help, "Nothing is accepting connections")
        assertFalse("isn't this project's database" in help, "nothing answered, so nothing rejected us")
    }

    @Test
    fun `names the address it tried and the command that gets it right`() {
        val help = databaseConnectionHelp(
            jdbcUrl = "jdbc:postgresql://localhost:5433/flashcards",
            cause = "boom",
            command = "make admin ARGS=\"…\"",
        )

        // The address is the whole point: the reader is usually sure it connected somewhere else.
        assertContains(help, "jdbc:postgresql://localhost:5433/flashcards")
        assertContains(help, "make admin")
        assertContains(help, "boom")
    }

    @Test
    fun `survives a driver that gave no message`() {
        val help = databaseConnectionHelp("jdbc:postgresql://localhost:5432/flashcards", null, "make start")

        assertContains(help, "Nothing is accepting connections")
        assertFalse("null" in help, "a missing cause must not print as the word null")
    }
}
