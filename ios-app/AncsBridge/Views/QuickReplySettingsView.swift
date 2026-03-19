import SwiftUI

struct QuickReplySettingsView: View {
    @StateObject private var store = QuickReplyStore()
    @State private var newMessage = ""
    @State private var editingIndex: Int? = nil
    @State private var editText = ""

    var body: some View {
        List {
            // Messages
            Section {
                ForEach(Array(store.messages.enumerated()), id: \.offset) { index, message in
                    if editingIndex == index {
                        HStack {
                            TextField("Message", text: $editText)
                                .textFieldStyle(.plain)
                                .onSubmit { saveEdit(at: index) }

                            Button("Save") { saveEdit(at: index) }
                                .buttonStyle(.borderless)
                                .foregroundColor(.blue)
                        }
                    } else {
                        Button {
                            editingIndex = index
                            editText = message
                        } label: {
                            HStack {
                                Text(message)
                                    .foregroundColor(.primary)
                                Spacer()
                                Image(systemName: "pencil")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
                .onDelete { store.remove(at: $0) }

                HStack {
                    TextField("Add new reply…", text: $newMessage)
                        .textFieldStyle(.plain)
                        .onSubmit { addMessage() }

                    if !newMessage.isEmpty {
                        Button {
                            addMessage()
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .foregroundColor(.blue)
                        }
                        .buttonStyle(.borderless)
                    }
                }
            } header: {
                Text("Messages")
            } footer: {
                Text("These messages appear when you decline a call from your watch. Tap to edit, swipe to delete.")
            }

            // Reset
            Section {
                Button("Reset to Defaults") {
                    store.resetToDefaults()
                }
                .foregroundColor(.red)
            }

            // Shortcut Setup
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Label("iOS Shortcut Required", systemImage: "sparkles")
                        .font(.subheadline)
                        .fontWeight(.semibold)

                    Text("To auto-send replies, create an iOS Shortcut named **\"WearBridge Reply\"** with these steps:")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    VStack(alignment: .leading, spacing: 6) {
                        stepRow(1, "Receive **Text** input")
                        stepRow(2, "Split Text by **|** (pipe)")
                        stepRow(3, "Send Message to **Item 1** with body **Item 2**")
                    }
                    .padding(.leading, 4)
                }
                .padding(.vertical, 4)

                Button {
                    if let url = URL(string: "shortcuts://create-shortcut") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Label("Open Shortcuts App", systemImage: "arrow.up.forward.app")
                }
            } header: {
                Text("Siri Shortcut")
            } footer: {
                Text("When you decline a call with a reply on your watch, WearBridge sends the caller name and message to this Shortcut via Bluetooth.")
            }
        }
        .navigationTitle("Quick Reply")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func addMessage() {
        let trimmed = newMessage.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        store.add(trimmed)
        newMessage = ""
    }

    private func saveEdit(at index: Int) {
        let trimmed = editText.trimmingCharacters(in: .whitespaces)
        if !trimmed.isEmpty {
            store.update(at: index, with: trimmed)
        }
        editingIndex = nil
        editText = ""
    }

    private func stepRow(_ number: Int, _ text: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("\(number).")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.blue)
                .frame(width: 16)
            Text(text)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}
