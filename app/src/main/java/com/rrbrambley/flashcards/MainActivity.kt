package com.rrbrambley.flashcards

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.create.ui.CreateDeckScreen
import com.rrbrambley.flashcards.create.ui.CreateDeckViewModel
import com.rrbrambley.flashcards.home.domain.HomeButtonAction
import com.rrbrambley.flashcards.home.ui.HomeScreen
import com.rrbrambley.flashcards.library.ui.LibraryScreen
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlashcardsTheme {
                HomeScaffolding()
            }
        }
    }
}

private enum class BottomDestination(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Default.Home),
    New("New", Icons.Default.Add),
    Library("Library", Icons.Default.Menu)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffolding(
    createDeckViewModel: CreateDeckViewModel = hiltViewModel(),
) {
    var currentDestination by rememberSaveable { mutableStateOf(BottomDestination.Home) }
    val createDeckUiState by createDeckViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(createDeckUiState.deckSaved) {
        if (createDeckUiState.deckSaved) {
            currentDestination = BottomDestination.Home
            createDeckViewModel.onDeckSavedHandled()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentDestination) {
                            BottomDestination.Home -> "Flashcards"
                            BottomDestination.New -> "New deck"
                            BottomDestination.Library -> "Library"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    if (currentDestination == BottomDestination.New) {
                        IconButton(onClick = createDeckViewModel::finishDeckCreation) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Finish deck creation",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                BottomDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination == destination,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentDestination == BottomDestination.New) {
                FloatingActionButton(onClick = createDeckViewModel::addDraftCard) {
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
            when (currentDestination) {
                BottomDestination.Home -> HomeScreen(
                    onButtonAction = { action ->
                        when (action) {
                            HomeButtonAction.NavigateToPractice -> {
                                val intent = Intent(context, FlashcardsActivity::class.java)
                                context.startActivity(intent)
                            }
                            HomeButtonAction.CreateNewFlashcardSet -> {
                                currentDestination = BottomDestination.New
                            }
                        }
                    },
                )
                BottomDestination.New -> CreateDeckScreen(
                    createDeckViewModel = createDeckViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
                BottomDestination.Library -> LibraryScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
