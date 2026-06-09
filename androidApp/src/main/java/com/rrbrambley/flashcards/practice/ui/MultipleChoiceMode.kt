package com.rrbrambley.flashcards.practice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.practice.grading.buildChoices
import com.rrbrambley.flashcards.shared.domain.Flashcard

/**
 * Multiple-choice practice: up to four options (the correct answer + distractors drawn from other
 * cards in the deck via the shared [buildChoices]). On pick, the right/wrong options highlight; "Next"
 * reports the outcome via [onResult]. Choices + selection key on [flashcard], so they're built once
 * per card and reset when the runner advances.
 */
@Composable
fun MultipleChoiceMode(
    flashcard: Flashcard,
    deck: List<Flashcard>,
    onResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = remember(flashcard) { buildChoices(flashcard, deck) }
    val correctIndex = remember(flashcard) { choices.indexOf(flashcard.answer.trim()) }
    var selected by remember(flashcard) { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CardPrompt(flashcard = flashcard)

        choices.forEachIndexed { index, option ->
            ChoiceButton(
                text = option,
                state = choiceState(index = index, selected = selected, correctIndex = correctIndex),
                onClick = { if (selected == null) selected = index },
            )
        }

        if (selected != null) {
            Button(
                onClick = { onResult(selected == correctIndex) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.practice_next))
            }
        }
    }
}

private enum class ChoiceState { Unanswered, Correct, WrongPick, Other }

private fun choiceState(index: Int, selected: Int?, correctIndex: Int): ChoiceState = when {
    selected == null -> ChoiceState.Unanswered
    index == correctIndex -> ChoiceState.Correct
    index == selected -> ChoiceState.WrongPick
    else -> ChoiceState.Other
}

@Composable
private fun ChoiceButton(text: String, state: ChoiceState, onClick: () -> Unit) {
    val container = when (state) {
        ChoiceState.Unanswered -> MaterialTheme.colorScheme.secondaryContainer
        ChoiceState.Correct -> CorrectColor
        ChoiceState.WrongPick -> IncorrectColor
        ChoiceState.Other -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when (state) {
        ChoiceState.Unanswered -> MaterialTheme.colorScheme.onSecondaryContainer
        ChoiceState.Correct, ChoiceState.WrongPick -> Color.White
        ChoiceState.Other -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = state == ChoiceState.Unanswered,
        shape = RoundedCornerShape(14.dp),
        // Keep the same colors when disabled (answered) so the right/wrong highlight stays visible.
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}
