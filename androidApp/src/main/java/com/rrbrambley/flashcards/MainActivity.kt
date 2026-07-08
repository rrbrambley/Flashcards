package com.rrbrambley.flashcards

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.auth.AuthState
import com.rrbrambley.flashcards.auth.AuthViewModel
import com.rrbrambley.flashcards.auth.ui.AuthHost
import com.rrbrambley.flashcards.create.ui.CreateDeckScreen
import com.rrbrambley.flashcards.create.ui.CreateDeckViewModel
import com.rrbrambley.flashcards.edit.ui.EditDeckActivity
import com.rrbrambley.flashcards.guest.ui.GuestCatalogScreen
import com.rrbrambley.flashcards.home.ui.HomeScreen
import com.rrbrambley.flashcards.library.ui.LibraryScreen
import com.rrbrambley.flashcards.practice.ui.FlashcardsActivity
import com.rrbrambley.flashcards.profile.ui.ProfileActivity
import com.rrbrambley.flashcards.shared.domain.HomeButtonAction
import com.rrbrambley.flashcards.shared.domain.PracticeMode
import com.rrbrambley.flashcards.ui.theme.FlashcardsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlashcardsTheme {
                FlashcardsApp()
            }
        }
    }
}

/**
 * Gates the app: logged-out shows the auth flow (or the guest catalog, if the user chose to browse
 * without an account); logged-in shows the main scaffolding.
 */
@Composable
private fun FlashcardsApp(authViewModel: AuthViewModel = hiltViewModel()) {
    val authState by authViewModel.authState.collectAsState()
    var browsingAsGuest by rememberSaveable { mutableStateOf(false) }

    // Once signed in, drop the guest flag so a later logout returns to the auth screen, not the catalog.
    LaunchedEffect(authState) {
        if (authState == AuthState.LoggedIn) browsingAsGuest = false
    }

    when (authState) {
        AuthState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        AuthState.LoggedOut -> if (browsingAsGuest) {
            GuestScaffolding(onSignIn = { browsingAsGuest = false })
        } else {
            AuthHost(
                authViewModel = authViewModel,
                modifier = Modifier.fillMaxSize(),
                onBrowseAsGuest = { browsingAsGuest = true },
            )
        }

        AuthState.LoggedIn -> HomeScaffolding()
    }
}

/** The guest shell: the public catalog with a "Sign in" affordance; tapping a deck starts practice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestScaffolding(onSignIn: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.guest_catalog_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    TextButton(onClick = onSignIn) {
                        Text(stringResource(R.string.guest_sign_in))
                    }
                },
            )
        },
    ) { innerPadding ->
        GuestCatalogScreen(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            onPracticeDeck = { deck ->
                val intent = Intent(context, FlashcardsActivity::class.java).apply {
                    putExtra(FlashcardsActivity.DECK_ID_EXTRA, deck.id)
                    putExtra(FlashcardsActivity.GUEST_EXTRA, true)
                    putExtra(FlashcardsActivity.MODE_EXTRA, PracticeMode.Classic.key)
                }
                context.startActivity(intent)
            },
        )
    }
}

private enum class BottomDestination(@StringRes val labelRes: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Default.Home),
    New(R.string.nav_new, Icons.Default.Add),
    Library(R.string.nav_library, Icons.Default.Menu),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffolding(
    createDeckViewModel: CreateDeckViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    var currentDestination by rememberSaveable { mutableStateOf(BottomDestination.Home) }
    var showAccountMenu by remember { mutableStateOf(false) }
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
                        text = stringResource(
                            when (currentDestination) {
                                BottomDestination.Home -> R.string.flashcards
                                BottomDestination.New -> R.string.main_title_new_deck
                                BottomDestination.Library -> R.string.nav_library
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    when (currentDestination) {
                        BottomDestination.New -> {
                            IconButton(
                                onClick = createDeckViewModel::finishDeckCreation,
                                enabled = !createDeckUiState.isSaving,
                            ) {
                                if (createDeckUiState.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.main_cd_finish_deck_creation),
                                    )
                                }
                            }
                        }
                        BottomDestination.Home,
                        BottomDestination.Library,
                        -> {
                            IconButton(onClick = { showAccountMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.main_cd_account_menu),
                                )
                            }
                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.main_profile)) },
                                    onClick = {
                                        showAccountMenu = false
                                        context.startActivity(Intent(context, ProfileActivity::class.java))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.main_log_out)) },
                                    onClick = {
                                        showAccountMenu = false
                                        authViewModel.logout()
                                    },
                                )
                            }
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
                        onClick = {
                            currentDestination = destination
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            when (currentDestination) {
                BottomDestination.New -> {
                    FloatingActionButton(onClick = createDeckViewModel::addDraftCard) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_flashcard),
                        )
                    }
                }
                BottomDestination.Home,
                BottomDestination.Library,
                -> Unit
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
                            is HomeButtonAction.NavigateToPractice -> {
                                val intent = Intent(context, FlashcardsActivity::class.java).apply {
                                    putExtra(FlashcardsActivity.DECK_ID_EXTRA, action.deckId)
                                }
                                context.startActivity(intent)
                            }
                            HomeButtonAction.CreateNewFlashcardSet -> {
                                currentDestination = BottomDestination.New
                            }
                            is HomeButtonAction.ContinuePractice -> {
                                val intent = Intent(context, FlashcardsActivity::class.java).apply {
                                    putExtra(FlashcardsActivity.SESSION_ID_EXTRA, action.sessionId)
                                }
                                context.startActivity(intent)
                            }
                        }
                    },
                )
                BottomDestination.New -> CreateDeckScreen(
                    createDeckViewModel = createDeckViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
                BottomDestination.Library -> LibraryScreen(
                    modifier = Modifier.fillMaxSize(),
                    onPracticeDeck = { sessionId ->
                        val intent = Intent(context, FlashcardsActivity::class.java).apply {
                            putExtra(FlashcardsActivity.SESSION_ID_EXTRA, sessionId)
                        }
                        context.startActivity(intent)
                    },
                    onEditDeck = { deck ->
                        val intent = Intent(context, EditDeckActivity::class.java).apply {
                            putExtra(EditDeckActivity.DECK_ID_EXTRA, deck.id)
                        }
                        context.startActivity(intent)
                    },
                )
            }
        }
    }
}
