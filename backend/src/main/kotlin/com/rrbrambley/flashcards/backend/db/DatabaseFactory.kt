package com.rrbrambley.flashcards.backend.db

import com.rrbrambley.flashcards.backend.auth.Passwords
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

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
            SchemaUtils.create(Users, RefreshTokens, Decks, Flashcards, PracticeSessions)
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
        }

        val hasGlobalFlagsDeck = Decks
            .selectAll()
            .where { (Decks.ownerUserId eq null) and (Decks.title eq COUNTRY_FLAGS_TITLE) }
            .any()
        if (!hasGlobalFlagsDeck) {
            val deckId = Decks.insertAndGetId {
                it[title] = COUNTRY_FLAGS_TITLE
                it[ownerUserId] = null
                it[createdAtMillis] = now
            }.value
            countryFlagCards.forEachIndexed { index, card ->
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

    private const val COUNTRY_FLAGS_TITLE = "Country Flags"

    private data class SeedCard(val question: String, val answer: String, val imageUrl: String?)

    // Mirrors the app's seeded flashcards in FlashcardLocalDataSource.kt verbatim.
    private val countryFlagCards = listOf(
        SeedCard(
            "What is this country?",
            "Canada",
            "https://upload.wikimedia.org/wikipedia/commons/d/d9/Flag_of_Canada_%28Pantone%29.svg",
        ),
        SeedCard(
            "What is this country?",
            "Kenya",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Flag_of_Kenya.svg/1920px-Flag_of_Kenya.svg.png",
        ),
        SeedCard(
            "What is this country?",
            "India",
            "https://upload.wikimedia.org/wikipedia/en/4/41/Flag_of_India.svg",
        ),
    )
}
