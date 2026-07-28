import Shared
import SwiftUI

/// A notification mapped to a plain Swift value — avoids the `Foundation.Notification` name clash and
/// gives the list an `Identifiable` row.
struct AppNotification: Identifiable {
    let id: Int64
    let type: String
    let data: [String: String]
    let isRead: Bool
    let createdAtMillis: Int64
}

/// Thin iOS adapter over the shared offline-first `NotificationRepository` (#321): mirrors the cached
/// list + unread count into `@Published` (best-effort re-sync on subscribe) and forwards mark-read.
/// Notifications are server-produced; the client only reads + marks read.
@MainActor
final class NotificationsViewModel: ObservableObject {
    @Published private(set) var notifications: [AppNotification] = []
    @Published private(set) var unreadCount = 0
    @Published private(set) var loaded = false

    private let repository: NotificationRepository
    private var listTask: Task<Void, Never>?
    private var countTask: Task<Void, Never>?

    init(repository: NotificationRepository) {
        self.repository = repository
    }

    func start() {
        let repo = repository
        listTask = Task { [weak self] in
            for await list in asyncStream(BridgingKt.notificationsAdapter(repo)) {
                self?.notifications = ((list as? [Shared.Notification]) ?? []).map { $0.toApp() }
                self?.loaded = true
            }
        }
        countTask = Task { [weak self] in
            for await count in asyncStream(BridgingKt.unreadCountAdapter(repo)) {
                if let count { self?.unreadCount = Int(count) }
            }
        }
    }

    func stopObserving() {
        listTask?.cancel()
        countTask?.cancel()
    }

    func markRead(_ id: Int64) {
        Task { try? await repository.markRead(id: id) }
    }

    func markAllRead() {
        Task { try? await repository.markAllRead() }
    }
}

private extension Shared.Notification {
    func toApp() -> AppNotification {
        AppNotification(
            id: id,
            type: type,
            data: data as? [String: String] ?? [:],
            isRead: isRead,
            createdAtMillis: createdAtMillis
        )
    }
}
