import Shared
import SwiftUI

/// Home tab: the server-computed feed (with offline fallback) — Continue practice / Practice the
/// global deck / Create a set — plus the account menu (Log out). Mirrors the Android home.
struct HomeView: View {
    @EnvironmentObject private var container: AppContainer
    @StateObject private var viewModel: HomeViewModel
    private let onCreateDeck: () -> Void

    /// `.fullScreenCover(item:)` needs an Identifiable; wraps the practice entry the feed launches.
    private struct PracticePresentation: Identifiable {
        let id = UUID()
        let entry: PracticeEntry
    }
    @State private var practice: PracticePresentation?
    @State private var showProfile = false

    init(repository: HomeRepository, apiClient: FlashcardApiClient, onCreateDeck: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: HomeViewModel(repository: repository, apiClient: apiClient))
        self.onCreateDeck = onCreateDeck
    }

    var body: some View {
        content
            .navigationTitle("Home")
            .toolbar { accountMenu }
            .fullScreenCover(item: $practice) { presentation in
                PracticeView(
                    flashcardRepository: container.flashcardRepository,
                    sessionRepository: container.practiceSessionRepository,
                    entry: presentation.entry,
                    featureFlagStore: container.featureFlagStore,
                    apiClient: container.apiClient
                )
            }
            .sheet(isPresented: $showProfile) {
                ProfileView(
                    service: container.profileService,
                    avatarSelectionEnabled: container.featureFlagStore.isEnabled(FeatureFlag.avatarSelection)
                )
            }
            .task { await viewModel.observe() }
            .task { await viewModel.loadStreak() }
            .safeAreaInset(edge: .bottom) {
                if viewModel.refreshFailed {
                    RefreshFailedBanner(message: "Couldn't refresh. Showing your saved feed.")
                }
            }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingView()
        case let .loaded(items):
            if items.isEmpty {
                EmptyStateView(title: "Home", systemImage: "house", message: "Your practice feed will appear here.")
            } else {
                feed(items)
            }
        case let .failed(message):
            ErrorRetryView(message: message) { Task { await viewModel.refresh() } }
        }
    }

    private func feed(_ items: [HomeData]) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                // Overall practice streak (FLA-106), pinned above the feed when active.
                if let streak = viewModel.streak, streak > 0 {
                    StreakBadge(streak: streak)
                }
                // Group consecutive cards under their section header (FLA-96); header-less for nil.
                ForEach(Array(HomeDataKt.groupHomeBySection(items: items).enumerated()), id: \.offset) { _, group in
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        if let section = group.section {
                            Text(section.uppercased())
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundStyle(.secondary)
                        }
                        ForEach(Array(group.items.enumerated()), id: \.offset) { _, item in
                            FeedCard(item: item) { action in handle(action) }
                        }
                    }
                }
            }
            .padding(Spacing.md)
        }
        .refreshable { await viewModel.refresh() }
    }

    private func handle(_ action: HomeButtonAction) {
        if action is HomeButtonActionCreateNewFlashcardSet {
            onCreateDeck()
        } else if let resume = action as? HomeButtonActionContinuePractice {
            practice = PracticePresentation(entry: .session(resume.sessionId))
        } else if let practiceDeck = action as? HomeButtonActionNavigateToPractice {
            // The backend/offline layer resolves which deck (the featured global deck) and its id.
            // Featured practice from Home uses Classic; the Library offers the mode chooser.
            practice = PracticePresentation(entry: .deck(practiceDeck.deckId, mode: PracticeMode.classic.key))
        }
    }

    private var accountMenu: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                Button("Profile", systemImage: "person.crop.circle") { showProfile = true }
                Button("Log out", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) {
                    // Clears the Keychain (and revokes server-side) → RootView returns to sign-in.
                    Task { try? await container.authService.logout() }
                }
            } label: {
                Image(systemName: "person.crop.circle")
            }
            .accessibilityLabel("Account")
        }
    }
}

/// A single home-feed card: a title, optional in-progress session detail, and an action button.
private struct FeedCard: View {
    let item: HomeData
    let onAction: (HomeButtonAction) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(item.title)
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let session = item.session {
                SessionDetail(session: session)
            }
            if let button = item.button {
                Button(button.message) { onAction(button.action) }
                    .buttonStyle(.primary)
            }
        }
        .padding(Spacing.md)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: CornerRadius.card))
    }
}

/// Mode + score + a progress bar for an in-progress session, shown on its "continue" home card.
private struct SessionDetail: View {
    let session: HomeSessionInfo

    var body: some View {
        let total = Int(session.totalCards)
        let current = Int(session.currentCardIndex)
        let modeLabel = PracticeMode.companion.fromKey(key: session.mode).label

        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.sm) {
                Text(modeLabel)
                    .font(.caption2)
                    .fontWeight(.semibold)
                    .padding(.horizontal, Spacing.sm)
                    .padding(.vertical, 2)
                    .background(Color.accentColor.opacity(0.15), in: Capsule())
                    .foregroundStyle(Color.accentColor)
                Text("✓ \(session.numCorrect)").font(.caption).fontWeight(.medium).foregroundStyle(.green)
                Text("✗ \(session.numIncorrect)").font(.caption).fontWeight(.medium).foregroundStyle(.red)
                if total > 0 {
                    Spacer()
                    Text("\(SessionProgress.shared.position(currentCardIndex: session.currentCardIndex, total: session.totalCards)) of \(total)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            if total > 0 {
                ProgressView(value: Double(current), total: Double(total))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
