import Shared
import SwiftUI

/// Library tab: the user's decks + the global catalog, offline-first, with title search and
/// A–Z / recently-practiced sorting. Tapping a deck opens a deck-actions sheet — Practice / Edit /
/// Delete (parity with Android, FLA-58); Practice and Delete are also row-swipe shortcuts.
struct LibraryView: View {
    @EnvironmentObject private var featureFlagStore: FeatureFlagStore
    @StateObject private var viewModel: LibraryViewModel
    private let flashcardRepository: FlashcardRepository
    private let sessionRepository: PracticeSessionRepository
    private let imageUploader: ImageUploader
    private let apiClient: FlashcardApiClient

    /// The tapped deck, presenting the actions sheet. Holds the whole deck (title / card count /
    /// ownership drive the sheet's contents).
    private struct SelectedDeck: Identifiable {
        let deck: FlashcardDeck
        var id: Int64 { deck.id }
    }
    @State private var selectedDeck: SelectedDeck?

    /// `.sheet(item:)` needs an Identifiable; a deck id wrapper presents the edit sheet.
    private struct EditingDeck: Identifiable { let id: Int64 }
    @State private var editing: EditingDeck?

    /// The deck awaiting delete confirmation (editable decks only).
    private struct PendingDelete: Identifiable { let id: Int64; let title: String }
    @State private var pendingDelete: PendingDelete?

    /// The deck being practiced (presents the full-screen practice run).
    private struct PracticingDeck: Identifiable {
        let id: Int64
        let mode: String
    }
    @State private var practicing: PracticingDeck?

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        imageUploader: ImageUploader,
        apiClient: FlashcardApiClient
    ) {
        self.flashcardRepository = flashcardRepository
        self.sessionRepository = sessionRepository
        self.imageUploader = imageUploader
        self.apiClient = apiClient
        _viewModel = StateObject(
            wrappedValue: LibraryViewModel(
                flashcardRepository: flashcardRepository,
                sessionRepository: sessionRepository
            )
        )
    }

    var body: some View {
        content
            .navigationTitle("Library")
            .searchable(text: $viewModel.searchQuery, prompt: "Search decks")
            .toolbar { sortMenu }
            .sheet(item: $editing) { item in
                EditDeckView(repository: flashcardRepository, imageUploader: imageUploader, deckId: item.id)
            }
            .fullScreenCover(item: $practicing) { item in
                PracticeView(
                    flashcardRepository: flashcardRepository,
                    sessionRepository: sessionRepository,
                    entry: .deck(item.id, mode: item.mode),
                    featureFlagStore: featureFlagStore,
                    apiClient: apiClient
                )
            }
            // Deck-actions sheet on tap (parity with Android): Practice / Edit / Delete. Edit opens
            // the (read-only for the global deck) edit screen; Delete is owned-decks only.
            .confirmationDialog(
                selectedDeck?.deck.title ?? "",
                isPresented: Binding(get: { selectedDeck != nil }, set: { if !$0 { selectedDeck = nil } }),
                titleVisibility: .visible,
                presenting: selectedDeck
            ) { selected in
                // One button per mode (the iOS take on the Android mode chooser).
                if !selected.deck.flashcards.isEmpty {
                    ForEach(PracticeMode.allCases) { mode in
                        Button("Practice (\(mode.label))") {
                            practicing = PracticingDeck(id: selected.deck.id, mode: mode.rawValue)
                        }
                    }
                }
                Button("Edit deck") { editing = EditingDeck(id: selected.deck.id) }
                if selected.deck.isEditable {
                    Button("Delete deck", role: .destructive) {
                        pendingDelete = PendingDelete(id: selected.deck.id, title: selected.deck.title)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: { selected in
                Text("^[\(selected.deck.flashcards.count) card](inflect: true)")
            }
            .confirmationDialog(
                "Delete “\(pendingDelete?.title ?? "")”?",
                isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
                titleVisibility: .visible,
                presenting: pendingDelete
            ) { deck in
                Button("Delete", role: .destructive) { Task { await viewModel.deleteDeck(deck.id) } }
                Button("Cancel", role: .cancel) {}
            } message: { _ in
                Text("This deck and its cards will be permanently deleted.")
            }
            .alert("Couldn't delete deck", isPresented: Binding(get: { viewModel.deleteError != nil }, set: { if !$0 { viewModel.deleteError = nil } })) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.deleteError ?? "")
            }
            .task { await viewModel.observeDecks() }
            .task { await viewModel.observeLastPracticed() }
            .task { await viewModel.observeRefreshFailures() }
            .safeAreaInset(edge: .bottom) {
                // Only banner over an actual deck list; the empty state has its own offline copy.
                if viewModel.refreshFailed && viewModel.hasAnyDecks {
                    RefreshFailedBanner(message: "Couldn't refresh your decks. Showing your saved library.")
                }
            }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingView()
        case let .loaded(decks):
            if decks.isEmpty {
                emptyState
            } else {
                deckList(decks)
            }
        case let .failed(message):
            ErrorRetryView(message: message) { Task { await viewModel.refresh() } }
        }
    }

    @ViewBuilder private var emptyState: some View {
        if viewModel.hasAnyDecks {
            // Decks exist, but the active search matched none.
            EmptyStateView(title: "No matches", systemImage: "magnifyingglass", message: "No decks match your search.")
        } else if viewModel.refreshFailed {
            // Nothing cached and the refresh failed (offline / server down) — not "you have none".
            EmptyStateView(
                title: "Can't reach the server",
                systemImage: "wifi.slash",
                message: "Check your connection and pull to refresh."
            )
        } else {
            EmptyStateView(title: "No decks yet", systemImage: "rectangle.stack", message: "Create a set from the New tab.")
        }
    }

    private func deckList(_ decks: [FlashcardDeck]) -> some View {
        List {
            ForEach(decks, id: \.id) { deck in
                Button {
                    selectedDeck = SelectedDeck(deck: deck)
                } label: {
                    DeckCard(
                        title: deck.title,
                        category: (deck.tags as? [String])?.first,
                        cardCount: deck.flashcards.count
                    )
                }
                .buttonStyle(.plain)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.md, bottom: Spacing.xs, trailing: Spacing.md))
                .swipeActions(edge: .leading) {
                    // Quick swipe-to-practice uses Classic; tap the deck to choose another mode.
                    Button {
                        practicing = PracticingDeck(id: deck.id, mode: PracticeMode.classic.rawValue)
                    } label: {
                        Label("Practice", systemImage: "play.fill")
                    }
                    .tint(.green)
                }
                .swipeActions(edge: .trailing) {
                    // The global catalog deck isn't deletable.
                    if deck.isEditable {
                        Button(role: .destructive) {
                            pendingDelete = PendingDelete(id: deck.id, title: deck.title)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .refreshable { await viewModel.refresh() }
    }

    private var sortMenu: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                Picker("Sort", selection: $viewModel.sortOrder) {
                    ForEach(DeckSortOrder.allCases) { order in
                        Text(order.label).tag(order)
                    }
                }
            } label: {
                Image(systemName: "arrow.up.arrow.down")
            }
            .accessibilityLabel("Sort decks")
        }
    }
}
