package com.rrbrambley.flashcards.guest.ui

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.createFlashcardHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuestCatalogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_startsAsLoading() {
        // The init load() coroutine is queued on the (not-yet-advanced) test dispatcher, so the
        // initial state is observable before any request runs.
        val viewModel = GuestCatalogViewModel(catalogClient(EMPTY_PAGE))

        assertEquals(GuestCatalogUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun load_success_emitsLoadedWithTheCatalogDecks() = runTest(testDispatcher) {
        val body = """{"items":[{"title":"Flags of the World","flashcards":[]}],"nextCursor":null}"""
        val viewModel = GuestCatalogViewModel(catalogClient(body))

        // Await the terminal state (joins the off-scheduler MockEngine request deterministically).
        val state = viewModel.uiState.first { it is GuestCatalogUiState.Loaded } as GuestCatalogUiState.Loaded
        assertEquals(listOf("Flags of the World"), state.decks.map { it.title })
    }

    @Test
    fun load_failure_emitsFailed() = runTest(testDispatcher) {
        val viewModel = GuestCatalogViewModel(failingClient())

        assertTrue(viewModel.uiState.first { it is GuestCatalogUiState.Failed } is GuestCatalogUiState.Failed)
    }

    @Test
    fun retry_afterFailure_reloadsTheCatalog() = runTest(testDispatcher) {
        // One engine whose behaviour flips between the two load() calls (a flag rather than a request
        // counter, since 5xx responses are retried, so the first load makes several requests).
        var shouldFail = true
        val engine = MockEngine {
            if (shouldFail) {
                respondError(HttpStatusCode.InternalServerError)
            } else {
                respond(EMPTY_PAGE, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
        }
        val viewModel = GuestCatalogViewModel(clientOf(engine))

        viewModel.uiState.first { it is GuestCatalogUiState.Failed }

        shouldFail = false
        viewModel.load()
        assertTrue(viewModel.uiState.first { it is GuestCatalogUiState.Loaded } is GuestCatalogUiState.Loaded)
    }

    // --- helpers ---

    private fun catalogClient(bodyJson: String): FlashcardApiClient =
        clientOf(MockEngine { respond(bodyJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json")) })

    private fun failingClient(): FlashcardApiClient =
        clientOf(MockEngine { respondError(HttpStatusCode.InternalServerError) })

    private fun clientOf(engine: MockEngine): FlashcardApiClient =
        FlashcardApiClient(createFlashcardHttpClient(engine), baseUrl = "http://localhost") { null }

    private companion object {
        const val EMPTY_PAGE = """{"items":[],"nextCursor":null}"""
    }
}
