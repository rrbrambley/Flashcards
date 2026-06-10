package com.rrbrambley.flashcards.practice.grading

import com.rrbrambley.flashcards.shared.domain.Flashcard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Parity guard (FLA-81): asserts the shared Kotlin grading matches the canonical golden fixture that
 * the web's Vitest suite also loads (`webApp/src/practice/grading/parity.test.ts`). The fixture is
 * the single source of truth — if someone changes the typo threshold or the choice rules on only one
 * platform, that platform's run fails against the fixture, so drift is caught in CI.
 *
 * This lives in `jvmTest` (not `commonTest`) purely so it can load the fixture as a classpath
 * resource; the grading code itself is `commonMain`, so verifying it on the JVM verifies the exact
 * same logic that runs on iOS.
 */
class GradingParityFixtureTest {

    @Serializable
    private data class Fixtures(val textGrading: List<TextCase>, val multipleChoice: List<ChoiceCase>)

    @Serializable
    private data class TextCase(
        val name: String,
        val input: String,
        val answer: String,
        val expectedCorrect: Boolean,
        val expectedSimilarity: Double? = null,
    )

    @Serializable
    private data class FixtureCard(val question: String, val answer: String)

    @Serializable
    private data class ChoiceCase(
        val name: String,
        val cards: List<FixtureCard>,
        val cardIndex: Int,
        val count: Int,
        val expectedSize: Int,
        val correct: String,
        val allowedDistractors: List<String>,
    )

    private val fixtures: Fixtures by lazy {
        val stream = javaClass.getResourceAsStream("/grading-fixtures.json")
        assertNotNull(stream, "grading-fixtures.json not on the test classpath")
        JSON.decodeFromString(stream.bufferedReader().use { it.readText() })
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }

    @Test
    fun textGradingMatchesTheGoldenFixture() {
        for (case in fixtures.textGrading) {
            val grade = gradeTextAnswer(case.input, case.answer)
            assertEquals(case.expectedCorrect, grade.correct, "correct mismatch for '${case.name}'")
            case.expectedSimilarity?.let { expected ->
                assertTrue(
                    abs(grade.similarity - expected) < 1e-9,
                    "similarity mismatch for '${case.name}': expected $expected, got ${grade.similarity}",
                )
            }
        }
    }

    @Test
    fun multipleChoiceMatchesTheGoldenFixture() {
        // The Fisher–Yates RNG streams differ between Kotlin and JS, so the fixture asserts
        // seed-independent properties (size, includes-correct, distractor pool, no dupes) rather
        // than exact ordering — see the fixture's note. A fixed seed only makes the run reproducible.
        for (case in fixtures.multipleChoice) {
            val deck = case.cards.map { Flashcard(it.question, it.answer) }
            val choices =
                buildChoices(deck[case.cardIndex], deck, count = case.count, random = kotlin.random.Random(42))

            assertEquals(case.expectedSize, choices.size, "size mismatch for '${case.name}'")
            assertContains(choices, case.correct, "missing correct answer for '${case.name}'")
            assertEquals(choices.size, choices.toSet().size, "duplicate choices for '${case.name}'")
            choices.filter { it != case.correct }.forEach { distractor ->
                assertContains(case.allowedDistractors, distractor, "unexpected distractor for '${case.name}'")
            }
        }
    }
}
