import XCTest
import Shared
@testable import Flashcards

@MainActor
final class NotificationsViewModelTests: XCTestCase {
    func testObservesCachedListAndUnreadCount() async throws {
        let repo = FakeNotificationRepository(
            notifications: [notif(id: 1, isRead: false, replier: "Ann"), notif(id: 2, isRead: true)],
            unread: 1
        )
        let viewModel = NotificationsViewModel(repository: repo)
        viewModel.start()
        await waitUntil(timeout: 2) { viewModel.loaded && viewModel.unreadCount == 1 }

        XCTAssertEqual(viewModel.notifications.map(\.id), [1, 2])
        XCTAssertEqual(viewModel.notifications.first?.data["replierDisplayName"], "Ann")
        XCTAssertEqual(viewModel.unreadCount, 1)
        viewModel.stopObserving()
    }

    func testMarkReadAndMarkAllReadForwardToRepository() async {
        let repo = FakeNotificationRepository(notifications: [], unread: 0)
        let viewModel = NotificationsViewModel(repository: repo)

        viewModel.markRead(5)
        await waitUntil(timeout: 2) { repo.readIds == [5] }

        viewModel.markAllRead()
        await waitUntil(timeout: 2) { repo.markedAll }
    }

    private func notif(id: Int64, isRead: Bool, replier: String? = nil) -> Shared.Notification {
        Shared.Notification(
            id: id,
            type: "discussion_reply",
            data: replier.map { ["replierDisplayName": $0] } ?? [:],
            isRead: isRead,
            createdAtMillis: id
        )
    }
}

private final class FakeNotificationRepository: NotificationRepository {
    private let notifications: [Shared.Notification]
    private let unread: Int32
    private(set) var readIds: [Int64] = []
    private(set) var markedAll = false

    init(notifications: [Shared.Notification], unread: Int32) {
        self.notifications = notifications
        self.unread = unread
    }

    func observeNotifications() -> any Kotlinx_coroutines_coreFlow {
        FlowTestSupportKt.oneShotFlow(value: notifications)
    }

    func observeUnreadCount() -> any Kotlinx_coroutines_coreFlow {
        FlowTestSupportKt.oneShotFlow(value: KotlinInt(int: unread))
    }

    func markRead(id: Int64) async throws {
        readIds.append(id)
    }

    func markAllRead() async throws {
        markedAll = true
    }
}
