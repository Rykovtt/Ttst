import Foundation

enum ArchiveLogic {
    struct SearchHit: Identifiable {
        var person: Person
        var matchedIn: String?
        var id: UUID { person.id }
    }

    /// Поиск по всем полям; все слова запроса должны найтись.
    static func search(_ all: [Person], query: String, groups: [PeopleGroup] = []) -> [SearchHit] {
        let words = query.lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
        if words.isEmpty { return all.map { SearchHit(person: $0, matchedIn: nil) } }
        let groupNames = Dictionary(uniqueKeysWithValues: groups.map { ($0.id, $0.name) })
        return all.compactMap { p in
            let sources = searchSources(p, groupNames: groupNames)
            let ok = words.allSatisfy { w in sources.contains { $0.1.lowercased().contains(w) } }
            if !ok { return nil }
            let nameHit = words.allSatisfy { p.fullName.lowercased().contains($0) || p.nickname.lowercased().contains($0) }
            var where_: String?
            if !nameHit, let s = sources.first(where: { s in words.contains { s.1.lowercased().contains($0) } }) {
                where_ = "\(s.0): \(snippet(s.1, words: words))"
            }
            return SearchHit(person: p, matchedIn: where_)
        }
    }

    private static func searchSources(_ p: Person, groupNames: [UUID: String]) -> [(String, String)] {
        var list: [(String, String)] = [
            ("Имя", "\(p.lastName) \(p.firstName) \(p.middleName)"), ("Прозвище", p.nickname), ("Отношение", p.relation),
            ("Работа", "\(p.company) \(p.position)"), ("Город", p.city), ("Знакомство", p.howMet), ("Заметки", p.notes),
        ]
        p.contacts.forEach { list.append(($0.type.title, $0.value)) }
        p.details.forEach { list.append(($0.name.isBlank ? $0.category : $0.name, $0.value)) }
        p.groupIds.compactMap { groupNames[$0] }.forEach { list.append(("Группа", $0)) }
        p.journal.forEach { list.append(($0.kind, $0.text)) }
        p.places.forEach { list.append(($0.kind.title, $0.address)) }
        p.photos.filter { !$0.caption.isBlank }.forEach { list.append(("Фото", $0.caption)) }
        return list.filter { !$0.1.isBlank }
    }

    private static func snippet(_ text: String, words: [String]) -> String {
        let lower = text.lowercased()
        let chars = Array(text)
        let idx = words.compactMap { w -> Int? in
            guard let r = lower.range(of: w) else { return nil }
            return lower.distance(from: lower.startIndex, to: r.lowerBound)
        }.min() ?? 0
        let start = max(0, idx - 20), end = min(chars.count, idx + 50)
        return (start > 0 ? "…" : "") + String(chars[start..<end]).replacingOccurrences(of: "\n", with: " ") + (end < chars.count ? "…" : "")
    }

    static func sort(_ list: [SearchHit], mode: SortMode) -> [SearchHit] {
        switch mode {
        case .name: return list.sorted { $0.person.sortKey < $1.person.sortKey }
        case .recent: return list.sorted { $0.person.createdAt > $1.person.createdAt }
        case .closeness: return list.sorted {
            $0.person.closeness != $1.person.closeness ? $0.person.closeness > $1.person.closeness : $0.person.sortKey < $1.person.sortKey
        }
        case .longAgo: return list.sorted { ($0.person.lastContactAt ?? .distantPast) < ($1.person.lastContactAt ?? .distantPast) }
        }
    }

    static var calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.firstWeekday = 2
        return c
    }()

    /// Ближайший день рождения (сегодня или позже).
    static func nextBirthday(_ p: Person, today: Date = Date()) -> Date? {
        guard let d = p.birthDay, let m = p.birthMonth, (1...12).contains(m), (1...31).contains(d) else { return nil }
        let cal = calendar
        let t = cal.startOfDay(for: today)
        let year = cal.component(.year, from: t)
        func at(_ y: Int) -> Date {
            let first = cal.date(from: DateComponents(year: y, month: m, day: 1))!
            let len = cal.range(of: .day, in: .month, for: first)!.count
            return cal.date(from: DateComponents(year: y, month: m, day: min(d, len)))!
        }
        let thisYear = at(year)
        return thisYear < t ? at(year + 1) : thisYear
    }

    static func daysUntilBirthday(_ p: Person, today: Date = Date()) -> Int? {
        guard let next = nextBirthday(p, today: today) else { return nil }
        return calendar.dateComponents([.day], from: calendar.startOfDay(for: today), to: next).day
    }

    static func age(_ p: Person, today: Date = Date()) -> Int? {
        guard let y = p.birthYear else { return nil }
        let c = calendar.dateComponents([.year, .month, .day], from: today)
        var age = c.year! - y
        if let m = p.birthMonth {
            let d = p.birthDay ?? 1
            if c.month! < m || (c.month! == m && c.day! < d) { age -= 1 }
        }
        return (0...150).contains(age) ? age : nil
    }

    static func turningAge(_ p: Person, today: Date = Date()) -> Int? {
        guard let y = p.birthYear, let next = nextBirthday(p, today: today) else { return nil }
        return calendar.component(.year, from: next) - y
    }

    static func upcomingBirthdays(_ all: [Person], within days: Int, today: Date = Date()) -> [(Person, Int)] {
        all.compactMap { p in daysUntilBirthday(p, today: today).map { (p, $0) } }
            .filter { $0.1 <= days }
            .sorted { $0.1 < $1.1 }
    }

    static func fillTemplate(_ template: String, _ p: Person) -> String {
        let first = p.firstName.isBlank ? p.displayName : p.firstName
        let fm = [p.firstName, p.middleName].filter { !$0.isBlank }.joined(separator: " ")
        return MessageLang.canonicalize(template)
            .replacingOccurrences(of: "{имя}", with: first)
            .replacingOccurrences(of: "{отчество}", with: p.middleName)
            .replacingOccurrences(of: "{фамилия}", with: p.lastName)
            .replacingOccurrences(of: "{имя_отчество}", with: fm.isBlank ? p.displayName : fm)
            .replacingOccurrences(of: "{прозвище}", with: p.nickname.isBlank ? p.firstName : p.nickname)
    }

    static let monthsGen = MessageLang.ru.monthsGen

    static func formatBirthday(_ p: Person) -> String? {
        guard let d = p.birthDay, let m = p.birthMonth, (1...12).contains(m) else { return nil }
        return p.birthYear.map { "\(d) \(monthsGen[m - 1]) \($0)" } ?? "\(d) \(monthsGen[m - 1])"
    }

    static func plural(_ n: Int, _ one: String, _ few: String, _ many: String) -> String {
        let n10 = n % 10, n100 = n % 100
        if n10 == 1 && n100 != 11 { return one }
        if (2...4).contains(n10) && !(12...14).contains(n100) { return few }
        return many
    }

    static func ageString(_ age: Int) -> String { "\(age) \(plural(age, "год", "года", "лет"))" }

    static func daysString(_ days: Int) -> String {
        switch days {
        case 0: return "сегодня"
        case 1: return "завтра"
        default: return "через \(days) \(plural(days, "день", "дня", "дней"))"
        }
    }
}

enum SortMode: String, CaseIterable, Identifiable {
    case name, recent, closeness, longAgo
    var id: String { rawValue }
    var title: String {
        switch self {
        case .name: return "По имени"
        case .recent: return "Недавно добавленные"
        case .closeness: return "По близости"
        case .longAgo: return "Давно не общались"
        }
    }
}

/// Подсказки «до мелочей».
enum DetailTemplates {
    static let categories: [(String, [String])] = [
        ("Семья", ["Супруг(а)", "Дети", "Родители", "Братья и сёстры", "Питомцы", "Семейное положение"]),
        ("Интересы", ["Хобби", "Спорт", "Любимая музыка", "Любимые фильмы", "Любимые книги", "Игры", "Путешествия"]),
        ("Вкусы", ["Любимая еда", "Не ест", "Любимые напитки", "Кофе / чай — как пьёт", "Любимый ресторан", "Любимые цвета", "Любимые цветы"]),
        ("Подарки", ["Идеи подарков", "Что уже дарил(а)", "Размер одежды", "Размер обуви", "Размер кольца", "Мечтает о"]),
        ("Работа и учёба", ["Образование", "Профессия", "Навыки", "Карьера", "Бизнес / проекты", "Доход / статус"]),
        ("Личность", ["Характер", "Знак зодиака", "Ценности / взгляды", "Цели и мечты", "Страхи", "Больные темы", "Чувство юмора", "Языки"]),
        ("Здоровье", ["Аллергии", "Особенности здоровья", "Вредные привычки", "Группа крови"]),
        ("Быт", ["Автомобиль", "Где живёт", "Распорядок дня", "Удобное время для звонка"]),
        ("Связи", ["Общие знакомые", "Кто познакомил", "Может помочь с", "Я должен(а)", "Мне должны"]),
        ("Важные даты", ["Годовщина свадьбы", "День знакомства", "Именины", "Другая дата"]),
    ]
    static let relations = ["Семья", "Друг", "Близкий друг", "Коллега", "Знакомый", "Сосед", "Клиент", "Партнёр", "Одноклассник", "Однокурсник"]
    static let genders = ["Мужской", "Женский"]
    static let journalKinds = ["Встреча", "Звонок", "Переписка", "Событие", "Подарок", "Заметка"]
}
