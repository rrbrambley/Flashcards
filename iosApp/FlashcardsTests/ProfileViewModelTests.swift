import XCTest
import Shared
@testable import Flashcards

@MainActor
final class ProfileViewModelTests: XCTestCase {
    private let catalog = [
        AvatarDto(key: "dragon", url: "https://cdn.test/avatars/dragon.png"),
        AvatarDto(key: "yeti", url: "https://cdn.test/avatars/yeti.png"),
    ]

    func test_load_populatesCatalogAndCurrentSelection() async {
        let service = FakeProfileService(
            me: makeMe(avatarKey: "dragon", avatarUrl: "https://cdn.test/avatars/dragon.png"),
            avatars: catalog
        )
        let vm = ProfileViewModel(service: service)

        await vm.load()

        XCTAssertFalse(vm.isLoading)
        XCTAssertEqual(vm.avatars.map { $0.key }, ["dragon", "yeti"])
        XCTAssertEqual(vm.selectedAvatarKey, "dragon")
        XCTAssertEqual(vm.avatarUrl, "https://cdn.test/avatars/dragon.png")
    }

    func test_select_sendsKeyAndUpdatesSelection() async {
        let service = FakeProfileService(me: makeMe(), avatars: catalog)
        let vm = ProfileViewModel(service: service)
        await vm.load()

        await vm.select(key: "yeti")

        XCTAssertEqual(service.updatedKeys, ["yeti"])
        XCTAssertEqual(vm.selectedAvatarKey, "yeti")
        XCTAssertEqual(vm.avatarUrl, "https://cdn.test/avatars/yeti.png")
        XCTAssertFalse(vm.isSaving)
    }

    func test_clear_sendsBlankKeyAndResetsSelection() async {
        let service = FakeProfileService(
            me: makeMe(avatarKey: "dragon", avatarUrl: "https://cdn.test/avatars/dragon.png"),
            avatars: catalog
        )
        let vm = ProfileViewModel(service: service)
        await vm.load()

        await vm.clear()

        XCTAssertEqual(service.updatedKeys, [""])
        XCTAssertNil(vm.selectedAvatarKey)
        XCTAssertNil(vm.avatarUrl)
    }

    func test_load_emptyCatalog_whenAvatarsFail_stillShowsProfile() async {
        let service = FakeProfileService(me: makeMe(), avatars: catalog)
        service.failAvatars = true
        let vm = ProfileViewModel(service: service)

        await vm.load()

        XCTAssertFalse(vm.loadFailed)
        XCTAssertTrue(vm.avatars.isEmpty)
    }

    func test_load_failsWhenMeFails() async {
        let service = FakeProfileService(me: makeMe(), avatars: catalog)
        service.failMe = true
        let vm = ProfileViewModel(service: service)

        await vm.load()

        XCTAssertTrue(vm.loadFailed)
    }

    func test_select_setsErrorOnFailure() async {
        let service = FakeProfileService(me: makeMe(), avatars: catalog)
        service.failUpdate = true
        let vm = ProfileViewModel(service: service)
        await vm.load()

        await vm.select(key: "yeti")

        XCTAssertTrue(vm.avatarError)
        XCTAssertFalse(vm.isSaving)
    }
}
