import Foundation

/// A single editable card in the create/edit deck form. `question = term`, `answer = definition`
/// (matching the shared `Flashcard`). A front-of-card image is optional; an **image-only** card is
/// allowed (the term is optional when an image is present), matching Android's validation.
///
/// `uploading` / `uploadError` are transient per-card upload state and are intentionally excluded
/// from equality so an in-flight upload (or a cleared error) doesn't register as a dirty edit.
struct CardDraft: Identifiable, Equatable {
    let id: UUID
    var term: String
    var definition: String
    var imageUrl: String?
    /// Extra accepted answers for Test mode (FLA-109). No authoring UI on iOS yet — carried through
    /// edits so they're preserved on save (web is the authoring surface for now).
    var alternativeAnswers: [String]
    /// Stable backend card id (FLA-113), carried through edits so it's preserved on save; "" when new.
    var cardUid: String
    var uploading: Bool
    var uploadError: String?

    init(
        id: UUID = UUID(),
        term: String = "",
        definition: String = "",
        imageUrl: String? = nil,
        alternativeAnswers: [String] = [],
        cardUid: String = "",
        uploading: Bool = false,
        uploadError: String? = nil
    ) {
        self.id = id
        self.term = term
        self.definition = definition
        self.imageUrl = imageUrl
        self.alternativeAnswers = alternativeAnswers
        self.cardUid = cardUid
        self.uploading = uploading
        self.uploadError = uploadError
    }

    /// Complete when there's a definition plus either a term or an image (parity with Android:
    /// `definition.isNotBlank() && (term.isNotBlank() || imageUrl != null)`).
    var isComplete: Bool { !definition.trimmed.isEmpty && (!term.trimmed.isEmpty || imageUrl != nil) }
    var isStarted: Bool { !term.trimmed.isEmpty || !definition.trimmed.isEmpty || imageUrl != nil }

    static func == (lhs: CardDraft, rhs: CardDraft) -> Bool {
        lhs.id == rhs.id && lhs.term == rhs.term && lhs.definition == rhs.definition &&
            lhs.imageUrl == rhs.imageUrl && lhs.alternativeAnswers == rhs.alternativeAnswers &&
            lhs.cardUid == rhs.cardUid
    }
}

extension Array where Element == CardDraft {
    var completeCards: [CardDraft] { filter(\.isComplete) }
    var hasIncompleteStartedCard: Bool { contains { $0.isStarted && !$0.isComplete } }
}
