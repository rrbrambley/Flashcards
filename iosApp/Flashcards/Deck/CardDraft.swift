import Foundation
import Shared

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
    /// Raw editable text for extra Test-mode accepted answers (FLA-109/FLA-111), one per line; parsed
    /// to `[String]` on save (see `DeckForm.parseAlternatives`). Seeded from the saved alternatives when editing.
    var alternatives: String
    /// Stable backend card id (FLA-113), carried through edits so it's preserved on save; "" when new.
    var cardUid: String
    var uploading: Bool
    var uploadError: String?

    init(
        id: UUID = UUID(),
        term: String = "",
        definition: String = "",
        imageUrl: String? = nil,
        alternatives: String = "",
        cardUid: String = "",
        uploading: Bool = false,
        uploadError: String? = nil
    ) {
        self.id = id
        self.term = term
        self.definition = definition
        self.imageUrl = imageUrl
        self.alternatives = alternatives
        self.cardUid = cardUid
        self.uploading = uploading
        self.uploadError = uploadError
    }

    /// Completeness/started rules delegate to the shared `DeckForm` (FLA-192) so all platforms agree.
    var isComplete: Bool { DeckForm.shared.isCardComplete(term: term, definition: definition, hasImage: imageUrl != nil) }
    var isStarted: Bool { DeckForm.shared.isCardStarted(term: term, definition: definition, hasImage: imageUrl != nil) }

    static func == (lhs: CardDraft, rhs: CardDraft) -> Bool {
        lhs.id == rhs.id && lhs.term == rhs.term && lhs.definition == rhs.definition &&
            lhs.imageUrl == rhs.imageUrl && lhs.alternatives == rhs.alternatives &&
            lhs.cardUid == rhs.cardUid
    }
}

extension Array where Element == CardDraft {
    var completeCards: [CardDraft] { filter(\.isComplete) }
    var hasIncompleteStartedCard: Bool { contains { $0.isStarted && !$0.isComplete } }
}
