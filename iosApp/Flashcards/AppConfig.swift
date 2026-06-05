import Foundation

/// Build-scheme configuration. `BACKEND_BASE_URL` is a per-config build setting (see
/// `project.yml`) surfaced through Info.plist, so the simulator can hit `localhost` while a
/// device build points at a LAN/host address — without code changes.
enum AppConfig {
    static var backendBaseURL: String {
        let value = Bundle.main.object(forInfoDictionaryKey: "BackendBaseURL") as? String
        // Fall back to the simulator default if the key is missing/blank.
        if let value, !value.isEmpty { return value }
        return "http://localhost:8080"
    }

    /// The iOS OAuth client ID for Google Sign-In, or "" when unconfigured (the button is then
    /// hidden — parity with Android/web). Surfaced from the `GOOGLE_IOS_CLIENT_ID` build setting.
    static var googleIOSClientID: String {
        (Bundle.main.object(forInfoDictionaryKey: "GoogleIOSClientID") as? String) ?? ""
    }
}
