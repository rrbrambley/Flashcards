import SwiftUI

/// Home tab — placeholder until FLA-47 renders the shared `HomeRepository` feed.
struct HomeView: View {
    var body: some View {
        EmptyStateView(
            title: "Home",
            systemImage: "house",
            message: "Your practice feed will appear here."
        )
        .navigationTitle("Home")
    }
}
