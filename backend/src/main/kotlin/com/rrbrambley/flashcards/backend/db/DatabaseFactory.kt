package com.rrbrambley.flashcards.backend.db

import com.rrbrambley.flashcards.backend.answerstats.normalizeAnswer
import com.rrbrambley.flashcards.backend.auth.Passwords
import com.rrbrambley.flashcards.backend.auth.Permission
import com.rrbrambley.flashcards.backend.auth.Role
import com.rrbrambley.flashcards.backend.flags.FeatureFlag
import com.rrbrambley.flashcards.data.mapping.DeckTags
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

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
                PracticeAnswers,
                Roles,
                Permissions,
                RolePermissions,
                UserRoles,
                DiscussionThreads,
                DiscussionMessages,
                DiscussionReports,
                AnswerSuggestions,
                Notifications,
                FeatureFlags,
                FeatureFlagUserOverrides,
                FeatureFlagRoleOverrides,
            )
            backfillCardUids()
            backfillNormalizedAnswers()
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

    /**
     * Populates [PracticeAnswers.normalizedAnswer] for answers logged before the column existed, so
     * the per-card "most common answers" aggregation sees historical data too (#346). Only rows with a
     * submitted answer and a still-null normalized form are touched, so it's a cheap idempotent no-op
     * on subsequent boots.
     */
    private fun backfillNormalizedAnswers() {
        PracticeAnswers.selectAll()
            .where { PracticeAnswers.submittedText.isNotNull() and PracticeAnswers.normalizedAnswer.isNull() }
            .forEach { row ->
                PracticeAnswers.update({ PracticeAnswers.id eq row[PracticeAnswers.id] }) {
                    it[normalizedAnswer] = normalizeAnswer(row[submittedText])
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

        // Drop the legacy minimal test deck if it lingers from an older seed (still ownerless), then
        // build the global catalog decks. Idempotent across restarts. (Deck deletes cascade to their
        // flashcards + practice sessions — see Tables.kt.) Flags is seeded first so it stays the
        // lowest-id global deck, which the home feed features.
        Decks.deleteWhere { (Decks.ownerUserId eq null) and (Decks.title eq LEGACY_FLAGS_TITLE) }

        // FLA-120: decouple "global" from ownership. Any legacy ownerless deck (pre-FLA-120 catalog)
        // becomes owned by the demo admin + flagged global, so visibility now keys on `isGlobal`.
        Decks.update({ Decks.ownerUserId eq null }) {
            it[ownerUserId] = demoUserId
            it[isGlobal] = true
        }

        // FLA-212: the map-image country deck shipped briefly as "Maps of the World" with a larger card
        // set (before dropping the countries that render as an invisible dot). Drop any existing copy so
        // it's rebuilt below under the current title with the trimmed set, rather than lingering with
        // now-removed cards whose images 404. (Cascades to its cards + sessions — see Tables.kt.)
        Decks.deleteWhere { (Decks.isGlobal eq true) and (Decks.title eq LEGACY_COUNTRIES_TITLE) }

        // The geography catalog decks carry a starter "Geography" category so the tag UI has
        // something to group/filter on out of the box.
        seedGlobalDeck(demoUserId, now, FLAGS_TITLE, flagSeedCards(), tags = listOf(GEOGRAPHY_TAG))
        // Databases seeded before #369 still point at flagcdn; the deck already exists, so seeding
        // above skips it. Rewrite those cards to the normalised copies.
        backfillFlagImageUrls()
        seedGlobalDeck(demoUserId, now, COUNTRIES_TITLE, mapSeedCards(), tags = listOf(GEOGRAPHY_TAG))
        seedGlobalDeck(
            demoUserId,
            now,
            NATIONAL_CAPITALS_TITLE,
            textSeedCards("/seed/national-capitals.json", "country", "capital"),
            tags = listOf(GEOGRAPHY_TAG),
        )
        seedGlobalDeck(
            demoUserId,
            now,
            US_STATE_CAPITALS_TITLE,
            textSeedCards("/seed/us-state-capitals.json", "state", "capital"),
            tags = listOf(GEOGRAPHY_TAG),
        )
        seedGlobalDeck(
            demoUserId,
            now,
            WORLD_CURRENCIES_TITLE,
            textSeedCards("/seed/world-currencies.json", "country", "currency"),
        )

        seedRbac(demoUserId)
        seedFeatureFlags()
    }

    /**
     * Seeds the feature-flag catalog from the code-defined [FeatureFlag] enum: each flag's row is
     * created once with its default enabled state, which then becomes admin-owned. Idempotent — an
     * existing flag's `enabled` is left untouched (so an admin toggle survives restarts); only the
     * description is refreshed to match the catalog.
     */
    private fun seedFeatureFlags() {
        FeatureFlag.entries.forEach { flag ->
            val exists = FeatureFlags.selectAll().where { FeatureFlags.key eq flag.key }.any()
            if (!exists) {
                FeatureFlags.insert {
                    it[key] = flag.key
                    it[description] = flag.description
                    it[enabled] = flag.defaultEnabled
                }
            } else {
                FeatureFlags.update({ FeatureFlags.key eq flag.key }) {
                    it[description] = flag.description
                }
            }
        }
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
     * Inserts a global catalog deck with [cards] under [title], owned by [ownerId] (the demo admin) and
     * flagged global (FLA-120), unless a global deck with that title already exists. Guarded so restarts
     * don't duplicate it; rebuilt on a reset + reseed.
     */
    private fun seedGlobalDeck(
        ownerId: Long,
        now: Long,
        title: String,
        cards: List<SeedCard>,
        tags: List<String> = emptyList(),
    ) {
        val exists = Decks
            .selectAll()
            .where { (Decks.isGlobal eq true) and (Decks.title eq title) }
            .any()
        if (exists) return

        val deckId = Decks.insertAndGetId {
            it[Decks.title] = title
            it[ownerUserId] = ownerId
            it[isGlobal] = true
            it[createdAtMillis] = now
            it[Decks.tags] = DeckTags.encode(tags)
        }.value
        cards.forEachIndexed { index, card ->
            Flashcards.insert {
                it[Flashcards.deckId] = deckId
                it[cardUid] = java.util.UUID.randomUUID().toString()
                it[question] = card.question
                it[answer] = card.answer
                it[imageUrl] = card.imageUrl
                it[position] = index
            }
        }
    }

    /** Titles of the seeded global catalog decks. Public so clients/tests can resolve them by name. */
    const val FLAGS_TITLE = "Flags of the World"

    /** The map-image country-identification deck: named for the goal (name the country), not the medium. */
    const val COUNTRIES_TITLE = "Countries of the World"
    const val NATIONAL_CAPITALS_TITLE = "National Capitals"
    const val US_STATE_CAPITALS_TITLE = "U.S. State Capitals"
    const val WORLD_CURRENCIES_TITLE = "World Currencies"
    private const val LEGACY_FLAGS_TITLE = "Country Flags"
    private const val LEGACY_COUNTRIES_TITLE = "Maps of the World"
    private const val GEOGRAPHY_TAG = "Geography"

    /** jsDelivr serves this repo's checked-in seed images straight from GitHub. */
    private const val REPO_CDN_BASE = "https://cdn.jsdelivr.net/gh/rrbrambley/Flashcards@main/tools"

    /** The locator maps generated by `tools/country-maps/`. */
    private const val MAPS_CDN_BASE = "$REPO_CDN_BASE/country-maps/maps"

    /** The normalised flags generated by `tools/country-flags/` — see that tool's README and #369. */
    private const val FLAGS_CDN_BASE = "$REPO_CDN_BASE/country-flags/flags"

    /** Where the flags used to come from, before normalisation; matched to rewrite existing rows. */
    private const val LEGACY_FLAGS_URL_PREFIX = "https://flagcdn.com/"

    private data class SeedCard(val question: String, val answer: String, val imageUrl: String?)

    /**
     * Builds the Flags of the World cards from the checked-in `seed/flags.json` (ISO 3166-1
     * alpha-2 `code` → `name`, generated from https://flagcdn.com/en/codes.json, 2-letter codes
     * only). Front is the flag image only (empty question); back is the country/territory name.
     *
     * The flags originate from flagcdn but are served from **this repo** via jsDelivr, like the maps:
     * flagcdn's own SVGs compose stars out of nested `<use>` references, which every client's SVG
     * renderer gets wrong differently (#369 — iOS mangled them, and #363 was the Android half). The
     * checked-in copies are normalised to plain paths by `tools/country-flags/`, so they draw the same
     * everywhere. Regenerate with `npm run generate` there; never point this back at flagcdn directly.
     */
    private fun flagSeedCards(): List<SeedCard> {
        val text = DatabaseFactory::class.java.getResourceAsStream("/seed/flags.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Missing seed resource: /seed/flags.json")
        return Json.parseToJsonElement(text).jsonArray.map { element ->
            val obj = element.jsonObject
            val code = obj.getValue("code").jsonPrimitive.content
            val name = obj.getValue("name").jsonPrimitive.content
            SeedCard(question = "", answer = name, imageUrl = "$FLAGS_CDN_BASE/$code.svg")
        }
    }

    /**
     * Repoints already-seeded flag cards at the normalised copies (#369).
     *
     * [seedGlobalDeck] returns early when a deck exists, so a database seeded before the switch would
     * otherwise keep serving flagcdn's originals forever — the deck is only built once. Rewrites just
     * the host prefix, keeping each card's `{code}.svg` filename, and matches on the old prefix so it
     * is idempotent and touches nothing else.
     */
    internal fun backfillFlagImageUrls() {
        val flagsDeckId = Decks
            .selectAll()
            .where { (Decks.isGlobal eq true) and (Decks.title eq FLAGS_TITLE) }
            .firstOrNull()?.get(Decks.id)?.value
            ?: return

        // A couple hundred rows read once at boot, so filtering here rather than in SQL costs nothing
        // and keeps the prefix match in one place.
        val stale = Flashcards
            .selectAll()
            .where { Flashcards.deckId eq flagsDeckId }
            .mapNotNull { row ->
                val url = row[Flashcards.imageUrl]
                if (url != null && url.startsWith(LEGACY_FLAGS_URL_PREFIX)) row[Flashcards.id].value to url else null
            }

        stale.forEach { (cardId, oldUrl) ->
            val file = oldUrl.removePrefix(LEGACY_FLAGS_URL_PREFIX)
            Flashcards.update({ Flashcards.id eq cardId }) { it[imageUrl] = "$FLAGS_CDN_BASE/$file" }
        }
    }

    /**
     * Builds the Maps of the World cards from the checked-in `seed/maps.json` (ISO 3166-1 alpha-2
     * `code` → `name`, a subset of `flags.json` — the countries that have map geometry). Front is a
     * locator-map image only (empty question); back is the country name. The maps are generated from
     * public-domain Natural Earth data (see `tools/country-maps/`) and served — like the flags — by
     * URL, here from this repo via the jsDelivr CDN (no upload to our storage needed).
     */
    private fun mapSeedCards(): List<SeedCard> {
        val text = DatabaseFactory::class.java.getResourceAsStream("/seed/maps.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Missing seed resource: /seed/maps.json")
        return Json.parseToJsonElement(text).jsonArray.map { element ->
            val obj = element.jsonObject
            val code = obj.getValue("code").jsonPrimitive.content
            val name = obj.getValue("name").jsonPrimitive.content
            SeedCard(question = "", answer = name, imageUrl = "$MAPS_CDN_BASE/$code.png")
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
