package com.rrbrambley.flashcards.backend.cli

/**
 * A tiny `--key value` / `--flag` parser for [AdminCommand]s (hand-rolled to avoid a CLI dependency).
 * Repeated options accumulate (e.g. `--role admin --role editor`); a `--key` with no following value
 * (or followed by another `--…`) is a boolean flag. The command name is stripped before parsing, so
 * only options remain.
 */
class AdminArgs(tokens: List<String>) {
    private val options = LinkedHashMap<String, MutableList<String>>()
    private val flags = LinkedHashSet<String>()

    init {
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (!token.startsWith("--")) throw AdminError("unexpected argument '$token' (options must start with --)")
            val key = token.removePrefix("--")
            val next = tokens.getOrNull(i + 1)
            if (next == null || next.startsWith("--")) {
                flags += key
                i += 1
            } else {
                options.getOrPut(key) { mutableListOf() }.add(next)
                i += 2
            }
        }
    }

    /** The last value given for [name], or null if absent. */
    fun optional(name: String): String? = options[name]?.last()

    /** The value for [name], or an [AdminError] if it wasn't provided. */
    fun required(name: String): String = optional(name) ?: throw AdminError("missing required --$name")

    /** All values given for [name] (for repeatable options), in order. */
    fun list(name: String): List<String> = options[name].orEmpty()

    /** True if [name] was given as a bare `--flag` or as `--name value`. */
    fun has(name: String): Boolean = name in flags || name in options
}
