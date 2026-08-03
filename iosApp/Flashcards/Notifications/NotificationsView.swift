import Shared
import SwiftUI

/// A toolbar bell + unread badge (#321). Observes the unread count from the shared repository; tapping
/// opens the notifications center. Gated by the caller on the `notifications` feature flag.
struct NotificationsBadgeButton: View {
    let onTap: () -> Void
    @StateObject private var viewModel: NotificationsViewModel

    init(repository: NotificationRepository, onTap: @escaping () -> Void) {
        self.onTap = onTap
        _viewModel = StateObject(wrappedValue: NotificationsViewModel(repository: repository))
    }

    var body: some View {
        Button(action: onTap) {
            Image(systemName: "bell")
                .overlay(alignment: .topTrailing) {
                    if viewModel.unreadCount > 0 {
                        Text(viewModel.unreadCount > 99 ? "99+" : "\(viewModel.unreadCount)")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Color.red, in: Capsule())
                            .offset(x: 9, y: -9)
                    }
                }
        }
        .accessibilityLabel(
            viewModel.unreadCount > 0
                ? Text("Notifications, \(viewModel.unreadCount) unread")
                : Text("Notifications")
        )
        .onAppear { viewModel.start() }
        .onDisappear { viewModel.stopObserving() }
    }
}

/// The in-app notifications center (#321): the caller's notifications newest-first, with per-item +
/// mark-all read. Tapping marks read (deep-linking to the source is a follow-up). Presented as a sheet.
struct NotificationsView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: NotificationsViewModel

    init(repository: NotificationRepository) {
        _viewModel = StateObject(wrappedValue: NotificationsViewModel(repository: repository))
    }

    var body: some View {
        NavigationStack {
            Group {
                if !viewModel.loaded {
                    ProgressView()
                } else if viewModel.notifications.isEmpty {
                    ContentUnavailableView("No notifications yet", systemImage: "bell")
                } else {
                    List(viewModel.notifications) { notification in
                        Button { viewModel.markRead(notification.id) } label: {
                            NotificationRow(notification: notification)
                        }
                        .listRowBackground(notification.isRead ? Color.clear : Color.accentColor.opacity(0.08))
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if viewModel.unreadCount > 0 {
                        Button("Mark all read") { viewModel.markAllRead() }
                    }
                }
            }
            .task { viewModel.start() }
            .onDisappear { viewModel.stopObserving() }
        }
    }
}

private struct NotificationRow: View {
    let notification: AppNotification

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            if !notification.isRead {
                Circle()
                    .fill(Color.accentColor)
                    .frame(width: 8, height: 8)
                    .padding(.top, 6)
            }
            VStack(alignment: .leading, spacing: 2) {
                notificationText
                    .font(.body)
                    .fontWeight(notification.isRead ? .regular : .semibold)
                    .foregroundStyle(.primary)
                Text(relativeTime(notification.createdAtMillis))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }

    /// Per-type copy from the notification's `data` payload (#321), localized via the String Catalog.
    private var notificationText: Text {
        switch notification.type {
        case "discussion_reply":
            let replier = notification.data["replierDisplayName"] ?? String(localized: "Someone")
            return Text("\(replier) replied to your comment")
        case "answer_suggestion_reviewed":
            let answer = notification.data["suggestedAnswer"] ?? ""
            let deck = notification.data["deckTitle"] ?? String(localized: "a deck")
            return notification.data["accepted"] == "true"
                ? Text("Your suggestion \"\(answer)\" was added to \(deck)")
                : Text("Your suggestion \"\(answer)\" wasn't added to \(deck)")
        default:
            return Text("You have a new notification")
        }
    }
}

private let relativeFormatter: RelativeDateTimeFormatter = {
    let formatter = RelativeDateTimeFormatter()
    formatter.unitsStyle = .abbreviated
    return formatter
}()

private func relativeTime(_ millis: Int64) -> String {
    relativeFormatter.localizedString(for: Date(timeIntervalSince1970: Double(millis) / 1000), relativeTo: Date())
}
