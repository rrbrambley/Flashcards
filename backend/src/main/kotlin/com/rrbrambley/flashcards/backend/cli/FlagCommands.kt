package com.rrbrambley.flashcards.backend.cli

import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.flags.FeatureFlagRepository
import org.jetbrains.exposed.sql.selectAll

/** `flag list` — lists feature flags with their global state and override counts. */
object FlagListCommand : AdminCommand {
    override val name = "flag list"
    override val usage = "flag list   List feature flags with their global state + override counts."

    override suspend fun run(args: AdminArgs, out: Appendable) {
        val flags = FeatureFlagRepository.listFlags()
        if (flags.isEmpty()) {
            out.appendLine("No feature flags.")
            return
        }
        flags.forEach { flag ->
            out.appendLine(
                "${flag.key}  [global: ${if (flag.enabled) "on" else "off"}]  " +
                    "users: ${flag.userOverrides.size}  roles: ${flag.roleOverrides.size}",
            )
        }
    }
}

/** `flag set --key <key> --enabled <true|false>` — sets a flag's global default state. */
object FlagSetCommand : AdminCommand {
    override val name = "flag set"
    override val usage = "flag set --key <key> --enabled <true|false>   Set a flag's global state."

    override suspend fun run(args: AdminArgs, out: Appendable) {
        val key = args.required("key")
        val enabled = booleanArg(args.required("enabled"))
        FeatureFlagRepository.setGlobal(key, enabled)
        out.appendLine("Set '$key' global state to ${if (enabled) "on" else "off"}.")
    }
}

/**
 * `flag override --key <key> (--email <email> | --role <key>) --enabled <true|false>` —
 * sets a per-user or per-role override.
 */
object FlagOverrideCommand : AdminCommand {
    override val name = "flag override"
    override val usage =
        "flag override --key <key> (--email <email> | --role <key>) --enabled <true|false>   Set an override."

    override suspend fun run(args: AdminArgs, out: Appendable) {
        val key = args.required("key")
        val enabled = booleanArg(args.required("enabled"))
        val email = args.optional("email")
        val role = args.optional("role")
        when {
            email != null && role != null -> throw AdminError("give either --email or --role, not both")
            email != null -> {
                val userId = userIdByEmail(email.trim())
                FeatureFlagRepository.setUserOverride(key, userId, enabled)
                out.appendLine("Set '$key' override for $email to ${if (enabled) "on" else "off"}.")
            }
            role != null -> {
                FeatureFlagRepository.setRoleOverride(key, role, enabled)
                out.appendLine("Set '$key' override for role '$role' to ${if (enabled) "on" else "off"}.")
            }
            else -> throw AdminError("give --email <email> or --role <key>")
        }
    }
}

/**
 * `flag clear --key <key> (--email <email> | --role <key>)` — removes a per-user or per-role override
 * (so the flag falls back to its global default for that target).
 */
object FlagClearCommand : AdminCommand {
    override val name = "flag clear"
    override val usage = "flag clear --key <key> (--email <email> | --role <key>)   Clear an override."

    override suspend fun run(args: AdminArgs, out: Appendable) {
        val key = args.required("key")
        val email = args.optional("email")
        val role = args.optional("role")
        when {
            email != null && role != null -> throw AdminError("give either --email or --role, not both")
            email != null -> {
                val userId = userIdByEmail(email.trim())
                FeatureFlagRepository.clearUserOverride(key, userId)
                out.appendLine("Cleared '$key' override for $email.")
            }
            role != null -> {
                FeatureFlagRepository.clearRoleOverride(key, role)
                out.appendLine("Cleared '$key' override for role '$role'.")
            }
            else -> throw AdminError("give --email <email> or --role <key>")
        }
    }
}

private fun booleanArg(value: String): Boolean =
    value.toBooleanStrictOrNull() ?: throw AdminError("--enabled must be true or false (got '$value')")

private suspend fun userIdByEmail(email: String): Long = dbQuery {
    Users.selectAll().where { Users.email eq email }.firstOrNull()?.get(Users.id)?.value
        ?: throw AdminError("no user with email '$email'")
}
