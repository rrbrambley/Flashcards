import XCTest
import Shared
@testable import Flashcards

/// Regression test for FLA-57: a failing shared repository call must bridge to a *catchable* Swift
/// error rather than terminating the app. The repositories are pointed at an unreachable backend so
/// every networked write fails; reaching the `catch` (instead of crashing the test process) is the
/// behavior the `@Throws` annotations restore.
final class ApiErrorBridgingTests: XCTestCase {
    private func unreachableSdk() -> FlashcardSdk {
        // Port 1 refuses immediately — deterministic, no external network.
        IosFlashcardSdkKt.createIosFlashcardSdk(
            baseUrl: "http://127.0.0.1:1",
            tokenStore: InMemoryTokenStore(),
            homeFeedStrings: DefaultHomeFeedStrings.shared
        )
    }

    func test_saveFlashcardDeck_failure_isCatchable() async {
        let repo = unreachableSdk().flashcardRepository
        let deck = FlashcardDeck(
            id: 0, title: "x",
            flashcards: [Flashcard(question: "a", answer: "b", imageUrl: nil, alternativeAnswers: [], cardUid: "")],
            isEditable: true,
            tags: [],
            discussionsEnabled: false,
            isGlobal: false
        )
        do {
            try await repo.saveFlashcardDeck(deck: deck)
            XCTFail("expected the unreachable backend to throw")
        } catch {
            // Caught (not crashed) → the error bridged to Swift. ✅
        }
    }

    func test_startOrResumeSession_failure_isCatchable() async {
        let repo = unreachableSdk().practiceSessionRepository
        do {
            _ = try await repo.startOrResumeSession(deckId: 1, mode: "flashcards", shuffle: false, questionCount: nil)
            XCTFail("expected the unreachable backend to throw")
        } catch {
            // Caught (not crashed). ✅  (Offline the local-session mint hits a FOREIGN KEY failure
            // for this never-cached deck, which bridges to a catchable Swift error — the point here.)
        }
    }

    // The Swift VMs call the API client directly (e.g. getStreaks at launch via `try?`), so its
    // suspend endpoints need @Throws too — otherwise an offline backend crashes the app on launch.
    func test_getStreaks_failure_isCatchable() async {
        let apiClient = unreachableSdk().apiClient
        do {
            _ = try await apiClient.getStreaks(tz: "America/Los_Angeles")
            XCTFail("expected the unreachable backend to throw")
        } catch {
            // Caught (not crashed) → the error bridged to Swift. ✅
        }
    }
}
