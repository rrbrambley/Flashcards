import Shared
import SwiftUI

/// Library tab: the user's decks + the global catalog, offline-first, with title search and
/// A–Z / recently-practiced sorting. Tapping a deck opens the edit sheet (FLA-45); swipe to
/// delete (FLA-46).
struct LibraryView: View {
    @StateObject private var viewModel: LibraryViewModel
    private let flashcardRepository: FlashcardRepository
    private let sessionRepository: PracticeSessionRepository
    private let imageUploader: ImageUploader

    /// `.sheet(item:)` needs an Identifiable; a deck id wrapper presents the edit sheet.
    private struct EditingDeck: Identifiable { let id: Int64 }
    @State private var editing: EditingDeck?

    /// The deck awaiting delete confirmation (editable decks only).
    private struct PendingDelete: Identifiable { let id: Int64; let title: String }
    @State private var pendingDelete: PendingDelete?

    /// The deck being practiced (presents the full-screen practice run).
    private struct PracticingDeck: Identifiable { let id: Int64 }
    @State private var practicing: PracticingDeck?

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        imageUploader: ImageUploader
    ) {
        self.flashcardRepository = flashcardRepository
        self.sessionRepository = sessionRepository
        self.imageUploader = imageUploader
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
                    entry: .deck(item.id)
                )
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

    private var emptyState: some View {
        // Distinguish an empty library from a search with no matches.
        viewModel.hasAnyDecks
            ? EmptyStateView(title: "No matches", systemImage: "magnifyingglass", message: "No decks match your search.")
            : EmptyStateView(title: "No decks yet", systemImage: "rectangle.stack", message: "Create a set from the New tab.")
    }

    private func deckList(_ decks: [FlashcardDeck]) -> some View {
        List {
            ForEach(decks, id: \.id) { deck in
                Button {
                    editing = EditingDeck(id: deck.id)
                } label: {
                    DeckCard(title: deck.title, cardCount: deck.flashcards.count)
                }
                .buttonStyle(.plain)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.md, bottom: Spacing.xs, trailing: Spacing.md))
                .swipeActions(edge: .leading) {
                    Button {
                        practicing = PracticingDeck(id: deck.id)
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
        }
    }
}
