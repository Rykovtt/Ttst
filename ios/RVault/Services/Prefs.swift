import Foundation

/// Ключи настроек (UserDefaults / @AppStorage).
enum Prefs {
    static let lock = "lock"
    static let privacy = "privacy"
    static let birthdays = "birthdays"
    static let sort = "sort"
    static let country = "country"
    static let messageLang = "msg_lang"
    static let appTitle = "app_title"
    static let apptChannel = "appt_channel"
    static let apptDuration = "appt_duration"
    static let apptClientOffsets = "appt_client_offsets"
    static let apptMyOffsets = "appt_my_offsets"
    static let apptSendConfirm = "appt_send_confirm"

    static var d: UserDefaults { .standard }

    static func register() {
        d.register(defaults: [
            privacy: true, birthdays: true, country: "UA", messageLang: MessageLang.ru.rawValue,
            apptChannel: NotifyChannel.whatsapp.rawValue, apptDuration: 60,
            apptClientOffsets: "\(24 * 60),120", apptMyOffsets: "60", apptSendConfirm: true,
            sort: SortMode.name.rawValue,
        ])
    }

    static var defaultCountry: Country { PhoneFormat.byIso(d.string(forKey: country)) }
    static var defaultLang: MessageLang { MessageLang(rawValue: d.string(forKey: messageLang) ?? "") ?? .ru }
    static func lang(for p: Person) -> MessageLang { MessageLang(rawValue: p.language) ?? defaultLang }

    static func templateKey(_ kind: TemplateKind, _ lang: MessageLang) -> String { "tpl_\(kind.rawValue)_\(lang.rawValue)" }

    static func template(_ kind: TemplateKind, _ lang: MessageLang) -> String {
        d.string(forKey: templateKey(kind, lang)) ?? lang.template(kind)
    }

    static func offsets(_ key: String) -> [Int] {
        (d.string(forKey: key) ?? "").split(separator: ",").compactMap { Int($0) }.sorted()
    }

    static func setOffsets(_ key: String, _ list: [Int]) {
        d.set(list.sorted().map(String.init).joined(separator: ","), forKey: key)
    }
}
