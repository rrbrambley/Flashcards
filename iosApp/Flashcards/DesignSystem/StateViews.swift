import SwiftUI

/// Centered spinner for a screen's `.loading` state.
struct LoadingView: View {
    var body: some View {
        ProgressView()
            .controlSize(.large)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Full-screen error with a retry action, for a screen's `.failed` state.
struct ErrorRetryView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Something went wrong", systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        } actions: {
            Button("Try again", action: retry)
                .buttonStyle(.borderedProminent)
        }
    }
}

/// Empty-state placeholder (no data yet). Thin wrapper over `ContentUnavailableView` for a
/// consistent call site across screens.
struct EmptyStateView: View {
    let title: String
    let systemImage: String
    let message: String

    var body: some View {
        ContentUnavailableView(title, systemImage: systemImage, description: Text(message))
    }
}

/// Unobtrusive bottom banner shown over cached content when a background refresh fails (offline /
/// server down) — parity with Android's "couldn't refresh" snackbar. Use via `.safeAreaInset`.
struct RefreshFailedBanner: View {
    let message: String

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "wifi.slash")
            Text(message)
                .font(.footnote)
        }
        .padding(.vertical, Spacing.sm)
        .padding(.horizontal, Spacing.md)
        .frame(maxWidth: .infinity)
        .background(.thinMaterial)
    }
}

/// Inline form validation/error text (red, footnote) — used by the deck form sections.
struct FormErrorText: View {
    private let message: String
    init(_ message: String) { self.message = message }
    var body: some View {
        Text(message)
            .font(.footnote)
            .foregroundStyle(.red)
    }
}
