import Foundation

struct PairedDevice: Identifiable, Codable {
    let id: String // UUID string
    let name: String?
    let firstPairedDate: Date
    var lastSeenDate: Date
}
