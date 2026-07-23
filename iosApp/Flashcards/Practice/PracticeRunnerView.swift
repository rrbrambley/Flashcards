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

    /// Resolved together with `gradeAtEnd`: a grade-at-the-end run is always single-sitting, a
    /// card-by-card run is single-sitting only when timed (#306/#307).
    @State private var resolved: (gradeAtEnd: Bool, singleSitting: Bool)?

    var body: some View {
        if let resolved {
            if resolved.gradeAtEnd {
                // A grade-at-the-end batch is inherently single-sitting, so its guard is always on.
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
                    singleSitting: resolved.singleSitting,
                    authService: authService
                )
            }
        } else {
            LoadingView()
                .task {
                    async let grade = entry.resolveGradeAtEnd(sessionRepository: sessionRepository)
                    async let single = entry.resolveSingleSitting(sessionRepository: sessionRepository)
                    resolved = (await grade, await single)
                }
        }
    }
}
