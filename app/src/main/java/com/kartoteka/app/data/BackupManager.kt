package com.kartoteka.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Резервная копия: ZIP (data.json + фото), зашифрованный паролем (PBKDF2 + AES-256).
 * Файл можно хранить где угодно — без пароля его не прочитать.
 */
class BackupManager(private val context: Context, private val repo: Repository) {

    class WrongPasswordException : Exception("Неверный пароль или повреждённый файл")

    suspend fun export(uri: Uri, password: String): Int {
        val all = repo.getAll()
        val groups = repo.getGroups()
        val relations = repo.rawDao.getRelations()
        val appointments = repo.rawDao.allAppointments()
        val out = context.contentResolver.openOutputStream(uri) ?: error("Не удалось открыть файл")
        out.use { raw ->
            val stream = wrapOutput(raw, password)
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(toJson(all, groups, relations, appointments).toString().toByteArray())
                zip.closeEntry()
                val files = all.flatMap { pf -> pf.photos.map { it.path } + listOfNotNull(pf.person.avatarPath) }.toSet()
                files.map(::File).filter { it.exists() }.forEach { f ->
                    zip.putNextEntry(ZipEntry("photos/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return all.size
    }

    /** @param replace true — стереть текущую картотеку перед восстановлением. */
    suspend fun import(uri: Uri, password: String, replace: Boolean): Int {
        val input = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
        val photoDir = repo.photos.dir
        val tmpDir = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var json: String? = null
        try {
            input.use { raw ->
                ZipInputStream(wrapInput(raw, password)).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        when {
                            e.name == "data.json" -> json = zip.readBytes().decodeToString()
                            e.name.startsWith("photos/") && !e.isDirectory -> {
                                val name = File(e.name).name
                                File(tmpDir, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            tmpDir.deleteRecursively()
            throw WrongPasswordException()
        }
        val data = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: run {
            tmpDir.deleteRecursively(); throw WrongPasswordException()
        }

        fun restoredPath(old: String?): String? {
            if (old.isNullOrBlank()) return null
            val src = File(tmpDir, File(old).name)
            if (!src.exists()) return null
            val dst = File(photoDir, src.name)
            if (!dst.exists()) src.copyTo(dst)
            return dst.absolutePath
        }

        val dao = repo.rawDao
        var count = 0
        val restoredReminders = mutableListOf<AppointmentReminder>()
        repo.database.withTransaction {
            if (replace) {
                dao.getAll().forEach { pf ->
                    repo.photos.delete(pf.person.avatarPath); pf.photos.forEach { repo.photos.delete(it.path) }
                }
                dao.wipePersons()
                dao.wipeGroups()
            }
            val existingGroups = dao.getGroups().associateBy { it.name }
            val groupMap = HashMap<Long, Long>()
            val personMap = HashMap<Long, Long>()
            data.optJSONArray("groups")?.objects()?.forEach { g ->
                val name = g.getString("name")
                val newId = existingGroups[name]?.id ?: dao.insertGroup(
                    Group(name = name, color = g.optLong("color", 0xFF5B4BD6), emoji = g.optString("emoji"))
                )
                groupMap[g.getLong("id")] = newId
            }
            data.optJSONArray("persons")?.objects()?.forEach { o ->
                val p = o.getJSONObject("person")
                val person = Person(
                    lastName = p.optString("lastName"), firstName = p.optString("firstName"),
                    middleName = p.optString("middleName"), nickname = p.optString("nickname"),
                    birthDay = p.optIntOrNull("birthDay"), birthMonth = p.optIntOrNull("birthMonth"),
                    birthYear = p.optIntOrNull("birthYear"), gender = p.optString("gender"),
                    relation = p.optString("relation"), closeness = p.optInt("closeness"),
                    company = p.optString("company"), position = p.optString("position"),
                    city = p.optString("city"), address = p.optString("address"),
                    howMet = p.optString("howMet"), notes = p.optString("notes"),
                    favorite = p.optBoolean("favorite"), avatarPath = restoredPath(p.optStringOrNull("avatarPath")),
                    createdAt = p.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = p.optLong("updatedAt", System.currentTimeMillis()),
                    lastContactAt = p.optLongOrNull("lastContactAt"),
                )
                val contacts = o.optJSONArray("contacts")?.objects()?.map {
                    ContactItem(type = it.optString("type"), label = it.optString("label"), value = it.optString("value"))
                }.orEmpty()
                val details = o.optJSONArray("details")?.objects()?.map {
                    DetailField(category = it.optString("category"), name = it.optString("name"), value = it.optString("value"))
                }.orEmpty()
                val groupIds = o.optJSONArray("groups")?.let { arr -> (0 until arr.length()).mapNotNull { groupMap[arr.getLong(it)] } }.orEmpty()
                // v1: адрес хранился в карточке — переносим в «Дом».
                val places = o.optJSONArray("places")?.objects()?.map {
                    Place(kind = it.optString("kind"), label = it.optString("label"), address = it.optString("address"),
                        lat = it.optDoubleOrNull("lat"), lng = it.optDoubleOrNull("lng"))
                } ?: listOfNotNull(person.address.takeIf { it.isNotBlank() }?.let { Place(kind = PlaceKind.HOME.name, address = it) })
                val id = dao.savePerson(person, contacts, details, groupIds, places)
                if (o.has("id")) personMap[o.getLong("id")] = id
                o.optJSONArray("photos")?.objects()?.forEach { ph ->
                    restoredPath(ph.optString("path"))?.let { path ->
                        dao.insertPhoto(Photo(personId = id, path = path, caption = ph.optString("caption"), addedAt = ph.optLong("addedAt")))
                    }
                }
                o.optJSONArray("journal")?.objects()?.forEach { j ->
                    dao.insertJournal(JournalEntry(personId = id, date = j.optLong("date"), kind = j.optString("kind"), text = j.optString("text")))
                }
                count++
            }
            data.optJSONArray("relations")?.objects()?.forEach { r ->
                val a = personMap[r.getLong("personId")] ?: return@forEach
                val b = personMap[r.getLong("relatedId")] ?: return@forEach
                if (dao.relationCount(a, b) == 0) dao.insertRelation(Relation(personId = a, relatedId = b, type = r.optString("type")))
            }
            data.optJSONArray("appointments")?.objects()?.forEach { j ->
                val pid = personMap[j.getLong("personId")] ?: return@forEach
                val a = Appointment(
                    personId = pid, start = j.getLong("start"), durationMin = j.optInt("durationMin", 60),
                    title = j.optString("title"), place = j.optString("place"), notes = j.optString("notes"),
                    channel = j.optString("channel", NotifyChannel.NONE.name), status = j.optString("status", AppointmentStatus.PLANNED.name),
                    createdAt = j.optLong("createdAt", System.currentTimeMillis()),
                )
                val aid = dao.insertAppointment(a)
                val reminders = if (a.appointmentStatus == AppointmentStatus.PLANNED) AppointmentLogic.buildReminders(
                    a.copy(id = aid),
                    AppointmentLogic.offsetsFromString(j.optString("clientOffsets")),
                    AppointmentLogic.offsetsFromString(j.optString("myOffsets")),
                ) else emptyList()
                restoredReminders += reminders.zip(dao.insertReminders(reminders)) { r, rid -> r.copy(id = rid) }
            }
        }
        com.kartoteka.app.reminders.ReminderScheduler.schedule(context, restoredReminders)
        tmpDir.deleteRecursively()
        return count
    }

    private fun toJson(all: List<PersonFull>, groups: List<Group>, relations: List<Relation>, appointments: List<AppointmentFull>): JSONObject = JSONObject().apply {
        put("format", "kartoteka")
        put("version", 2)
        put("relations", JSONArray(relations.map { JSONObject().put("personId", it.personId).put("relatedId", it.relatedId).put("type", it.type) }))
        put("appointments", JSONArray(appointments.map { af ->
            val a = af.appointment
            JSONObject().put("personId", a.personId).put("start", a.start).put("durationMin", a.durationMin)
                .put("title", a.title).put("place", a.place).put("notes", a.notes).put("channel", a.channel)
                .put("status", a.status).put("createdAt", a.createdAt)
                .put("clientOffsets", AppointmentLogic.offsetsToString(af.reminders.filter { it.target == ReminderTarget.CLIENT.name }.map { it.offsetMin }))
                .put("myOffsets", AppointmentLogic.offsetsToString(af.reminders.filter { it.target == ReminderTarget.ME.name }.map { it.offsetMin }))
        }))
        put("exportedAt", System.currentTimeMillis())
        put("groups", JSONArray(groups.map { g ->
            JSONObject().put("id", g.id).put("name", g.name).put("color", g.color).put("emoji", g.emoji)
        }))
        put("persons", JSONArray(all.map { pf ->
            val p = pf.person
            JSONObject()
                .put("id", p.id)
                .put("places", JSONArray(pf.places.map {
                    JSONObject().put("kind", it.kind).put("label", it.label).put("address", it.address).put("lat", it.lat).put("lng", it.lng)
                }))
                .put("person", JSONObject().apply {
                    put("lastName", p.lastName); put("firstName", p.firstName); put("middleName", p.middleName)
                    put("nickname", p.nickname); put("birthDay", p.birthDay); put("birthMonth", p.birthMonth)
                    put("birthYear", p.birthYear); put("gender", p.gender); put("relation", p.relation)
                    put("closeness", p.closeness); put("company", p.company); put("position", p.position)
                    put("city", p.city); put("address", p.address); put("howMet", p.howMet); put("notes", p.notes)
                    put("favorite", p.favorite); put("avatarPath", p.avatarPath); put("createdAt", p.createdAt)
                    put("updatedAt", p.updatedAt); put("lastContactAt", p.lastContactAt)
                })
                .put("contacts", JSONArray(pf.contacts.map {
                    JSONObject().put("type", it.type).put("label", it.label).put("value", it.value)
                }))
                .put("details", JSONArray(pf.details.sortedBy { it.position }.map {
                    JSONObject().put("category", it.category).put("name", it.name).put("value", it.value)
                }))
                .put("photos", JSONArray(pf.photos.map {
                    JSONObject().put("path", it.path).put("caption", it.caption).put("addedAt", it.addedAt)
                }))
                .put("groups", JSONArray(pf.groups.map { it.id }))
                .put("journal", JSONArray(pf.journal.map {
                    JSONObject().put("date", it.date).put("kind", it.kind).put("text", it.text)
                }))
        }))
    }

    private fun wrapOutput(raw: OutputStream, password: String): OutputStream {
        if (password.isEmpty()) {
            raw.write(MAGIC_PLAIN); return raw
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        raw.write(MAGIC_ENC); raw.write(salt); raw.write(iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), IvParameterSpec(iv))
        return CipherOutputStream(raw, cipher)
    }

    private fun wrapInput(raw: InputStream, password: String): InputStream {
        val din = DataInputStream(raw)
        val magic = ByteArray(MAGIC_ENC.size).also { din.readFully(it) }
        return when {
            magic.contentEquals(MAGIC_PLAIN) -> din
            magic.contentEquals(MAGIC_ENC) -> {
                val salt = ByteArray(16).also { din.readFully(it) }
                val iv = ByteArray(16).also { din.readFully(it) }
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), IvParameterSpec(iv))
                CipherInputStream(din, cipher)
            }
            else -> throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private companion object {
        val MAGIC_ENC = "KRTK1".toByteArray()
        val MAGIC_PLAIN = "KRTK0".toByteArray()
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else optString(key)
