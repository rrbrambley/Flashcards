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
        let shuffle: Bool
        let questionCount: Int32?
        let gradeAtEnd: Bool
        let timeLimitSeconds: Int32?
    }
    @State private var practicing: PracticingDeck?

    /// The deck being configured before practice (presents the mode + Questions + Shuffle sheet).
    private struct ConfiguringDeck: Identifiable {
        let id: Int64
        let title: String
        let cardCount: Int
    }
    @State private var configuring: ConfiguringDeck?
    // Deferred until the config sheet finishes dismissing, so the full-screen run presents cleanly.
    @State private var pendingStart: PracticingDeck?

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
                PracticeRunnerView(
                    flashcardRepository: flashcardRepository,
                    sessionRepository: sessionRepository,
                    entry: .deck(
                        item.id, mode: item.mode, shuffle: item.shuffle,
                        questionCount: item.questionCount, gradeAtEnd: item.gradeAtEnd,
                        timeLimitSeconds: item.timeLimitSeconds
                    ),
                    featureFlagStore: featureFlagStore,
                    apiClient: apiClient
                )
            }
            // Configure the run (mode + Questions + Shuffle) before launching it (FLA-200/219). Present
            // the full-screen run only after this sheet has fully dismissed, via onDismiss + pendingStart.
            .sheet(item: $configuring, onDismiss: {
                if let pendingStart {
                    practicing = pendingStart
                    self.pendingStart = nil
                }
            }) { item in
                PracticeConfigView(
                    deckTitle: item.title,
                    availableModes: PracticeMode.available(flags: featureFlagStore.flags),
                    maxQuestions: item.cardCount,
                    // Fail-open like the mode gating: offered unless the flag is explicitly off.
                    questionCountEnabled: featureFlagStore.flags[FeatureFlag.practiceQuestionCount] != false,
                    gradeAtEndEnabled: featureFlagStore.flags[FeatureFlag.practiceGradeAtEnd] != false,
                    timerEnabled: featureFlagStore.flags[FeatureFlag.practiceTimer] != false
                ) { mode, shuffle, questionCount, gradeAtEnd, timeLimitSeconds in
                    pendingStart = PracticingDeck(
                        id: item.id, mode: mode, shuffle: shuffle,
                        questionCount: questionCount, gradeAtEnd: gradeAtEnd,
                        timeLimitSeconds: timeLimitSeconds
                    )
                    configuring = nil
                }
            }
            // Deck-actions sheet on tap (parity with Android): Practice / Edit / Delete. Edit opens
            // the (read-only for the global deck) edit screen; Delete is owned-decks only.
            .confirmationDialog(
                selectedDeck?.deck.title ?? "",
                isPresented: Binding(get: { selectedDeck != nil }, set: { if !$0 { selectedDeck = nil } }),
                titleVisibility: .visible,
                presenting: selectedDeck
            ) { selected in
                // "Practice" opens the configure sheet (mode + Shuffle); a confirmationDialog can't
                // host a toggle, so the choices moved into PracticeConfigView (FLA-200).
                if !selected.deck.flashcards.isEmpty {
                    Button("Practice") {
                        configuring = ConfiguringDeck(
                            id: selected.deck.id,
                            title: selected.deck.title,
                            cardCount: selected.deck.flashcards.count
                        )
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
                    // Quick swipe-to-practice uses Classic + saved order; tap the deck to configure
                    // the mode + Shuffle (FLA-200).
                    Button {
                        practicing = PracticingDeck(
                            id: deck.id, mode: PracticeMode.classic.key,
                            shuffle: false, questionCount: nil, gradeAtEnd: false, timeLimitSeconds: nil
                        )
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
                    ForEach(DeckSortOrder.entries) { order in
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
