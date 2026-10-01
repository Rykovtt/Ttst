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

    // --- адреса ---
    @Query("DELETE FROM places WHERE personId = :personId")
    suspend fun deletePlaces(personId: Long)

    @Insert
    suspend fun insertPlaces(items: List<Place>)

    @Query("UPDATE places SET lat = :lat, lng = :lng WHERE id = :id")
    suspend fun setPlaceCoords(id: Long, lat: Double?, lng: Double?)

    @Query("SELECT * FROM places WHERE lat IS NULL AND address != ''")
    suspend fun placesWithoutCoords(): List<Place>

    // --- связи ---
    @Query("SELECT * FROM relations")
    fun observeRelations(): Flow<List<Relation>>

    @Query("SELECT * FROM relations")
    suspend fun getRelations(): List<Relation>

    @Insert
    suspend fun insertRelation(r: Relation): Long

    @Query("DELETE FROM relations WHERE id = :id")
    suspend fun deleteRelation(id: Long)

    @Query(
        """SELECT COUNT(*) FROM relations WHERE (personId = :a AND relatedId = :b) OR (personId = :b AND relatedId = :a)"""
    )
    suspend fun relationCount(a: Long, b: Long): Int

    // --- записи ---
    @Transaction
    @Query("SELECT * FROM appointments WHERE start < :to AND start + durationMin * 60000 > :from ORDER BY start")
    fun observeAppointments(from: Long, to: Long): Flow<List<AppointmentFull>>

    @Transaction
    @Query("SELECT * FROM appointments WHERE personId = :personId ORDER BY start DESC")
    fun observePersonAppointments(personId: Long): Flow<List<AppointmentFull>>

    @Transaction
    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointment(id: Long): AppointmentFull?

    @Transaction
    @Query("SELECT * FROM appointments WHERE id = :id")
    fun observeAppointment(id: Long): Flow<AppointmentFull?>

    @Transaction
    @Query("SELECT * FROM appointments WHERE start < :to AND start + durationMin * 60000 > :from")
    suspend fun appointmentsBetween(from: Long, to: Long): List<AppointmentFull>

    @Transaction
    @Query("SELECT * FROM appointments")
    suspend fun allAppointments(): List<AppointmentFull>

    @Insert
    suspend fun insertAppointment(a: Appointment): Long

    @Update
    suspend fun updateAppointment(a: Appointment)

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteAppointment(id: Long)

    @Query("UPDATE appointments SET status = :status WHERE id = :id")
    suspend fun setAppointmentStatus(id: Long, status: String)

    @Query("SELECT * FROM appointment_reminders WHERE appointmentId = :appointmentId AND sentAt IS NULL")
    suspend fun pendingRemindersFor(appointmentId: Long): List<AppointmentReminder>

    @Query("DELETE FROM appointment_reminders WHERE appointmentId = :appointmentId AND sentAt IS NULL")
    suspend fun deletePendingReminders(appointmentId: Long)

    @Insert
    suspend fun insertReminders(items: List<AppointmentReminder>): List<Long>

    @Query("SELECT * FROM appointment_reminders WHERE id = :id")
    suspend fun getReminder(id: Long): AppointmentReminder?

    @Query("UPDATE appointment_reminders SET sentAt = :time WHERE id = :id")
    suspend fun markReminderSent(id: Long, time: Long)

    @Query("SELECT * FROM appointment_reminders WHERE sentAt IS NULL AND fireAt > :since")
    suspend fun upcomingReminders(since: Long): List<AppointmentReminder>

    // --- услуги ---
    @Query("SELECT * FROM services ORDER BY position, name COLLATE NOCASE")
    fun observeServices(): Flow<List<ServiceTemplate>>

    @Query("SELECT * FROM services ORDER BY position, name COLLATE NOCASE")
    suspend fun getServices(): List<ServiceTemplate>

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun getService(id: Long): ServiceTemplate?

    @Insert
    suspend fun insertService(s: ServiceTemplate): Long

    @Update
    suspend fun updateService(s: ServiceTemplate)

    @Query("DELETE FROM services WHERE id = :id")
    suspend fun deleteService(id: Long)

    @Query("UPDATE appointments SET serviceId = NULL WHERE serviceId = :id")
    suspend fun detachService(id: Long)

    @Transaction
    suspend fun savePerson(
        person: Person,
        contacts: List<ContactItem>,
        details: List<DetailField>,
        groupIds: Collection<Long>,
        places: List<Place>? = null,
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
        if (places != null) {
            deletePlaces(id)
            insertPlaces(places.filter { it.address.isNotBlank() || it.hasCoords }.map { it.copy(id = 0, personId = id) })
        }
        return id
    }

    @Query("DELETE FROM persons")
    suspend fun wipePersons()

    @Query("DELETE FROM groups")
    suspend fun wipeGroups()
}
