import Foundation
import SwiftUI
import UIKit

/// Весь архив в одном файле с защитой iOS (NSFileProtectionComplete):
/// пока телефон заблокирован, файл зашифрован ключом устройства.
@MainActor
final class Store: ObservableObject {
    static let shared = Store()

    @Published private(set) var archive = Archive()

    private let fileURL: URL
    let photosDir: URL

    private init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        fileURL = docs.appendingPathComponent("archive.json")
        photosDir = docs.appendingPathComponent("photos", isDirectory: true)
        try? FileManager.default.createDirectory(at: photosDir, withIntermediateDirectories: true,
                                                 attributes: [.protectionKey: FileProtectionType.complete])
        load()
    }

    var people: [Person] { archive.people }
    var groups: [PeopleGroup] { archive.groups }
    var relations: [Relation] { archive.relations }
    var appointments: [Appointment] { archive.appointments }

    func person(_ id: UUID?) -> Person? { archive.people.first { $0.id == id } }
    func group(_ id: UUID) -> PeopleGroup? { archive.groups.first { $0.id == id } }
    func groups(of p: Person) -> [PeopleGroup] { p.groupIds.compactMap { group($0) } }

    // MARK: - Люди

    func save(_ person: Person) {
        var p = person
        p.updatedAt = Date()
        if let i = archive.people.firstIndex(where: { $0.id == p.id }) {
            let old = archive.people[i]
            archive.people[i] = p
            if let a = old.avatar, a != p.avatar { deletePhotoFileIfUnused(a) }
        } else {
            archive.people.append(p)
        }
        persist()
    }

    func update(_ id: UUID, _ change: (inout Person) -> Void) {
        guard let i = archive.people.firstIndex(where: { $0.id == id }) else { return }
        change(&archive.people[i])
        persist()
    }

    func delete(_ id: UUID) {
        guard let p = person(id) else { return }
        archive.people.removeAll { $0.id == id }
        archive.relations.removeAll { $0.personId == id || $0.relatedId == id }
        archive.appointments.removeAll { $0.personId == id }
        p.photos.forEach { deletePhotoFileIfUnused($0.file) }
        if let a = p.avatar { deletePhotoFileIfUnused(a) }
        persist()
    }

    func addJournal(_ personId: UUID, kind: String, text: String, date: Date = Date()) {
        update(personId) {
            $0.journal.append(JournalEntry(date: date, kind: kind, text: text))
            if kind != "Заметка", ($0.lastContactAt ?? .distantPast) < date { $0.lastContactAt = date }
        }
    }

    func touchContact(_ personId: UUID) {
        update(personId) { $0.lastContactAt = Date() }
    }

    // MARK: - Фото

    func addPhoto(_ image: UIImage, to personId: UUID) {
        guard let file = storePhoto(image) else { return }
        update(personId) {
            $0.photos.append(Photo(file: file))
            if $0.avatar == nil { $0.avatar = file }
        }
    }

    func deletePhoto(_ photo: Photo, of personId: UUID) {
        update(personId) {
            $0.photos.removeAll { $0.id == photo.id }
            if $0.avatar == photo.file { $0.avatar = $0.photos.first?.file }
        }
        deletePhotoFileIfUnused(photo.file)
    }

    /// Сохраняет JPEG (до 2048 px) во внутреннюю папку. Возвращает имя файла.
    func storePhoto(_ image: UIImage) -> String? {
        let resized = image.resized(maxSide: 2048)
        guard let data = resized.jpegData(compressionQuality: 0.88) else { return nil }
        let name = UUID().uuidString + ".jpg"
        do {
            try data.write(to: photosDir.appendingPathComponent(name), options: [.atomic, .completeFileProtection])
            return name
        } catch {
            return nil
        }
    }

    func storePhotoData(_ data: Data, name: String) {
        try? data.write(to: photosDir.appendingPathComponent(name), options: [.atomic, .completeFileProtection])
    }

    func photoURL(_ file: String) -> URL { photosDir.appendingPathComponent(file) }

    func image(_ file: String?) -> UIImage? {
        guard let file else { return nil }
        return UIImage(contentsOfFile: photoURL(file).path)
    }

    private func deletePhotoFileIfUnused(_ file: String) {
        let used = archive.people.contains { p in p.avatar == file || p.photos.contains { $0.file == file } }
        if !used { try? FileManager.default.removeItem(at: photoURL(file)) }
    }

    // MARK: - Группы и связи

    func save(_ group: PeopleGroup) {
        if let i = archive.groups.firstIndex(where: { $0.id == group.id }) { archive.groups[i] = group } else { archive.groups.append(group) }
        persist()
    }

    func deleteGroup(_ id: UUID) {
        archive.groups.removeAll { $0.id == id }
        for i in archive.people.indices { archive.people[i].groupIds.removeAll { $0 == id } }
        persist()
    }

    @discardableResult
    func addRelation(_ personId: UUID, _ relatedId: UUID, _ type: RelationType) -> Bool {
        let exists = archive.relations.contains {
            ($0.personId == personId && $0.relatedId == relatedId) || ($0.personId == relatedId && $0.relatedId == personId)
        }
        guard personId != relatedId, !exists else { return false }
        archive.relations.append(Relation(personId: personId, relatedId: relatedId, type: type))
        persist()
        return true
    }

    func deleteRelation(_ id: UUID) {
        archive.relations.removeAll { $0.id == id }
        persist()
    }

    // MARK: - Записи

    func appointment(_ id: UUID?) -> Appointment? { archive.appointments.first { $0.id == id } }

    func save(_ a: Appointment) {
        if let i = archive.appointments.firstIndex(where: { $0.id == a.id }) { archive.appointments[i] = a } else { archive.appointments.append(a) }
        persist()
    }

    func deleteAppointment(_ id: UUID) {
        archive.appointments.removeAll { $0.id == id }
        persist()
    }

    func markReminderSent(_ id: UUID, key: String) {
        guard let i = archive.appointments.firstIndex(where: { $0.id == id }) else { return }
        if !archive.appointments[i].sentReminders.contains(key) { archive.appointments[i].sentReminders.append(key) }
        persist()
    }

    // MARK: - Прочее

    func normalizeAllPhones(country: Country) -> Int {
        var changed = 0
        for i in archive.people.indices {
            for j in archive.people[i].contacts.indices where archive.people[i].contacts[j].type.isPhone {
                let old = archive.people[i].contacts[j].value
                let n = PhoneFormat.normalize(old, country: country)
                if n != old {
                    archive.people[i].contacts[j].value = n
                    changed += 1
                }
            }
        }
        if changed > 0 { persist() }
        return changed
    }

    func replaceArchive(_ new: Archive) {
        archive = new
        persist()
    }

    /// Добавляет данные из копии: людей с новыми id, группы — по названию.
    func merge(_ other: Archive) {
        var groupMap: [UUID: UUID] = [:]
        for g in other.groups {
            if let existing = archive.groups.first(where: { $0.name == g.name }) {
                groupMap[g.id] = existing.id
            } else {
                archive.groups.append(g)
                groupMap[g.id] = g.id
            }
        }
        let known = Set(archive.people.map(\.id))
        for var p in other.people where !known.contains(p.id) {
            p.groupIds = p.groupIds.compactMap { groupMap[$0] }
            archive.people.append(p)
        }
        let ids = Set(archive.people.map(\.id))
        let rels = Set(archive.relations.map(\.id))
        archive.relations += other.relations.filter { ids.contains($0.personId) && ids.contains($0.relatedId) && !rels.contains($0.id) }
        let appts = Set(archive.appointments.map(\.id))
        archive.appointments += other.appointments.filter { ids.contains($0.personId) && !appts.contains($0.id) }
        persist()
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        if let a = try? decoder.decode(Archive.self, from: data) { archive = a }
    }

    private func persist() {
        objectWillChange.send()
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .secondsSince1970
        if let data = try? encoder.encode(archive) {
            try? data.write(to: fileURL, options: [.atomic, .completeFileProtection])
        }
        Reminders.reschedule()
    }
}

extension UIImage {
    func resized(maxSide: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxSide else { return self }
        let scale = maxSide / longest
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in draw(in: CGRect(origin: .zero, size: target)) }
    }
}
