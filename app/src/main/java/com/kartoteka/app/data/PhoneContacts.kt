package com.kartoteka.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds

data class PhoneContact(
    val id: Long,
    val name: String,
    val phones: List<String>,
    val emails: List<String>,
    val birthday: String?,
    val organization: String?,
    val hasPhoto: Boolean,
)

/** Чтение контактов телефона для импорта в картотеку. */
class PhoneContacts(private val context: Context) {

    fun load(): List<PhoneContact> {
        val cr = context.contentResolver
        val phones = HashMap<Long, MutableList<String>>()
        val emails = HashMap<Long, MutableList<String>>()
        val birthdays = HashMap<Long, String>()
        val orgs = HashMap<Long, String>()

        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val value = c.getString(2) ?: continue
                when (c.getString(1)) {
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> phones.getOrPut(id) { mutableListOf() }.add(value)
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> emails.getOrPut(id) { mutableListOf() }.add(value)
                    CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                        if (c.getInt(3) == CommonDataKinds.Event.TYPE_BIRTHDAY) birthdays[id] = value
                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> orgs[id] = value
                }
            }
        }

        val result = mutableListOf<PhoneContact>()
        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_ID),
            null, null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                result += PhoneContact(
                    id = id, name = name,
                    phones = phones[id].orEmpty().distinctBy { ArchiveLogic.normalizePhone(it) },
                    emails = emails[id].orEmpty().distinct(),
                    birthday = birthdays[id], organization = orgs[id],
                    hasPhoto = !c.isNull(2),
                )
            }
        }
        return result
    }

    suspend fun import(contacts: List<PhoneContact>, repo: Repository): Int {
        contacts.forEach { pc ->
            val parts = pc.name.trim().split(Regex("\\s+"))
            val (first, last) = when (parts.size) {
                1 -> parts[0] to ""
                else -> parts[0] to parts.drop(1).joinToString(" ")
            }
            val bd = parseBirthday(pc.birthday)
            val avatar = if (pc.hasPhoto) loadPhoto(pc.id, repo.photos) else null
            val person = Person(
                firstName = first, lastName = last, company = pc.organization.orEmpty(),
                birthDay = bd?.first, birthMonth = bd?.second, birthYear = bd?.third, avatarPath = avatar,
            )
            val contactItems = pc.phones.map { ContactItem(type = ContactType.PHONE.name, value = it) } +
                pc.emails.map { ContactItem(type = ContactType.EMAIL.name, value = it) }
            val id = repo.savePerson(person, contactItems, emptyList(), emptyList())
            if (avatar != null) repo.rawDao.insertPhoto(Photo(personId = id, path = avatar))
        }
        return contacts.size
    }

    private fun loadPhoto(contactId: Long, storage: PhotoStorage): String? {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        return ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, uri, true)
            ?.use { storage.importStream(it) }
    }

    /** Форматы: yyyy-MM-dd или --MM-dd (без года). */
    private fun parseBirthday(raw: String?): Triple<Int, Int, Int?>? {
        if (raw == null) return null
        Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(raw)?.let {
            val (y, m, d) = it.destructured
            return Triple(d.toInt(), m.toInt(), y.toInt())
        }
        Regex("""^--(\d{2})-(\d{2})""").find(raw)?.let {
            val (m, d) = it.destructured
            return Triple(d.toInt(), m.toInt(), null)
        }
        return null
    }
}
