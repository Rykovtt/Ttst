package com.kartoteka.app.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import com.kartoteka.app.data.Appointment
import com.kartoteka.app.data.AppointmentLogic
import com.kartoteka.app.data.ContactItem
import com.kartoteka.app.data.Place
import com.kartoteka.app.data.RelationType
import com.kartoteka.app.data.DetailField
import com.kartoteka.app.data.Group
import com.kartoteka.app.data.JournalEntry
import com.kartoteka.app.data.Person
import com.kartoteka.app.ui.KartotekaRoot
import com.kartoteka.app.ui.theme.KartotekaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "uk-w400dp-h860dp-xxhdpi")
class UkScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()

    @Before
    fun seed() = runBlocking {
        val repo = app.repository
        val family = repo.saveGroup(Group(name = "Семья", color = 0xFFD9544D, emoji = "👨‍👩‍👧"))
        val work = repo.saveGroup(Group(name = "Работа", color = 0xFF2F7ED8, emoji = "💼"))
        val sport = repo.saveGroup(Group(name = "Спортзал", color = 0xFF0E8A7E, emoji = "🏋️"))

        val soon = LocalDate.now().plusDays(3)
        val annaPhoto = portrait(0xFFB4436C.toInt(), 0xFFFFC7A8.toInt())
        val anna = repo.savePerson(
            Person(
                firstName = "Анна", lastName = "Смирнова", middleName = "Викторовна", nickname = "Аня",
                birthDay = soon.dayOfMonth, birthMonth = soon.monthValue, birthYear = 1992, gender = "Женский",
                relation = "Близкий друг", closeness = 5, company = "Яндекс", position = "Дизайнер",
                city = "Москва", howMet = "Учились вместе в МГУ, познакомились на первом курсе",
                notes = "Не любит сюрпризы. Всегда опаздывает на 10 минут :)", favorite = true, avatarPath = annaPhoto,
            ),
            listOf(
                ContactItem(type = "PHONE", value = "+380671234567"),
                ContactItem(type = "TELEGRAM", value = "@anna_sm"),
                ContactItem(type = "EMAIL", value = "anna@example.com"),
                ContactItem(type = "INSTAGRAM", value = "anna.draws"),
            ),
            listOf(
                DetailField(category = "Семья", name = "Супруг", value = "Дмитрий, программист"),
                DetailField(category = "Семья", name = "Дети", value = "Соня, 4 года (ДР 12 мая)"),
                DetailField(category = "Семья", name = "Питомцы", value = "Кот Бублик"),
                DetailField(category = "Вкусы", name = "Кофе / чай — как пьёт", value = "Флэт уайт на овсяном"),
                DetailField(category = "Вкусы", name = "Не ест", value = "Грибы, кинза"),
                DetailField(category = "Подарки", name = "Идеи подарков", value = "Скетчбук Moleskine, курс керамики"),
                DetailField(category = "Подарки", name = "Размер одежды", value = "S / 42"),
            ),
            listOf(family),
            listOf(
                Place(kind = "HOME", address = "Киев, ул. Саксаганского, 45", lat = 50.4380, lng = 30.5100),
                Place(kind = "WORK", address = "Киев, ул. Льва Толстого, 57", lat = 50.4395, lng = 30.4980),
            ),
        )
        repo.addPhoto(anna, annaPhoto, makeAvatarIfEmpty = false)
        repo.addPhoto(anna, portrait(0xFF5B4BD6.toInt(), 0xFFFFD8B5.toInt()))
        repo.addPhoto(anna, portrait(0xFF0E8A7E.toInt(), 0xFFF1C27D.toInt()))
        app.settings.autoBackup.set(true)
        app.settings.autoBackupFolder.set("content://com.android.externalstorage.documents/tree/primary%3ARVault")
        app.settings.autoBackupLastAt.set((System.currentTimeMillis() - 3_600_000L * 7).toString())

        val voice = repo.voices.newName()
        repo.voices.save(voice, ByteArray(com.kartoteka.app.data.VoiceStorage.BYTES_PER_SECOND * 2))
        repo.addVoiceNote(com.kartoteka.app.data.VoiceNote(personId = anna, file = voice, durationMs = 83_000,
            createdAt = System.currentTimeMillis() - 86_400_000L * 2,
            text = "Рассказала, что летом переезжает во Львов. Соня пошла в садик, Бублик снова болеет — спросить в следующий раз."))
        repo.addJournal(JournalEntry(personId = anna, kind = "Встреча", text = "Обедали в «Пушкине», рассказала про новую работу", date = System.currentTimeMillis() - 86_400_000L * 12))
        repo.addJournal(JournalEntry(personId = anna, kind = "Подарок", text = "Подарил(а) книгу про типографику", date = System.currentTimeMillis() - 86_400_000L * 90))

        val ivan = repo.savePerson(
            Person(firstName = "Иван", lastName = "Петров", gender = "Мужской", relation = "Коллега", company = "Сбер", city = "Казань", closeness = 3,
                avatarPath = portrait(0xFF2F7ED8.toInt(), 0xFFE0AC69.toInt())),
            listOf(ContactItem(type = "PHONE", value = "+7 900 111-22-33")),
            listOf(DetailField(category = "Интересы", name = "Хобби", value = "Рыбалка, шахматы")),
            listOf(work, sport),
            listOf(Place(kind = "WORK", address = "Киев, Кловский спуск, 7", lat = 50.4430, lng = 30.5430)),
        )
        val mama = repo.savePerson(Person(firstName = "Мама", gender = "Женский", relation = "Семья", closeness = 5, favorite = true), listOf(ContactItem(type = "PHONE", value = "+380501112233")), emptyList(), listOf(family),
            listOf(Place(kind = "HOME", address = "Буча, ул. Вокзальная, 10", lat = 50.5430, lng = 30.2120)))
        val dima = repo.savePerson(Person(firstName = "Дмитрий", lastName = "Смирнов", gender = "Мужской", relation = "Знакомый"), emptyList(), emptyList(), listOf(family))
        repo.addRelation(anna, mama, RelationType.PARENT)
        repo.addRelation(anna, dima, RelationType.SPOUSE)
        repo.addRelation(anna, ivan, RelationType.COLLEAGUE)

        val tattooId = repo.saveService(com.kartoteka.app.data.ServiceTemplate(name = "Тату-сеанс", durationMin = 180, place = "Студия на Подоле",
            clientOffsets = "1440,120", myOffsets = "60",
            tplReminder = "{имя}, завтра тату-сеанс в {время}. Выспитесь и хорошо поешьте!"))
        repo.saveService(com.kartoteka.app.data.ServiceTemplate(name = "Консультация", durationMin = 30, clientOffsets = "120", myOffsets = "30",
            tplConfirm = "{имя}, жду вас на консультацию {дата} в {время}.", tplReminder = "{имя}, напоминаю: консультация {когда} в {время}. Возьмите референсы."))
        val today = LocalDate.now()
        fun at(d: LocalDate, h: Int, m: Int) = AppointmentLogic.millis(d.atTime(h, m))
        repo.saveAppointment(Appointment(personId = anna, start = at(today, 14, 30), durationMin = 60, title = "Стрижка и укладка", place = "Салон на Саксаганского", channel = "WHATSAPP", serviceId = tattooId), listOf(24 * 60, 120), listOf(30))
        repo.saveAppointment(Appointment(personId = ivan, start = at(today, 17, 0), durationMin = 45, title = "Обсудить проект", channel = "TELEGRAM"), listOf(60), listOf(15))
        repo.saveAppointment(Appointment(personId = mama, start = at(today.plusDays(2), 11, 0), durationMin = 90, title = "Врач", channel = "SMS"), listOf(24 * 60), listOf(60))
        repo.saveAppointment(Appointment(personId = dima, start = at(today.plusDays(5), 19, 0), durationMin = 120, title = "Ужин", channel = "NONE"), emptyList(), listOf(120))
        repo.savePerson(Person(firstName = "Алексей", lastName = "Козлов", relation = "Друг", city = "Санкт-Петербург"), emptyList(), emptyList(), listOf(sport))
        repo.savePerson(Person(firstName = "Елена", lastName = "Орлова", relation = "Клиент", company = "ООО «Вектор»", position = "Директор"), emptyList(), emptyList(), listOf(work))
        repo.savePerson(Person(firstName = "Борис", lastName = "Никитин", relation = "Сосед", birthDay = LocalDate.now().dayOfMonth, birthMonth = LocalDate.now().monthValue, birthYear = 1970), emptyList(), emptyList(), emptyList())
        repo.savePerson(Person(firstName = "Ольга", lastName = "Васильева", relation = "Однокурсник", city = "Москва",
            avatarPath = portrait(0xFFE08E2B.toInt(), 0xFFFFDBAC.toInt())), emptyList(), emptyList(), emptyList())
        Unit
    }

    @Test
    fun walkThroughScreens() {
        compose.setContent { KartotekaTheme { KartotekaRoot(openPersonId = null, onPersonOpened = {}) } }
        settle()
        shot("01_home")
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Ассистент"), substring = true).performClick()
        settle()
        shot("27_noa")
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Назад")).performClick()
        settle()

        openCard("Анна Смирнова")
        settle()
        shot("02_person")
        compose.onNode(hasScrollToIndexAction() and vertical).performScrollToNode(hasText(com.kartoteka.app.i18n.t("Семья и связи")))
        settle()
        shot("09_person_relations")
        compose.onNode(hasScrollToIndexAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(com.kartoteka.app.i18n.t("Голосовые заметки")))
        settle()
        shot("19_person_voice")
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Добавить фото")).performClick()
        settle()
        screenShot("23_photo_menu")
        androidx.test.espresso.Espresso.pressBack()
        settle()

        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Редактировать")).performClick()
        settle()
        shot("03_edit")
        compose.onNode(hasScrollAction() and vertical).performScrollToNode(hasText(com.kartoteka.app.i18n.t("Адреса")))
        settle()
        shot("10_edit_phone_places")
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Закрыть")).performClick()
        settle()
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Назад")).performClick()
        settle()

        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Меню")).performClick()
        settle()
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Группы")).performClick()
        settle()
        shot("04_groups")
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Назад")).performClick()
        settle()

        compose.onNodeWithText(com.kartoteka.app.i18n.t("Календарь")).performClick()
        settle()
        shot("11_calendar")
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Стрижка и укладка")).performClick()
        settle()
        shot("12_appointment")
        compose.onNode(hasScrollAction() and vertical).performScrollToNode(hasText(com.kartoteka.app.i18n.t("Напомнить мне")))
        settle()
        shot("13_appointment_reminders")
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Закрыть")).performClick()
        settle()

        compose.onNodeWithText(com.kartoteka.app.i18n.t("Настройки")).performClick()
        settle()
        compose.onNode(hasScrollAction() and vertical).performScrollToNode(hasText(com.kartoteka.app.i18n.t("Записи и календарь")))
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Записи и календарь")).performClick()
        settle()
        compose.onNode(hasScrollAction() and vertical).performScrollToNode(hasText(com.kartoteka.app.i18n.t("Услуги и их шаблоны")))
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Услуги и их шаблоны")).performClick()
        settle()
        shot("17_services")
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Консультация")).performClick()
        settle()
        screenShot("18_service_editor")
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Закрыть")).performClick()
        settle()
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Назад")).performClick()
        settle()

        // Карта больше не вкладка — открывается с главной.
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Люди")).performClick()
        settle()
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Карта")).performClick()
        settle()
        shot("14_map")
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Назад")).performClick()
        settle()
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Меню")).performClick()
        settle()
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Статистика")).performClick()
        settle()
        shot("28_stats")
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Назад")).performClick()
        settle()
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Быстрые действия")).performClick()
        settle()
        shot("29_quick_menu")
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Быстрые действия")).performClick()
        settle()

        compose.onNodeWithText(com.kartoteka.app.i18n.t("Рассылка")).performClick()
        settle()
        compose.onNodeWithText(com.kartoteka.app.i18n.t("👨‍👩‍👧 Семья · 3")).performClick()
        settle()
        shot("05_broadcast")
        compose.onNodeWithText(com.kartoteka.app.i18n.t("По критериям")).performClick()
        settle()
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Женский")).performClick()
        settle()
        screenShot("15_criteria")
        androidx.test.espresso.Espresso.pressBack()
        settle()

        compose.onNodeWithText(com.kartoteka.app.i18n.t("Настройки")).performClick()
        settle()
        shot("06_settings")
        compose.onNode(hasScrollAction() and vertical).performScrollToNode(hasText(com.kartoteka.app.i18n.t("Язык приложения")))
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Язык приложения")).performClick()
        settle()
        shot("16_settings_language")
        compose.onNode(hasScrollAction() and vertical).performScrollToNode(hasText(com.kartoteka.app.i18n.t("Данные")))
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Данные")).performClick()
        settle()
        compose.onNode(hasScrollAction() and vertical).performScrollToNode(hasText(com.kartoteka.app.i18n.t("Пароль копий")))
        settle()
        shot("26_settings_autobackup")
    }

    @Test
    fun lockScreenAndIntruderLog() {
        app.pinLock.set("4821")
        app.settings.setLockEnabled(true)
        val now = System.currentTimeMillis()
        app.intruders.record(now - 3_600_000L * 5, File(portrait(0xFF3A3A3A.toInt(), 0xFFE0AC69.toInt())).readBytes())
        app.intruders.record(now - 3_600_000L * 5 + 9_000, null)
        app.intruders.record(now - 60_000, File(portrait(0xFF6B5B4B.toInt(), 0xFFFFC7A8.toInt())).readBytes())

        compose.setContent { KartotekaTheme { KartotekaRoot(openPersonId = null, onPersonOpened = {}) } }
        settle()
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Настройки")).performClick()
        settle()
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Приватность")).performClick()
        settle()
        compose.onNode(hasScrollAction() and vertical).performScrollToNode(hasText(com.kartoteka.app.i18n.t("Встряхнуть — закрыть")))
        settle()
        shot("20_settings_privacy")
        compose.onNodeWithText(com.kartoteka.app.i18n.t("Попытки входа")).performClick()
        settle()
        screenShot("21_intruder_log")
        compose.onAllNodesWithText(com.kartoteka.app.i18n.t("Неверный PIN-код"))[0].performClick()
        settle()
        compose.onNodeWithContentDescription(com.kartoteka.app.i18n.t("Закрыть")).assertExists()
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).onLast().captureRoboImage("screenshots/uk/22_intruder_photo.png")
        app.pinLock.clear()
        app.intruders.clear()
    }

    @Test
    @Config(qualifiers = "+night")
    fun homeDark() {
        compose.setContent { KartotekaTheme { KartotekaRoot(openPersonId = null, onPersonOpened = {}) } }
        settle()
        shot("07_home_dark")
        openCard("Анна Смирнова")
        settle()
        shot("08_person_dark")
    }

    /** Тап по левой части карточки (аватар): центр карточки может попасть в звезду «Избранное». */
    private fun openCard(name: String) =
        compose.onNodeWithText(name).performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.1f, centerY)) }

    /** Вертикальный контейнер прокрутки (горизонтальные ряды чипов в шапках не подходят). */
    private val vertical = androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)

    private fun settle() {
        repeat(4) {
            compose.waitForIdle()
            Thread.sleep(150) // Room и Coil работают в фоновых потоках
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("screenshots/uk/$name.png")

    /** Весь экран, включая диалоги и нижние листы. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun screenShot(name: String) = com.github.takahirom.roborazzi.captureScreenRoboImage("screenshots/uk/$name.png")

    /** Простой «портрет»-заглушка: градиентный фон и силуэт. */
    private fun portrait(bg: Int, skin: Int): String {
        val size = 600
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), bg, 0xFF1B1B21.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null
        p.color = 0xFF2B2B33.toInt()
        c.drawOval(150f, 390f, 450f, 700f, p)
        p.color = skin
        c.drawCircle(300f, 250f, 110f, p)
        val file = File(app.repository.photos.dir, "test_${System.nanoTime()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }
}
