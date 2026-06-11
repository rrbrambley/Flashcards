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

    private let repository: HomeRepository

    init(repository: HomeRepository) {
        self.repository = repository
    }

    /// The offline-first feed emits the cached data first, then the backend feed. If the backend
    /// fetch fails after we've shown cached data we keep it and flag a failed refresh; a failure with
    /// nothing cached is a hard error.
    func observe() async {
        do {
            for try await items in asyncThrowingStream(BridgingKt.homeAdapter(repository)) {
                refreshFailed = false
                state = .loaded((items as? [HomeData]) ?? [])
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
            for try await items in asyncThrowingStream(BridgingKt.homeAdapter(repository)) {
                refreshFailed = false
                state = .loaded((items as? [HomeData]) ?? [])
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
