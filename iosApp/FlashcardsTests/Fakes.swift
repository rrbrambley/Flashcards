import Foundation
import PhotosUI
import Shared
import SwiftUI
@testable import Flashcards

// MARK: - Domain builders

func makeDeck(
    id: Int64,
    _ title: String,
    cards: [Flashcard] = [],
    editable: Bool = true,
    tags: [String] = [],
    discussionsEnabled: Bool = false,
    isGlobal: Bool = false
) -> FlashcardDeck {
    // Kotlin default args don't bridge, so pass them all explicitly.
    FlashcardDeck(
        id: id,
        title: title,
        flashcards: cards,
        isEditable: editable,
        tags: tags,
        discussionsEnabled: discussionsEnabled,
        isGlobal: isGlobal
    )
}

func makeCard(_ question: String, _ answer: String, imageUrl: String? = nil, cardUid: String = "") -> Flashcard {
    // Kotlin default args don't bridge, so pass them all explicitly.
    Flashcard(question: question, answer: answer, imageUrl: imageUrl, alternativeAnswers: [], cardUid: cardUid)
}

func makeMe(avatarKey: String? = nil, avatarUrl: String? = nil) -> MeResponse {
    // Kotlin default args don't bridge, so pass them all explicitly.
    MeResponse(
        userId: 1,
        email: "rob@example.com",
        roles: [],
        permissions: [],
        displayName: "Rob B",
        avatarKey: avatarKey,
        avatarUrl: avatarUrl,
        flags: [:]
    )
}

func makeSession(
    id: Int64 = 1, deckId: Int64 = 1, index: Int32 = 0,
    correct: Int32 = 0, incorrect: Int32 = 0, completed: Bool = false, mode: String = "flashcards"
) -> PracticeSession {
    PracticeSession(
        id: id, deckId: deckId, deckTitle: "Deck", currentCardIndex: index,
        numCorrect: correct, numIncorrect: incorrect, isCompleted: completed, mode: mode,
        createdAtMillis: 0, updatedAtMillis: 0, pendingSync: false, shuffle: false, shuffleSeed: 0
    )
}

// MARK: - Repository fakes (back Flow methods with `oneShotFlow`, record suspend calls)

final class FakeFlashcardRepository: FlashcardRepository {
    var decks: [FlashcardDeck] = []
    var deck: FlashcardDeck?
    var saveError: Error?
    var deleteError: Error?

    private(set) var savedDeck: FlashcardDeck?
    private(set) var updatedDeck: FlashcardDeck?
    private(set) var deletedDeckId: Int64?

    func observeFlashcardDecks() -> any Kotlinx_coroutines_coreFlow { FlowTestSupportKt.oneShotFlow(value: decks) }
    func observeFlashcardDeck(deckId: Int64) -> any Kotlinx_coroutines_coreFlow { FlowTestSupportKt.oneShotFlow(value: deck) }
    func observeDeckRefreshFailures() -> any Kotlinx_coroutines_coreFlow { FlowTestSupportKt.emptyBooleanFlow() }

    func saveFlashcardDeck(deck: FlashcardDeck) async throws {
        if let saveError { throw saveError }
        savedDeck = deck
    }

    func updateFlashcardDeck(deck: FlashcardDeck) async throws {
        if let saveError { throw saveError }
        updatedDeck = deck
    }

    func deleteFlashcardDeck(deckId: Int64) async throws {
        if let deleteError { throw deleteError }
        deletedDeckId = deckId
    }
}

final class FakePracticeSessionRepository: PracticeSessionRepository {
    var session: PracticeSession?
    var activeSessions: [PracticeSession] = []
    var lastPracticed: [Int64: Int64] = [:]
    var startSessionId: Int64 = 1
    var startError: Error?

    private(set) var updatedProgress: (sessionId: Int64, index: Int32, correct: Int32, incorrect: Int32)?
    private(set) var completedSessionId: Int64?
    private(set) var startedMode: String?

    func startOrResumeSession(deckId: Int64, mode: String, shuffle: Bool) async throws -> KotlinLong {
        if let startError { throw startError }
        startedMode = mode
        return KotlinLong(longLong: startSessionId)
    }

    func observeActiveSessions() -> any Kotlinx_coroutines_coreFlow { FlowTestSupportKt.oneShotFlow(value: activeSessions) }

    func observeLastPracticedByDeck() -> any Kotlinx_coroutines_coreFlow {
        var dict: [KotlinLong: KotlinLong] = [:]
        for (key, value) in lastPracticed { dict[KotlinLong(longLong: key)] = KotlinLong(longLong: value) }
        return FlowTestSupportKt.oneShotFlow(value: dict)
    }

    func observeSession(sessionId: Int64) -> any Kotlinx_coroutines_coreFlow { FlowTestSupportKt.oneShotFlow(value: session) }

    func updateProgress(sessionId: Int64, currentCardIndex: Int32, numCorrect: Int32, numIncorrect: Int32) async throws {
        updatedProgress = (sessionId, currentCardIndex, numCorrect, numIncorrect)
    }

    func completeSession(sessionId: Int64) async throws { completedSessionId = sessionId }

    private(set) var deletedSessionId: Int64?
    func deleteSession(sessionId: Int64) async throws { deletedSessionId = sessionId }

    // recordAnswer fires from fire-and-forget Tasks that hop off the main actor, so guard the array
    // (the real repository is thread-safe; this fake isn't otherwise).
    private let answersLock = NSLock()
    private var _recordedAnswers: [(sessionId: Int64, cardUid: String, correct: Bool, submittedText: String?)] = []
    var recordedAnswers: [(sessionId: Int64, cardUid: String, correct: Bool, submittedText: String?)] {
        answersLock.lock(); defer { answersLock.unlock() }; return _recordedAnswers
    }

    // Mirrors the real Room-backed flow: recording re-emits the updated log (so the review's final
    // answer "lands" after completion).
    private let answerLog = MutableAnswerLog()

    func recordAnswer(sessionId: Int64, cardUid: String, correct: Bool, submittedText: String?) async throws {
        answersLock.lock()
        _recordedAnswers.append((sessionId, cardUid, correct, submittedText))
        let snapshot = _recordedAnswers.enumerated().map { index, r in
            PracticeAnswer(
                answerUid: "uid-\(index)",
                cardUid: r.cardUid,
                correct: r.correct,
                sequence: Int32(index),
                answeredAtMillis: 0,
                submittedText: r.submittedText
            )
        }
        answersLock.unlock()
        answerLog.set(answers: snapshot)
    }

    func observeAnswers(sessionId: Int64) -> any Kotlinx_coroutines_coreFlow { answerLog.flow() }
}

final class FakeHomeRepository: HomeRepository {
    var homeData: [HomeData] = []
    func observeHomeData() -> any Kotlinx_coroutines_coreFlow { FlowTestSupportKt.oneShotFlow(value: homeData) }
}

// MARK: - Service fakes

struct StubImageUploader: ImageUploading {
    var result: Result<String, Error> = .success("https://cdn.example/img.jpg")
    func upload(item: PhotosPickerItem) async throws -> String { try result.get() }
}

final class FakeAuthService: AuthenticationService {
    var loginResult: AuthResult = AuthResult.Success.shared
    var registerResult: AuthResult = AuthResult.Success.shared
    var googleResult: AuthResult = AuthResult.Success.shared
    private(set) var lastLogin: (email: String, password: String)?
    private(set) var lastRegister: (email: String, password: String)?

    func login(email: String, password: String) async throws -> AuthResult {
        lastLogin = (email, password)
        return loginResult
    }

    func register(email: String, password: String) async throws -> AuthResult {
        lastRegister = (email, password)
        return registerResult
    }

    func signInWithGoogle(idToken: String) async throws -> AuthResult { googleResult }
}

final class FakeProfileService: ProfileService {
    var meResponse: MeResponse
    var avatarList: [AvatarDto]
    var failMe = false
    var failAvatars = false
    var failUpdate = false
    private(set) var updatedKeys: [String] = []

    init(me: MeResponse, avatars: [AvatarDto]) {
        self.meResponse = me
        self.avatarList = avatars
    }

    func me() async throws -> MeResponse {
        if failMe { throw NSError(domain: "test", code: 1) }
        return meResponse
    }

    func avatars() async throws -> [AvatarDto] {
        if failAvatars { throw NSError(domain: "test", code: 1) }
        return avatarList
    }

    func updateAvatar(key: String) async throws -> MeResponse {
        if failUpdate { throw NSError(domain: "test", code: 1) }
        updatedKeys.append(key)
        meResponse = MeResponse(
            userId: meResponse.userId,
            email: meResponse.email,
            roles: meResponse.roles,
            permissions: meResponse.permissions,
            displayName: meResponse.displayName,
            avatarKey: key.isEmpty ? nil : key,
            avatarUrl: key.isEmpty ? nil : "https://cdn.test/avatars/\(key).png",
            flags: meResponse.flags
        )
        return meResponse
    }
}

final class FakeFeatureFlagService: FeatureFlagService {
    var flagMap: [String: Bool]
    var error: Error?

    init(flags: [String: Bool]) {
        self.flagMap = flags
    }

    func flags() async throws -> [String: Bool] {
        if let error { throw error }
        return flagMap
    }
}
