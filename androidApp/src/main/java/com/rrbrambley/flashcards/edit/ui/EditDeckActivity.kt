package com.rrbrambley.flashcards.edit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditDeckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val deckId = intent.getLongExtra(DECK_ID_EXTRA, MISSING_DECK_ID)
        if (deckId == MISSING_DECK_ID) {
            finish()
            return
        }

        setContent {
            FlashcardsTheme {
                EditDeckScaffold(
                    deckId = deckId,
                    onFinish = ::finish,
                )
            }
        }
    }

    companion object {
        const val DECK_ID_EXTRA = "com.rrbrambley.flashcards.DECK_ID"
        private const val MISSING_DECK_ID = -1L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDeckScaffold(
    deckId: Long,
    onFinish: () -> Unit,
    editDeckViewModel: EditDeckViewModel = hiltViewModel(),
) {
    val uiState by editDeckViewModel.uiState.collectAsState()
    val showUnsavedChangesDialog = remember { mutableStateOf(false) }

    LaunchedEffect(deckId) {
        editDeckViewModel.loadDeck(deckId)
    }

    LaunchedEffect(uiState.deckSaved) {
        if (uiState.deckSaved) {
            editDeckViewModel.onDeckSavedHandled()
            onFinish()
        }
    }

    fun attemptFinish() {
        if (uiState.isDirty) {
            showUnsavedChangesDialog.value = true
        } else {
            onFinish()
        }
    }

    BackHandler(onBack = ::attemptFinish)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditable) "Edit Flashcards" else "Flashcards",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = ::attemptFinish) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close edit deck",
                        )
                    }
                },
                actions = {
                    if (uiState.isEditable) {
                        IconButton(onClick = editDeckViewModel::finishDeckEditing) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save deck changes",
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.isEditable) {
                FloatingActionButton(onClick = editDeckViewModel::addDraftCard) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add flashcard",
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            EditDeckScreen(
                deckId = deckId,
                editDeckViewModel = editDeckViewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showUnsavedChangesDialog.value) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog.value = false },
            title = { Text("Unsaved changes") },
            text = { Text("You have unsaved edits. Save your changes before leaving, or keep editing.") },
            confirmButton = {
                TextButton(onClick = editDeckViewModel::finishDeckEditing) {
                    Text("Save changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog.value = false }) {
                    Text("Keep editing")
                }
            },
        )
    }
}
