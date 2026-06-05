import Shared
import SwiftUI

/// Renders the home feed from the shared `HomeRepository` (backend `GET /home`, with an offline
/// fallback derived from cached sessions + static items).
@MainActor
final class HomeViewModel: ObservableObject {
    @Published private(set) var state: LoadState<[HomeData]> = .loading

    private let repository: HomeRepository

    init(repository: HomeRepository) {
        self.repository = repository
    }

    func observe() async {
        for await items in asyncStream(BridgingKt.homeAdapter(repository)) {
            state = .loaded((items as? [HomeData]) ?? [])
        }
    }

    /// Pull-to-refresh: re-subscribe (which re-fetches) and await the first feed.
    func refresh() async {
        for await items in asyncStream(BridgingKt.homeAdapter(repository)) {
            state = .loaded((items as? [HomeData]) ?? [])
            return
        }
    }
}
