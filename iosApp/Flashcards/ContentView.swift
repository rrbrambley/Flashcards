import SwiftUI

/// Placeholder launch screen proving the app shell builds and runs. Replaced by the auth-gated
/// navigation shell (TabView) in FLA-37.
struct ContentView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "rectangle.on.rectangle.angled")
                .font(.system(size: 56))
                .foregroundStyle(.tint)
            Text("Flashcards")
                .font(.largeTitle.bold())
            Text("iOS app foundation")
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
