import Shared
import SwiftUI

/// The profile screen (FLA-167): a large preview of the current avatar + the picker grid from
/// `GET /avatars`. Tapping an option saves it immediately; "Remove avatar" clears it. The picker is
/// hidden (with a note) when the catalog is empty — no CDN configured. Presented from the Home
/// account menu.
struct ProfileView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: ProfileViewModel

    init(service: ProfileService) {
        _viewModel = StateObject(wrappedValue: ProfileViewModel(service: service))
    }

    private let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.md), count: 5)

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Profile")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }
                    }
                }
                .task { await viewModel.load() }
        }
    }

    @ViewBuilder private var content: some View {
        if viewModel.isLoading {
            LoadingView()
        } else if viewModel.loadFailed {
            ErrorRetryView(message: "Couldn't load your profile.") {
                Task { await viewModel.load() }
            }
        } else {
            form
        }
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text("AVATAR")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(.secondary)

                HStack(spacing: Spacing.md) {
                    AvatarView(url: viewModel.avatarUrl, name: viewModel.monogramName, size: 72)
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Text(viewModel.avatars.isEmpty
                            ? "Avatars are unavailable right now."
                            : "Pick an avatar shown on your profile and discussion posts.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        if viewModel.selectedAvatarKey != nil {
                            Button("Remove avatar") { Task { await viewModel.clear() } }
                                .font(.subheadline)
                                .disabled(viewModel.isSaving)
                        }
                    }
                }

                if !viewModel.avatars.isEmpty {
                    LazyVGrid(columns: columns, spacing: Spacing.md) {
                        ForEach(viewModel.avatars, id: \.key) { option in
                            Button {
                                Task { await viewModel.select(key: option.key) }
                            } label: {
                                AvatarView(url: option.url, name: option.key, size: 56)
                                    .overlay {
                                        Circle().strokeBorder(
                                            Color.accentColor,
                                            lineWidth: option.key == viewModel.selectedAvatarKey ? 3 : 0
                                        )
                                    }
                            }
                            .buttonStyle(.plain)
                            .disabled(viewModel.isSaving)
                            .accessibilityLabel("\(option.key) avatar")
                        }
                    }
                }

                if viewModel.avatarError {
                    Text("Couldn't update your avatar.")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
