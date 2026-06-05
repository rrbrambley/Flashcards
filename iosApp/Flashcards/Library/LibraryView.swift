import SwiftUI

/// Library tab — placeholder until FLA-42 lists decks from the offline-first `FlashcardRepository`.
struct LibraryView: View {
    var body: some View {
        ContentUnavailableView(
            "Library",
            systemImage: "rectangle.stack",
            description: Text("Your decks will appear here.")
        )
        .navigationTitle("Library")
    }
}
