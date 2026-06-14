import Shared
import SwiftUI

/// Loads the public global-deck catalog for guest mode (FLA-104) via the unauthenticated `/catalog`.
@MainActor
final class GuestCatalogViewModel: ObservableObject {
    enum State {
        case loading
        case loaded([FlashcardDeckDto])
        case failed
    }

    @Published private(set) var state: State = .loading

    private let apiClient: FlashcardApiClient

    init(apiClient: FlashcardApiClient) {
        self.apiClient = apiClient
    }

    func load() async {
        state = .loading
        // The seeded catalog is small; the first (default) page covers it.
        guard let page = try? await apiClient.getCatalog(limit: nil, cursor: nil) else {
            state = .failed
            return
        }
        state = .loaded((page.items as? [FlashcardDeckDto]) ?? [])
    }
}
