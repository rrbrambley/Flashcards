package com.rrbrambley.flashcards.backend.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.rrbrambley.flashcards.backend.db.DatabaseFactory
import com.rrbrambley.flashcards.backend.db.DbConfig
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * A small, scriptable operator CLI for the backend (user management, role assignment, …) that runs
 * against the same database the server uses, reusing the backend's own Exposed tables and auth
 * helpers (so password hashing, validation, and FK cascades match the app exactly).
 *
 * Run it via Gradle — `./gradlew :backend:admin --args="user list"` — or the `make admin` wrapper.
 * It talks **directly** to the database (bypassing HTTP/auth), so it's a local/operator tool, not
 * part of the deployed server; DB config comes from the same env/`application.conf` keys as the
 * server (`DB_JDBC_URL`, `DB_USER`, `DB_PASSWORD`).
 *
 * **Adding a command** is one object: implement [AdminCommand] and add it to [COMMANDS]. It then
 * shows up in `--help` and dispatches by name automatically.
 */
interface AdminCommand {
    /** Space-separated command path, e.g. `"user create"`. */
    val name: String

    /** One-line usage shown in `--help`. */
    val usage: String

    /** Runs the command with the already-parsed [args], writing human output to [out]. */
    suspend fun run(args: AdminArgs, out: Appendable)
}

/** A user-facing CLI error (bad input, not found, …). [main] prints its message and exits non-zero. */
class AdminError(message: String) : RuntimeException(message)

private val COMMANDS: List<AdminCommand> = listOf(
    UserCreateCommand,
    UserListCommand,
    UserDeleteCommand,
    RoleGrantCommand,
    RoleRevokeCommand,
)

fun main(argv: Array<String>) {
    val tokens = argv.toList()
    if (tokens.isEmpty() || tokens.first() in setOf("--help", "-h", "help")) {
        printUsage(System.out)
        return
    }

    val command = resolveCommand(tokens)
    if (command == null) {
        System.err.println("Unknown command: ${tokens.joinToString(" ")}\n")
        printUsage(System.err)
        exitProcess(1)
    }
    val rest = tokens.drop(command.name.split(" ").size)

    try {
        quietConnectionLogs()
        connectDatabase()
        runBlocking { command.run(AdminArgs(rest), System.out) }
    } catch (e: AdminError) {
        System.err.println("error: ${e.message}")
        exitProcess(1)
    } catch (e: IllegalArgumentException) {
        // e.g. Validation.* failures — surface the message, not a stack trace.
        System.err.println("error: ${e.message}")
        exitProcess(1)
    }
}

/** Finds the command whose space-separated name is a prefix of [tokens] (longest match wins). */
private fun resolveCommand(tokens: List<String>): AdminCommand? = COMMANDS
    .sortedByDescending { it.name.split(" ").size }
    .firstOrNull { command ->
        val nameTokens = command.name.split(" ")
        tokens.size >= nameTokens.size && tokens.subList(0, nameTokens.size) == nameTokens
    }

private fun printUsage(out: Appendable) {
    out.appendLine("Flashcards admin CLI")
    out.appendLine()
    out.appendLine("Usage: <command> [options]")
    out.appendLine()
    out.appendLine("Commands:")
    COMMANDS.forEach { out.appendLine("  ${it.usage}") }
}

/** Silences the pool/ORM INFO chatter so the CLI's own output is the only thing on the console. */
private fun quietConnectionLogs() {
    listOf("com.zaxxer.hikari", "Exposed").forEach { name ->
        (LoggerFactory.getLogger(name) as? Logger)?.level = Level.WARN
    }
}

/** Connects to the database using the same config keys as the server (no schema/seed). */
private fun connectDatabase() {
    val config = HoconApplicationConfig(ConfigFactory.load())
    DatabaseFactory.connect(
        DbConfig(
            jdbcUrl = config.property("db.jdbcUrl").getString(),
            user = config.property("db.user").getString(),
            password = config.property("db.password").getString(),
            maxPoolSize = config.property("db.maxPoolSize").getString().toInt(),
        ),
    )
}
