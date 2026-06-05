import SwiftUI

/// Signed-out landing — placeholder until FLA-39 adds the email/password login + register forms.
struct AuthPlaceholderView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "rectangle.on.rectangle.angled")
                .font(.system(size: 56))
                .foregroundStyle(.tint)
            Text("Flashcards")
                .font(.largeTitle.bold())
            Text("Sign in to start practicing")
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}
