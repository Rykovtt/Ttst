package com.kartoteka.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lastName: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val nickname: String = "",
    val birthDay: Int? = null,
    val birthMonth: Int? = null,
    val birthYear: Int? = null,
    val gender: String = "",
    val relation: String = "",
    val closeness: Int = 0,
    val company: String = "",
    val position: String = "",
    val city: String = "",
    val address: String = "",
    val howMet: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
    val avatarPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastContactAt: Long? = null,
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { nickname.ifBlank { "Без имени" } }

    val fullName: String
        get() = listOf(lastName, firstName, middleName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { displayName }

    val sortKey: String
        get() = (firstName.ifBlank { lastName.ifBlank { nickname } }).trim().lowercase()
}

enum class ContactType(val title: String) {
    PHONE("Телефон"),
    EMAIL("E-mail"),
    TELEGRAM("Telegram"),
    WHATSAPP("WhatsApp"),
    VIBER("Viber"),
    INSTAGRAM("Instagram"),
    VK("ВКонтакте"),
    FACEBOOK("Facebook"),
    WEBSITE("Сайт"),
    OTHER("Другое");

    companion object {
        fun of(name: String): ContactType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

@Entity(
    tableName = "contacts",
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class ContactItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long = 0,
    val type: String = ContactType.PHONE.name,
    val label: String = "",
    val value: String = "",
) {
    val contactType: ContactType get() = ContactType.of(type)
}

/** Произвольная деталь о человеке: «Интересы / Хобби / рыбалка». */
@Entity(
    tableName = "details",
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class DetailField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long = 0,
    val category: String = "",
    val name: String = "",
    val value: String = "",
    val position: Int = 0,
)

@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val path: String,
    val caption: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFF5B4BD6,
    val emoji: String = "",
)

@Entity(
    tableName = "person_groups",
    primaryKeys = ["personId", "groupId"],
    foreignKeys = [
        ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Group::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("groupId")],
)
data class PersonGroup(val personId: Long, val groupId: Long)

/** Хроника: встречи, звонки, события, заметки. */
@Entity(
    tableName = "journal",
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val date: Long = System.currentTimeMillis(),
    val kind: String = "Заметка",
    val text: String,
)

data class PersonFull(
    @Embedded val person: Person,
    @Relation(parentColumn = "id", entityColumn = "personId")
    val contacts: List<ContactItem>,
    @Relation(parentColumn = "id", entityColumn = "personId")
    val details: List<DetailField>,
    @Relation(parentColumn = "id", entityColumn = "personId")
    val photos: List<Photo>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(PersonGroup::class, parentColumn = "personId", entityColumn = "groupId"),
    )
    val groups: List<Group>,
    @Relation(parentColumn = "id", entityColumn = "personId")
    val journal: List<JournalEntry>,
) {
    fun firstOf(type: ContactType): String? =
        contacts.firstOrNull { it.contactType == type && it.value.isNotBlank() }?.value

    val phone: String? get() = firstOf(ContactType.PHONE)
    val whatsapp: String? get() = firstOf(ContactType.WHATSAPP) ?: phone
    val telegram: String? get() = firstOf(ContactType.TELEGRAM)
    val viber: String? get() = firstOf(ContactType.VIBER) ?: phone
    val email: String? get() = firstOf(ContactType.EMAIL)
}

data class GroupWithCount(
    @Embedded val group: Group,
    val count: Int,
)
