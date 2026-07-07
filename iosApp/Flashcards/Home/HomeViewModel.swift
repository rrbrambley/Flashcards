import Shared
import SwiftUI

/// Renders the home feed from the shared `HomeRepository` (backend `GET /home`, with an offline
/// fallback derived from cached sessions + static items).
@MainActor
final class HomeViewModel: ObservableObject {
    @Published private(set) var state: LoadState<[HomeData]> = .loading
    /// True when the latest background refresh failed but cached data is still shown — the view
    /// surfaces an unobtrusive banner (parity with Android's "couldn't refresh" snackbar).
    @Published private(set) var refreshFailed = false
    /// Overall practice streak (FLA-106); nil until loaded / when there's no active streak.
    @Published private(set) var streak: Int?

    private let repository: HomeRepository
    private let apiClient: FlashcardApiClient

    init(repository: HomeRepository, apiClient: FlashcardApiClient) {
        self.repository = repository
        self.apiClient = apiClient
    }

    /// Best-effort overall-streak fetch; a failure (or no streak) just leaves the badge hidden.
    func loadStreak() async {
        if let result = try? await apiClient.getStreaks(tz: TimeZone.current.identifier) {
            streak = Int(result.overall.current)
        }
    }

    /// The offline-first feed emits the local (cached) data first, then the backend feed. The stream
    /// stays alive through a backend outage (FLA-210): a failed refresh arrives as
    /// `HomeFeed.refreshFailed` (→ banner) with the local feed intact, so local changes keep flowing.
    /// `catch` only fires on a truly fatal stream error.
    func observe() async {
        do {
            for try await feed in asyncThrowingStream(BridgingKt.homeAdapter(repository)) {
                guard let feed = feed as? HomeFeed else { continue }
                state = .loaded(feed.cards)
                refreshFailed = feed.refreshFailed
            }
        } catch {
            markRefreshFailed()
        }
    }

    /// Pull-to-refresh: re-subscribe and await the backend feed (the stream emits cached data first,
    /// then the server feed), so the spinner reflects a real refresh rather than just the cache.
    func refresh() async {
        do {
            var emissions = 0
            for try await feed in asyncThrowingStream(BridgingKt.homeAdapter(repository)) {
                guard let feed = feed as? HomeFeed else { continue }
                state = .loaded(feed.cards)
                refreshFailed = feed.refreshFailed
                emissions += 1
                // First emission is the cache, second is the backend feed — stop once refreshed.
                if emissions >= 2 { return }
            }
        } catch {
            markRefreshFailed()
        }
    }

    private func markRefreshFailed() {
        if case .loaded = state {
            refreshFailed = true
        } else {
            state = .failed("Couldn't load your home feed. Check your connection.")
        }
    }
}
