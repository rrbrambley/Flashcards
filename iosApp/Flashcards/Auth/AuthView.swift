import Shared
import SwiftUI

/// Email/password login + register, toggled in place (mirrors the Android auth flow). On success
/// the shared `AuthService` stores tokens and `RootView` swaps in the main tabs.
struct AuthView: View {
    @StateObject private var viewModel: AuthViewModel

    init(authService: AuthService) {
        _viewModel = StateObject(wrappedValue: AuthViewModel(authService: authService))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                header
                fields
                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.subheadline)
                        .foregroundStyle(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                submitButton
                if viewModel.isGoogleConfigured {
                    divider
                    googleButton
                }
                switchButton
            }
            .padding(Spacing.lg)
        }
        .scrollDismissesKeyboard(.interactively)
        .animation(.default, value: viewModel.mode)
    }

    private var header: some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: "rectangle.on.rectangle.angled")
                .font(.system(size: 48))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(viewModel.title)
                .font(.title.bold())
        }
        .padding(.top, Spacing.xl)
    }

    private var fields: some View {
        VStack(spacing: Spacing.md) {
            AppTextField(
                placeholder: "Email",
                text: $viewModel.email,
                keyboard: .emailAddress,
                textContentType: .emailAddress
            )
            AppTextField(
                placeholder: "Password",
                text: $viewModel.password,
                isSecure: true,
                textContentType: viewModel.mode == .login ? .password : .newPassword,
                submitLabel: .go
            )
        }
        .disabled(viewModel.isSubmitting)
        .onSubmit { Task { await viewModel.submit() } }
    }

    private var submitButton: some View {
        Button {
            Task { await viewModel.submit() }
        } label: {
            if viewModel.isSubmitting {
                ProgressView().tint(.white)
            } else {
                Text(viewModel.submitTitle)
            }
        }
        .buttonStyle(.primary)
        .disabled(viewModel.isSubmitting)
    }

    private var divider: some View {
        HStack(spacing: Spacing.md) {
            separator
            Text("OR").font(.footnote).foregroundStyle(.secondary)
            separator
        }
    }

    private var separator: some View {
        Rectangle().fill(Color(.separator)).frame(height: 1)
    }

    private var googleButton: some View {
        Button {
            Task { await viewModel.signInWithGoogle() }
        } label: {
            Label(viewModel.googleTitle, systemImage: "g.circle")
        }
        .buttonStyle(.secondary)
        .disabled(viewModel.isSubmitting)
    }

    private var switchButton: some View {
        Button(viewModel.switchPrompt) { viewModel.toggleMode() }
            .font(.subheadline)
    }
}
