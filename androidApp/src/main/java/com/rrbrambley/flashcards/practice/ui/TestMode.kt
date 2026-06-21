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
import com.rrbrambley.flashcards.shared.domain.Flashcard

private data class TestGrade(val input: String, val correct: Boolean)

/**
 * Text-entry "Test" practice: the user types the answer, which is graded case-insensitively and
 * typo-tolerantly via the shared [gradeTextAnswer]. After submitting, the typed answer + a verdict
 * are revealed (plus the correct answer when wrong); "Next" reports the outcome via [onResult]. The
 * two-phase state keys on [flashcard], so it resets when the runner advances to the next card.
 */
@Composable
fun TestMode(
    flashcard: Flashcard,
    onResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    discussionsEnabled: Boolean = false,
    onDiscuss: () -> Unit = {},
) {
    var input by remember(flashcard) { mutableStateOf("") }
    var graded by remember(flashcard) { mutableStateOf<TestGrade?>(null) }

    fun submit() {
        graded = TestGrade(
            input = input,
            correct = gradeTextAnswer(input, flashcard.answer, flashcard.alternativeAnswers).correct,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CardPrompt(flashcard = flashcard)

        val currentGrade = graded
        if (currentGrade == null) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.practice_test_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.practice_test_check))
            }
        } else {
            TestVerdict(grade = currentGrade, answer = flashcard.answer)
            Button(onClick = { onResult(currentGrade.correct) }, modifier = Modifier.fillMaxWidth()) {
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
