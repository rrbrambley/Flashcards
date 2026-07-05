import Shared
import SwiftUI

/// Configure a practice run before it starts (FLA-200): pick a mode (the primary choice), adjust
/// settings (Shuffle, default On), then Start. Mirrors the web/Android configure-then-start flow.
/// Presented as a sheet from the library deck actions; on Start it hands the (mode, shuffle) choice
/// back to the caller, which launches the run.
struct PracticeConfigView: View {
    let deckTitle: String
    let onStart: (_ modeKey: String, _ shuffle: Bool) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selectedMode: String?
    @State private var shuffle = true

    var body: some View {
        NavigationStack {
            Form {
                Section("Choose a mode") {
                    ForEach(PracticeMode.entries) { mode in
                        Button {
                            selectedMode = mode.key
                        } label: {
                            HStack(alignment: .firstTextBaseline) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(mode.label).font(.headline).foregroundStyle(.primary)
                                    Text(mode.summary).font(.subheadline).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if selectedMode == mode.key {
                                    Image(systemName: "checkmark").foregroundStyle(.tint).fontWeight(.semibold)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(selectedMode == mode.key ? [.isSelected] : [])
                    }
                }
                Section("Settings") {
                    Toggle("Shuffle cards", isOn: $shuffle)
                }
            }
            .navigationTitle("Practice \(deckTitle)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Start") {
                        if let selectedMode { onStart(selectedMode, shuffle) }
                    }
                    .disabled(selectedMode == nil)
                }
            }
        }
    }
}
