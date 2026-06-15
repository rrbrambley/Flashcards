import Shared
import SwiftUI

/// Shown when a guest tries to leave an in-progress practice session (FLA-104): create an account to
/// save it, leave anyway (losing progress), or cancel. On success the practice screen dismisses and
/// the app flips to signed-in (the saved session appears under "Continue studying").
struct GuestSavePromptView: View {
    @ObservedObject var viewModel: PracticeViewModel
    let onLeave: () -> Void
    let onCancel: () -> Void

    @State private var email = ""
    @State private var password = ""

    private var saving: Bool { viewModel.saveState == .saving }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("You'll lose your progress if you leave. Create an account to save this session.")
                        .foregroundStyle(.secondary)
                }
                Section {
                    AppTextField(
                        placeholder: "Email",
                        text: $email,
                        keyboard: .emailAddress,
                        textContentType: .emailAddress
                    )
                    AppTextField(
                        placeholder: "Password",
                        text: $password,
                        isSecure: true,
                        textContentType: .newPassword
                    )
                    if case let .error(message) = viewModel.saveState {
                        Text(message).font(.subheadline).foregroundStyle(.red)
                    }
                    Button {
                        Task { await viewModel.saveProgressByCreatingAccount(email: email, password: password) }
                    } label: {
                        if saving {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text("Create account & save").frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(saving || email.isEmpty || password.isEmpty)
                }
                Section {
                    Button("Leave without saving", role: .destructive, action: onLeave).disabled(saving)
                }
            }
            .navigationTitle("Save your progress?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel).disabled(saving)
                }
            }
            .interactiveDismissDisabled(saving)
        }
    }
}
