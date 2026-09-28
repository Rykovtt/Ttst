import Foundation

enum TemplateKind: String, CaseIterable, Identifiable {
    case confirm, reminder, reschedule, cancel
    var id: String { rawValue }
    var title: String {
        switch self {
        case .confirm: return "Подтверждение"
        case .reminder: return "Напоминание"
        case .reschedule: return "Перенос"
        case .cancel: return "Отмена"
        }
    }
}

/// Язык исходящих сообщений. Плейсхолдеры понимаются на любом языке: {имя}, {імʼя}, {name}.
enum MessageLang: String, CaseIterable, Identifiable, Codable {
    case ru = "RU", uk = "UK", en = "EN"
    var id: String { rawValue }

    var title: String {
        switch self {
        case .ru: return "Русский"
        case .uk: return "Українська"
        case .en: return "English"
        }
    }

    var localeId: String {
        switch self {
        case .ru: return "ru"
        case .uk: return "uk"
        case .en: return "en"
        }
    }

    var monthsGen: [String] {
        switch self {
        case .ru: return ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"]
        case .uk: return ["січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"]
        case .en: return ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
        }
    }

    var weekdays: [String] { // с воскресенья, как Calendar.weekday
        switch self {
        case .ru: return ["воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота"]
        case .uk: return ["неділя", "понеділок", "вівторок", "середа", "четвер", "пʼятниця", "субота"]
        case .en: return ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
        }
    }

    var today: String { self == .ru ? "сегодня" : self == .uk ? "сьогодні" : "today" }
    var tomorrow: String { self == .en ? "tomorrow" : "завтра" }
    var afterTomorrow: String { self == .ru ? "послезавтра" : self == .uk ? "післязавтра" : "the day after tomorrow" }
    var minutes: String { self == .ru ? "мин" : self == .uk ? "хв" : "min" }
    var friends: String { self == .ru ? "друзья" : self == .uk ? "друзі" : "friends" }

    var greeting: String {
        switch self {
        case .ru: return "Привет, {имя}! "
        case .uk: return "Привіт, {імʼя}! "
        case .en: return "Hi {name}! "
        }
    }

    var tokens: [String] {
        switch self {
        case .ru: return ["имя", "имя_отчество", "фамилия", "прозвище", "дата", "время", "день_недели", "когда", "услуга", "место", "длительность"]
        case .uk: return ["імʼя", "імʼя_по_батькові", "прізвище", "прізвисько", "дата", "час", "день_тижня", "коли", "послуга", "місце", "тривалість"]
        case .en: return ["name", "full_name", "surname", "nickname", "date", "time", "weekday", "when", "service", "place", "duration"]
        }
    }

    var personTokens: [String] { tokens.prefix(4).map { "{\($0)}" } }
    var allTokens: [String] { tokens.map { "{\($0)}" } }

    func dateText(day: Int, month: Int) -> String {
        self == .en ? "\(monthsGen[month - 1]) \(day)" : "\(day) \(monthsGen[month - 1])"
    }

    func template(_ kind: TemplateKind) -> String {
        switch (self, kind) {
        case (.ru, .confirm): return "{имя}, здравствуйте! Подтверждаю вашу запись: {дата} ({день_недели}) в {время}.\n{услуга}\n{место}"
        case (.ru, .reminder): return "{имя}, напоминаю о записи: {когда} в {время}.\n{услуга}\n{место}\nЕсли планы изменились — пожалуйста, сообщите."
        case (.ru, .cancel): return "{имя}, здравствуйте! К сожалению, запись на {дата} в {время} отменяется. Давайте подберём другое время."
        case (.ru, .reschedule): return "{имя}, здравствуйте! Ваша запись перенесена: теперь {дата} ({день_недели}) в {время}.\n{место}"
        case (.uk, .confirm): return "{імʼя}, добрий день! Підтверджую ваш запис: {дата} ({день_тижня}) о {час}.\n{послуга}\n{місце}"
        case (.uk, .reminder): return "{імʼя}, нагадую про запис: {коли} о {час}.\n{послуга}\n{місце}\nЯкщо плани змінилися — будь ласка, повідомте."
        case (.uk, .cancel): return "{імʼя}, добрий день! На жаль, запис на {дата} о {час} скасовується. Давайте підберемо інший час."
        case (.uk, .reschedule): return "{імʼя}, добрий день! Ваш запис перенесено: тепер {дата} ({день_тижня}) о {час}.\n{місце}"
        case (.en, .confirm): return "Hi {name}! Your appointment is confirmed: {weekday}, {date} at {time}.\n{service}\n{place}"
        case (.en, .reminder): return "Hi {name}, a reminder about your appointment {when} at {time}.\n{service}\n{place}\nIf your plans have changed, please let me know."
        case (.en, .cancel): return "Hi {name}, unfortunately the appointment on {date} at {time} has to be cancelled. Let's find another time."
        case (.en, .reschedule): return "Hi {name}! Your appointment has been moved to {weekday}, {date} at {time}.\n{place}"
        }
    }

    /// Приводит плейсхолдеры любого языка к русским каноническим.
    static func canonicalize(_ text: String) -> String {
        var s = text.replacingOccurrences(of: "{ім'я", with: "{імʼя").replacingOccurrences(of: "{ім’я", with: "{імʼя")
        let canon = MessageLang.ru.tokens
        for lang in [MessageLang.uk, .en] {
            for (i, t) in lang.tokens.enumerated() {
                s = s.replacingOccurrences(of: "{\(t)}", with: "{\(canon[i])}")
            }
        }
        return s
    }
}
