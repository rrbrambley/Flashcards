import Foundation

/// A single editable card in the create/edit deck form. `question = term`, `answer = definition`
/// (matching the shared `Flashcard`). Image-only cards arrive with image upload in FLA-51, so for
/// now a complete card needs both a term and a definition.
struct CardDraft: Identifiable, Equatable {
    let id: UUID
    var term: String
    var definition: String

    init(id: UUID = UUID(), term: String = "", definition: String = "") {
        self.id = id
        self.term = term
        self.definition = definition
    }

    var isComplete: Bool { !term.trimmed.isEmpty && !definition.trimmed.isEmpty }
    var isStarted: Bool { !term.trimmed.isEmpty || !definition.trimmed.isEmpty }
}

extension Array where Element == CardDraft {
    var completeCards: [CardDraft] { filter(\.isComplete) }
    var hasIncompleteStartedCard: Bool { contains { $0.isStarted && !$0.isComplete } }
}
