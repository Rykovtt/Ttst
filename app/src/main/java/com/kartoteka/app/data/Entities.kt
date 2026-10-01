package com.kartoteka.app.data

import com.kartoteka.app.i18n.t

import androidx.room.ColumnInfo
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
    val howMet: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
    val avatarPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastContactAt: Long? = null,
    /** Язык сообщений этому человеку; пусто — как в настройках. */
    @ColumnInfo(defaultValue = "")
    val language: String = "",
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

enum class ContactType(private val titleRu: String) {
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

    /** Название на языке интерфейса. */
    val title: String get() = t(titleRu)

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
    @Relation(parentColumn = "id", entityColumn = "personId")
    val places: List<Place> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "personId")
    val voiceNotes: List<VoiceNote> = emptyList(),
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

enum class PlaceKind(private val titleRu: String) {
    HOME("Дом"), WORK("Работа"), OTHER("Другое");

    /** Название на языке интерфейса. */
    val title: String get() = t(titleRu)

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Адрес человека (дом, работа…) с координатами для карты. */
@Entity(
    tableName = "places",
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long = 0,
    val kind: String = PlaceKind.HOME.name,
    val label: String = "",
    /** Устарело: адреса теперь в таблице places (перенесены миграцией 1→2). */
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
) {
    val placeKind: PlaceKind get() = PlaceKind.of(kind)
    val hasCoords: Boolean get() = lat != null && lng != null
}

/** Связь «relatedId является TYPE для personId». Обратная сторона вычисляется через [RelationType.inverse]. */
@Entity(
    tableName = "relations",
    foreignKeys = [
        ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Person::class, ["id"], ["relatedId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId"), Index("relatedId")],
)
data class Relation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val relatedId: Long,
    val type: String,
)

/** Запись в календаре. */
@Entity(
    tableName = "appointments",
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId"), Index("start")],
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val start: Long,
    val durationMin: Int = 60,
    val title: String = "",
    val place: String = "",
    val notes: String = "",
    /** Канал оповещения человека: [NotifyChannel]. */
    val channel: String = NotifyChannel.NONE.name,
    val status: String = AppointmentStatus.PLANNED.name,
    val createdAt: Long = System.currentTimeMillis(),
    /** Услуга, по шаблонам которой пишем человеку; null — общие шаблоны. */
    val serviceId: Long? = null,
) {
    val end: Long get() = start + durationMin * 60_000L
    val notifyChannel: NotifyChannel get() = NotifyChannel.of(channel)
    val appointmentStatus: AppointmentStatus get() = AppointmentStatus.of(status)
}

enum class AppointmentStatus(private val titleRu: String) {
    PLANNED("Запланировано"), DONE("Состоялось"), CANCELLED("Отменено");

    /** Название на языке интерфейса. */
    val title: String get() = t(titleRu)

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: PLANNED
    }
}

enum class NotifyChannel(private val titleRu: String) {
    WHATSAPP("WhatsApp"), TELEGRAM("Telegram"), SMS("SMS"), NONE("Не оповещать");

    /** Название на языке интерфейса. */
    val title: String get() = t(titleRu)

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: NONE
    }
}

enum class ReminderTarget { CLIENT, ME }

/** Одно запланированное напоминание по записи — человеку или мне. */
@Entity(
    tableName = "appointment_reminders",
    foreignKeys = [ForeignKey(Appointment::class, ["id"], ["appointmentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("appointmentId"), Index("fireAt")],
)
data class AppointmentReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appointmentId: Long,
    val target: String,
    val offsetMin: Int,
    val fireAt: Long,
    val sentAt: Long? = null,
)

data class AppointmentFull(
    @Embedded val appointment: Appointment,
    @Relation(parentColumn = "personId", entityColumn = "id")
    val person: Person?,
    @Relation(parentColumn = "id", entityColumn = "appointmentId")
    val reminders: List<AppointmentReminder>,
)

/**
 * Услуга (шаблон записи): «Тату-сеанс», «Консультация»…
 * Свои длительность, место, напоминания и тексты сообщений.
 * Пустой текст — используется общий шаблон из настроек.
 */
@Entity(tableName = "services")
data class ServiceTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationMin: Int = 60,
    val place: String = "",
    /** За сколько минут напомнить человеку / мне, через запятую. */
    val clientOffsets: String = "",
    val myOffsets: String = "",
    val tplConfirm: String = "",
    val tplReminder: String = "",
    val tplReschedule: String = "",
    val tplCancel: String = "",
    val position: Int = 0,
) {
    fun template(kind: TemplateKind): String = when (kind) {
        TemplateKind.CONFIRM -> tplConfirm
        TemplateKind.REMINDER -> tplReminder
        TemplateKind.RESCHEDULE -> tplReschedule
        TemplateKind.CANCEL -> tplCancel
    }

    fun withTemplate(kind: TemplateKind, text: String): ServiceTemplate = when (kind) {
        TemplateKind.CONFIRM -> copy(tplConfirm = text)
        TemplateKind.REMINDER -> copy(tplReminder = text)
        TemplateKind.RESCHEDULE -> copy(tplReschedule = text)
        TemplateKind.CANCEL -> copy(tplCancel = text)
    }

    val ownTemplates: Int get() = TemplateKind.entries.count { template(it).isNotBlank() }
}

/** Голосовая заметка о человеке. Сам звук — в зашифрованном файле [file] (см. [VoiceStorage]). */
@Entity(
    tableName = "voice_notes",
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class VoiceNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val file: String,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
    /** Расшифровка (распознанная на устройстве или написанная вручную). */
    val text: String = "",
)
