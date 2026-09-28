import XCTest
@testable import RVaultCore

final class PhoneFormatTests: XCTestCase {
    let ua = PhoneFormat.byIso("UA"), ru = PhoneFormat.byIso("RU")

    func testUkrainian() {
        XCTAssertEqual(PhoneFormat.normalize("0930743829", country: ua), "+380930743829")
        XCTAssertEqual(PhoneFormat.normalize("(093) 074 38 29", country: ua), "+380930743829")
        XCTAssertEqual(PhoneFormat.normalize("380930743829", country: ua), "+380930743829")
        XCTAssertEqual(PhoneFormat.normalize("00380930743829", country: ua), "+380930743829")
        XCTAssertEqual(PhoneFormat.normalize("+7 900 111-22-33", country: ua), "+79001112233")
    }

    func testRussian() {
        XCTAssertEqual(PhoneFormat.normalize("8 900 111 22 33", country: ru), "+79001112233")
    }

    func testPretty() {
        XCTAssertEqual(PhoneFormat.pretty("+380930743829"), "+380 93 074 38 29")
        XCTAssertEqual(PhoneFormat.pretty("@user"), "@user")
        XCTAssertEqual(ua.flag, "🇺🇦")
    }
}

final class LogicTests: XCTestCase {
    func date(_ y: Int, _ m: Int, _ d: Int, _ h: Int = 0, _ min: Int = 0) -> Date {
        ArchiveLogic.calendar.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }

    func testSearchCyrillicCaseInsensitive() {
        var ivan = Person(lastName: "Петров", firstName: "Иван")
        ivan.details = [DetailField(category: "Интересы", name: "Хобби", value: "Рыбалка и шахматы")]
        let anna = Person(firstName: "Анна", notes: "Любит пионы")
        XCTAssertEqual(ArchiveLogic.search([ivan, anna], query: "иВАН").count, 1)
        let hit = ArchiveLogic.search([ivan, anna], query: "шахмат")
        XCTAssertEqual(hit.first?.matchedIn?.hasPrefix("Хобби:"), true)
        XCTAssertEqual(ArchiveLogic.search([ivan, anna], query: "пион").first?.person.firstName, "Анна")
    }

    func testBirthdays() {
        let p = Person(birthDay: 5, birthMonth: 1, birthYear: 1990)
        let today = date(2026, 9, 27)
        XCTAssertEqual(ArchiveLogic.nextBirthday(p, today: today), date(2027, 1, 5))
        XCTAssertEqual(ArchiveLogic.turningAge(p, today: today), 37)
        XCTAssertEqual(ArchiveLogic.age(p, today: today), 36)
        let leap = Person(birthDay: 29, birthMonth: 2)
        XCTAssertEqual(ArchiveLogic.nextBirthday(leap, today: date(2027, 1, 1)), date(2027, 2, 28))
        XCTAssertEqual(ArchiveLogic.daysString(21), "через 21 день")
    }

    func testTemplatesInAllLanguages() {
        let anna = Person(firstName: "Анна", middleName: "Викторовна")
        let a = Appointment(personId: anna.id, start: date(2026, 10, 2, 14, 30), title: "Стрижка", channel: .whatsapp)
        let now = date(2026, 10, 1, 9)
        XCTAssertEqual(AppointmentLogic.fill(MessageLang.uk.template(.reminder), a, anna, lang: .uk, now: now),
                       "Анна, нагадую про запис: завтра о 14:30.\nСтрижка\nЯкщо плани змінилися — будь ласка, повідомте.")
        XCTAssertEqual(AppointmentLogic.fill(MessageLang.ru.template(.confirm), a, anna, lang: .ru, now: now),
                       "Анна, здравствуйте! Подтверждаю вашу запись: 2 октября (пятница) в 14:30.\nСтрижка")
        XCTAssertEqual(AppointmentLogic.fill(MessageLang.en.template(.confirm), a, anna, lang: .en, now: now),
                       "Hi Анна! Your appointment is confirmed: Friday, October 2 at 14:30.\nСтрижка")
        XCTAssertEqual(ArchiveLogic.fillTemplate("{ім'я} / {name} / {імʼя_по_батькові}", anna), "Анна / Анна / Анна Викторовна")
    }

    func testReminders() {
        var a = Appointment(personId: UUID(), start: date(2026, 10, 2, 14, 30), channel: .whatsapp)
        a.clientOffsets = [24 * 60, 120]; a.myOffsets = [30]
        let r = AppointmentLogic.reminders(a, now: date(2026, 10, 2, 9, 30))
        XCTAssertEqual(r.map(\.key), ["CLIENT_120", "ME_30"])
        a.sentReminders = ["CLIENT_120"]
        XCTAssertEqual(AppointmentLogic.reminders(a, now: date(2026, 10, 2, 9, 30)).map(\.key), ["ME_30"])
        XCTAssertEqual(AppointmentLogic.offsetTitle(3 * 24 * 60), "за 3 дня")
    }

    func testRelations() {
        let maria = Person(firstName: "Мария", gender: "Женский"), ivan = Person(firstName: "Иван", gender: "Мужской")
        let r = Relation(personId: ivan.id, relatedId: maria.id, type: .parent)
        let people = [maria.id: maria, ivan.id: ivan]
        XCTAssertEqual(Relations.view(for: ivan.id, relations: [r], people: people).first?.label, "Мать")
        XCTAssertEqual(Relations.view(for: maria.id, relations: [r], people: people).first?.label, "Сын")
        RelationType.allCases.forEach { XCTAssertEqual($0.inverse.inverse, $0) }
    }

    func testFilter() {
        let today = date(2026, 9, 28)
        let anna = Person(firstName: "Анна", birthYear: 1990, gender: "Женский", city: "Киев")
        let olga = Person(firstName: "Ольга", gender: "Женский", city: " киев")
        let ivan = Person(firstName: "Иван", gender: "Мужской", city: "Львов")
        let f = RecipientFilter(genders: ["Женский"], cities: ["Киев"])
        XCTAssertEqual([anna, olga, ivan].filter { f.matches($0, today: today) }.map(\.firstName), ["Анна", "Ольга"])
        let age = RecipientFilter(ageFrom: 30, ageTo: 40)
        XCTAssertEqual([anna, olga, ivan].filter { age.matches($0, today: today) }.map(\.firstName), ["Анна"])
    }
}
