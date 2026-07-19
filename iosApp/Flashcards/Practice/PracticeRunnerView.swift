import Shared
import SwiftUI

/// Entry point for a practice run: resolves the session's grade-at-the-end flag (#293) — mirroring
/// Android's ViewModel peek — then shows the matching runner: the grade-at-the-end `BatchPracticeView`
/// or the card-by-card `PracticeView`. Drop-in for `PracticeView` at every presentation site, so a
/// resumed grade-at-the-end session lands on the batch runner too, not just a freshly-configured one.
struct PracticeRunnerView: View {
    let flashcardRepository: FlashcardRepository
    let sessionRepository: PracticeSessionRepository
    let entry: PracticeEntry
    let featureFlagStore: FeatureFlagStore
    let apiClient: FlashcardApiClient
    var authService: AuthService? = nil

    @State private var gradeAtEnd: Bool?

    var body: some View {
        if let gradeAtEnd {
            if gradeAtEnd {
                BatchPracticeView(
                    flashcardRepository: flashcardRepository,
                    sessionRepository: sessionRepository,
                    entry: entry,
                    apiClient: apiClient
                )
            } else {
                PracticeView(
                    flashcardRepository: flashcardRepository,
                    sessionRepository: sessionRepository,
                    entry: entry,
                    featureFlagStore: featureFlagStore,
                    apiClient: apiClient,
                    authService: authService
                )
            }
        } else {
            LoadingView()
                .task { gradeAtEnd = await entry.resolveGradeAtEnd(sessionRepository: sessionRepository) }
        }
    }
}
