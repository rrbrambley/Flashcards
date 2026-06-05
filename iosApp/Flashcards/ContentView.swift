import Shared
import SwiftUI

/// Placeholder launch screen. The subtitle is produced by the shared Kotlin framework
/// (`Greeting().greet()`), proving Swift↔Kotlin linkage. Replaced by the auth-gated
/// navigation shell (TabView) in FLA-37.
struct ContentView: View {
    private let greeting = Greeting().greet()

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "rectangle.on.rectangle.angled")
                .font(.system(size: 56))
                .foregroundStyle(.tint)
            Text("Flashcards")
                .font(.largeTitle.bold())
            Text(greeting)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
