package com.kartoteka.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class Repository(
    private val db: AppDatabase,
    val photos: PhotoStorage,
    val voices: VoiceStorage,
) {
    private val dao = db.dao()

    fun observeAll(): Flow<List<PersonFull>> = dao.observeAll()
    fun observePerson(id: Long): Flow<PersonFull?> = dao.observePerson(id)
    fun observeGroups(): Flow<List<GroupWithCount>> = dao.observeGroups()

    suspend fun getAll() = dao.getAll()
    suspend fun getPerson(id: Long) = dao.getPerson(id)
    suspend fun getGroups() = dao.getGroups()
    suspend fun knownDetailNames() = dao.detailNames()

    suspend fun savePerson(
        person: Person,
        contacts: List<ContactItem>,
        details: List<DetailField>,
        groupIds: Collection<Long>,
        places: List<Place>? = null,
    ): Long {
        val old = if (person.id != 0L) dao.getPerson(person.id) else null
        val id = dao.savePerson(person.copy(updatedAt = System.currentTimeMillis()), contacts, details, groupIds, places)
        // Аватар заменили — удалим старый файл, если он не лежит в галерее человека.
        val oldAvatar = old?.person?.avatarPath
        if (oldAvatar != null && oldAvatar != person.avatarPath && old.photos.none { it.path == oldAvatar }) {
            photos.delete(oldAvatar)
        }
        return id
    }

    suspend fun deletePerson(id: Long) {
        val pf = dao.getPerson(id) ?: return
        val notes = dao.voiceNotesOf(id)
        dao.deletePerson(id)
        notes.forEach { voices.delete(it.file) }
        photos.delete(pf.person.avatarPath)
        pf.photos.forEach { photos.delete(it.path) }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun addPhoto(personId: Long, path: String, makeAvatarIfEmpty: Boolean = true) {
        dao.insertPhoto(Photo(personId = personId, path = path))
        val p = dao.getPerson(personId)?.person ?: return
        if (makeAvatarIfEmpty && p.avatarPath == null) dao.setAvatar(personId, path)
    }

    suspend fun deletePhoto(photo: Photo) {
        dao.deletePhoto(photo)
        val p = dao.getPerson(photo.personId)?.person
        if (p?.avatarPath == photo.path) dao.setAvatar(photo.personId, null)
        photos.delete(photo.path)
    }

    /**
     * Фото из версий до 1.7 лежали открытыми .jpg — шифруем их и обновляем пути в базе.
     * Безопасно запускать при каждом старте: зашифрованные пропускаются. Возвращает число файлов.
     */
    suspend fun encryptLegacyPhotos(): Int {
        val paths = dao.getAll().flatMap { pf -> pf.photos.map { it.path } + listOfNotNull(pf.person.avatarPath) }
            .filterNot { photos.isEncrypted(it) }.toSet()
        if (paths.isEmpty()) return 0
        val moved = paths.mapNotNull { old -> photos.encryptLegacy(old)?.let { old to it } }
        db.withTransaction {
            moved.forEach { (old, new) -> dao.renamePhotoPath(old, new); dao.renameAvatarPath(old, new) }
        }
        moved.forEach { (old, _) -> photos.delete(old) }
        return moved.size
    }

    // --- голосовые заметки ---
    fun observeVoiceNotes(personId: Long) = dao.observeVoiceNotes(personId)
    suspend fun allVoiceNotes() = dao.allVoiceNotes()
    suspend fun addVoiceNote(note: VoiceNote): Long = dao.insertVoiceNote(note)
    suspend fun setVoiceText(id: Long, text: String) = dao.setVoiceText(id, text)
    suspend fun deleteVoiceNote(note: VoiceNote) {
        dao.deleteVoiceNote(note)
        voices.delete(note.file)
    }

    suspend fun updatePhotoCaption(photo: Photo, caption: String) = dao.updatePhoto(photo.copy(caption = caption))

    suspend fun setAvatar(personId: Long, path: String?) = dao.setAvatar(personId, path)

    suspend fun saveGroup(group: Group): Long =
        if (group.id == 0L) dao.insertGroup(group) else group.id.also { dao.updateGroup(group) }

    suspend fun deleteGroup(id: Long) = dao.deleteGroup(id)

    suspend fun addJournal(entry: JournalEntry) {
        dao.insertJournal(entry)
        if (entry.kind != "Заметка") dao.touchContact(entry.personId, entry.date)
    }

    suspend fun deleteJournal(entry: JournalEntry) = dao.deleteJournal(entry)

    /** Отметить, что сегодня связались (например, после рассылки). */
    suspend fun touchContact(personId: Long) = dao.touchContact(personId, System.currentTimeMillis())

    suspend fun addPersonsToGroup(groupId: Long, personIds: Collection<Long>) =
        dao.insertPersonGroups(personIds.map { PersonGroup(it, groupId) })

    /** Приводит все телефоны к международному виду. Возвращает число изменённых номеров. */
    suspend fun normalizeAllPhones(country: Country): Int {
        var changed = 0
        dao.getAll().forEach { pf ->
            var touched = false
            val contacts = pf.contacts.map { c ->
                if (!PhoneFormat.isPhoneType(c.contactType)) return@map c
                val n = PhoneFormat.normalize(c.value, country)
                if (n != c.value) { changed++; touched = true; c.copy(value = n) } else c
            }
            if (touched) dao.savePerson(pf.person, contacts, pf.details, pf.groups.map { it.id })
        }
        return changed
    }

    // --- адреса ---
    suspend fun setPlaceCoords(id: Long, lat: Double?, lng: Double?) = dao.setPlaceCoords(id, lat, lng)
    suspend fun placesWithoutCoords() = dao.placesWithoutCoords()

    // --- связи ---
    fun observeRelations() = dao.observeRelations()

    /** Добавляет связь, если между этими людьми её ещё нет. */
    suspend fun addRelation(personId: Long, relatedId: Long, type: RelationType): Boolean {
        if (personId == relatedId || dao.relationCount(personId, relatedId) > 0) return false
        dao.insertRelation(Relation(personId = personId, relatedId = relatedId, type = type.name))
        return true
    }

    suspend fun deleteRelation(id: Long) = dao.deleteRelation(id)

    // --- календарь ---
    fun observeAppointments(from: Long, to: Long) = dao.observeAppointments(from, to)
    fun observePersonAppointments(personId: Long) = dao.observePersonAppointments(personId)
    fun observeAppointment(id: Long) = dao.observeAppointment(id)
    suspend fun getAppointment(id: Long) = dao.getAppointment(id)
    suspend fun appointmentsBetween(from: Long, to: Long) = dao.appointmentsBetween(from, to)

    /**
     * Сохраняет запись и пересоздаёт неотправленные напоминания.
     * Возвращает id записи и новые напоминания (их нужно поставить в будильник).
     */
    suspend fun saveAppointment(a: Appointment, clientOffsets: List<Int>, myOffsets: List<Int>): Pair<Long, List<AppointmentReminder>> =
        db.withTransaction {
            val id = if (a.id == 0L) dao.insertAppointment(a) else a.id.also { dao.updateAppointment(a) }
            dao.deletePendingReminders(id)
            val saved = a.copy(id = id)
            val reminders = if (saved.appointmentStatus == AppointmentStatus.PLANNED)
                AppointmentLogic.buildReminders(saved, clientOffsets, myOffsets) else emptyList()
            val ids = dao.insertReminders(reminders)
            id to reminders.zip(ids) { r, rid -> r.copy(id = rid) }
        }

    suspend fun pendingRemindersFor(appointmentId: Long) = dao.pendingRemindersFor(appointmentId)
    suspend fun setAppointmentStatus(id: Long, status: AppointmentStatus) {
        dao.setAppointmentStatus(id, status.name)
        if (status != AppointmentStatus.PLANNED) dao.deletePendingReminders(id)
    }
    suspend fun deleteAppointment(id: Long) = dao.deleteAppointment(id)
    suspend fun getReminder(id: Long) = dao.getReminder(id)
    suspend fun markReminderSent(id: Long) = dao.markReminderSent(id, System.currentTimeMillis())
    suspend fun upcomingReminders(since: Long) = dao.upcomingReminders(since)

    // --- услуги ---
    fun observeServices() = dao.observeServices()
    suspend fun getServices() = dao.getServices()
    suspend fun getService(id: Long?) = id?.let { dao.getService(it) }

    suspend fun saveService(s: ServiceTemplate): Long =
        if (s.id == 0L) dao.insertService(s.copy(name = s.name.trim())) else s.id.also { dao.updateService(s.copy(name = s.name.trim())) }

    /** Записи остаются, но дальше пишут по общим шаблонам. */
    suspend fun deleteService(id: Long) = db.withTransaction {
        dao.detachService(id)
        dao.deleteService(id)
    }

    internal val rawDao get() = dao
    internal val database get() = db
}
