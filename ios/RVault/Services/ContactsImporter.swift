import Contacts
import Foundation
import UIKit

struct PhoneContact: Identifiable, Hashable {
    let id: String
    let name: String
    let givenName: String
    let familyName: String
    let phones: [String]
    let emails: [String]
    let organization: String
    let birthday: DateComponents?
    let thumbnail: Data?
}

/// Импорт людей из «Контактов» iPhone.
enum ContactsImporter {
    static func requestAccess() async -> Bool {
        (try? await CNContactStore().requestAccess(for: .contacts)) ?? false
    }

    static func load() -> [PhoneContact] {
        let keys: [CNKeyDescriptor] = [
            CNContactGivenNameKey, CNContactFamilyNameKey, CNContactPhoneNumbersKey, CNContactEmailAddressesKey,
            CNContactOrganizationNameKey, CNContactBirthdayKey, CNContactThumbnailImageDataKey,
        ] as [CNKeyDescriptor]
        var result: [PhoneContact] = []
        let request = CNContactFetchRequest(keysToFetch: keys)
        request.sortOrder = .givenName
        try? CNContactStore().enumerateContacts(with: request) { c, _ in
            let name = [c.givenName, c.familyName].filter { !$0.isEmpty }.joined(separator: " ")
            guard !name.isEmpty || !c.organizationName.isEmpty else { return }
            result.append(PhoneContact(
                id: c.identifier, name: name.isEmpty ? c.organizationName : name,
                givenName: c.givenName, familyName: c.familyName,
                phones: c.phoneNumbers.map { $0.value.stringValue },
                emails: c.emailAddresses.map { $0.value as String },
                organization: c.organizationName, birthday: c.birthday, thumbnail: c.thumbnailImageData
            ))
        }
        return result
    }

    @MainActor
    static func importContacts(_ list: [PhoneContact], country: Country) -> Int {
        let store = Store.shared
        for c in list {
            var p = Person(lastName: c.familyName, firstName: c.givenName.isEmpty ? c.name : c.givenName, company: c.organization)
            if let b = c.birthday {
                p.birthDay = b.day
                p.birthMonth = b.month
                p.birthYear = b.year
            }
            p.contacts = c.phones.map { ContactItem(type: .phone, value: PhoneFormat.normalize($0, country: country)) }
                + c.emails.map { ContactItem(type: .email, value: $0) }
            if let data = c.thumbnail, let img = UIImage(data: data), let file = store.storePhoto(img) {
                p.avatar = file
                p.photos = [Photo(file: file)]
            }
            store.save(p)
        }
        return list.count
    }
}
