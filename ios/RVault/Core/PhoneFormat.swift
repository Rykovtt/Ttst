import Foundation

struct Country: Hashable, Identifiable {
    let iso: String
    let name: String
    let code: String
    /// Национальный префикс, который отбрасывается («0» в Украине, «8» в России).
    let trunk: String
    let nationalLength: Int?
    let groups: [Int]
    var id: String { iso }

    var flag: String {
        iso.uppercased().unicodeScalars.compactMap { Unicode.Scalar(0x1F1E6 + $0.value - 65) }.map { String($0) }.joined()
    }
}

enum PhoneFormat {
    static let countries: [Country] = [
        Country(iso: "UA", name: "Украина", code: "380", trunk: "0", nationalLength: 9, groups: [2, 3, 2, 2]),
        Country(iso: "RU", name: "Россия", code: "7", trunk: "8", nationalLength: 10, groups: [3, 3, 2, 2]),
        Country(iso: "BY", name: "Беларусь", code: "375", trunk: "80", nationalLength: 9, groups: [2, 3, 2, 2]),
        Country(iso: "KZ", name: "Казахстан", code: "7", trunk: "8", nationalLength: 10, groups: [3, 3, 2, 2]),
        Country(iso: "MD", name: "Молдова", code: "373", trunk: "0", nationalLength: 8, groups: [2, 3, 3]),
        Country(iso: "PL", name: "Польша", code: "48", trunk: "", nationalLength: 9, groups: [3, 3, 3]),
        Country(iso: "DE", name: "Германия", code: "49", trunk: "0", nationalLength: nil, groups: [3, 4, 4]),
        Country(iso: "CZ", name: "Чехия", code: "420", trunk: "", nationalLength: 9, groups: [3, 3, 3]),
        Country(iso: "LT", name: "Литва", code: "370", trunk: "8", nationalLength: 8, groups: [3, 5]),
        Country(iso: "LV", name: "Латвия", code: "371", trunk: "", nationalLength: 8, groups: [2, 3, 3]),
        Country(iso: "EE", name: "Эстония", code: "372", trunk: "", nationalLength: nil, groups: [4, 4]),
        Country(iso: "GE", name: "Грузия", code: "995", trunk: "0", nationalLength: 9, groups: [3, 2, 2, 2]),
        Country(iso: "AM", name: "Армения", code: "374", trunk: "0", nationalLength: 8, groups: [2, 3, 3]),
        Country(iso: "AZ", name: "Азербайджан", code: "994", trunk: "0", nationalLength: 9, groups: [2, 3, 2, 2]),
        Country(iso: "UZ", name: "Узбекистан", code: "998", trunk: "", nationalLength: 9, groups: [2, 3, 2, 2]),
        Country(iso: "KG", name: "Кыргызстан", code: "996", trunk: "0", nationalLength: 9, groups: [3, 3, 3]),
        Country(iso: "IL", name: "Израиль", code: "972", trunk: "0", nationalLength: 9, groups: [2, 3, 4]),
        Country(iso: "TR", name: "Турция", code: "90", trunk: "0", nationalLength: 10, groups: [3, 3, 2, 2]),
        Country(iso: "GB", name: "Великобритания", code: "44", trunk: "0", nationalLength: 10, groups: [4, 6]),
        Country(iso: "US", name: "США / Канада", code: "1", trunk: "1", nationalLength: 10, groups: [3, 3, 4]),
        Country(iso: "IT", name: "Италия", code: "39", trunk: "", nationalLength: nil, groups: [3, 3, 4]),
        Country(iso: "ES", name: "Испания", code: "34", trunk: "", nationalLength: 9, groups: [3, 3, 3]),
        Country(iso: "FR", name: "Франция", code: "33", trunk: "0", nationalLength: 9, groups: [1, 2, 2, 2, 2]),
        Country(iso: "PT", name: "Португалия", code: "351", trunk: "", nationalLength: 9, groups: [3, 3, 3]),
        Country(iso: "NL", name: "Нидерланды", code: "31", trunk: "0", nationalLength: 9, groups: [1, 4, 4]),
        Country(iso: "AT", name: "Австрия", code: "43", trunk: "0", nationalLength: nil, groups: [3, 3, 4]),
        Country(iso: "CH", name: "Швейцария", code: "41", trunk: "0", nationalLength: 9, groups: [2, 3, 2, 2]),
        Country(iso: "RO", name: "Румыния", code: "40", trunk: "0", nationalLength: 9, groups: [3, 3, 3]),
        Country(iso: "BG", name: "Болгария", code: "359", trunk: "0", nationalLength: 9, groups: [2, 3, 4]),
        Country(iso: "AE", name: "ОАЭ", code: "971", trunk: "0", nationalLength: 9, groups: [2, 3, 4]),
        Country(iso: "TH", name: "Таиланд", code: "66", trunk: "0", nationalLength: 9, groups: [2, 3, 4]),
    ]

    static func byIso(_ iso: String?) -> Country { countries.first { $0.iso == iso } ?? countries[0] }

    /// «093 074 38 29» → «+380930743829».
    static func normalize(_ raw: String, country: Country) -> String {
        let trimmed = raw.trimmed
        if trimmed.isEmpty { return "" }
        var digits = trimmed.filter(\.isNumber)
        if digits.isEmpty { return trimmed }
        if trimmed.hasPrefix("+") { return "+" + digits }
        if digits.hasPrefix("00") { return "+" + digits.dropFirst(2) }
        let len = country.nationalLength
        if digits.hasPrefix(country.code), let len, digits.count == country.code.count + len { return "+" + digits }
        if !country.trunk.isEmpty, digits.hasPrefix(country.trunk), len == nil || digits.count == country.trunk.count + len! {
            digits = String(digits.dropFirst(country.trunk.count))
        }
        return "+" + country.code + digits
    }

    static func countryOf(_ e164: String, preferred: Country? = nil) -> Country? {
        guard e164.hasPrefix("+") else { return nil }
        let digits = e164.dropFirst()
        if let preferred, digits.hasPrefix(preferred.code) { return preferred }
        return countries.filter { digits.hasPrefix($0.code) }.max { $0.code.count < $1.code.count }
    }

    /// «+380930743829» → «+380 93 074 38 29».
    static func pretty(_ value: String, preferred: Country? = nil) -> String {
        guard value.hasPrefix("+"), value.dropFirst().allSatisfy(\.isNumber), let c = countryOf(value, preferred: preferred) else { return value }
        let national = Array(value.dropFirst(1 + c.code.count))
        var parts: [String] = []
        var i = 0
        for g in c.groups where i < national.count {
            parts.append(String(national[i..<min(national.count, i + g)]))
            i += g
        }
        if i < national.count { parts.append(String(national[i...])) }
        return "+\(c.code) " + parts.joined(separator: " ")
    }

    /// Цифры для wa.me и т.п.; российское 8XXXXXXXXXX → 7XXXXXXXXXX.
    static func digitsForLink(_ raw: String) -> String {
        var d = raw.filter(\.isNumber)
        if d.count == 11, d.hasPrefix("8"), !raw.hasPrefix("+") { d = "7" + d.dropFirst() }
        return d
    }
}
