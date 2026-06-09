import Shared
import SwiftUI

/// New tab: create a deck — a title plus term/definition card rows, each with an optional
/// front-of-card image — saved through the shared offline-first `FlashcardRepository`.
struct CreateDeckView: View {
    @StateObject private var viewModel: CreateDeckViewModel

    init(repository: FlashcardRepository, imageUploader: ImageUploader) {
        _viewModel = StateObject(
            wrappedValue: CreateDeckViewModel(repository: repository, imageUploader: imageUploader)
        )
    }

    var body: some View {
        Form {
            Section {
                Text("Add a title, then the terms and definitions you want to practice.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            if viewModel.justSaved {
                Label("Deck created", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            }

            DeckFormSections(
                deckTitle: $viewModel.deckTitle,
                category: $viewModel.category,
                cards: $viewModel.cards,
                showErrors: viewModel.showErrors,
                onAddCard: viewModel.addCard,
                onRemoveCard: viewModel.removeCard,
                onPickImage: viewModel.pickImage,
                onRemoveImage: viewModel.removeImage
            )

            if let error = viewModel.saveError {
                Section { FormErrorText(error) }
            }
        }
        .navigationTitle("New deck")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if viewModel.isSaving {
                    ProgressView()
                } else {
                    Button("Create") { Task { await viewModel.save() } }
                        .bold()
                }
            }
        }
        // Auto-dismiss the "Deck created" confirmation.
        .task(id: viewModel.justSaved) {
            guard viewModel.justSaved else { return }
            try? await Task.sleep(for: .seconds(2))
            viewModel.acknowledgeSaved()
        }
    }
}
