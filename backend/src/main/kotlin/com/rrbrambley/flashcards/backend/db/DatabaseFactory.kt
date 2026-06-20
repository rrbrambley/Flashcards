package com.rrbrambley.flashcards.backend.db

import com.rrbrambley.flashcards.backend.auth.Passwords
import com.rrbrambley.flashcards.backend.auth.Permission
import com.rrbrambley.flashcards.backend.auth.Role
import com.rrbrambley.flashcards.data.mapping.DeckTags
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

    /**
     * Connects Exposed to the database (HikariCP pool) without creating or seeding the schema. For
     * tools that operate on an already-provisioned database — e.g. the admin CLI — and for [init],
     * which connects and then provisions/seeds.
     */
    fun connect(config: DbConfig) {
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
    }

    fun init(config: DbConfig) {
        connect(config)

        transaction {
            // createMissingTablesAndColumns (vs. create) also adds newly-introduced nullable columns
            // — e.g. refresh_tokens.rotated_at_millis — to an already-provisioned dev database.
            SchemaUtils.createMissingTablesAndColumns(
                Users,
                RefreshTokens,
                Decks,
                Flashcards,
                PracticeSessions,
                Roles,
                Permissions,
                RolePermissions,
                UserRoles,
                DiscussionThreads,
                DiscussionMessages,
            )
            backfillCardUids()
            seed()
        }
    }

    /** Assigns a stable cardUid to any pre-existing flashcard that predates the column (FLA-113). */
    private fun backfillCardUids() {
        Flashcards.selectAll().where { Flashcards.cardUid.isNull() }.forEach { row ->
            Flashcards.update({ Flashcards.id eq row[Flashcards.id] }) {
                it[cardUid] = java.util.UUID.randomUUID().toString()
            }
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

        // Drop the legacy minimal test deck if it lingers from an older seed, then build the global
        // catalog decks. Idempotent across restarts; rebuilt on a reset + reseed. (Deck deletes
        // cascade to their flashcards + practice sessions — see Tables.kt.) Flags is seeded first so
        // it stays the lowest-id global deck, which the home feed features.
        Decks.deleteWhere { (Decks.ownerUserId eq null) and (Decks.title eq LEGACY_FLAGS_TITLE) }

        // The geography catalog decks carry a starter "Geography" category so the tag UI has
        // something to group/filter on out of the box.
        seedGlobalDeck(now, FLAGS_TITLE, flagSeedCards(), tags = listOf(GEOGRAPHY_TAG))
        seedGlobalDeck(
            now,
            NATIONAL_CAPITALS_TITLE,
            textSeedCards("/seed/national-capitals.json", "country", "capital"),
            tags = listOf(GEOGRAPHY_TAG),
        )
        seedGlobalDeck(
            now,
            US_STATE_CAPITALS_TITLE,
            textSeedCards("/seed/us-state-capitals.json", "state", "capital"),
            tags = listOf(GEOGRAPHY_TAG),
        )
        seedGlobalDeck(now, WORLD_CURRENCIES_TITLE, textSeedCards("/seed/world-currencies.json", "country", "currency"))

        seedRbac(demoUserId)
    }

    /**
     * Seeds the RBAC catalog (roles, permissions, and each role's grants) from the code-defined
     * [Role]/[Permission] enums, then bootstraps the demo user as an admin. Idempotent across restarts
     * (guarded inserts); rebuilt on a reset + reseed. Real-world admin assignment is a separate
     * concern (initially SQL/seed; a future "manage users & roles" feature).
     */
    private fun seedRbac(demoUserId: Long) {
        val permissionIds = Permission.entries.associate { permission ->
            val id = Permissions
                .selectAll()
                .where { Permissions.key eq permission.key }
                .firstOrNull()
                ?.get(Permissions.id)
                ?.value
                ?: Permissions.insertAndGetId {
                    it[key] = permission.key
                    it[description] = permission.description
                }.value
            permission to id
        }

        Role.entries.forEach { role ->
            val roleId = Roles
                .selectAll()
                .where { Roles.key eq role.key }
                .firstOrNull()
                ?.get(Roles.id)
                ?.value
                ?: Roles.insertAndGetId {
                    it[key] = role.key
                    it[description] = role.description
                }.value

            role.permissions.forEach { permission ->
                val permissionId = permissionIds.getValue(permission)
                val granted = RolePermissions
                    .selectAll()
                    .where { (RolePermissions.roleId eq roleId) and (RolePermissions.permissionId eq permissionId) }
                    .any()
                if (!granted) {
                    RolePermissions.insert {
                        it[RolePermissions.roleId] = roleId
                        it[RolePermissions.permissionId] = permissionId
                    }
                }
            }

            // Bootstrap: the demo user is an admin so there's a working admin for dev/testing.
            if (role == Role.ADMIN) {
                val assigned = UserRoles
                    .selectAll()
                    .where { (UserRoles.userId eq demoUserId) and (UserRoles.roleId eq roleId) }
                    .any()
                if (!assigned) {
                    UserRoles.insert {
                        it[userId] = demoUserId
                        it[UserRoles.roleId] = roleId
                    }
                }
            }
        }
    }

    /**
     * Inserts a global (ownerless) catalog deck with [cards] under [title], unless a global deck with
     * that title already exists. Guarded so restarts don't duplicate it; rebuilt on a reset + reseed.
     */
    private fun seedGlobalDeck(now: Long, title: String, cards: List<SeedCard>, tags: List<String> = emptyList()) {
        val exists = Decks
            .selectAll()
            .where { (Decks.ownerUserId eq null) and (Decks.title eq title) }
            .any()
        if (exists) return

        val deckId = Decks.insertAndGetId {
            it[Decks.title] = title
            it[ownerUserId] = null
            it[createdAtMillis] = now
            it[Decks.tags] = DeckTags.encode(tags)
        }.value
        cards.forEachIndexed { index, card ->
            Flashcards.insert {
                it[Flashcards.deckId] = deckId
                it[question] = card.question
                it[answer] = card.answer
                it[imageUrl] = card.imageUrl
                it[position] = index
            }
        }
    }

    /** Titles of the seeded global catalog decks. Public so clients/tests can resolve them by name. */
    const val FLAGS_TITLE = "Flags of the World"
    const val NATIONAL_CAPITALS_TITLE = "National Capitals"
    const val US_STATE_CAPITALS_TITLE = "U.S. State Capitals"
    const val WORLD_CURRENCIES_TITLE = "World Currencies"
    private const val LEGACY_FLAGS_TITLE = "Country Flags"
    private const val GEOGRAPHY_TAG = "Geography"

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

    /**
     * Builds text-only cards (no images) from a checked-in JSON array of objects under `seed/`, taking the
     * [questionKey] field as the front and [answerKey] as the back. Used for the National Capitals,
     * U.S. State Capitals, and World Currencies global decks.
     */
    private fun textSeedCards(resource: String, questionKey: String, answerKey: String): List<SeedCard> {
        val text = DatabaseFactory::class.java.getResourceAsStream(resource)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Missing seed resource: $resource")
        return Json.parseToJsonElement(text).jsonArray.map { element ->
            val obj = element.jsonObject
            SeedCard(
                question = obj.getValue(questionKey).jsonPrimitive.content,
                answer = obj.getValue(answerKey).jsonPrimitive.content,
                imageUrl = null,
            )
        }
    }
}
