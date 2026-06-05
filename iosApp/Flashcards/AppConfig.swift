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
}
