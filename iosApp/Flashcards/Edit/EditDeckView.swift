import Shared
import SwiftUI

/// Edit a deck, presented as a sheet from the library. Reuses the create form (`DeckFormSections`),
/// pre-filled; guards against discarding unsaved changes; the read-only global deck is view-only.
struct EditDeckView: View {
    @StateObject private var viewModel: EditDeckViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showDiscardConfirm = false

    init(repository: FlashcardRepository, deckId: Int64) {
        _viewModel = StateObject(wrappedValue: EditDeckViewModel(repository: repository, deckId: deckId))
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    LoadingView()
                } else {
                    Form {
                        if !viewModel.isEditable {
                            Section {
                                Label("This deck is read-only and can't be edited.", systemImage: "lock")
                                    .foregroundStyle(.secondary)
                            }
                        }
                        DeckFormSections(
                            deckTitle: $viewModel.deckTitle,
                            cards: $viewModel.cards,
                            isEditable: viewModel.isEditable,
                            showErrors: viewModel.showErrors,
                            onAddCard: viewModel.addCard,
                            onRemoveCard: viewModel.removeCard
                        )
                        if let error = viewModel.saveError {
                            Section { FormErrorText(error) }
                        }
                    }
                }
            }
            .navigationTitle(viewModel.isEditable ? "Edit deck" : "Deck")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(viewModel.isEditable ? "Cancel" : "Done") { attemptDismiss() }
                }
                if viewModel.isEditable {
                    ToolbarItem(placement: .confirmationAction) {
                        if viewModel.isSaving {
                            ProgressView()
                        } else {
                            Button("Save") { Task { await viewModel.save() } }
                                .bold()
                        }
                    }
                }
            }
            .interactiveDismissDisabled(viewModel.isDirty)
            .confirmationDialog("Discard changes?", isPresented: $showDiscardConfirm, titleVisibility: .visible) {
                Button("Discard", role: .destructive) { dismiss() }
                Button("Keep editing", role: .cancel) {}
            }
            .task { await viewModel.load() }
            .onChange(of: viewModel.didSave) { _, saved in
                if saved { dismiss() }
            }
        }
    }

    private func attemptDismiss() {
        if viewModel.isDirty {
            showDiscardConfirm = true
        } else {
            dismiss()
        }
    }
}
