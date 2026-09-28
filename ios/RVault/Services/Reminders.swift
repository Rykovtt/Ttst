import Foundation
import UserNotifications

/// Локальные уведомления: напоминания о записях (мне и «пора написать человеку») и дни рождения.
/// iOS хранит не больше 64 запланированных уведомлений — ставим ближайшие 60
/// и пересчитываем при каждом изменении и запуске.
enum Reminders {
    static let categoryClient = "CLIENT_REMINDER"

    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    @MainActor
    static func reschedule() {
        let store = Store.shared
        let now = Date()
        var items: [(Date, UNNotificationRequest)] = []

        for a in store.appointments {
            guard let p = store.person(a.personId) else { continue }
            for r in AppointmentLogic.reminders(a, now: now) {
                let content = UNMutableNotificationContent()
                content.sound = .default
                content.userInfo = ["appointment": a.id.uuidString, "key": r.key, "target": r.target.rawValue]
                if r.target == .me {
                    content.title = "\(AppointmentLogic.timeText(a.start)) · \(p.displayName)"
                    let parts = [AppointmentLogic.offsetTitle(r.offset).replacingOccurrences(of: "за ", with: "Через "), a.title, a.place]
                    content.body = parts.filter { !$0.isBlank }.joined(separator: " · ")
                } else {
                    content.title = "Напомнить \(p.displayName) о записи"
                    content.body = "Нажмите — откроется \(a.channel.title) с готовым текстом"
                    content.categoryIdentifier = categoryClient
                }
                items.append((r.fireAt, request("appt-\(a.id.uuidString)-\(r.key)", content, r.fireAt)))
            }
        }

        if Prefs.d.bool(forKey: Prefs.birthdays) {
            let cal = ArchiveLogic.calendar
            for p in store.people {
                guard let next = ArchiveLogic.nextBirthday(p, today: now) else { continue }
                for daysBefore in [0, 3] {
                    guard let day = cal.date(byAdding: .day, value: -daysBefore, to: next),
                          let fire = cal.date(bySettingHour: 9, minute: 0, second: 0, of: day), fire > now else { continue }
                    let content = UNMutableNotificationContent()
                    content.sound = .default
                    content.userInfo = ["person": p.id.uuidString]
                    let age = ArchiveLogic.turningAge(p, today: now).map { " — исполняется \(ArchiveLogic.ageString($0))" } ?? ""
                    content.title = daysBefore == 0 ? "🎂 Сегодня день рождения: \(p.displayName)" : "Через 3 дня день рождения: \(p.displayName)"
                    content.body = "Не забудьте поздравить\(age)"
                    items.append((fire, request("bd-\(p.id.uuidString)-\(daysBefore)", content, fire)))
                }
            }
        }

        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        for (_, req) in items.sorted(by: { $0.0 < $1.0 }).prefix(60) { center.add(req) }
    }

    private static func request(_ id: String, _ content: UNNotificationContent, _ date: Date) -> UNNotificationRequest {
        let comps = ArchiveLogic.calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        return UNNotificationRequest(identifier: id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false))
    }
}
