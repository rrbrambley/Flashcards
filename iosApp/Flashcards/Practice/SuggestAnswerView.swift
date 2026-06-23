import Shared
import SwiftUI

/// Drives the Test-mode "this should be correct" action (FLA-135): a learner suggests their typed
/// answer as an acceptable alternative for a global-deck card, queued for an admin to review. Posting
/// needs auth, so a guest is shown an inline sign-in/up prompt that — on success — replays the
/// captured suggestion and switches to the signed-in state (mirrors the discussion conversion flow).
@MainActor
final class SuggestAnswerViewModel: ObservableObject {
    @Published private(set) var submitting = false
    @Published private(set) var submitted = false
    @Published var errorMessage: String?
    @Published private(set) var isGuest: Bool
    @Published var showAuthPrompt = false
    @Published var authError: String?
    @Published private(set) var authSubmitting = false

    let cardUid: String
    let suggestedAnswer: String
    private let apiClient: FlashcardApiClient
    private let authService: AuthenticationService?

    init(
        cardUid: String,
        suggestedAnswer: String,
        isGuest: Bool,
        apiClient: FlashcardApiClient,
        authService: AuthenticationService?
    ) {
        self.cardUid = cardUid
        self.suggestedAnswer = suggestedAnswer
        self.isGuest = isGuest
        self.apiClient = apiClient
        self.authService = authService
    }

    /// Suggests the typed answer. A guest is intercepted: the sign-in prompt is shown instead.
    func suggest() {
        let text = suggestedAnswer.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !submitting, !submitted else { return }
        if isGuest {
            authError = nil
            showAuthPrompt = true
            return
        }
        Task { await send(text) }
    }

    /// Guest conversion: register or log in, then replay the captured suggestion before flipping to
    /// the signed-in state.
    func authenticateAndSuggest(register: Bool, email: String, password: String) {
        guard let authService else { return }
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !password.isEmpty else {
            authError = "Enter your email and password."
            return
        }
        authSubmitting = true
        authError = nil
        Task {
            let result = try? await (
                register
                    ? authService.register(email: trimmed, password: password)
                    : authService.login(email: trimmed, password: password)
            )
            if let failure = result as? AuthResult.Failure {
                authError = failure.message
                authSubmitting = false
                return
            }
            guard result is AuthResult.Success else {
                authError = "Something went wrong. Check your connection and try again."
                authSubmitting = false
                return
            }
            isGuest = false
            authSubmitting = false
            showAuthPrompt = false
            await send(suggestedAnswer.trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }

    private func send(_ text: String) async {
        submitting = true
        errorMessage = nil
        do {
            try await apiClient.suggestAnswer(cardUid: cardUid, suggestedAnswer: text)
            submitted = true
        } catch {
            errorMessage = "Couldn't send your suggestion. Check your connection and try again."
        }
        submitting = false
    }
}

/// The Test-mode "this should be correct" action (FLA-135), shown on a global-deck card after the
/// learner's answer is graded wrong. State is per-card — the parent re-inits this view via `.id`.
struct SuggestAnswerView: View {
    @StateObject private var viewModel: SuggestAnswerViewModel

    init(
        cardUid: String,
        suggestedAnswer: String,
        isGuest: Bool,
        apiClient: FlashcardApiClient,
        authService: AuthService?
    ) {
        _viewModel = StateObject(
            wrappedValue: SuggestAnswerViewModel(
                cardUid: cardUid,
                suggestedAnswer: suggestedAnswer,
                isGuest: isGuest,
                apiClient: apiClient,
                authService: authService
            )
        )
    }

    var body: some View {
        VStack(spacing: Spacing.sm) {
            if viewModel.submitted {
                Text("Thanks — we'll review your suggestion.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            } else {
                Button(action: viewModel.suggest) {
                    Label("This should be correct", systemImage: "checkmark.circle")
                }
                .font(.subheadline)
                .disabled(viewModel.submitting)
                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .sheet(isPresented: $viewModel.showAuthPrompt) {
            SuggestionAuthPrompt(viewModel: viewModel)
        }
    }
}

/// Guest conversion (FLA-135): register or log in inline; the view model replays the captured
/// suggestion on success before flipping to the signed-in state.
private struct SuggestionAuthPrompt: View {
    @ObservedObject var viewModel: SuggestAnswerViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var register = true
    @State private var email = ""
    @State private var password = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(
                        register
                            ? "Create an account to suggest this answer and help improve the deck."
                            : "Log in to suggest this answer and help improve the deck."
                    )
                    .foregroundStyle(.secondary)
                }
                Section {
                    TextField("Email", text: $email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    SecureField("Password", text: $password)
                        .textContentType(register ? .newPassword : .password)
                    if let authError = viewModel.authError {
                        Text(authError).font(.footnote).foregroundStyle(.red)
                    }
                }
                Section {
                    Button(register ? "Create account & suggest" : "Log in & suggest") {
                        viewModel.authenticateAndSuggest(register: register, email: email, password: password)
                    }
                    .disabled(viewModel.authSubmitting)
                    Button(register ? "Have an account? Log in" : "Need an account? Register") {
                        register.toggle()
                    }
                    .font(.footnote)
                }
            }
            .navigationTitle("Suggest an answer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
