package com.kartoteka.app.data

import kotlinx.coroutines.flow.Flow

class Repository(
    private val db: AppDatabase,
    val photos: PhotoStorage,
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
    ): Long {
        val old = if (person.id != 0L) dao.getPerson(person.id) else null
        val id = dao.savePerson(person.copy(updatedAt = System.currentTimeMillis()), contacts, details, groupIds)
        // Аватар заменили — удалим старый файл, если он не лежит в галерее человека.
        val oldAvatar = old?.person?.avatarPath
        if (oldAvatar != null && oldAvatar != person.avatarPath && old.photos.none { it.path == oldAvatar }) {
            photos.delete(oldAvatar)
        }
        return id
    }

    suspend fun deletePerson(id: Long) {
        val pf = dao.getPerson(id) ?: return
        dao.deletePerson(id)
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

    internal val rawDao get() = dao
    internal val database get() = db
}
