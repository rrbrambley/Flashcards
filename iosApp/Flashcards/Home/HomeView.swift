import SwiftUI

/// Home tab — placeholder until FLA-47 renders the shared `HomeRepository` feed.
struct HomeView: View {
    var body: some View {
        ContentUnavailableView(
            "Home",
            systemImage: "house",
            description: Text("Your practice feed will appear here.")
        )
        .navigationTitle("Home")
    }
}
