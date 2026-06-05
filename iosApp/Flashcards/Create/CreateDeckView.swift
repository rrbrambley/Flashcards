import Shared
import SwiftUI

/// New tab: create a deck — a title plus term/definition card rows — saved through the shared
/// offline-first `FlashcardRepository`. (Front-of-card images arrive in FLA-51.)
struct CreateDeckView: View {
    @StateObject private var viewModel: CreateDeckViewModel

    init(repository: FlashcardRepository) {
        _viewModel = StateObject(wrappedValue: CreateDeckViewModel(repository: repository))
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

            Section("Title") {
                TextField("Deck title", text: $viewModel.deckTitle)
                if viewModel.titleError {
                    errorText("Enter a deck title")
                }
            }

            ForEach(Array(viewModel.cards.enumerated()), id: \.element.id) { index, card in
                Section("Card \(index + 1)") {
                    TextField("Term", text: $viewModel.cards[index].term)
                    TextField("Definition", text: $viewModel.cards[index].definition)
                    if viewModel.cardError(card) {
                        errorText("Enter both a term and a definition")
                    }
                    if viewModel.cards.count > 1 {
                        Button("Remove card", systemImage: "trash", role: .destructive) {
                            viewModel.removeCard(card.id)
                        }
                    }
                }
            }

            if viewModel.cardCountError {
                Section {
                    errorText("Add at least one card with a term and a definition.")
                }
            }

            Section {
                Button {
                    viewModel.addCard()
                } label: {
                    Label("Add card", systemImage: "plus")
                }
            }

            if let error = viewModel.saveError {
                Section {
                    errorText(error)
                }
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

    private func errorText(_ message: String) -> some View {
        Text(message)
            .font(.footnote)
            .foregroundStyle(.red)
    }
}
