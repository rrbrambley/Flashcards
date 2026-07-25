import Foundation
import Shared

/// Recovers the shared `ActionError` from an error thrown by a shared `@Throws` client call and maps
/// it to localized copy for the discussion / suggestion write actions (FLA-196). Brings iOS to parity
/// with Android, which structures these failures (rate-limit / locked / rejected-with-message) instead
/// of showing one generic string. The mapping (status → case) lives in the shared `ActionError`; only
/// the copy is per-platform.
enum ActionErrorCopy {
    private static func recover(_ error: Error) -> ActionError {
        // Kotlin/Native delivers a @Throws exception either as the bridged KotlinThrowable directly or
        // wrapped in NSError.userInfo["KotlinException"]; handle both, else fall back to Generic.
        if let throwable = error as? KotlinThrowable {
            return ActionError.companion.fromThrowable(error: throwable)
        }
        if let throwable = (error as NSError).userInfo["KotlinException"] as? KotlinThrowable {
            return ActionError.companion.fromThrowable(error: throwable)
        }
        return ActionError.Generic.shared
    }

    static func discussionPost(_ error: Error) -> String {
        switch recover(error) {
        case is ActionError.RateLimit:
            return String(localized: "You're posting too quickly. Please wait a moment.")
        case is ActionError.Locked:
            return String(localized: "This thread is locked.")
        case let rejected as ActionError.Rejected:
            // The server's rejection reason is passed through as-is (already human copy, not localizable).
            return rejected.message ?? String(localized: "Your message couldn't be posted.")
        default:
            return String(localized: "Couldn't post your message. Check your connection and try again.")
        }
    }

    static func suggestion(_ error: Error) -> String {
        switch recover(error) {
        case is ActionError.RateLimit:
            return String(localized: "You're suggesting too quickly. Please wait a moment.")
        case let rejected as ActionError.Rejected:
            return rejected.message ?? String(localized: "That suggestion couldn't be accepted.")
        default:
            return String(localized: "Couldn't send your suggestion. Check your connection and try again.")
        }
    }
}
