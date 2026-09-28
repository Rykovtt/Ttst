import Foundation

// Модель данных RVault для iOS. Совпадает по смыслу с Android-версией.

enum ContactType: String, Codable, CaseIterable, Identifiable {
    case phone = "PHONE", email = "EMAIL", telegram = "TELEGRAM", whatsapp = "WHATSAPP", viber = "VIBER"
    case instagram = "INSTAGRAM", vk = "VK", facebook = "FACEBOOK", website = "WEBSITE", other = "OTHER"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .phone: return "Телефон"
        case .email: return "E-mail"
        case .telegram: return "Telegram"
        case .whatsapp: return "WhatsApp"
        case .viber: return "Viber"
        case .instagram: return "Instagram"
        case .vk: return "ВКонтакте"
        case .facebook: return "Facebook"
        case .website: return "Сайт"
        case .other: return "Другое"
        }
    }

    var isPhone: Bool { self == .phone || self == .whatsapp || self == .viber }
}

struct ContactItem: Codable, Hashable, Identifiable {
    var id = UUID()
    var type: ContactType = .phone
    var label = ""
    var value = ""
}

struct DetailField: Codable, Hashable, Identifiable {
    var id = UUID()
    var category = ""
    var name = ""
    var value = ""
}

struct Photo: Codable, Hashable, Identifiable {
    var id = UUID()
    /// Имя файла в папке фото приложения.
    var file: String
    var caption = ""
    var addedAt = Date()
}

struct JournalEntry: Codable, Hashable, Identifiable {
    var id = UUID()
    var date = Date()
    var kind = "Заметка"
    var text = ""
}

enum PlaceKind: String, Codable, CaseIterable, Identifiable {
    case home = "HOME", work = "WORK", other = "OTHER"
    var id: String { rawValue }
    var title: String {
        switch self {
        case .home: return "Дом"
        case .work: return "Работа"
        case .other: return "Другое"
        }
    }
}

struct Place: Codable, Hashable, Identifiable {
    var id = UUID()
    var kind: PlaceKind = .home
    var label = ""
    var address = ""
    var lat: Double?
    var lng: Double?
    var hasCoords: Bool { lat != nil && lng != nil }
}

struct Person: Codable, Hashable, Identifiable {
    var id = UUID()
    var lastName = ""
    var firstName = ""
    var middleName = ""
    var nickname = ""
    var birthDay: Int?
    var birthMonth: Int?
    var birthYear: Int?
    var gender = ""
    var relation = ""
    var closeness = 0
    var company = ""
    var position = ""
    var city = ""
    var howMet = ""
    var notes = ""
    var favorite = false
    /// Имя файла главного фото.
    var avatar: String?
    var createdAt = Date()
    var updatedAt = Date()
    var lastContactAt: Date?
    /// Язык сообщений этому человеку; пусто — как в настройках.
    var language = ""
    var contacts: [ContactItem] = []
    var details: [DetailField] = []
    var photos: [Photo] = []
    var groupIds: [UUID] = []
    var journal: [JournalEntry] = []
    var places: [Place] = []

    var displayName: String {
        let n = [firstName, lastName].filter { !$0.isBlank }.joined(separator: " ")
        if !n.isBlank { return n }
        return nickname.isBlank ? "Без имени" : nickname
    }

    var fullName: String {
        let n = [lastName, firstName, middleName].filter { !$0.isBlank }.joined(separator: " ")
        return n.isBlank ? displayName : n
    }

    var sortKey: String {
        let k = !firstName.isBlank ? firstName : (!lastName.isBlank ? lastName : nickname)
        return k.trimmingCharacters(in: .whitespaces).lowercased()
    }

    var initials: String {
        let a = firstName.first ?? nickname.first ?? lastName.first
        let b = firstName.isBlank ? nil : lastName.first
        let s = [a, b].compactMap { $0 }.map(String.init).joined().uppercased()
        return s.isEmpty ? "?" : s
    }

    func first(_ type: ContactType) -> String? {
        contacts.first { $0.type == type && !$0.value.isBlank }?.value
    }

    var phone: String? { first(.phone) }
    var whatsapp: String? { first(.whatsapp) ?? phone }
    var telegram: String? { first(.telegram) }
    var viber: String? { first(.viber) ?? phone }
    var email: String? { first(.email) }
}

struct PeopleGroup: Codable, Hashable, Identifiable {
    var id = UUID()
    var name: String
    var color: UInt32 = 0x5B4BD6
    var emoji = ""
    var title: String { [emoji, name].filter { !$0.isEmpty }.joined(separator: " ") }
}

struct Relation: Codable, Hashable, Identifiable {
    var id = UUID()
    /// «related — это type для person».
    var personId: UUID
    var relatedId: UUID
    var type: RelationType
}

enum AppointmentStatus: String, Codable, CaseIterable {
    case planned = "PLANNED", done = "DONE", cancelled = "CANCELLED"
    var title: String {
        switch self {
        case .planned: return "Запланировано"
        case .done: return "Состоялось"
        case .cancelled: return "Отменено"
        }
    }
}

enum NotifyChannel: String, Codable, CaseIterable, Identifiable {
    case whatsapp = "WHATSAPP", telegram = "TELEGRAM", sms = "SMS", none = "NONE"
    var id: String { rawValue }
    var title: String {
        switch self {
        case .whatsapp: return "WhatsApp"
        case .telegram: return "Telegram"
        case .sms: return "SMS"
        case .none: return "Не оповещать"
        }
    }

    func target(for p: Person) -> String? {
        switch self {
        case .whatsapp: return p.whatsapp
        case .telegram: return p.telegram
        case .sms: return p.phone
        case .none: return nil
        }
    }
}

struct Appointment: Codable, Hashable, Identifiable {
    var id = UUID()
    var personId: UUID
    var start: Date
    var durationMin = 60
    var title = ""
    var place = ""
    var notes = ""
    var channel: NotifyChannel = .none
    var status: AppointmentStatus = .planned
    var createdAt = Date()
    /// За сколько минут напомнить человеку / мне.
    var clientOffsets: [Int] = []
    var myOffsets: [Int] = []
    /// Уже отправленные напоминания: "CLIENT_120", "ME_30".
    var sentReminders: [String] = []

    var end: Date { start.addingTimeInterval(TimeInterval(durationMin * 60)) }
}

/// Весь архив — один зашифрованный файл.
struct Archive: Codable {
    var people: [Person] = []
    var groups: [PeopleGroup] = []
    var relations: [Relation] = []
    var appointments: [Appointment] = []
}

extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
