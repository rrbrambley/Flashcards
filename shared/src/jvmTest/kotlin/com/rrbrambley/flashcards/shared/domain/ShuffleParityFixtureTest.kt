package com.rrbrambley.flashcards.shared.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Parity guard (FLA-200): asserts the shared Kotlin [SessionOrdering] shuffle matches the canonical
 * golden fixture that the web's Vitest suite also loads (`webApp/src/practice/grading/...`). The
 * fixture is the single source of truth — a change to the mulberry32 PRNG or the Fisher–Yates on only
 * one platform fails that platform's run against the fixture, so cross-platform drift is caught in CI.
 *
 * Lives in `jvmTest` (not `commonTest`) purely to load the fixture as a classpath resource; the
 * ordering itself is `commonMain`, so verifying it on the JVM verifies the same logic that runs on iOS.
 */
class ShuffleParityFixtureTest {

    @Serializable
    private data class Fixtures(val seededOrders: List<OrderCase>)

    @Serializable
    private data class OrderCase(val name: String, val seed: Long, val size: Int, val expectedOrder: List<Int>)

    private val fixtures: Fixtures by lazy {
        val stream = javaClass.getResourceAsStream("/shuffle-fixtures.json")
        assertNotNull(stream, "shuffle-fixtures.json not on the test classpath")
        JSON.decodeFromString(stream.bufferedReader().use { it.readText() })
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }

    @Test
    fun seededOrder_matchesGoldenFixture() {
        assertTrue(fixtures.seededOrders.isNotEmpty(), "fixture has no cases")
        for (case in fixtures.seededOrders) {
            assertEquals(
                case.expectedOrder,
                SessionOrdering.order(case.size, shuffle = true, seed = case.seed),
                "shuffle order drifted for ${case.name}",
            )
        }
    }
}
