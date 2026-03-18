import Foundation

struct PairedDevice: Identifiable, Codable {
    let id: String // UUID string
    var name: String
    var model: String?       // e.g. "Pixel Watch 2", "Galaxy Watch 6"
    let firstPairedDate: Date
    var lastSeenDate: Date

    static let knownModels = [
        "Google Pixel Watch",
        "Google Pixel Watch 2",
        "Google Pixel Watch 3",
        "Samsung Galaxy Watch 4",
        "Samsung Galaxy Watch 5",
        "Samsung Galaxy Watch 5 Pro",
        "Samsung Galaxy Watch 6",
        "Samsung Galaxy Watch 6 Classic",
        "Samsung Galaxy Watch 7",
        "Samsung Galaxy Watch Ultra",
        "Samsung Galaxy Watch FE",
        "Wear OS Watch (Other)"
    ]
}
