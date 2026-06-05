import SwiftUI

/// The shared `Form` sections for building/editing a deck — a title field plus term/definition card
/// rows with add/remove and inline validation. Used by both Create (FLA-44) and Edit (FLA-45);
/// `isEditable = false` renders it read-only (the global catalog deck).
struct DeckFormSections: View {
    @Binding var deckTitle: String
    @Binding var cards: [CardDraft]
    var isEditable: Bool = true
    let showErrors: Bool
    let onAddCard: () -> Void
    let onRemoveCard: (UUID) -> Void

    var body: some View {
        Section("Title") {
            TextField("Deck title", text: $deckTitle)
                .disabled(!isEditable)
            if showErrors && deckTitle.trimmed.isEmpty {
                FormErrorText("Enter a deck title")
            }
        }

        ForEach(Array(cards.enumerated()), id: \.element.id) { index, card in
            Section("Card \(index + 1)") {
                TextField("Term", text: $cards[index].term)
                    .disabled(!isEditable)
                TextField("Definition", text: $cards[index].definition)
                    .disabled(!isEditable)
                if showErrors && card.isStarted && !card.isComplete {
                    FormErrorText("Enter both a term and a definition")
                }
                if isEditable && cards.count > 1 {
                    Button("Remove card", systemImage: "trash", role: .destructive) {
                        onRemoveCard(card.id)
                    }
                }
            }
        }

        if showErrors && cards.completeCards.isEmpty {
            Section {
                FormErrorText("Add at least one card with a term and a definition.")
            }
        }

        if isEditable {
            Section {
                Button {
                    onAddCard()
                } label: {
                    Label("Add card", systemImage: "plus")
                }
            }
        }
    }
}
