import SwiftUI

/// New tab — placeholder until FLA-44 builds the create-deck form over `FlashcardRepository`.
struct CreateDeckView: View {
    var body: some View {
        EmptyStateView(
            title: "New set",
            systemImage: "plus.square",
            message: "Create a flashcard set here."
        )
        .navigationTitle("New")
    }
}
