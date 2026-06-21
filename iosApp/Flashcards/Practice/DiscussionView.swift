import Shared
import SwiftUI

/// The 💬 control each mode shows once a card's answer is revealed, opening its discussion (FLA-123).
struct DiscussButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label("Discuss this card", systemImage: "bubble.left.and.bubble.right")
        }
        .font(.subheadline)
    }
}

/// Drives a card's discussion thread (FLA-123): loads the thread + first page, paginates, and posts
/// messages/replies over the shared `FlashcardApiClient`. Reads are public; posting needs auth, so a
/// guest is shown an inline sign-in/up prompt that — on success — posts the captured message and
/// switches to the signed-in state (mirrors web/Android + the guest "save progress" flow).
@MainActor
final class DiscussionViewModel: ObservableObject {
    @Published private(set) var messages: [DiscussionMessageDto] = []
    @Published private(set) var loading = true
    @Published private(set) var loadFailed = false
    @Published private(set) var isLocked = false
    @Published private(set) var hasMore = false
    @Published private(set) var posting = false
    @Published var postError: String?
    @Published private(set) var isGuest: Bool
    @Published var showAuthPrompt = false
    @Published var authError: String?
    @Published private(set) var authSubmitting = false
    /// Increments on each successful post so the composing field can clear itself.
    @Published private(set) var postedTick = 0

    let cardUid: String
    private let apiClient: FlashcardApiClient
    private let authService: AuthenticationService?
    private var nextCursor: String?
    private var pendingContent: String?
    private var pendingParentId: Int64?

    init(cardUid: String, isGuest: Bool, apiClient: FlashcardApiClient, authService: AuthenticationService?) {
        self.cardUid = cardUid
        self.isGuest = isGuest
        self.apiClient = apiClient
        self.authService = authService
    }

    func load() async {
        loading = true
        loadFailed = false
        do {
            let thread = try await apiClient.getDiscussionThread(cardUid: cardUid)
            let page = try await apiClient.getDiscussionMessages(cardUid: cardUid, limit: nil, cursor: nil)
            isLocked = thread.isLocked
            messages = (page.items as? [DiscussionMessageDto]) ?? []
            nextCursor = page.nextCursor
            hasMore = page.nextCursor != nil
            loading = false
        } catch {
            loading = false
            loadFailed = true
        }
    }

    func loadMore() async {
        guard let cursor = nextCursor else { return }
        do {
            let page = try await apiClient.getDiscussionMessages(cardUid: cardUid, limit: nil, cursor: cursor)
            messages += (page.items as? [DiscussionMessageDto]) ?? []
            nextCursor = page.nextCursor
            hasMore = page.nextCursor != nil
        } catch {
            // Leave the existing messages; the user can retry.
        }
    }

    /// Posts `content` (an optional reply to `replyTo`). A guest is intercepted: the message is
    /// captured and the sign-in prompt is shown instead.
    func post(content: String, replyTo: DiscussionMessageDto?) {
        let text = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !posting else { return }
        if isGuest {
            pendingContent = text
            pendingParentId = replyTo?.id
            authError = nil
            showAuthPrompt = true
            return
        }
        posting = true
        postError = nil
        Task {
            await send(text, parentId: replyTo?.id)
            posting = false
        }
    }

    /// Guest conversion: register or log in, then post the captured message before signing in.
    func authenticateAndPost(register: Bool, email: String, password: String) {
        guard let authService, let text = pendingContent else { return }
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
            // Signed in — post the captured message, then flip to the signed-in state.
            await send(text, parentId: pendingParentId)
            pendingContent = nil
            pendingParentId = nil
            isGuest = false
            authSubmitting = false
            showAuthPrompt = false
        }
    }

    private func send(_ text: String, parentId: Int64?) async {
        do {
            let parent = parentId.map { KotlinLong(value: $0) }
            let message = try await apiClient.postDiscussionMessage(
                cardUid: cardUid,
                content: text,
                parentMessageId: parent
            )
            messages.append(message)
            postError = nil
            postedTick += 1
        } catch {
            postError = "Couldn't post your message. Check your connection and try again."
        }
    }
}

/// The per-card discussion thread as a sheet (FLA-123): paginated messages with one level of
/// replies, a post box, and a guest sign-in/up conversion. Reads are public; posting needs auth.
struct DiscussionView: View {
    @StateObject private var viewModel: DiscussionViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var input = ""
    @State private var replyTo: DiscussionMessageDto?

    init(cardUid: String, isGuest: Bool, apiClient: FlashcardApiClient, authService: AuthService?) {
        _viewModel = StateObject(
            wrappedValue: DiscussionViewModel(
                cardUid: cardUid,
                isGuest: isGuest,
                apiClient: apiClient,
                authService: authService
            )
        )
    }

    private var topLevel: [DiscussionMessageDto] {
        viewModel.messages.filter { $0.parentMessageId == nil }
    }

    private func replies(for id: Int64) -> [DiscussionMessageDto] {
        viewModel.messages.filter { $0.parentMessageId?.int64Value == id }
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.loading {
                    LoadingView()
                } else if viewModel.loadFailed {
                    ContentUnavailableView(
                        "Couldn't load the discussion",
                        systemImage: "exclamationmark.bubble",
                        description: Text("Check your connection and try again.")
                    )
                } else {
                    threadList
                }
            }
            .navigationTitle("Discussion")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if !viewModel.loading, !viewModel.loadFailed {
                    postBar
                }
            }
            .task { await viewModel.load() }
            .onChange(of: viewModel.postedTick) { _, _ in
                input = ""
                replyTo = nil
            }
            .sheet(isPresented: $viewModel.showAuthPrompt) {
                DiscussionAuthPrompt(viewModel: viewModel)
            }
        }
    }

    @ViewBuilder private var threadList: some View {
        if topLevel.isEmpty {
            ContentUnavailableView(
                "No messages yet",
                systemImage: "bubble.left",
                description: Text("Start the conversation!")
            )
        } else {
            List {
                ForEach(topLevel, id: \.id) { message in
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        MessageRow(message: message)
                        if !viewModel.isLocked {
                            Button("Reply") { replyTo = message }
                                .font(.caption)
                        }
                        ForEach(replies(for: message.id), id: \.id) { reply in
                            MessageRow(message: reply)
                                .padding(.leading, Spacing.lg)
                        }
                    }
                    .padding(.vertical, Spacing.xs)
                }
                if viewModel.hasMore {
                    Button("Load more") { Task { await viewModel.loadMore() } }
                        .font(.subheadline)
                }
            }
            .listStyle(.plain)
        }
    }

    @ViewBuilder private var postBar: some View {
        if viewModel.isLocked {
            Text("🔒 This discussion is locked.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .padding()
                .background(.bar)
        } else {
            VStack(spacing: Spacing.sm) {
                if let replyTo {
                    HStack {
                        Text("Replying to \(replyTo.authorDisplayName)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button("Cancel") { self.replyTo = nil }
                            .font(.caption)
                    }
                }
                if let postError = viewModel.postError {
                    Text(postError)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                HStack(spacing: Spacing.sm) {
                    TextField(
                        viewModel.isGuest ? "Sign in to join the discussion…" : "Add a message…",
                        text: $input,
                        axis: .vertical
                    )
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(1 ... 4)

                    Button(viewModel.isGuest ? "Sign in" : "Post") {
                        viewModel.post(content: input, replyTo: replyTo)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.posting || input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .padding()
            .background(.bar)
        }
    }
}

/// One message: author + relative time, then the body.
private struct MessageRow: View {
    let message: DiscussionMessageDto

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(message.authorDisplayName)
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text(relativeTime(message.createdAtMillis))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Text(message.content)
                .font(.body)
        }
    }
}

/// Guest conversion (FLA-123): register or log in inline; the view model posts the captured message
/// on success before flipping to the signed-in state.
private struct DiscussionAuthPrompt: View {
    @ObservedObject var viewModel: DiscussionViewModel
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
                            ? "Create an account to post your message and help others learn."
                            : "Log in to post your message and help others learn."
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
                    Button(register ? "Create account & post" : "Log in & post") {
                        viewModel.authenticateAndPost(register: register, email: email, password: password)
                    }
                    .disabled(viewModel.authSubmitting)
                    Button(register ? "Have an account? Log in" : "Need an account? Register") {
                        register.toggle()
                    }
                    .font(.footnote)
                }
            }
            .navigationTitle("Join the discussion")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}

/// Compact relative time: "just now", "5m", "3h", "2d", else an absolute date.
private func relativeTime(_ millis: Int64) -> String {
    let seconds = Date().timeIntervalSince1970 - Double(millis) / 1000
    if seconds < 60 { return "just now" }
    let minutes = Int(seconds / 60)
    if minutes < 60 { return "\(minutes)m" }
    let hours = minutes / 60
    if hours < 24 { return "\(hours)h" }
    let days = hours / 24
    if days < 7 { return "\(days)d" }
    return Date(timeIntervalSince1970: Double(millis) / 1000)
        .formatted(date: .abbreviated, time: .omitted)
}
