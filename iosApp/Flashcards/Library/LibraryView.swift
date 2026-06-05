import Shared
import SwiftUI

/// Library tab: the user's decks + the global catalog, offline-first. (Search + sort arrive in
/// FLA-43; deck actions in FLA-44/45/46.)
struct LibraryView: View {
    @StateObject private var viewModel: LibraryViewModel

    init(repository: FlashcardRepository) {
        _viewModel = StateObject(wrappedValue: LibraryViewModel(repository: repository))
    }

    var body: some View {
        content
            .navigationTitle("Library")
            .task { await viewModel.observe() }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingView()
        case let .loaded(decks):
            if decks.isEmpty {
                EmptyStateView(
                    title: "No decks yet",
                    systemImage: "rectangle.stack",
                    message: "Create a set from the New tab."
                )
            } else {
                deckList(decks)
            }
        case let .failed(message):
            ErrorRetryView(message: message) { Task { await viewModel.refresh() } }
        }
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
}
