package com.rrbrambley.flashcards.core

/**
 * Deterministic [StringProvider] for unit tests (no Android resources). Renders a resource as
 * `string:<resId>` and appends any format args as `:<arg>`, so assertions can check both the
 * resolved string id and the arguments without depending on the real copy.
 */
class FakeStringProvider : StringProvider {
    override fun getString(resId: Int): String = "string:$resId"

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        "string:$resId" + formatArgs.joinToString("") { ":$it" }
}
