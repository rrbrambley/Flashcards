import Shared
import SwiftUI

/// The guest landing (FLA-104): the public global-deck catalog. Browse and practice without an
/// account; "Sign in" returns to the auth flow. Tapping a deck starts a session-less practice run.
struct GuestHomeView: View {
    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel: GuestCatalogViewModel
    private let onSignIn: () -> Void

    private struct PracticingDeck: Identifiable {
        let id: Int64
        let mode: String
    }
    @State private var practicing: PracticingDeck?

    init(apiClient: FlashcardApiClient, onSignIn: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: GuestCatalogViewModel(apiClient: apiClient))
        self.onSignIn = onSignIn
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Pick a deck")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Sign in", action: onSignIn)
                    }
                }
                .fullScreenCover(item: $practicing) { deck in
                    PracticeRunnerView(
                        flashcardRepository: container.flashcardRepository,
                        sessionRepository: container.practiceSessionRepository,
                        entry: .guestDeck(deck.id, mode: deck.mode),
                        featureFlagStore: container.featureFlagStore,
                        apiClient: container.apiClient,
                        authService: container.authService
                    )
                }
                .task { await viewModel.load() }
        }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingView()
        case let .loaded(decks) where decks.isEmpty:
            EmptyStateView(title: "Pick a deck", systemImage: "rectangle.stack", message: "No decks are available yet.")
        case let .loaded(decks):
            List {
                Section {
                    ForEach(decks, id: \.id) { deck in
                        Button {
                            practicing = PracticingDeck(id: deck.id, mode: PracticeMode.classic.key)
                        } label: {
                            DeckCard(title: deck.title, category: (deck.tags as? [String])?.first, cardCount: deck.flashcards.count)
                        }
                        .buttonStyle(.plain)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.md, bottom: Spacing.xs, trailing: Spacing.md))
                    }
                } header: {
                    Text("Start studying — no account needed.")
                        .textCase(nil)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .listStyle(.plain)
            .refreshable { await viewModel.load() }
        case .failed:
            ErrorRetryView(message: "Couldn't load the deck catalog.") { Task { await viewModel.load() } }
        }
    }
}
