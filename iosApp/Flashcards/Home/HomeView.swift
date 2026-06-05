import SwiftUI

/// Home tab — placeholder feed until FLA-47. Hosts the account menu (Log out) for now.
struct HomeView: View {
    @EnvironmentObject private var container: AppContainer

    var body: some View {
        EmptyStateView(
            title: "Home",
            systemImage: "house",
            message: "Your practice feed will appear here."
        )
        .navigationTitle("Home")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("Log out", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) {
                        // Clears the Keychain (and revokes server-side) → RootView returns to sign-in.
                        Task { try? await container.authService.logout() }
                    }
                } label: {
                    Image(systemName: "person.crop.circle")
                }
            }
        }
    }
}
