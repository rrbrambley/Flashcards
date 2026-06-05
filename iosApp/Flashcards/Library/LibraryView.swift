import Shared
import SwiftUI

/// Library tab: the user's decks + the global catalog, offline-first, with title search and
/// A–Z / recently-practiced sorting. (Deck actions arrive in FLA-44/45/46.)
struct LibraryView: View {
    @StateObject private var viewModel: LibraryViewModel

    init(flashcardRepository: FlashcardRepository, sessionRepository: PracticeSessionRepository) {
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
                DeckCard(title: deck.title, cardCount: deck.flashcards.count)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.md, bottom: Spacing.xs, trailing: Spacing.md))
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
