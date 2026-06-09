import PhotosUI
import SwiftUI

/// The shared `Form` sections for building/editing a deck — a title field plus term/definition card
/// rows (with an optional front-of-card image) and inline validation. Used by both Create (FLA-44)
/// and Edit (FLA-45); `isEditable = false` renders it read-only (the global catalog deck) but still
/// shows card images.
struct DeckFormSections: View {
    @Binding var deckTitle: String
    @Binding var category: String
    @Binding var cards: [CardDraft]
    var isEditable: Bool = true
    let showErrors: Bool
    let onAddCard: () -> Void
    let onRemoveCard: (UUID) -> Void
    let onPickImage: (UUID, PhotosPickerItem) -> Void
    let onRemoveImage: (UUID) -> Void

    var body: some View {
        Section("Title") {
            TextField("Deck title", text: $deckTitle)
                .disabled(!isEditable)
            if showErrors && deckTitle.trimmed.isEmpty {
                FormErrorText("Enter a deck title")
            }
        }

        Section("Category") {
            TextField("Category (optional)", text: $category)
                .disabled(!isEditable)
        }

        ForEach(Array(cards.enumerated()), id: \.element.id) { index, card in
            Section("Card \(index + 1)") {
                TextField("Term", text: $cards[index].term)
                    .disabled(!isEditable)
                TextField("Definition", text: $cards[index].definition)
                    .disabled(!isEditable)
                CardImageRow(
                    card: card,
                    isEditable: isEditable,
                    onPick: { onPickImage(card.id, $0) },
                    onRemove: { onRemoveImage(card.id) }
                )
                if showErrors && card.isStarted && !card.isComplete {
                    FormErrorText("Each card needs a definition, plus a term or an image.")
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
                FormErrorText("Add at least one card with a definition and a term or image.")
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

/// The optional front-of-card image for one card: shows the image with a remove affordance, an
/// uploading spinner, or an "Add image" PhotosPicker — plus any per-card upload error. Owns its own
/// picker selection so each card row picks independently.
private struct CardImageRow: View {
    let card: CardDraft
    let isEditable: Bool
    let onPick: (PhotosPickerItem) -> Void
    let onRemove: () -> Void

    @State private var pickerItem: PhotosPickerItem?

    var body: some View {
        Group {
            if let imageUrl = card.imageUrl, !imageUrl.isEmpty {
                RemoteCardImage(url: imageUrl)
                    .frame(maxWidth: .infinity)
                    .frame(maxHeight: 160)
                if isEditable {
                    Button("Remove image", systemImage: "trash", role: .destructive) { onRemove() }
                }
            } else if card.uploading {
                HStack(spacing: Spacing.sm) {
                    ProgressView()
                    Text("Uploading image…").foregroundStyle(.secondary)
                }
            } else if isEditable {
                PhotosPicker(selection: $pickerItem, matching: .images) {
                    Label("Add image", systemImage: "photo")
                }
            }
            if let uploadError = card.uploadError {
                FormErrorText(uploadError)
            }
        }
        .onChange(of: pickerItem) { _, newItem in
            guard let newItem else { return }
            onPick(newItem)
            pickerItem = nil
        }
    }
}
