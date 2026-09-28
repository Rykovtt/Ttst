package com.kartoteka.app.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
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
@Config(application = TestApp::class, sdk = [34], qualifiers = "ru-w400dp-h860dp-xxhdpi")
class ScreensTest {

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

        val today = LocalDate.now()
        fun at(d: LocalDate, h: Int, m: Int) = AppointmentLogic.millis(d.atTime(h, m))
        repo.saveAppointment(Appointment(personId = anna, start = at(today, 14, 30), durationMin = 60, title = "Стрижка и укладка", place = "Салон на Саксаганского", channel = "WHATSAPP"), listOf(24 * 60, 120), listOf(30))
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

        compose.onNodeWithText("Анна Смирнова").performClick()
        settle()
        shot("02_person")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Семья и связи"))
        settle()
        shot("09_person_relations")

        compose.onNodeWithContentDescription("Редактировать").performClick()
        settle()
        shot("03_edit")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Адреса"))
        settle()
        shot("10_edit_phone_places")
        compose.onNodeWithContentDescription("Закрыть").performClick()
        settle()
        compose.onNodeWithContentDescription("Назад").performClick()
        settle()

        compose.onNodeWithContentDescription("Группы").performClick()
        settle()
        shot("04_groups")
        compose.onNodeWithContentDescription("Назад").performClick()
        settle()

        compose.onNodeWithText("Календарь").performClick()
        settle()
        shot("11_calendar")
        compose.onNodeWithText("Анна Смирнова").performClick()
        settle()
        shot("12_appointment")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Напомнить мне"))
        settle()
        shot("13_appointment_reminders")
        compose.onNodeWithContentDescription("Закрыть").performClick()
        settle()

        compose.onNodeWithText("Карта").performClick()
        settle()
        shot("14_map")

        compose.onNodeWithText("Рассылка").performClick()
        settle()
        compose.onNodeWithText("👨‍👩‍👧 Семья · 3").performClick()
        settle()
        shot("05_broadcast")
        compose.onNodeWithText("По критериям").performClick()
        settle()
        compose.onNodeWithText("Женский").performClick()
        settle()
        screenShot("15_criteria")
        androidx.test.espresso.Espresso.pressBack()
        settle()

        compose.onNodeWithText("Настройки").performClick()
        settle()
        shot("06_settings")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Язык сообщений"))
        settle()
        shot("16_settings_language")
    }

    @Test
    @Config(qualifiers = "+night")
    fun homeDark() {
        compose.setContent { KartotekaTheme { KartotekaRoot(openPersonId = null, onPersonOpened = {}) } }
        settle()
        shot("07_home_dark")
        compose.onNodeWithText("Анна Смирнова").performClick()
        settle()
        shot("08_person_dark")
    }

    private fun settle() {
        repeat(4) {
            compose.waitForIdle()
            Thread.sleep(150) // Room и Coil работают в фоновых потоках
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("screenshots/$name.png")

    /** Весь экран, включая диалоги и нижние листы. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun screenShot(name: String) = com.github.takahirom.roborazzi.captureScreenRoboImage("screenshots/$name.png")

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
