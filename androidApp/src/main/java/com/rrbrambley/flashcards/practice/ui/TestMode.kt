package com.rrbrambley.flashcards.practice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.practice.discussions.DiscussButton
import com.rrbrambley.flashcards.practice.grading.gradeTextAnswer
import com.rrbrambley.flashcards.practice.suggestions.SuggestAnswerAction
import com.rrbrambley.flashcards.shared.domain.Flashcard

private data class TestGrade(val input: String, val correct: Boolean)

/**
 * Text-entry "Test" practice: the user types the answer, which is graded case-insensitively and
 * typo-tolerantly via the shared [gradeTextAnswer]. After submitting, the typed answer + a verdict
 * are revealed (plus the correct answer when wrong); grading scores it via [onGraded] (so the streak
 * badge shows on the verdict) and "Next" advances via [onAdvance]. The two-phase state keys on
 * [flashcard], so it resets when the runner advances to the next card.
 */
@Composable
fun TestMode(
    flashcard: Flashcard,
    onGraded: (Boolean, String?) -> Unit,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
    discussionsEnabled: Boolean = false,
    onDiscuss: () -> Unit = {},
    canSuggest: Boolean = false,
    isGuest: Boolean = false,
) {
    var input by remember(flashcard) { mutableStateOf("") }
    var graded by remember(flashcard) { mutableStateOf<TestGrade?>(null) }
    // Guards an accidental empty submit (keyboard Done or Check) from grading it wrong (FLA-190).
    var confirmingBlank by remember(flashcard) { mutableStateOf(false) }
    // Hold the answer UI until the prompt image is on screen (#302 review); no image → ready at once.
    val hasImage = !flashcard.imageUrl.isNullOrBlank()
    var imageReady by remember(flashcard) { mutableStateOf(!hasImage) }

    fun grade(value: String) {
        val result = TestGrade(
            input = value,
            correct = gradeTextAnswer(value, flashcard.answer, flashcard.alternativeAnswers).correct,
        )
        graded = result
        // Score it now (the verdict is on screen) so the streak badge shows on this answer, not the next card.
        onGraded(result.correct, result.input)
    }

    fun submit() {
        if (input.isBlank()) {
            // Confirm the skip instead of silently grading a blank answer wrong; the user can still
            // submit blank via Confirm (an intentional "I don't know this"), so Check is never disabled.
            confirmingBlank = true
            return
        }
        grade(input)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CardPrompt(flashcard = flashcard, onImageReady = { imageReady = true })

        // Only reveal the answer UI once the prompt image has loaded (#302 review).
        if (!imageReady) return@Column

        val currentGrade = graded
        if (currentGrade == null) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    // Typing again means they didn't want to skip — dismiss the prompt.
                    if (confirmingBlank) confirmingBlank = false
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.practice_test_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.practice_test_check))
            }
            if (confirmingBlank) {
                Text(
                    text = stringResource(R.string.practice_test_blank_confirm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        confirmingBlank = false
                        grade(input)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.practice_test_blank_confirm_action))
                }
            }
        } else {
            TestVerdict(grade = currentGrade, answer = flashcard.answer)
            // Teach the full set of valid responses (FLA-131); shown on either verdict.
            if (flashcard.alternativeAnswers.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.practice_test_also_acceptable,
                        flashcard.alternativeAnswers.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // On a global-deck card graded wrong, offer to suggest the typed answer as acceptable
            // (FLA-134) — but never for a blank answer (a skip can't be a valid alternative, FLA-190).
            if (canSuggest &&
                !currentGrade.correct &&
                currentGrade.input.isNotBlank() &&
                flashcard.cardUid.isNotBlank()
            ) {
                SuggestAnswerAction(
                    cardUid = flashcard.cardUid,
                    suggestedAnswer = currentGrade.input,
                    isGuest = isGuest,
                )
            }
            Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.practice_next))
            }
            // Discussion opens once the answer is revealed (after grading), mirroring web.
            if (discussionsEnabled) {
                DiscussButton(onClick = onDiscuss)
            }
        }
    }
}

@Composable
private fun TestVerdict(grade: TestGrade, answer: String) {
    val color = if (grade.correct) CorrectColor else IncorrectColor
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = grade.input.trim().ifBlank { stringResource(R.string.practice_test_blank) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                if (grade.correct) R.string.practice_test_correct else R.string.practice_test_incorrect,
            ),
            color = color,
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (!grade.correct) {
        Text(
            text = stringResource(R.string.practice_test_answer_label, answer),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal val CorrectColor = Color(0xFF2F9E4A)
internal val IncorrectColor = Color(0xFFD33D3D)
