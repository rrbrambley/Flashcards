import SwiftUI

/// New tab — placeholder until FLA-44 builds the create-deck form over `FlashcardRepository`.
struct CreateDeckView: View {
    var body: some View {
        ContentUnavailableView(
            "New set",
            systemImage: "plus.square",
            description: Text("Create a flashcard set here.")
        )
        .navigationTitle("New")
    }
}
