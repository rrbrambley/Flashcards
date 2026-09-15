package com.rrbrambley.flashcards.practice.grading

/**
 * Renders spoken number words as digits, so a correctly *spoken* numeric answer can be graded
 * against a card whose answer is written in digits (#390).
 *
 * The problem this solves: [gradeTextAnswer] scores character-level edit distance, so "nineteen
 * eighty" against "1980" scores ≈0.07 on a 0.85 threshold. Test mode marks a genuinely correct
 * spoken answer wrong every time the answer is numeric.
 *
 * **These are additional candidates, never a replacement.** Callers grade the original transcript
 * first and only fall back to these, which is what makes the conversion safe to be eager: a card
 * answered "one piece" still grades against the words, because the original wins before any variant
 * is tried. Nothing here loosens a threshold or changes how any string is graded — it only changes
 * *which* strings are offered, exactly like the n-best rescoring it sits beside.
 *
 * Two readings are emitted because English says numbers both ways and the transcript doesn't say
 * which is meant:
 *  - **arithmetic** — "twenty three" → 23, "one hundred and five" → 105, "two thousand" → 2000
 *  - **concatenated** — the year/digit-string reading: "nineteen eighty" → 1980 (not 99),
 *    "twenty twenty four" → 2024, "one two three" → 123
 *
 * Whichever grades correct wins; if neither does, the caller keeps the original transcript. Scale
 * words ("hundred", "thousand") rule out the concatenated reading, since "two thousand" is never
 * "2" followed by "1000".
 *
 * Mirrored in `webApp/src/practice/grading/spokenNumbers.ts` and pinned by the `spokenNumbers`
 * section of the golden fixture, so the two can't drift (FLA-81).
 */

private val UNITS: Map<String, Int> = mapOf(
    "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
    "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
)

private val TEENS: Map<String, Int> = mapOf(
    "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
    "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
)

private val TENS: Map<String, Int> = mapOf(
    "twenty" to 20,
    "thirty" to 30,
    "forty" to 40,
    "fifty" to 50,
    "sixty" to 60,
    "seventy" to 70,
    "eighty" to 80,
    "ninety" to 90,
)

private val SCALES: Map<String, Long> = mapOf(
    "hundred" to 100L,
    "thousand" to 1_000L,
    "million" to 1_000_000L,
    "billion" to 1_000_000_000L,
)

private fun valueOfWord(token: String): Int? = UNITS[token] ?: TEENS[token] ?: TENS[token]

private fun isNumberWord(token: String): Boolean = valueOfWord(token) != null || SCALES.containsKey(token)

/** Strips surrounding punctuation so "nineteen eighty," still reads as a number word. */
private fun key(token: String): String = token.lowercase().trim('.', ',', '!', '?', ';', ':', '"', '\'')

/**
 * Alternative renderings of [transcript] with spoken numbers written as digits. Empty when the
 * transcript contains no number words, or when every rendering equals the original.
 */
fun spokenNumberVariants(transcript: String): List<String> {
    val tokens = transcript.split(" ").filter { it.isNotEmpty() }
    if (tokens.none { isNumberWord(key(it)) }) return emptyList()

    val runs = numberRuns(tokens)
    if (runs.isEmpty()) return emptyList()

    val arithmetic = render(tokens, runs) { arithmeticValue(it).toString() }
    val concatenated = render(tokens, runs) { concatenatedValue(it) ?: arithmeticValue(it).toString() }

    return listOf(arithmetic, concatenated).distinct().filter { it != transcript && it.isNotBlank() }
}

/** Index ranges of maximal runs of number words, with a joining "and" absorbed when flanked by them. */
private fun numberRuns(tokens: List<String>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    tokens.forEachIndexed { i, raw ->
        val token = key(raw)
        val joins = token == "and" &&
            start >= 0 &&
            i + 1 < tokens.size &&
            isNumberWord(key(tokens[i + 1]))
        when {
            isNumberWord(token) -> if (start < 0) start = i
            joins -> Unit // keep the run open across "and"
            else -> {
                if (start >= 0) runs += start until i
                start = -1
            }
        }
    }
    if (start >= 0) runs += start until tokens.size
    // A trailing "and" can't end a run — "five and" is five, then a word.
    return runs.map { range ->
        var end = range.last
        while (end > range.first && key(tokens[end]) == "and") end--
        range.first..end
    }
}

/** Rebuilds the sentence with each run replaced by [renderRun]'s digits. */
private fun render(tokens: List<String>, runs: List<IntRange>, renderRun: (List<String>) -> String): String {
    val out = mutableListOf<String>()
    var i = 0
    while (i < tokens.size) {
        val run = runs.firstOrNull { it.first == i }
        if (run != null) {
            out += renderRun(tokens.slice(run).map { key(it) })
            i = run.last + 1
        } else {
            out += tokens[i]
            i++
        }
    }
    return out.joinToString(" ")
}

/** Standard accumulation: tens+units add, scales multiply. "one hundred and five" → 105. */
private fun arithmeticValue(run: List<String>): Long {
    var total = 0L
    var current = 0L
    for (token in run) {
        if (token == "and") continue
        val word = valueOfWord(token)
        if (word != null) {
            current += word
            continue
        }
        val scale = SCALES.getValue(token)
        if (scale == 100L) {
            current = (if (current == 0L) 1L else current) * 100
        } else {
            total += (if (current == 0L) 1L else current) * scale
            current = 0
        }
    }
    return total + current
}

/**
 * The year/digit-string reading: split the run into the groups a speaker would pause between and
 * concatenate their digits. "nineteen eighty" → "1980", "nineteen oh five" → "1905".
 *
 * Null when the run contains a scale word — "two thousand" is 2000, never "2" then "1000".
 */
private fun concatenatedValue(run: List<String>): String? {
    if (run.any { SCALES.containsKey(it) }) return null
    val groups = mutableListOf<Int>()
    var i = 0
    while (i < run.size) {
        val token = run[i]
        if (token == "and") {
            i++
            continue
        }
        val value = valueOfWord(token) ?: return null
        // A tens word swallows a following unit: "twenty three" is one group, not two.
        if (TENS.containsKey(token) && i + 1 < run.size) {
            val next = UNITS[run[i + 1]]
            if (next != null && next != 0) {
                groups += value + next
                i += 2
                continue
            }
        }
        groups += value
        i++
    }
    if (groups.size < 2) return null // one group reads the same as arithmetic
    return groups.joinToString("") { it.toString() }
}
