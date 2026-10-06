package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaBenchmark
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaIntentJson
import com.kartoteka.app.assistant.NoaInterpreter
import com.kartoteka.app.assistant.NoaParser
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.util.Random

/**
 * Разовый генератор библиотеки примеров assets/examples.tsv (фраза TAB компактный JSON без reply).
 * Источники: фразы из существующих тестов и шаблоны с подстановкой имён/дат/приложений. Каждую фразу разбирают правила,
 * результат пишется в JSON (имя — в начальной форме), и пример остаётся, только если JSON через интерпретатор даёт ту же команду.
 * Запуск: GEN_EXAMPLES=1 ./gradlew :app:testDebugUnitTest --tests '*NoaExamplesGenerator*'. Без переменной тест пропускается.
 */
class NoaExamplesGenerator {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val ip = NoaInterpreter(null)

    /** Имя: начальная форма и падежи (вин., дат., род.). */
    private class Nm(val nom: String, val acc: String, val dat: String, val gen: String) {
        fun forms() = listOf(nom, acc, dat, gen).map { it.lowercase() }
    }

    private val ruNames = listOf(
        Nm("Анна", "Анну", "Анне", "Анны"), Nm("Аня", "Аню", "Ане", "Ани"), Nm("Мария", "Марию", "Марии", "Марии"), Nm("Маша", "Машу", "Маше", "Маши"),
        Nm("Света", "Свету", "Свете", "Светы"), Nm("Оля", "Олю", "Оле", "Оли"), Nm("Ольга", "Ольгу", "Ольге", "Ольги"), Nm("Катя", "Катю", "Кате", "Кати"),
        Nm("Лена", "Лену", "Лене", "Лены"), Nm("Наташа", "Наташу", "Наташе", "Наташи"), Nm("Юля", "Юлю", "Юле", "Юли"), Nm("Даша", "Дашу", "Даше", "Даши"),
        Nm("Таня", "Таню", "Тане", "Тани"), Nm("Олег", "Олега", "Олегу", "Олега"), Nm("Илья", "Илью", "Илье", "Ильи"), Nm("Дима", "Диму", "Диме", "Димы"),
        Nm("Саша", "Сашу", "Саше", "Саши"), Nm("Петя", "Петю", "Пете", "Пети"), Nm("Максим", "Максима", "Максиму", "Максима"), Nm("Игорь", "Игоря", "Игорю", "Игоря"),
        Nm("Андрей", "Андрея", "Андрею", "Андрея"), Nm("Сергей", "Сергея", "Сергею", "Сергея"), Nm("Володя", "Володю", "Володе", "Володи"), Nm("Коля", "Колю", "Коле", "Коли"),
        Nm("Мама", "Маму", "Маме", "Мамы"), Nm("Папа", "Папу", "Папе", "Папы"), Nm("Бабушка", "Бабушку", "Бабушке", "Бабушки"),
    )
    private val ukNames = listOf(
        Nm("Олена", "Олену", "Олені", "Олени"), Nm("Оля", "Олю", "Олі", "Олі"), Nm("Марія", "Марію", "Марії", "Марії"), Nm("Ірина", "Ірину", "Ірині", "Ірини"),
        Nm("Катя", "Катю", "Каті", "Каті"), Nm("Соломія", "Соломію", "Соломії", "Соломії"), Nm("Ганна", "Ганну", "Ганні", "Ганни"), Nm("Тарас", "Тараса", "Тарасу", "Тараса"),
        Nm("Богдан", "Богдана", "Богдану", "Богдана"), Nm("Іван", "Івана", "Івану", "Івана"), Nm("Андрій", "Андрія", "Андрію", "Андрія"), Nm("Олег", "Олега", "Олегу", "Олега"),
        Nm("Назар", "Назара", "Назару", "Назара"), Nm("Мама", "Маму", "Мамі", "Мами"), Nm("Тато", "Тата", "Тату", "Тата"), Nm("Ліза", "Лізу", "Лізі", "Лізи"),
    )
    private val enNames = listOf("Anna", "John", "Mike", "Kate", "Sam", "Alex", "Mom", "Dad").map { Nm(it, it, it, it) }
    private val allNames = ruNames + ukNames + enNames
    private val lemmaOf: Map<String, String> = HashMap<String, String>().also { m -> for (n in allNames) for (f in n.forms()) m.putIfAbsent(f, n.nom) }

    private val ruDay = listOf("завтра", "послезавтра", "в пятницу", "на понедельник", "на субботу", "на 12 октября", "на воскресенье", "на среду", "на 20 число")
    private val ukDay = listOf("завтра", "післязавтра", "у п'ятницю", "на понеділок", "на суботу", "на 12 жовтня", "на середу")
    private val enDay = listOf("tomorrow", "on friday", "on monday", "next tuesday")
    private val ruTime = listOf("в 15:00", "в 10", "на 18:30", "в 9 утра", "в 7 вечера", "в 12:30", "в 14", "в 11:15")
    private val ukTime = listOf("о 15:00", "о 10", "на 18:30", "о 9 ранку", "о 7 вечора", "о 12:30")
    private val enTime = listOf("at 3pm", "at 10", "at 6:30pm", "at 9am")
    private val ruSvc = listOf("маникюр", "стрижку", "тату", "массаж", "консультацию", "педикюр")
    private val ukSvc = listOf("манікюр", "стрижку", "тату", "масаж", "консультацію")
    private val apps = listOf("телеграм", "ватсап", "вайбер", "ютуб", "spotify", "waze", "камеру", "галерею", "калькулятор", "gmail", "инстаграм", "часы", "браузер")
    private val closeApps = listOf("waze", "вейз", "ютуб", "spotify", "телеграм", "ватсап", "гугл карты", "браузер", "инстаграм", "youtube", "viber")
    private val songs = listOf("Скриптонит", "Океан Ельзи", "Imagine Dragons", "Queen", "Bohemian Rhapsody", "Кино", "Монатик", "Время и Стекло", "Coldplay", "Мумий Тролль", "Джамала")
    private val places = listOf("Крещатик 22", "Киевская 5", "аэропорт", "ближайшая заправка", "вокзал", "ТЦ Ocean Plaza", "Победы 14", "Хрещатик 10", "центр")
    private val msgs = listOf("опаздываю", "буду через 10 минут", "уже еду", "перезвоню позже", "купи хлеб", "жду тебя у входа", "спасибо большое", "завтра не смогу")
    private val ukMsgs = listOf("запізнююсь", "буду за десять хвилин", "вже їду", "передзвоню пізніше", "купи хліб", "дякую")
    private val notes = listOf("любит кофе", "вернул долг", "аллергия на цитрусы", "просила скидку", "предпочитает вечер", "новый номер телефона")
    private val ukNotes = listOf("любить каву", "повернув борг", "алергія на цитрусові", "просила знижку")
    private val reminds = listOf("позвонить в банк", "купить цветы", "забрать посылку", "оплатить интернет", "принять таблетки", "забрать детей из школы")
    private val ukReminds = listOf("зателефонувати лікарю", "купити квіти", "забрати посилку", "оплатити інтернет")
    private val qs = listOf("рецепт борща", "погода в Киеве", "курс доллара", "новости спорта", "расписание поездов", "ближайшая аптека", "кто такой Илон Маск", "как приготовить пасту")
    private val tg = listOf("в телеграм", "в вотсап", "смской", "в вайбер")
    private val kinds = listOf("домой", "на работу")

    /** Шаблон: [lang, текст]. Подстановки: <acc> <dat> <gen> <nom> — имя; <day> <time> <svc> <app> <capp> <song> <place> <msg> <note> <rem> <q> <ch> <min>. */
    private val templates: List<Pair<String, String>> = buildList {
        fun ru(vararg s: String) = s.forEach { add("ru" to it) }
        fun uk(vararg s: String) = s.forEach { add("uk" to it) }
        fun en(vararg s: String) = s.forEach { add("en" to it) }
        // записи
        ru("запиши <acc> на <day> <time>", "запиши <acc> на <svc> <day>", "запиши <acc> на <svc> <day> <time>", "запиши <acc> <day>", "запиши мне <acc> на <day> <time>",
            "добавь запись <gen> <day> <time>", "запиши клиента <acc> <day> <time>", "оформи запись <gen> на <svc> <day> <time>", "запиши <acc> на приём <day>", "запишите <acc> <day> <time>")
        uk("запиши <acc> на <day> <time>", "запиши <acc> на <svc> <day>", "запишіть <acc> <day> <time>", "додай запис <gen> на <svc> <day> <time>", "запиши <acc> <day>")
        en("book <nom> for <svc> <day> <time>", "book <nom> <day> <time>", "schedule <nom> <day>")
        ru("отмени запись <gen> <day>", "отмени запись <gen>", "отмени ближайшую запись <gen>", "отмени встречу с <ins> <day>", "отмени запись на <day> для <gen>", "сними запись <gen> <day>")
        uk("скасуй запис <gen> <day>", "скасуй запис <gen>", "скасуй найближчий запис <gen>")
        ru("удали запись <gen> <day>", "удали запись <gen>", "удали из календаря запись <gen>")
        uk("видали запис <gen>", "видали запис <gen> <day>")
        ru("перенеси <acc> <day>", "перенеси <acc> с пятницы на субботу", "перенеси запись <gen> <day> <time>", "перенеси <acc> на <day> <time>", "перенеси <acc> на другое время", "передвинь запись <gen> на <day>", "перенеси встречу <gen> <day>")
        uk("перенеси <acc> <day>", "перенеси запис <gen> на <day> <time>", "пересунь запис <gen> на <day>", "перенеси <acc> на інший час")
        // звонки и сообщения
        ru("позвони <dat>", "позвони <dat> пожалуйста", "набери <acc>", "набери номер <gen>", "соедини меня с <ins>", "позвони <dat> на мобильный", "срочно позвони <dat>", "нужно позвонить <dat>", "вызови <acc>")
        uk("зателефонуй <dat>", "подзвони <dat>", "набери <acc>", "подзвони <dat> будь ласка", "потрібно подзвонити <dat>")
        en("call <nom>", "call <nom> please", "phone <nom>", "dial <nom>")
        ru("напиши <dat> что <msg>", "напиши <dat> <ch> что <msg>", "отправь <dat> сообщение что <msg>", "скажи <dat> что <msg>", "напиши <dat>", "отправь <dat> <ch> <msg>", "сообщи <dat> что <msg>", "напиши <dat> <ch>", "передай <dat> что <msg>")
        uk("напиши <dat> що <ukmsg>", "надішли <dat> повідомлення що <ukmsg>", "напиши <dat> у телеграм що <ukmsg>", "скажи <dat> що <ukmsg>", "відправ <dat> у вайбер що <ukmsg>", "напиши <dat>")
        en("text <nom> that <msg_en>", "send <nom> a message that <msg_en>", "message <nom> on telegram that <msg_en>", "tell <nom> <msg_en>")
        ru("ответь <dat> <msg>", "ответь <dat> что <msg>", "ответь <dat> в телеграм <msg>")
        uk("відповідай <dat> <ukmsg>", "відповідай <dat> що <ukmsg>")
        ru("прочитай сообщения", "прочитай сообщения от <gen>", "что пишет <nom>", "что написала <nom>", "прочитай новые сообщения", "есть ли сообщения от <gen>", "прочитай что прислал <nom>", "что мне написали")
        uk("прочитай повідомлення", "прочитай повідомлення від <gen>", "що пише <nom>", "що написала <nom>")
        en("read my messages", "read messages from <nom>", "what did <nom> write")
        // карточки, заметки, поиск
        ru("добавь заметку <dat>: <note>", "добавь <dat> заметку <note>", "запиши в заметки <gen> <note>", "сделай заметку про <acc>: <note>", "запиши в хронику <gen> что <note>", "добавь в журнал <gen> <note>")
        uk("додай нотатку <dat>: <uknote>", "додай <dat> нотатку <uknote>", "запиши в нотатки <gen> <uknote>")
        ru("открой <acc>", "открой карточку <gen>", "покажи карточку <gen>", "открой профиль <gen>", "открой контакт <acc>", "покажи <acc>", "зайди в карточку <gen>", "открой <acc> в картотеке")
        uk("відкрий <acc>", "відкрий картку <gen>", "покажи картку <gen>", "відкрий профіль <gen>")
        ru("найди <acc>", "найди клиента <acc>", "найди в картотеке <acc>", "поищи <acc>", "найди всех клиентов на букву А", "найди контакт <acc>", "найди клиентов с аллергией")
        uk("знайди <acc>", "знайди клієнта <acc>", "пошукай <acc>")
        ru("открой инстаграм <gen>", "открой телеграм <gen>", "открой фейсбук <gen>", "открой вайбер <gen>", "открой почту <gen>", "открой сайт <gen>", "открой страницу <gen> в инстаграм")
        uk("відкрий інстаграм <gen>", "відкрий телеграм <gen>", "відкрий пошту <gen>")
        // маршруты
        ru("построй маршрут к <dat>", "построй маршрут <place>", "проложи маршрут <kind>", "поехали <kind>", "проложи маршрут к <dat> через вейз", "навигация до <gen>", "веди меня к <dat>", "едем к <dat>",
            "маршрут до <place>", "как доехать до <place>", "проложи маршрут до <place> в гугл картах", "поехали <kind> через вейз", "навигатор <kind>", "построй маршрут до <place> в яндекс картах", "проложи путь к <dat>")
        uk("проклади маршрут до <gen>", "поїхали <kind_uk>", "проклади маршрут <kind_uk>", "маршрут до <place>", "їдемо до <gen>", "проклади маршрут через вейз <kind_uk>")
        en("navigate to <place>", "directions to <place>", "take me home")
        // расписание и информация
        ru("что у меня <day>", "какие записи <day>", "что у меня сегодня", "кто у меня записан <day>", "покажи расписание <day>", "что в планах <day>", "какой у меня график <day>", "что у меня на неделе", "что запланировано <day>", "сколько клиентов <day>")
        uk("що в мене <day>", "що в мене сьогодні", "які записи <day>", "покажи розклад <day>", "хто в мене записаний <day>")
        en("what do i have <day>", "what's on my schedule <day>", "show my schedule <day>")
        ru("когда день рождения у <gen>", "какой телефон у <gen>", "где живёт <nom>", "какой адрес у <gen>", "что ты знаешь про <acc>", "расскажи про <acc>", "расскажи про клиента <acc>", "какой номер у <gen>",
            "когда родился <nom>", "у кого скоро день рождения", "сколько лет <dat>", "дай номер <gen>", "какая у <gen> почта", "когда <nom> была в последний раз")
        uk("коли день народження у <gen>", "який телефон у <gen>", "де живе <nom>", "яка адреса у <gen>", "розкажи про <acc>", "що ти знаєш про <acc>", "який номер у <gen>", "коли народився <nom>")
        en("what is <nom>'s phone number", "when is <nom>'s birthday", "tell me about <nom>")
        ru("добавь <acc> в избранное", "убери <acc> из избранного", "поставь звёздочку <dat>", "отметь <acc> избранным")
        uk("додай <acc> в обране", "прибери <acc> з обраного")
        ru("выбери <acc>", "возьми контакт <acc>", "выбери клиента <acc>", "возьми <acc>")
        uk("обери <acc>", "візьми контакт <acc>", "вибери клієнта <acc>")
        ru("скопируй номер <gen>", "скопируй адрес <gen> в буфер", "отправь номер <gen> в телеграм", "перешли контакт <gen>", "поделись карточкой <gen>", "скопируй заметки <gen> в блокнот", "найди адрес <gen> в гугле", "отправь адрес <gen> в вотсап")
        uk("скопіюй номер <gen>", "надішли номер <gen> у телеграм", "скопіюй адресу <gen>")
        // телефон
        ru("запусти <app>", "открой приложение <app>", "включи <app>", "открой <app>", "запусти приложение <app>", "зайди в <app>", "открой мне <app>", "запусти <app> пожалуйста")
        uk("запусти <app>", "відкрий застосунок <app>", "відкрий <app>", "увімкни <app>")
        en("open <capp>", "launch <capp>")
        ru("закрой <capp>", "закрой приложение <capp>", "выключи <capp>", "останови <capp>", "закрой <capp> пожалуйста", "заверши <capp>")
        uk("закрий <capp>", "закрий застосунок <capp>", "вимкни <capp>")
        en("close <capp>", "quit <capp>")
        ru("загугли <q>", "поищи в интернете <q>", "найди в интернете <q>", "поищи в гугле <q>", "погугли <q>", "найди в гугле <q>")
        uk("загугли <q>", "пошукай в інтернеті <q>", "знайди в інтернеті <q>")
        en("google <q>")
        ru("поставь будильник <time>", "разбуди меня <time>", "будильник <time>", "поставь будильник на 7 утра", "заведи будильник на 6:30", "разбуди меня завтра в 8", "поставь будильник на 5:45")
        uk("постав будильник <time>", "розбуди мене <time>", "будильник на 7 ранку", "постав будильник на 6:30")
        en("set an alarm for 7am", "wake me up at 6:30", "alarm at 8")
        ru("поставь таймер на <min> минут", "таймер на <min> минут", "засеки <min> минут", "таймер на час", "поставь таймер на полчаса", "таймер на 1 час 30 минут", "поставь таймер на 45 секунд", "таймер на две минуты")
        uk("постав таймер на <min> хвилин", "таймер на <min> хвилин", "таймер на годину", "постав таймер на пів години")
        en("set a timer for <min> minutes", "timer for 10 minutes")
        ru("напомни <rem>", "напомни мне <rem>", "напомни завтра в 10 <rem>", "напомни через час <rem>", "поставь напоминание <rem> <day> <time>", "напомни мне сегодня вечером <rem>", "напомни <time> <rem>", "создай напоминание <rem> <day>")
        uk("нагадай <ukrem>", "нагадай мені <ukrem>", "нагадай завтра о 10 <ukrem>", "нагадай через годину <ukrem>", "постав нагадування <ukrem> <day>")
        en("remind me to <rem_en>", "remind me tomorrow at 10 to <rem_en>", "remind me in an hour to <rem_en>")
        ru("включи фонарик", "выключи фонарик", "включи фонарь", "зажги фонарик", "потуши фонарик", "выруби фонарик", "включи свет на телефоне", "фонарик")
        uk("увімкни ліхтарик", "вимкни ліхтарик", "ліхтарик", "засвіти ліхтарик")
        en("turn on the flashlight", "flashlight off", "turn off the flashlight")
        ru("открой настройки wi-fi", "открой настройки блютуз", "включи не беспокоить", "открой настройки звука", "включи режим полёта", "открой настройки экрана", "включи вайфай", "включи блютуз", "открой настройки батареи",
            "выключи вайфай", "выключи блютуз", "включи режим экономии", "включи геолокацию", "открой настройки телефона", "включи не беспокоить на час")
        uk("відкрий налаштування wi-fi", "увімкни блютуз", "увімкни не турбувати", "відкрий налаштування звуку", "увімкни режим польоту", "вимкни вайфай")
        // музыка
        ru("включи <song>", "включи песню <song>", "поставь <song>", "включи музыку <song>", "сыграй <song>", "включи плейлист <song>", "включи любую песню <song>", "включи <song> в спотифай", "включи <song> на ютубе",
            "включи музыку", "включи плейлист вперемешку", "включи что-нибудь хорошее", "поставь музыку для работы", "включи альбом <song>", "включи видео <song> на ютубе", "включи песню <song> вперемешку")
        uk("увімкни <song>", "увімкни пісню <song>", "постав <song>", "увімкни музику <song>", "увімкни плейлист для бігу", "увімкни плейлист вперемішку", "зіграй <song>", "увімкни музику")
        en("play <song>", "play some music", "play <song> on spotify", "play my playlist shuffled", "put on <song>")
        ru("поставь на паузу", "пауза", "продолжи", "включи следующий трек", "следующая песня", "предыдущий трек", "верни предыдущую", "сделай громче", "потише", "сделай тише", "останови музыку", "перемешай треки", "поставь на повтор",
            "что сейчас играет", "пропусти этот трек", "включи дальше", "громче", "продолжай воспроизведение", "ещё раз эту песню", "убавь громкость", "прибавь звук", "верни назад", "поставь на паузу пожалуйста", "что это за песня")
        uk("постав на паузу", "пауза", "продовжуй", "наступна пісня", "попередній трек", "гучніше", "тихіше", "зупини музику", "перемішай треки", "що зараз грає", "пропусти цей трек", "зроби гучніше")
        en("pause", "pause the music", "next song", "previous track", "louder", "turn it down", "resume", "skip this track", "what's playing")
        // экраны и система
        ru("открой календарь", "покажи календарь", "открой раздел календарь", "открой карту", "покажи карту", "открой рассылку", "открой настройки приложения", "открой услуги", "покажи услуги", "открой список людей",
            "открой раздел люди", "перейди в календарь", "открой раздел услуги", "покажи рассылку", "открой экран настроек", "перейди на карту", "открой раздел настройки", "открой календарь пожалуйста", "открой мой календарь", "открой календарь записей", "открой карту пожалуйста", "открой мою карту", "открой список услуг", "открой каталог услуг",
            "открой список клиентов", "открой клиентов", "открой раздел рассылки", "открой массовую рассылку", "открой рассылки", "открой календарь на неделю", "зайди в календарь", "зайди в настройки", "открой настройки приложения пожалуйста")
        uk("відкрий календар", "покажи календар", "відкрий карту", "відкрий розсилку", "відкрий налаштування", "відкрий послуги", "покажи карту", "відкрий розділ люди", "перейди в календар", "відкрий календар записів", "відкрий мій календар", "відкрий розсилки", "відкрий список послуг", "відкрий календар будь ласка", "зайди в календар")
        en("open the calendar", "show the map", "open settings", "open the people list")
        ru("закрой приложение", "сверни", "сверни приложение", "на главный экран", "выйди на главную", "домой", "закрой это", "закрой приложение пожалуйста", "закрой это приложение", "закрой текущее приложение", "закрой программу", "закрой окно", "иди на главный экран", "вернись на главный экран", "свернись")
        uk("закрий застосунок", "закрий застосунок будь ласка", "закрий цей застосунок", "закрий поточний застосунок", "згорни", "на головний екран", "додому", "закрий це")
        en("go home", "minimize", "home screen")
        ru("заблокируй", "заблокируй приложение", "закрой сейф", "закрой сейф на замок", "заблокируй экран", "блокировка", "поставь блокировку", "запри приложение", "закрой всё на пин", "заблокируй сейф")
        uk("заблокуй", "заблокуй застосунок", "закрий сейф", "заблокуй екран")
        en("lock", "lock the app", "lock the screen")
        ru("сделай резервную копию", "сделай бэкап", "создай резервную копию", "бэкап данных", "сохрани резервную копию")
        uk("зроби резервну копію", "зроби бекап", "створи резервну копію")
        // цепочки
        ru("напиши <dat> что <msg> и сверни", "напиши <dat> <ch> что <msg> и закрой приложение", "открой <acc> и добавь заметку <note>", "позвони <dat> и сверни", "запиши <acc> на <day> <time> и отправь ей подтверждение",
            "открой карточку <gen> и позвони", "проложи маршрут к <dat> и позвони", "найди <acc> и открой карточку", "напиши <dat> что <msg> и включи музыку", "включи <song> и построй маршрут домой",
            "позвони <dat> и напиши ей что <msg>", "поставь на паузу и закрой <capp>", "напиши <dat> что <msg> и поставь будильник на 7", "запиши <acc> на <day> <time> и добавь заметку <note>",
            "перенеси <acc> <day> и напиши ей что <msg>", "отмени запись <gen> и напиши ему что <msg>", "открой <acc> и построй маршрут", "включи фонарик и поставь таймер на 5 минут")
        uk("напиши <dat> що <ukmsg> і згорни", "відкрий <acc> і додай нотатку <uknote>", "подзвони <dat> і згорни", "запиши <acc> на <day> <time> і надішли їй підтвердження", "проклади маршрут до <gen> і подзвони",
            "увімкни <song> і проклади маршрут додому", "напиши <dat> що <ukmsg> і постав таймер на 5 хвилин")
        en("text <nom> that <msg_en> and go home", "open <nom> and add a note <note_en>", "call <nom> and then go home")
    }

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun cls(i: NoaIntent): String = if (i is NoaIntent.Sequence) "Seq" else i::class.simpleName.orEmpty()

    /** Падеж «творительный» делаем по имени: для немногих шаблонов с «с <ins>» берём родительный-подобную форму — правила её всё равно проверят. */
    private fun fill(lang: String, tpl: String, r: Random): Pair<String, Nm?>? {
        val pool = when (lang) { "uk" -> ukNames; "en" -> enNames; else -> ruNames }
        val nm = pool[r.nextInt(pool.size)]
        fun <T> pick(l: List<T>) = l[r.nextInt(l.size)]
        val day = pick(when (lang) { "uk" -> ukDay; "en" -> enDay; else -> ruDay })
        val time = pick(when (lang) { "uk" -> ukTime; "en" -> enTime; else -> ruTime })
        var s = tpl
        fun rep(k: String, v: () -> String) { if (s.contains(k)) s = s.replace(k, v()) }
        rep("<acc>") { nm.acc }; rep("<dat>") { nm.dat }; rep("<gen>") { nm.gen }; rep("<nom>") { nm.nom }; rep("<ins>") { nm.gen }
        rep("<day>") { day }; rep("<time>") { time }
        rep("<svc>") { pick(when (lang) { "uk" -> ukSvc; "en" -> listOf("a haircut", "manicure", "a massage", "a consultation"); else -> ruSvc }) }
        rep("<gen_app>") { pick(closeApps) }
        rep("<capp>") { pick(if (lang == "en") listOf("spotify", "youtube", "waze", "telegram", "whatsapp", "viber", "chrome") else closeApps) }
        rep("<app>") { pick(apps) }
        rep("<song>") { pick(songs) }; rep("<place>") { pick(places) }
        rep("<ukmsg>") { pick(ukMsgs) }; rep("<msg_en>") { pick(listOf("i am late", "i will be there soon", "call me back", "see you tomorrow")) }; rep("<msg>") { pick(msgs) }
        rep("<uknote>") { pick(ukNotes) }; rep("<note_en>") { pick(listOf("likes coffee", "paid the debt", "prefers evenings")) }; rep("<note>") { pick(notes) }
        rep("<ukrem>") { pick(ukReminds) }; rep("<rem_en>") { pick(listOf("call the bank", "buy flowers", "pick up the package")) }; rep("<rem>") { pick(reminds) }
        rep("<q>") { pick(if (lang == "en") listOf("weather in London", "pasta recipe", "dollar rate", "who is Elon Musk") else qs) }; rep("<ch>") { pick(tg) }; rep("<min>") { pick(listOf(5, 10, 15, 20, 25, 30)).toString() }
        rep("<kind_uk>") { pick(listOf("додому", "на роботу")) }; rep("<kind>") { pick(kinds) }
        if (s.contains('<')) return null
        s = s.replace(" на в ", " на ").replace(" на на ", " на ").replace(" на у ", " на ").replace(" на о ", " на ")
        return s to nm
    }

    /** Нужен ли первому шагу человек, а во фразе его нет (местоимение/пусто) — такой пример требует прошлого контекста. */
    private fun needsContext(i: NoaIntent): Boolean {
        val s = if (i is NoaIntent.Sequence) i.steps.first() else i
        val has = when (s) {
            is NoaIntent.CreateAppointment, is NoaIntent.Open, is NoaIntent.Call, is NoaIntent.Message, is NoaIntent.AddNote, is NoaIntent.OpenContact,
            is NoaIntent.PersonInfo, is NoaIntent.Favorite, is NoaIntent.Select, is NoaIntent.CancelAppointment, is NoaIntent.MoveAppointment,
            is NoaIntent.ShareData, is NoaIntent.Reply -> true
            else -> false
        }
        if (!has) return false
        val p = NoaParser.personOf(s).trim()
        return p.isBlank() || p.lowercase() in NoaParser.PRONOUNS
    }

    /** Имена людей в шагах → начальная форма из таблицы; null — человек не из таблицы (мусорный пример). */
    private fun lemmatize(i: NoaIntent): NoaIntent? {
        fun one(s: NoaIntent): NoaIntent? {
            if (s is NoaIntent.Find) return lemmaOf[s.query.trim().lowercase()]?.let { NoaIntent.Find(it) } ?: s
            val p = NoaParser.personOf(s).trim()
            if (p.isBlank() || p.lowercase() in NoaParser.PRONOUNS) return s
            val first = p.split(' ').first().lowercase()
            val lem = lemmaOf[first] ?: return null
            return NoaParser.withPerson(s, lem)
        }
        return if (i is NoaIntent.Sequence) NoaIntent.Sequence(i.steps.map { one(it) ?: return null }) else one(i)
    }

    private val CHAT = listOf(
        "позвони" to """{"actions":[],"ask":"Кому позвонить?"}""", "напиши" to """{"actions":[],"ask":"Кому написать?"}""",
        "запиши на завтра" to """{"actions":[],"ask":"Кого записать?"}""", "добавь заметку" to """{"actions":[],"ask":"К кому добавить заметку?"}""",
        "поставь будильник" to """{"actions":[],"ask":"На какое время поставить будильник?"}""", "поставь таймер" to """{"actions":[],"ask":"На сколько поставить таймер?"}""",
        "открой" to """{"actions":[],"ask":"Что открыть?"}""", "зателефонуй" to """{"actions":[],"ask":"Кому зателефонувати?"}""", "напомни" to """{"actions":[],"ask":"О чём напомнить?"}""",
        "как дела" to """{"actions":[],"reply":"Отлично! Чем помочь?"}""", "привет" to """{"actions":[],"reply":"Привет! Чем помочь?"}""", "спасибо" to """{"actions":[],"reply":"Пожалуйста!"}""",
        "розкажи анекдот" to """{"actions":[],"reply":"Приходить програміст до лікаря..."}""", "сколько будет 15% от 80" to """{"actions":[],"reply":"12."}""",
        "как тебя зовут" to """{"actions":[],"reply":"Я Ноа, ваш помощник."}""", "що ти вмієш" to """{"actions":[],"reply":"Дзвоню, пишу, записую клієнтів, веду маршрут."}""",
        "столица Франции" to """{"actions":[],"reply":"Париж."}""", "який сьогодні день" to """{"actions":[],"reply":"<from Today>"}""", "расскажи шутку" to """{"actions":[],"reply":"Почему программисты любят темноту? Потому что свет привлекает баги."}""",
        "как дела у тебя" to """{"actions":[],"reply":"Всё хорошо, спасибо!"}""", "як справи" to """{"actions":[],"reply":"Чудово! Чим допомогти?"}""",
    )

    @Test fun generate() {
        assumeTrue(System.getenv("GEN_EXAMPLES") == "1")
        val golden = File("src/main/assets/golden_phrases.txt").readLines().map(::norm).toSet()
        val rows = LinkedHashMap<String, Triple<String, String, String>>() // norm -> phrase, json, class
        val tags = HashMap<String, String>()
        val stats = HashMap<String, Int>()
        val tplFail = HashMap<String, Int>()
        val tplOk = HashMap<String, Int>()

        fun consider(phrase: String, nmUsed: Nm?, tag: String): Boolean {
            val n = norm(phrase)
            if (n in golden) { stats.merge("golden", 1, Int::plus); return false }
            if (n in rows) return false
            val intent = NoaParser.parse(phrase, now)
            if (intent is NoaIntent.Unknown) { tplFail.merge("$tag unknown", 1, Int::plus); return false }
            if (needsContext(intent)) { tplFail.merge("$tag ctx", 1, Int::plus); return false }
            val first = norm(phrase).split(' ').firstOrNull { it !in setOf("санта", "ноа", "noa", "слушай", "слухай", "пожалуйста", "ну", "а", "так", "эй") }.orEmpty()
            val steps = if (intent is NoaIntent.Sequence) intent.steps else listOf(intent)
            val nameInPhrase = norm(phrase).split(' ').any { w -> lemmaOf.containsKey(w) }
            val noPerson = steps.all { NoaParser.personOf(it).isBlank() }
            // Правила иногда ошибаются на редких оборотах: такие фразы в примеры не берём.
            if (nameInPhrase && noPerson && steps.none { it is NoaIntent.Reply || it is NoaIntent.Find }) { tplFail.merge("$tag nameDropped", 1, Int::plus); return false }
            if (steps.first() is NoaIntent.Message && (first.startsWith("откр") || first.startsWith("відкр") || first.startsWith("open"))) { tplFail.merge("$tag openAsMsg", 1, Int::plus); return false }
            if (steps.any { it is NoaIntent.Message && it.text == null && !it.aboutAppointment } && norm(phrase).split(' ').size > 4) { tplFail.merge("$tag msgNoText", 1, Int::plus); return false }
            if (Regex("^(напиш|відправ|отправ|надішл|скажи|сообщи|передай|text|send|tell|message)").containsMatchIn(first) && steps.first() !is NoaIntent.Message && steps.first() !is NoaIntent.Reply
                && steps.first() !is NoaIntent.ShareData) { tplFail.merge("$tag writeVerbNotMsg", 1, Int::plus); return false }
            if (steps.any { it is NoaIntent.Reply && Regex("телеграм|вотсап|ватсап|вайбер|смс|sms").containsMatchIn((it as NoaIntent.Reply).text.lowercase()) }) { tplFail.merge("$tag replyChannel", 1, Int::plus); return false }
            val lem = lemmatize(intent) ?: run { tplFail.merge("$tag person", 1, Int::plus); return false }
            if (nmUsed != null && lem !is NoaIntent.Sequence) {
                val p = NoaParser.personOf(lem)
                if (p.isNotBlank() && p.lowercase() !in NoaParser.PRONOUNS && p != nmUsed.nom) { tplFail.merge("$tag wrongperson", 1, Int::plus); return false }
            }
            val json = NoaIntentJson.toJson(lem) ?: run { tplFail.merge("$tag nojson", 1, Int::plus); return false }
            val back = ip.fromJson(json, phrase, now)
            val want = NoaBenchmark.signature(intent)
            val got = if (back?.intent != null) NoaBenchmark.signature(back.intent) else "—"
            if (got != want) { tplFail.merge("$tag roundtrip", 1, Int::plus); println("RT FAIL «$phrase» want=$want got=$got json=$json"); return false }
            rows[n] = Triple(phrase, json, cls(intent)); tags[n] = tag
            tplOk.merge(tag, 1, Int::plus)
            return true
        }

        // 1. шаблоны: по 14 попыток на шаблон, детерминированно
        val r = Random(20261004L)
        for ((lang, tpl) in templates) {
            repeat(14) {
                val (s, nm) = fill(lang, tpl, r) ?: return@repeat
                consider(s, nm, tpl)
            }
        }
        // 2. фразы из тестов: только понятные (человек из таблицы, не меньше двух слов либо короткая команда)
        val lit = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")
        val files = listOf(
            "src/test/java/com/kartoteka/app/data/NoaScenariosTest.kt", "src/test/java/com/kartoteka/app/data/NoaScenarios2Test.kt",
            "src/test/java/com/kartoteka/app/data/NoaCommandsTest.kt", "src/test/java/com/kartoteka/app/data/NoaParserTest.kt",
            "src/test/java/com/kartoteka/app/screens/NoaExecutorTest.kt", "src/test/java/com/kartoteka/app/screens/NoaExecutor2Test.kt",
        )
        val fromTests = ArrayList<String>()
        for (f in files) for (m in lit.findAll(File(f).readText())) {
            val s = m.groupValues[1].replace("\\\"", "\"").trim()
            if (s.length !in 5..110 || '$' in s || '\\' in s || s.none { it.isLetter() } || s.split(' ').size < 2) continue
            val first = norm(s).substringBefore(' ')
            if (first in setOf("запись", "приём", "прием", "в", "на", "следующей", "позвонить", "создай")) continue
            fromTests += s
        }
        var fromTestsKept = 0
        for (s in fromTests) if (consider(s, null, "tests")) fromTestsKept++
        stats["fromTests"] = fromTestsKept

        println("rows=${rows.size} stats=$stats")
        rows.values.groupBy { it.third }.toSortedMap().forEach { (c, l) -> println("CLS $c ${l.size}") }
        tplFail.entries.sortedBy { it.key }.forEach { println("FAILTPL ${it.key} x${it.value}") }
        templates.forEach { (_, t) -> if ((tplOk[t] ?: 0) == 0) println("DEADTPL $t") }
        // 3. отбор: ограничение на класс, половина — фразы из тестов, остальное — шаблоны по кругу (чтобы формулировки не повторялись)
        val caps = mapOf("Seq" to 60, "CreateAppointment" to 40, "Message" to 40, "Call" to 30, "Play" to 35, "PersonInfo" to 35, "MoveAppointment" to 28,
            "CancelAppointment" to 28, "Remind" to 28, "Route" to 28, "Select" to 12, "Favorite" to 16, "OpenContact" to 20, "Reply" to 20, "GoHome" to 20)
        val pickR = Random(7)
        val chosen = ArrayList<Pair<String, String>>()
        for ((c, list) in rows.entries.groupBy { it.value.third }.toSortedMap()) {
            val cap = caps[c] ?: 25
            val own = list.filter { tags[it.key] == "tests" }.shuffled(pickR).take(cap / 2)
            val groups = list.filter { tags[it.key] != "tests" }.groupBy { tags[it.key] }.values.map { it.shuffled(pickR).toMutableList() }.shuffled(pickR)
            val out = own.toMutableList()
            while (out.size < cap && groups.any { it.isNotEmpty() }) for (g in groups) if (out.size < cap && g.isNotEmpty()) out += g.removeAt(0)
            out.forEach { chosen += it.value.first to it.value.second }
        }
        for ((p, j) in CHAT) if (!j.contains("<from")) chosen += p to j
        val shuffled = chosen.shuffled(Random(11))
        println("FINAL ${shuffled.size}")
        File("/home/user/Ttst/.claude/worktrees/agent-aedabd24f8baf59be/app/src/main/assets/examples.tsv").writeText(shuffled.joinToString("\n", postfix = "\n") { it.first + "\t" + it.second })
    }
}
