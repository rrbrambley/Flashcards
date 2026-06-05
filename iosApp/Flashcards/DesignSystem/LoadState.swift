import Foundation

/// The iOS analogue of Android's sealed `*UiState`. A view model is a `@MainActor`
/// `ObservableObject` exposing a single `@Published private(set) var state`, and most data screens
/// type that state as a screen-specific enum built on (or shaped like) `LoadState`. It collects a
/// shared-repository Flow via `asyncStream(_:)` (see `KotlinBridging.swift`) and calls suspend
/// repository functions with `try await`.
///
/// Example:
/// ```
/// @MainActor final class LibraryViewModel: ObservableObject {
///     @Published private(set) var state: LoadState<[FlashcardDeck]> = .loading
///     private let repository: FlashcardRepository
///     init(repository: FlashcardRepository) { self.repository = repository }
///     func observe() async {
///         for await decks in asyncStream(BridgingKt.decksAdapter(repository)) {
///             state = .loaded(decks ?? [])
///         }
///     }
/// }
/// ```
enum LoadState<Value> {
    case loading
    case loaded(Value)
    case failed(String)
}

extension LoadState: Equatable where Value: Equatable {}
