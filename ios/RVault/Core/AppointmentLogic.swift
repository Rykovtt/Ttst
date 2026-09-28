import Foundation

enum AppointmentLogic {
    static let presets = [10, 30, 60, 120, 180, 24 * 60, 2 * 24 * 60, 3 * 24 * 60, 7 * 24 * 60]

    static func offsetTitle(_ min: Int) -> String {
        let p = ArchiveLogic.plural
        if min == 0 { return "в момент начала" }
        if min % (7 * 24 * 60) == 0 { let n = min / (7 * 24 * 60); return "за \(n) \(p(n, "неделю", "недели", "недель"))" }
        if min % (24 * 60) == 0 { let n = min / (24 * 60); return n == 1 ? "за сутки" : "за \(n) \(p(n, "день", "дня", "дней"))" }
        if min % 60 == 0 { let n = min / 60; return n == 1 ? "за час" : "за \(n) \(p(n, "час", "часа", "часов"))" }
        return "за \(min) мин"
    }

    static var cal: Calendar { ArchiveLogic.calendar }

    static func dateText(_ d: Date, _ lang: MessageLang = .ru) -> String {
        let c = cal.dateComponents([.day, .month], from: d)
        return lang.dateText(day: c.day!, month: c.month!)
    }

    static func timeText(_ d: Date) -> String {
        let c = cal.dateComponents([.hour, .minute], from: d)
        return String(format: "%02d:%02d", c.hour!, c.minute!)
    }

    static func weekday(_ d: Date, _ lang: MessageLang = .ru) -> String {
        lang.weekdays[cal.component(.weekday, from: d) - 1]
    }

    static func whenText(_ target: Date, now: Date, _ lang: MessageLang) -> String {
        let days = cal.dateComponents([.day], from: cal.startOfDay(for: now), to: cal.startOfDay(for: target)).day ?? 0
        switch days {
        case 0: return lang.today
        case 1: return lang.tomorrow
        case 2: return lang.afterTomorrow
        default: return (lang == .en ? "on " : "") + dateText(target, lang)
        }
    }

    /// Подстановка данных записи; пустые строки убираются.
    static func fill(_ template: String, _ a: Appointment, _ p: Person, lang: MessageLang = .ru, now: Date = Date()) -> String {
        let text = ArchiveLogic.fillTemplate(template, p)
            .replacingOccurrences(of: "{дата}", with: dateText(a.start, lang))
            .replacingOccurrences(of: "{время}", with: timeText(a.start))
            .replacingOccurrences(of: "{день_недели}", with: weekday(a.start, lang))
            .replacingOccurrences(of: "{когда}", with: whenText(a.start, now: now, lang))
            .replacingOccurrences(of: "{услуга}", with: a.title)
            .replacingOccurrences(of: "{место}", with: a.place)
            .replacingOccurrences(of: "{длительность}", with: "\(a.durationMin) \(lang.minutes)")
        return text.split(separator: "\n", omittingEmptySubsequences: false)
            .map { String($0).replacingOccurrences(of: "\\s+$", with: "", options: .regularExpression) }
            .filter { !$0.isBlank }.joined(separator: "\n").trimmed
    }

    struct Reminder: Hashable {
        enum Target: String { case client = "CLIENT", me = "ME" }
        var target: Target
        var offset: Int
        var fireAt: Date
        var key: String { "\(target.rawValue)_\(offset)" }
    }

    /// Предстоящие напоминания (без прошедших и уже отправленных).
    static func reminders(_ a: Appointment, now: Date = Date()) -> [Reminder] {
        guard a.status == .planned else { return [] }
        let client = a.channel == .none ? [] : a.clientOffsets.map { Reminder(target: .client, offset: $0, fireAt: a.start.addingTimeInterval(TimeInterval(-$0 * 60))) }
        let me = a.myOffsets.map { Reminder(target: .me, offset: $0, fireAt: a.start.addingTimeInterval(TimeInterval(-$0 * 60))) }
        return (client + me).filter { $0.fireAt > now && !a.sentReminders.contains($0.key) }
    }

    static func conflicts(_ a: Appointment, others: [Appointment]) -> [Appointment] {
        others.filter { $0.id != a.id && $0.status != .cancelled && $0.start < a.end && a.start < $0.end }
    }
}

/// Критерии выбора получателей (условия через «И»).
struct RecipientFilter: Equatable {
    var groupIds: Set<UUID> = []
    var genders: Set<String> = []
    var relations: Set<String> = []
    var cities: Set<String> = []
    var minCloseness = 0
    var favoritesOnly = false
    var ageFrom: Int?
    var ageTo: Int?
    var birthdayWithinDays: Int?
    var noContactDays: Int?

    var isEmpty: Bool { self == RecipientFilter() }

    func matches(_ p: Person, today: Date = Date()) -> Bool {
        if !groupIds.isEmpty && !p.groupIds.contains(where: groupIds.contains) { return false }
        if !genders.isEmpty && !genders.contains(p.gender) { return false }
        if !relations.isEmpty && !relations.contains(p.relation) { return false }
        if !cities.isEmpty && !cities.contains(where: { $0.lowercased() == p.city.trimmed.lowercased() }) { return false }
        if p.closeness < minCloseness { return false }
        if favoritesOnly && !p.favorite { return false }
        if ageFrom != nil || ageTo != nil {
            guard let age = ArchiveLogic.age(p, today: today) else { return false }
            if let f = ageFrom, age < f { return false }
            if let t = ageTo, age > t { return false }
        }
        if let d = birthdayWithinDays {
            guard let days = ArchiveLogic.daysUntilBirthday(p, today: today), days <= d else { return false }
        }
        if let d = noContactDays, let last = p.lastContactAt, today.timeIntervalSince(last) < Double(d) * 86400 { return false }
        return true
    }
}
