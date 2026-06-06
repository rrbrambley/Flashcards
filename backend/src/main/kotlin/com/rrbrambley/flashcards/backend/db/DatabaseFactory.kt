package com.rrbrambley.flashcards.backend.db

import com.rrbrambley.flashcards.backend.auth.Passwords
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

data class DbConfig(val jdbcUrl: String, val user: String, val password: String, val maxPoolSize: Int = 5)

object DatabaseFactory {
    /**
     * Fixed dev *refresh* token for the seeded demo user. Exchange it at POST /auth/refresh for an
     * access-token JWT (curl/local dev convenience). Seeded with a far-future expiry.
     */
    const val DEMO_TOKEN = "demo-token"
    const val DEMO_EMAIL = "demo@flashcards.dev"
    private const val DEMO_PASSWORD = "demo"
    private const val TEN_YEARS_MILLIS = 10L * 365 * 24 * 60 * 60 * 1000

    fun init(config: DbConfig) {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        Database.connect(HikariDataSource(hikari))

        transaction {
            // createMissingTablesAndColumns (vs. create) also adds newly-introduced nullable columns
            // — e.g. refresh_tokens.rotated_at_millis — to an already-provisioned dev database.
            SchemaUtils.createMissingTablesAndColumns(Users, RefreshTokens, Decks, Flashcards, PracticeSessions)
            seed()
        }
    }

    /** Idempotent: guarded so restarts (without a volume wipe) don't duplicate rows. */
    private fun seed() {
        val now = System.currentTimeMillis()

        val demoUserId = Users
            .selectAll()
            .where { Users.email eq DEMO_EMAIL }
            .firstOrNull()
            ?.get(Users.id)
            ?.value
            ?: Users.insertAndGetId {
                it[email] = DEMO_EMAIL
                it[passwordHash] = Passwords.hash(DEMO_PASSWORD)
                it[createdAtMillis] = now
            }.value

        val hasDemoToken = RefreshTokens
            .selectAll()
            .where { RefreshTokens.token eq DEMO_TOKEN }
            .any()
        if (!hasDemoToken) {
            RefreshTokens.insert {
                it[token] = DEMO_TOKEN
                it[userId] = demoUserId
                it[createdAtMillis] = now
                it[expiresAtMillis] = now + TEN_YEARS_MILLIS
            }
        } else {
            // Keep the dev demo token usable across restarts even after it's been rotated.
            RefreshTokens.update({ RefreshTokens.token eq DEMO_TOKEN }) { it[rotatedAtMillis] = null }
        }

        // Drop the legacy minimal test deck if it lingers from an older seed, then build the full
        // Flags of the World catalog deck. Idempotent across restarts; rebuilt on a reset + reseed.
        // (Deck deletes cascade to its flashcards + practice sessions — see Tables.kt.)
        Decks.deleteWhere { (Decks.ownerUserId eq null) and (Decks.title eq LEGACY_FLAGS_TITLE) }

        val hasFlagsDeck = Decks
            .selectAll()
            .where { (Decks.ownerUserId eq null) and (Decks.title eq FLAGS_TITLE) }
            .any()
        if (!hasFlagsDeck) {
            val deckId = Decks.insertAndGetId {
                it[title] = FLAGS_TITLE
                it[ownerUserId] = null
                it[createdAtMillis] = now
            }.value
            flagSeedCards().forEachIndexed { index, card ->
                Flashcards.insert {
                    it[Flashcards.deckId] = deckId
                    it[question] = card.question
                    it[answer] = card.answer
                    it[imageUrl] = card.imageUrl
                    it[position] = index
                }
            }
        }
    }

    /** Title of the seeded global catalog deck. Public so clients/tests can resolve it by name. */
    const val FLAGS_TITLE = "Flags of the World"
    private const val LEGACY_FLAGS_TITLE = "Country Flags"

    private data class SeedCard(val question: String, val answer: String, val imageUrl: String?)

    /**
     * Builds the Flags of the World cards from the checked-in `seed/flags.json` (ISO 3166-1
     * alpha-2 `code` → `name`, generated from https://flagcdn.com/en/codes.json, 2-letter codes
     * only). Front is the flag image only (empty question); back is the country/territory name.
     * Flag SVGs are served by flagcdn.com — a free, Cloudflare-hosted CDN of public-domain national
     * flags — referenced by URL like the cards' other images (no upload to our storage needed).
     */
    private fun flagSeedCards(): List<SeedCard> {
        val text = DatabaseFactory::class.java.getResourceAsStream("/seed/flags.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Missing seed resource: /seed/flags.json")
        return Json.parseToJsonElement(text).jsonArray.map { element ->
            val obj = element.jsonObject
            val code = obj.getValue("code").jsonPrimitive.content
            val name = obj.getValue("name").jsonPrimitive.content
            SeedCard(question = "", answer = name, imageUrl = "https://flagcdn.com/$code.svg")
        }
    }
}
