package com.kartoteka.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveDao {
    @Transaction
    @Query("SELECT * FROM persons")
    fun observeAll(): Flow<List<PersonFull>>

    @Transaction
    @Query("SELECT * FROM persons")
    suspend fun getAll(): List<PersonFull>

    @Transaction
    @Query("SELECT * FROM persons WHERE id = :id")
    fun observePerson(id: Long): Flow<PersonFull?>

    @Transaction
    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getPerson(id: Long): PersonFull?

    @Insert
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Query("UPDATE persons SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE persons SET avatarPath = :path, updatedAt = :now WHERE id = :id")
    suspend fun setAvatar(id: Long, path: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE persons SET lastContactAt = :time WHERE id = :id AND (lastContactAt IS NULL OR lastContactAt < :time)")
    suspend fun touchContact(id: Long, time: Long)

    // --- контакты и детали ---
    @Query("DELETE FROM contacts WHERE personId = :personId")
    suspend fun deleteContacts(personId: Long)

    @Insert
    suspend fun insertContacts(items: List<ContactItem>)

    @Query("DELETE FROM details WHERE personId = :personId")
    suspend fun deleteDetails(personId: Long)

    @Insert
    suspend fun insertDetails(items: List<DetailField>)

    @Query("SELECT DISTINCT name FROM details ORDER BY name")
    suspend fun detailNames(): List<String>

    // --- фото ---
    @Insert
    suspend fun insertPhoto(photo: Photo): Long

    @Delete
    suspend fun deletePhoto(photo: Photo)

    @Update
    suspend fun updatePhoto(photo: Photo)

    // --- группы ---
    @Query(
        """SELECT g.*, (SELECT COUNT(*) FROM person_groups pg WHERE pg.groupId = g.id) AS count
           FROM groups g ORDER BY g.name COLLATE NOCASE"""
    )
    fun observeGroups(): Flow<List<GroupWithCount>>

    @Query("SELECT * FROM groups ORDER BY name COLLATE NOCASE")
    suspend fun getGroups(): List<Group>

    @Insert
    suspend fun insertGroup(group: Group): Long

    @Update
    suspend fun updateGroup(group: Group)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("DELETE FROM person_groups WHERE personId = :personId")
    suspend fun clearPersonGroups(personId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPersonGroups(items: List<PersonGroup>)

    @Query("SELECT * FROM person_groups")
    suspend fun getPersonGroups(): List<PersonGroup>

    // --- хроника ---
    @Insert
    suspend fun insertJournal(entry: JournalEntry): Long

    @Delete
    suspend fun deleteJournal(entry: JournalEntry)

    @Transaction
    suspend fun savePerson(
        person: Person,
        contacts: List<ContactItem>,
        details: List<DetailField>,
        groupIds: Collection<Long>,
    ): Long {
        val id = if (person.id == 0L) insertPerson(person) else person.id.also { updatePerson(person) }
        deleteContacts(id)
        insertContacts(contacts.filter { it.value.isNotBlank() }.map { it.copy(id = 0, personId = id) })
        deleteDetails(id)
        insertDetails(
            details.filter { it.name.isNotBlank() || it.value.isNotBlank() }
                .mapIndexed { i, d -> d.copy(id = 0, personId = id, position = i) }
        )
        clearPersonGroups(id)
        insertPersonGroups(groupIds.map { PersonGroup(id, it) })
        return id
    }

    @Query("DELETE FROM persons")
    suspend fun wipePersons()

    @Query("DELETE FROM groups")
    suspend fun wipeGroups()
}
