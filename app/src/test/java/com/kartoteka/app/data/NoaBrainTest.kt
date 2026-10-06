package com.kartoteka.app.data

import com.kartoteka.app.assistant.Clarify
import com.kartoteka.app.assistant.Interpreted
import com.kartoteka.app.assistant.NoaContext
import com.kartoteka.app.assistant.NoaFallback
import com.kartoteka.app.assistant.NoaIntent
import com.kartoteka.app.assistant.NoaInterpreter
import com.kartoteka.app.assistant.NoaMatch
import com.kartoteka.app.assistant.NoaMedia
import com.kartoteka.app.assistant.NoaText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * «Мозг» Ноа без телефона: готовые ответы модели (хорошие, в обёртке, кривые, с синонимами, выдуманными именами и номерами,
 * с недостающими полями, с уточняющим вопросом, болтовня, слишком длинные, обрезанные) → команды и реплики;
 * сопоставление имён, уточняющие вопросы и ответы на них, чистка текста для озвучки, бюджет промпта.
 */
class NoaBrainTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val ip = NoaInterpreter(null)
    private val names = listOf("Анна Иванова (Аня)", "Илья Рыков", "Илья Ким", "Мария Фролова", "Олег Петренко", "Мама")

    /** Компактная запись результата: «Call(Мама)», «Msg(Аня,TELEGRAM,привет)», «- |r:реплика |ask:вопрос». */
    private fun show(i: NoaIntent): String = when (i) {
        is NoaIntent.Sequence -> i.steps.joinToString(" > ") { show(it) }
        is NoaIntent.Call -> "Call(${i.personQuery})"
        is NoaIntent.Message -> "Msg(${i.personQuery},${i.channel},${i.text}${if (i.aboutAppointment) ",about" else ""})"
        is NoaIntent.AddNote -> "Note(${i.personQuery},${i.text})"
        is NoaIntent.Open -> "Open(${i.personQuery})"
        is NoaIntent.Find -> "Find(${i.query})"
        is NoaIntent.Reply -> "Reply(${i.personQuery},${i.text})"
        is NoaIntent.CreateAppointment -> "Appt(${i.personQuery},${i.dateTime},${i.serviceQuery})"
        is NoaIntent.CancelAppointment -> "Cancel(${i.personQuery},${i.date},del=${i.delete})"
        is NoaIntent.MoveAppointment -> "Move(${i.personQuery},${i.dateTime})"
        is NoaIntent.Play -> "Play(${i.query},artist=${i.artist})"
        is NoaIntent.Route -> "Route(${i.personQuery},${i.kind},${i.place},${i.app})"
        is NoaIntent.Alarm -> "Alarm(${i.hour}:${"%02d".format(i.minute)})"
        is NoaIntent.Timer -> "Timer(${i.seconds})"
        is NoaIntent.Media -> "Media(${i.control})"
        is NoaIntent.Agenda -> "Agenda(${i.date})"
        else -> i.toString()
    }

    private fun show(r: Interpreted?): String = if (r == null) "null" else
        (r.intent?.let { show(it) } ?: "-") + (r.reply?.let { " |r:$it" } ?: "") + (r.ask?.let { " |ask:${it.question}" } ?: "")

    private class Case(val name: String, val user: String, val raw: String, val expect: String, val people: List<String>? = null, val hasLast: Boolean = false, val data: String = "")

    private val cases = listOf(
        // --- хорошие ответы ---
        Case("call good", "позвони маме", """{"actions":[{"action":"call","person":"мама"}],"reply":"Звоню"}""", "Call(Мама) |r:Звоню"),
        Case("message channel from phrase beats model", "напиши Ане в телеграм что опоздаю",
            """{"actions":[{"action":"message","person":"Аня","channel":"whatsapp","text":"опоздаю"}],"reply":"Пишу"}""", "Msg(Анна Иванова,TELEGRAM,опоздаю) |r:Пишу"),
        Case("message channel sms in phrase", "отправь Олегу смс что буду", """{"actions":[{"action":"message","person":"Олег","text":"буду"}]}""", "Msg(Олег Петренко,SMS,буду)"),
        Case("message default whatsapp", "скажи Марии привет", """{"actions":[{"action":"message","person":"Мария","text":"привет"}]}""", "Msg(Мария Фролова,WHATSAPP,привет)"),
        Case("message model channel kept when phrase silent", "x", """{"actions":[{"action":"message","person":"Мария","channel":"telegram","text":"привет"}]}""", "Msg(Мария Фролова,TELEGRAM,привет)"),
        Case("fenced json", "позвони Олегу", "```json\n{\"actions\":[{\"action\":\"call\",\"person\":\"Олег\"}],\"reply\":\"Звоню Олегу\"}\n```", "Call(Олег Петренко) |r:Звоню Олегу"),
        Case("text around json", "позвони Олегу", "Конечно! {\"actions\":[{\"action\":\"call\",\"person\":\"Олег\"}]} Готово.", "Call(Олег Петренко)"),
        Case("steps instead of actions", "позвони Олегу", """{"steps":[{"action":"call","person":"Олег"}]}""", "Call(Олег Петренко)"),
        Case("intent key instead of action", "позвони Олегу", """{"actions":[{"intent":"call","person":"Олег"}]}""", "Call(Олег Петренко)"),
        Case("type key instead of action", "позвони Олегу", """{"actions":[{"type":"call","person":"Олег"}]}""", "Call(Олег Петренко)"),
        Case("action name case and dashes", "запиши Марию на завтра в 15:00", """{"actions":[{"action":"Create-Appointment","person":"Мария"}]}""",
            "Appt(Мария Фролова,2026-10-05T15:00,null)"),
        Case("smart quotes", "позвони маме", "{\u201Cactions\u201D:[{\u201Caction\u201D:\u201Ccall\u201D,\u201Cperson\u201D:\u201Cмама\u201D}]}", "Call(Мама)"),
        Case("chain message then home", "напиши Ане что опоздаю и сверни",
            """{"actions":[{"action":"message","person":"Аня","text":"опоздаю"},{"action":"go_home"}],"reply":"Пишу"}""", "Msg(Анна Иванова,WHATSAPP,опоздаю) > GoHome |r:Пишу"),
        Case("chain two people", "позвони Олегу и напиши Марии что перезвоню",
            """{"actions":[{"action":"call","person":"Олег"},{"action":"message","person":"Мария","text":"перезвоню"}]}""", "Call(Олег Петренко) > Msg(Мария Фролова,WHATSAPP,перезвоню)"),
        // --- синонимы действий и полей ---
        Case("alias send_message recipient body", "x", """{"actions":[{"action":"send_message","recipient":"Мария","body":"привет"}]}""", "Msg(Мария Фролова,WHATSAPP,привет)"),
        Case("alias whatsapp action implies channel", "x", """{"actions":[{"action":"whatsapp","to":"Олег","content":"хай"}]}""", "Msg(Олег Петренко,WHATSAPP,хай)"),
        Case("alias telegram action implies channel", "x", """{"actions":[{"action":"telegram","to":"Олег","content":"хай"}]}""", "Msg(Олег Петренко,TELEGRAM,хай)"),
        Case("alias text action", "x", """{"actions":[{"action":"text","contact":"Олег","message":"хай"}]}""", "Msg(Олег Петренко,WHATSAPP,хай)"),
        Case("alias sms action", "x", """{"actions":[{"action":"sms","person":"Олег","text":"хай"}]}""", "Msg(Олег Петренко,SMS,хай)"),
        Case("alias contact for call", "x", """{"actions":[{"action":"call","contact":"Олег"}]}""", "Call(Олег Петренко)"),
        Case("alias client for call", "x", """{"actions":[{"action":"call","client":"Мария"}]}""", "Call(Мария Фролова)"),
        Case("alias dial name", "x", """{"actions":[{"action":"dial","name":"Мария"}]}""", "Call(Мария Фролова)"),
        Case("alias note content", "x", """{"actions":[{"action":"note","name":"Аня","content":"любит чай"}]}""", "Note(Анна Иванова,любит чай)"),
        Case("alias add_note title", "x", """{"actions":[{"action":"add_note","person":"Мария","title":"позвонить в пятницу"}]}""", "Note(Мария Фролова,позвонить в пятницу)"),
        Case("alias open", "x", """{"actions":[{"action":"open","person":"Олег"}]}""", "Open(Олег Петренко)"),
        Case("alias search with person field", "x", """{"actions":[{"action":"search","person":"Олег"}]}""", "Find(Олег)"),
        Case("alias find q", "x", """{"actions":[{"action":"find","q":"клиенты"}]}""", "Find(клиенты)"),
        Case("alias play", "x", """{"actions":[{"action":"play","query":"Земфира"}]}""", "Play(Земфира,artist=false)"),
        Case("alias play song title", "x", """{"actions":[{"action":"play_music","song":"Группа крови"}]}""", "Play(Группа крови,artist=false)"),
        Case("any song becomes artist", "x", """{"actions":[{"action":"play_music","query":"любую песню Земфиры"}]}""", "Play(Земфиры,artist=true)"),
        Case("action pause", "x", """{"actions":[{"action":"pause"}]}""", "Media(PAUSE)"),
        Case("action stop", "x", """{"actions":[{"action":"stop"}]}""", "Media(STOP)"),
        Case("action louder", "x", """{"actions":[{"action":"louder"}]}""", "Media(LOUDER)"),
        Case("media control volume_down", "x", """{"actions":[{"action":"media","control":"volume_down"}]}""", "Media(QUIETER)"),
        Case("alias torch", "x", """{"actions":[{"action":"torch","on":"false"}]}""", "Flashlight(on=false)"),
        Case("alias open_app", "x", """{"actions":[{"action":"open_app","application":"youtube"}]}""", "LaunchApp(name=youtube)"),
        Case("alias google web_search", "x", """{"actions":[{"action":"google","q":"курс доллара"}]}""", "WebSearch(query=курс доллара)"),
        Case("alias dial for call with person key", "x", """{"actions":[{"action":"dial","person":"Мама"}]}""", "Call(Мама)"),
        Case("alias navigate", "x", """{"actions":[{"action":"navigate","place":"Крещатик 22","app":"Waze"}]}""", "Route(,null,Крещатик 22,waze)"),
        Case("route person home", "x", """{"actions":[{"action":"route","person":"Мама","kind":"home"}]}""", "Route(Мама,HOME,,null)"),
        Case("share_data keeps to", "x", """{"actions":[{"action":"share_data","person":"Олег","data":"phone","to":"telegram"}]}""", "ShareData(personQuery=Олег Петренко, data=PHONE, target=app:telegram)"),
        Case("open_contact keeps contact field", "x", """{"actions":[{"action":"open_contact","person":"Аня","contact":"instagram"}]}""", "OpenContact(personQuery=Анна Иванова, type=INSTAGRAM)"),
        Case("open_contact unknown type dropped", "x", """{"actions":[{"action":"open_contact","person":"Аня","contact":"tiktok"}],"reply":"Открываю"}""", "null"),
        Case("person_info alias info", "x", """{"actions":[{"action":"info","person":"Олег","topic":"birthday"}]}""", "PersonInfo(personQuery=Олег Петренко, topic=BIRTHDAY, question=x)"),
        Case("favorite string false", "x", """{"actions":[{"action":"favorite","person":"Олег","on":"нет"}]}""", "Favorite(personQuery=Олег Петренко, on=false)"),
        Case("go_home alias minimize", "x", """{"actions":[{"action":"minimize"}]}""", "GoHome"),
        Case("lock", "x", """{"actions":[{"action":"lock"}]}""", "Lock"),
        Case("backup", "x", """{"actions":[{"action":"backup"}]}""", "Backup"),
        Case("close_app without app goes home", "x", """{"actions":[{"action":"close_app"}]}""", "GoHome"),
        Case("open_screen", "x", """{"actions":[{"action":"open_screen","section":"calendar"}]}""", "OpenScreen(section=CALENDAR)"),
        Case("phone_settings", "x", """{"actions":[{"action":"phone_settings","what":"wifi"}]}""", "PhoneSettings(what=wifi)"),
        // --- имена: починка по списку людей ---
        Case("inflected name fixed", "позвони Ане", """{"actions":[{"action":"call","person":"Ане"}]}""", "Call(Анна Иванова)"),
        Case("nickname fixed to full", "позвони Ане", """{"actions":[{"action":"call","person":"Аня"}]}""", "Call(Анна Иванова)"),
        Case("misheard surname fuzzy", "позвони Рыкову", """{"actions":[{"action":"call","person":"Рыкова"}]}""", "Call(Илья Рыков)"),
        Case("ukrainian spelling matches russian book", "подзвони Ілля Риков", """{"actions":[{"action":"call","person":"Ілля Риков"}]}""", "Call(Илья Рыков)"),
        Case("ambiguous first name asks", "позвони Илье", """{"actions":[{"action":"call","person":"Илья"}]}""", "- |ask:Кого именно: Илья Рыков, Илья Ким?"),
        Case("ambiguous resolved by phrase", "позвони Илье Киму", """{"actions":[{"action":"call","person":"Илья"}]}""", "Call(Илья Ким)"),
        Case("full name unambiguous", "x", """{"actions":[{"action":"call","person":"Илья Рыков"}]}""", "Call(Илья Рыков)"),
        Case("hallucinated name asks who", "позвони", """{"actions":[{"action":"call","person":"Сергей"}]}""", "- |ask:Кому позвонить?"),
        Case("hallucinated name replaced by name from phrase", "позвони Олегу", """{"actions":[{"action":"call","person":"Сергей Иванов"}]}""", "Call(Олег Петренко)"),
        Case("unknown name said by user is kept", "позвони Тарасу", """{"actions":[{"action":"call","person":"Тарас"}]}""", "Call(Тарас)"),
        Case("name from data is kept", "кому звонить сегодня", """{"actions":[{"action":"call","person":"Тарас"}]}""", "Call(Тарас)", data = "Today 04.10: 10:00 Тарас Кузьменко"),
        Case("no people list keeps model name", "x", """{"actions":[{"action":"call","person":"Петя"}]}""", "Call(Петя)", people = emptyList()),
        Case("pronoun inherits previous person", "открой Олега и добавь ему заметку",
            """{"actions":[{"action":"open_person","person":"Олег"},{"action":"add_note","person":"ему","text":"вернул долг"}]}""", "Open(Олег Петренко) > Note(Олег Петренко,вернул долг)"),
        Case("blank person inherits", "открой Олега и добавь заметку",
            """{"actions":[{"action":"open_person","person":"Олег"},{"action":"add_note","text":"вернул долг"}]}""", "Open(Олег Петренко) > Note(Олег Петренко,вернул долг)"),
        Case("pronoun kept when someone was discussed", "добавь ей заметку", """{"actions":[{"action":"add_note","person":"ей","text":"любит чай"}]}""", "Note(ей,любит чай)", hasLast = true),
        Case("pronoun without anyone asks", "добавь ей заметку", """{"actions":[{"action":"add_note","person":"ей","text":"любит чай"}]}""", "- |ask:К кому добавить заметку?"),
        Case("phone as person invented", "позвони", """{"actions":[{"action":"call","person":"+380501234567"}]}""", "- |ask:Кому позвонить?"),
        Case("phone as person said by user", "позвони на +380501234567", """{"actions":[{"action":"call","person":"+380501234567"}]}""", "Call(+380501234567)"),
        Case("person as object ignored", "x", """{"actions":[{"action":"call","person":{"name":"Аня"}}]}""", "- |ask:Кому позвонить?"),
        // --- обязательные поля ---
        Case("find without query asks", "найди", """{"actions":[{"action":"find"}]}""", "- |ask:Что искать?"),
        Case("launch_app without app asks", "запусти", """{"actions":[{"action":"launch_app"}]}""", "- |ask:Какое приложение?"),
        Case("reply without text asks", "ответь Олегу", """{"actions":[{"action":"reply","person":"Олег"}]}""", "- |ask:Что ответить?"),
        Case("note without text asks", "заметка Олегу", """{"actions":[{"action":"add_note","person":"Олег"}]}""", "- |ask:Что записать в заметку?"),
        Case("note without person asks", "добавь заметку любит чай", """{"actions":[{"action":"add_note","text":"любит чай"}]}""", "- |ask:К кому добавить заметку?"),
        Case("message without person asks", "напиши что опоздаю", """{"actions":[{"action":"message","text":"опоздаю"}]}""", "- |ask:Кому написать?"),
        Case("route without target asks", "веди", """{"actions":[{"action":"route"}]}""", "- |ask:Куда ехать?"),
        Case("alarm without time asks", "поставь будильник", """{"actions":[{"action":"alarm"}]}""", "- |ask:На какое время поставить будильник?"),
        Case("timer without duration asks", "поставь таймер", """{"actions":[{"action":"timer"}]}""", "- |ask:На сколько поставить таймер?"),
        Case("appointment without person asks", "запиши на завтра", """{"actions":[{"action":"create_appointment"}]}""", "- |ask:Кого записать?"),
        Case("unknown action only is dropped", "x", """{"actions":[{"action":"teleport","person":"Аня"}]}""", "null"),
        // --- время, даты, канал берём из фразы ---
        Case("alarm time from phrase beats model", "разбуди меня в 7:30", """{"actions":[{"action":"alarm","time":"09:00"}]}""", "Alarm(7:30)"),
        Case("alarm time from model", "x", """{"actions":[{"action":"alarm","time":"07:45"}]}""", "Alarm(7:45)"),
        Case("alarm time omitted by model but in phrase", "разбуди меня в 6:15", """{"actions":[{"action":"alarm"}]}""", "Alarm(6:15)"),
        Case("timer minutes from phrase beats model", "таймер на 10 минут", """{"actions":[{"action":"timer","minutes":5}]}""", "Timer(600)"),
        Case("timer from model", "x", """{"actions":[{"action":"timer","minutes":3}]}""", "Timer(180)"),
        Case("timer half an hour from phrase", "поставь таймер на полчаса", """{"actions":[{"action":"timer"}]}""", "Timer(1800)"),
        Case("timer hour and minutes from phrase", "таймер на 1 час 20 минут", """{"actions":[{"action":"timer","minutes":1}]}""", "Timer(4800)"),
        Case("appointment date from phrase not model", "запиши Марию на завтра в 15:00", """{"actions":[{"action":"create_appointment","person":"Мария","service":"Тату","date":"2020-01-01"}]}""",
            "Appt(Мария Фролова,2026-10-05T15:00,тату)"),
        Case("cancel with date from phrase", "отмени запись Олега на завтра", """{"actions":[{"action":"cancel_appointment","person":"Олег"}]}""", "Cancel(Олег Петренко,2026-10-05,del=false)"),
        Case("delete flag", "удали запись Олега", """{"actions":[{"action":"delete_appointment","person":"Олег"}]}""", "Cancel(Олег Петренко,null,del=true)"),
        Case("move to friday", "перенеси Олега на завтра в 11:00", """{"actions":[{"action":"move_appointment","person":"Олег"}]}""", "Move(Олег Петренко,2026-10-05T11:00)"),
        Case("agenda day from model", "x", """{"actions":[{"action":"agenda","day":"tomorrow"}]}""", "Agenda(2026-10-05)"),
        Case("agenda day from phrase beats model", "что у меня послезавтра", """{"actions":[{"action":"agenda","day":"tomorrow"}]}""", "Agenda(2026-10-06)"),
        Case("message text omitted takes phrase", "напиши Ане что опоздаю на 10 минут", """{"actions":[{"action":"message","person":"Аня"}]}""", "Msg(Анна Иванова,WHATSAPP,опоздаю на 10 минут)"),
        Case("message text that is whole command is cut", "напиши Ане что опоздаю", """{"actions":[{"action":"message","person":"Аня","text":"напиши Ане что опоздаю"}]}""", "Msg(Анна Иванова,WHATSAPP,опоздаю)"),
        Case("message about appointment uses template", "отправь ему подтверждение", """{"actions":[{"action":"message","person":"ему","text":"придумал сам"}]}""", "Msg(ему,WHATSAPP,null,about)", hasLast = true),
        // --- уточняющие вопросы от модели ---
        Case("ask field", "напиши", """{"actions":[],"ask":"Кому написать?"}""", "- |ask:Кому написать?"),
        Case("clarify alias", "поставь", """{"actions":[],"clarify":"В какое время?"}""", "- |ask:В какое время?"),
        Case("ask with markdown", "поставь", """{"actions":[],"ask":"**Когда** поставить? 🙂"}""", "- |ask:Когда поставить?"),
        Case("actions win over ask", "позвони маме", """{"actions":[{"action":"call","person":"мама"}],"ask":"Точно?"}""", "Call(Мама)"),
        Case("question reply keeps dialog open", "привет", """{"actions":[],"reply":"Привет! Чем помочь?"}""", "- |r:Привет! Чем помочь? |ask:Привет! Чем помочь?"),
        Case("clarifying question in reply", "позвони Илье", """{"actions":[],"reply":"Какого Илью вы имеете в виду?"}""", "- |r:Какого Илью вы имеете в виду? |ask:Какого Илью вы имеете в виду?"),
        // --- болтовня и ответы по данным ---
        Case("chitchat", "как дела", """{"actions":[],"reply":"Отлично, спасибо."}""", "- |r:Отлично, спасибо."),
        Case("knowledge", "столица франции", """{"actions":[],"reply":"Столица Франции — Париж."}""", "- |r:Столица Франции — Париж."),
        Case("chat action", "привет", """{"action":"chat","reply":"Привет"}""", "- |r:Привет"),
        Case("chat action without reply is silent", "привет", """{"action":"chat"}""", "-"),
        Case("answer from data", "когда Аня была у меня", """{"actions":[],"reply":"Аня была у вас 28 сентября."}""", "- |r:Аня была у вас 28 сентября."),
        Case("english reply", "hi", """{"actions":[],"reply":"Hello! Nice to hear you."}""", "- |r:Hello! Nice to hear you."),
        Case("placeholder reply dropped", "когда Аня была", """{"actions":[],"reply":"<from Data>"}""", "null"),
        Case("noa label stripped", "привет", """{"actions":[],"reply":"Ноа: Привет."}""", "- |r:Привет."),
        Case("markdown and emoji stripped", "привет", """{"actions":[],"reply":"**Привет!** 😊 Рада вас слышать."}""", "- |r:Привет! Рада вас слышать."),
        Case("bullet list flattened", "что умеешь", """{"actions":[],"reply":"- звонить\n- писать\n- записывать"}""", "- |r:звонить. писать. записывать"),
        Case("invented phone dropped", "какой телефон у Олега", """{"actions":[],"reply":"Телефон Олега: +380671112233. Звоните."}""", "- |r:Звоните."),
        Case("invented phone only reply is null", "какой телефон у Олега", """{"actions":[],"reply":"Телефон Олега: +380671112233."}""", "null"),
        Case("phone from data allowed", "какой телефон у Олега", """{"actions":[],"reply":"Телефон Олега: +380671112233."}""", "- |r:Телефон Олега: +380671112233.", data = "Олег Петренко · tel +380671112233"),
        Case("short numbers allowed", "сколько у меня людей", """{"actions":[],"reply":"В книжке 124 человека."}""", "- |r:В книжке 124 человека."),
        Case("reply claiming action without any is kept as chat", "x", """{"actions":[{"action":"fly"}],"reply":"Не умею летать"}""", "- |r:Не умею летать"),
        Case("reply of dropped action is not spoken", "x", """{"actions":[{"action":"open_screen","section":"nowhere"}],"reply":"Открываю"}""", "null"),
        // --- кривой и обрезанный JSON ---
        Case("truncated reply", "позвони маме", """{"actions":[{"action":"call","person":"мама"}],"reply":"Зв""", "Call(Мама)"),
        Case("truncated action loses person", "позвони", """{"actions":[{"action":"call","person":"Ол""", "- |ask:Кому позвонить?"),
        Case("trailing commas", "позвони маме", """{"actions":[{"action":"call","person":"мама",},],"reply":"Звоню",}""", "Call(Мама) |r:Звоню"),
        Case("unquoted keys single quotes", "x", "{actions:[{action:'add_note',person:'Аня',text:'любит чай'}]}", "Note(Анна Иванова,любит чай)"),
        Case("bare array", "x", """[{"action":"media","control":"next"},"go_home"]""", "Media(NEXT) > GoHome"),
        Case("actions as object", "x", """{"actions":{"action":"flashlight","on":false}}""", "Flashlight(on=false)"),
        Case("named action map", "x", """{"actions":{"timer":{"minutes":"5"}}}""", "Timer(300)"),
        Case("duplicate steps collapsed", "позвони маме", """{"actions":[{"action":"call","person":"мама"},{"action":"call","person":"мама"}]}""", "Call(Мама)"),
        Case("more than five steps capped", "x", """{"actions":[{"action":"media","control":"next"},{"action":"media","control":"pause"},{"action":"media","control":"stop"},{"action":"media","control":"louder"},{"action":"media","control":"quieter"},{"action":"go_home"},{"action":"lock"}]}""",
            "Media(NEXT) > Media(PAUSE) > Media(STOP) > Media(LOUDER) > Media(QUIETER)"),
        Case("garbage text", "x", "извините, я не могу помочь", "null"),
        Case("empty", "x", "", "null"),
        Case("brace only", "x", "{", "null"),
        Case("empty actions no reply", "x", """{"actions":[],"reply":""}""", "null"),
        Case("null actions", "x", """{"actions":null}""", "null"),
    )

    @Test fun cannedModelOutputs() {
        assertTrue("нужно ≥80 готовых ответов, сейчас ${cases.size}", cases.size >= 80)
        val bad = cases.mapNotNull { c ->
            val got = show(ip.fromJson(c.raw, c.user, now, c.people ?: names, c.hasLast, c.data))
            if (got == c.expect) null else "${c.name}: ждали «${c.expect}», получили «$got»"
        }
        assertTrue("\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun overLongReplyIsCappedAtSentence() {
        val sentence = "Это довольно длинное предложение для проверки обрезки. "
        val reply = sentence.repeat(12).trim()
        val r = ip.fromJson("""{"actions":[],"reply":"$reply"}""", "x", now, names)!!
        assertTrue(r.reply!!.length <= 280)
        assertTrue("обрезка по границе предложения: ${r.reply}", r.reply!!.endsWith("."))
        assertFalse(r.reply!!.endsWith("…"))
    }

    @Test fun overLongReplyWithoutSentencesIsCappedAtWord() {
        val reply = "слово ".repeat(100).trim()
        val r = NoaText.speakable(reply)
        assertTrue(r.length <= 281); assertTrue(r.endsWith("…")); assertFalse(r.dropLast(1).endsWith(" "))
    }

    // --- чистка текста ---

    @Test fun speakableStripsMarkdownAndEmoji() {
        assertEquals("Заголовок. Первое. Второе", NoaText.speakable("# Заголовок\n1. Первое\n2. Второе"))
        assertEquals("Смотри ссылку и код", NoaText.speakable("Смотри [ссылку](http://x.y) и `код`"))
        assertEquals("Готово! Всё хорошо", NoaText.speakable("Готово! ✅ Всё хорошо 👍🏽"))
        assertEquals("snake_case остаётся", NoaText.speakable("snake_case остаётся"))
        assertEquals("курсив и жирный", NoaText.speakable("*курсив* и __жирный__"))
    }

    @Test fun scrubNumbersKeepsOnlyKnownNumbers() {
        assertEquals("Звоните.", NoaText.scrubNumbers("Номер +38 (067) 111-22-33. Звоните.", ""))
        assertEquals("Номер +38 (067) 111-22-33.", NoaText.scrubNumbers("Номер +38 (067) 111-22-33.", "tel 380671112233"))
        assertEquals("Через 15 минут, в 2026 году.", NoaText.scrubNumbers("Через 15 минут, в 2026 году.", ""))
    }

    // --- сопоставление имён ---

    private val refs = NoaMatch.refs(names)

    @Test fun matcherHandlesInflectionAndAlphabets() {
        assertEquals("Анна Иванова", NoaMatch.resolve("Ане", refs).hits.single().display)
        assertEquals("Анна Иванова", NoaMatch.resolve("Анне Ивановой", refs).hits.single().display)
        assertEquals("Илья Рыков", NoaMatch.resolve("Ілля Риков", refs).hits.single().display)
        assertEquals("Мама", NoaMatch.resolve("маме", refs).hits.single().display)
        assertEquals(NoaMatch.Kind.MANY, NoaMatch.resolve("Илье", refs).kind)
        assertEquals(NoaMatch.Kind.NONE, NoaMatch.resolve("Сергей", refs).kind)
    }

    @Test fun matcherFuzzyForSpeechErrors() {
        assertEquals("Илья Рыков", NoaMatch.resolve("ильерикову", refs).hits.single().display)
        assertEquals("Мария Фролова", NoaMatch.resolve("Мориа", refs).hits.single().display)
    }

    @Test fun phraseNameFinderIsStrict() {
        assertEquals("Олег Петренко", NoaMatch.fromPhrase("позвони Олегу сегодня", refs)!!.display)
        assertNull(NoaMatch.fromPhrase("что у меня сегодня", refs))
        assertNull(NoaMatch.fromPhrase("позвони Илье", refs)) // неоднозначно
        assertEquals("Илья Ким", NoaMatch.fromPhrase("позвони Илье Киму", refs)!!.display)
        assertTrue(NoaMatch.mentions("позвони Тарасу", "Тарас"))
        assertFalse(NoaMatch.mentions("позвони Олегу", "Тарас"))
    }

    // --- уточняющие вопросы и ответы ---

    private fun ask(raw: String, user: String, people: List<String> = names, hasLast: Boolean = false): Clarify =
        ip.fromJson(raw, user, now, people, hasLast)!!.ask!!

    private fun answer(c: Clarify, text: String, people: List<String> = names) = runBlocking { ip.answer(c, text, now, people) }

    @Test fun answerFillsPerson() {
        val c = ask("""{"actions":[{"action":"message","text":"опоздаю"}]}""", "напиши что опоздаю")
        assertEquals("Кому написать?", c.question)
        assertEquals("Msg(Анна Иванова,WHATSAPP,опоздаю)", show(answer(c, "Ане")!!.intent!!))
        assertEquals("Msg(Олег Петренко,WHATSAPP,опоздаю)", show(answer(c, "для Олега")!!.intent!!))
        assertEquals("Msg(Мария Фролова,WHATSAPP,опоздаю)", show(answer(c, "Марии Фроловой")!!.intent!!))
    }

    @Test fun answerKeepsRestOfChain() {
        val c = ask("""{"actions":[{"action":"call"},{"action":"go_home"}]}""", "позвони и сверни")
        assertEquals("Call(Олег Петренко) > GoHome", show(answer(c, "Олегу")!!.intent!!))
    }

    @Test fun answerChoosesAmongCandidates() {
        val c = ask("""{"actions":[{"action":"call","person":"Илья"}]}""", "позвони Илье")
        assertEquals(listOf("Илья Рыков", "Илья Ким"), c.options)
        assertEquals("Call(Илья Ким)", show(answer(c, "Ким")!!.intent!!))
        assertEquals("Call(Илья Ким)", show(answer(c, "второй")!!.intent!!))
        assertEquals("Call(Илья Рыков)", show(answer(c, "первый")!!.intent!!))
        assertEquals("Call(Илья Рыков)", show(answer(c, "Рыкову")!!.intent!!))
        assertEquals("Call(Илья Ким)", show(answer(c, "второго")!!.intent!!))
    }

    @Test fun answerStillAmbiguousAsksOnceMoreThenGivesUp() {
        val c = ask("""{"actions":[{"action":"call","person":"Илья"}]}""", "позвони Илье")
        val again = answer(c, "Илья")!!
        assertNotNull(again.ask); assertEquals(2, again.ask!!.round)
        assertNull(answer(again.ask!!, "Илья"))
    }

    @Test fun answerFillsTimeAndDuration() {
        val alarm = ask("""{"actions":[{"action":"alarm"}]}""", "поставь будильник")
        assertEquals("Alarm(7:30)", show(answer(alarm, "в 7:30")!!.intent!!))
        assertEquals("Alarm(6:00)", show(answer(alarm, "на 6 утра")!!.intent!!))
        val timer = ask("""{"actions":[{"action":"timer"}]}""", "поставь таймер")
        assertEquals("Timer(300)", show(answer(timer, "5 минут")!!.intent!!))
        assertEquals("Timer(600)", show(answer(timer, "10")!!.intent!!))
        assertEquals("Timer(1800)", show(answer(timer, "полчаса")!!.intent!!))
    }

    @Test fun answerFillsTextQueryAndApp() {
        val reply = ask("""{"actions":[{"action":"reply","person":"Олег"}]}""", "ответь Олегу")
        assertEquals("Reply(Олег Петренко,буду в семь)", show(answer(reply, "буду в семь")!!.intent!!))
        val find = ask("""{"actions":[{"action":"find"}]}""", "найди")
        assertEquals("Find(клиенты из Киева)", show(answer(find, "клиенты из Киева")!!.intent!!))
        val app = ask("""{"actions":[{"action":"launch_app"}]}""", "запусти")
        assertEquals("LaunchApp(name=youtube)", show(answer(app, "youtube")!!.intent!!))
        val route = ask("""{"actions":[{"action":"route"}]}""", "веди")
        assertEquals("Route(,null,Крещатик 22,null)", show(answer(route, "Крещатик 22")!!.intent!!))
    }

    @Test fun answerNoCancels() {
        val c = ask("""{"actions":[{"action":"call"}]}""", "позвони")
        val r = answer(c, "нет, отмена")!!
        assertNull(r.intent); assertEquals("Хорошо, отменила.", r.reply)
    }

    @Test fun unfillableAnswerGoesToModelWithPendingContext() {
        val c = ask("""{"actions":[{"action":"call"}]}""", "позвони")
        var prompt = ""
        ip.askOverride = { p -> prompt = p; """{"actions":[{"action":"call","person":"Мама"}],"reply":"Звоню маме"}""" }
        val r = answer(c, "ну той, що завжди відповідає по ночах")!!
        assertEquals("Call(Мама)", show(r.intent!!))
        assertTrue(prompt.contains("Earlier:\nUser: позвони\nNoa asked: Кому позвонить?"))
        assertTrue(prompt.contains("ну той, що завжди"))
    }

    @Test fun freeQuestionAnswerGoesToModel() {
        val c = ip.fromJson("""{"actions":[],"ask":"В какое время?"}""", "запиши Марию на завтра", now, names)!!.ask!!
        assertNull(c.need)
        ip.askOverride = { """{"actions":[{"action":"create_appointment","person":"Мария"}]}""" }
        val r = answer(c, "в 15:00")!!
        assertEquals("Appt(Мария Фролова,2026-10-05T15:00,null)", show(r.intent!!))
    }

    // --- модель целиком (подмена ответа) ---

    @Test fun interpretEndToEndWithCannedBrain() {
        ip.askOverride = { """{"actions":[{"action":"call","person":"Ане"}],"reply":"Звоню"}""" }
        val r = runBlocking { ip.interpret("позвони Ане", now, names, "Data line", "") }!!
        assertEquals("Call(Анна Иванова) |r:Звоню", show(r))
    }

    @Test fun silentOrBrokenBrainGivesNull() {
        ip.askOverride = { null }
        assertNull(runBlocking { ip.interpret("позвони Ане", now, names) })
        ip.askOverride = { "бла-бла" }
        assertNull(runBlocking { ip.interpret("позвони Ане", now, names) })
        assertNull(NoaInterpreter(null).let { runBlocking { it.interpret("позвони", now) } })
    }

    @Test fun multiAskPendingCarriesPhraseForDates() {
        val c = ask("""{"actions":[{"action":"create_appointment"}]}""", "запиши на завтра в 15:00")
        assertEquals("Appt(Мария Фролова,2026-10-05T15:00,null)", show(answer(c, "Марию")!!.intent!!))
    }

    // --- подсказки при неудаче ---

    @Test fun fallbackSuggestionsFollowTopic() {
        assertTrue(NoaFallback.suggestions("запиши как-нибудь клиента").first.startsWith("Запиши"))
        assertTrue(NoaFallback.suggestions("напиши там что-то").first.startsWith("Напиши"))
        assertTrue(NoaFallback.suggestions("набери кого-то").first.startsWith("Позвони"))
        assertTrue(NoaFallback.suggestions("поставь будильник").first.startsWith("Поставь таймер"))
        assertEquals("Что у меня сегодня?", NoaFallback.suggestions("абракадабра").first)
        val m = NoaFallback.message("запиши кого-то")
        assertTrue(m, m.startsWith("Не поняла, скажите иначе — например: «") && m.contains("» или «"))
        assertNotNull(NoaFallback.suggestions("").second)
    }

    // --- бюджет промпта ---

    @Test fun staticPromptSizeAndShape() {
        val p = NoaInterpreter.staticPrompt("Ukrainian")
        println("static prompt = ${p.length} chars")
        assertTrue("${p.length}", p.length < 2000)
        assertTrue(p.contains("Ukrainian"))
        // порядок: схема → правила → примеры (последними)
        assertTrue(p.indexOf("JSON only") < p.indexOf("Rules:") && p.indexOf("Rules:") < p.indexOf("→ {\"actions\""))
        for (w in listOf("not in the book", "never invent", "ask")) assertTrue(w, p.contains(w))
        assertTrue("8–10 примеров", p.lines().count { it.contains(" → {") } in 8..10)
        assertTrue(p.lines().count { it.contains(" → {") && Regex("[іїє]").containsMatchIn(it.substringBefore(" → ")) } >= 2)
    }

    @Test fun promptStaysInsideBudgetEvenWithHugeInputs() {
        val bigNames = (1..300).map { "Имя$it Фамилия$it" }
        val bigData = (1..60).joinToString("\n") { "Строка данных номер $it с довольно длинным описанием события" }
        val hist = "User: " + "длинная реплика ".repeat(40) + "\nNoa: " + "ответ ".repeat(60)
        val user = "очень длинная фраза пользователя ".repeat(40)
        for (lang in listOf("позвони маме", user, "що в мене завтра")) {
            val p = ip.prompt(lang, now, bigNames, bigData, hist, null)
            println("prompt(${lang.length}) = ${p.length}")
            assertTrue("${p.length} > ${NoaContext.TOTAL_CHARS + 1}", p.length <= NoaContext.TOTAL_CHARS + 1)
            assertTrue(p.endsWith("JSON:")); assertTrue(p.contains("Command: \""))
        }
        val pending = Clarify("Кому написать?", null, emptyList(), 0, emptyList(), "напиши что опоздаю")
        val p = ip.prompt("Ане", now, bigNames, bigData, hist, pending)
        assertTrue(p.length <= NoaContext.TOTAL_CHARS + 1)
        assertTrue("вопрос и прежняя фраза не отрезаются", p.contains("Noa asked: Кому написать?") && p.contains("User: напиши что опоздаю"))
    }

    @Test fun promptKeepsMostImportantPartsWhenTight() {
        val data = "Анна Иванова (Аня) · клиент\nlast contact 28.09\n" + "очень длинная строка ".repeat(60)
        val p = ip.prompt("что с Аней", now, listOf("Анна Иванова (Аня)", "Олег"), data, "", null)
        assertTrue(p.length <= NoaContext.TOTAL_CHARS + 1)
        assertTrue(p.contains("Data:\nАнна Иванова (Аня) · клиент"))
        assertTrue(p.contains("Command: \"что с Аней\""))
        assertTrue(p.contains("People: Анна Иванова (Аня)"))
    }

    @Test fun fitDropsLowPriorityFirstAndKeepsOrder() {
        val b = listOf(
            NoaContext.Block("A".repeat(50), 3), NoaContext.Block("B".repeat(50), 1), NoaContext.Block("C".repeat(50), 2),
        )
        // влезает два самых важных (B, C) — порядок вывода как во входном списке
        assertEquals("B".repeat(50) + "\n" + "C".repeat(50), NoaContext.fit(b, 105))
        assertEquals("", NoaContext.fit(b, 10))
        assertEquals(b.joinToString("\n") { it.text }, NoaContext.fit(b, 1000))
    }

    @Test fun fitTrimsLowPriorityBlockAtLineBoundary() {
        val lines = (1..10).joinToString("\n") { "строка номер $it" }
        val out = NoaContext.fit(listOf(NoaContext.Block("важное", 1), NoaContext.Block(lines, 2)), 80)
        assertTrue(out.length <= 80)
        assertTrue(out.startsWith("важное\nстрока номер 1"))
        assertFalse("обрезка по строке, не посреди слова", out.endsWith("строк") || out.endsWith("номе"))
        assertTrue(out.lines().all { it == "важное" || Regex("строка номер \\d+").matches(it) })
    }

    @Test fun fitHonoursBlockMax() {
        val out = NoaContext.fit(listOf(NoaContext.Block("x, ".repeat(100), 1, max = 40)), 1000)
        assertTrue(out.length <= 40)
    }

    @Test fun commandIsCappedAndQuotesEscaped() {
        val p = ip.prompt("скажи \"привет\" " + "я".repeat(500), now, emptyList(), "", "", null)
        assertTrue(p.substringAfter("Command: \"").substringBefore("\"\nJSON:").length <= NoaContext.COMMAND_MAX)
        assertFalse(p.substringAfter("Command: \"").substringBefore("\"\nJSON:").contains('"'))
    }

    @Test fun mentionedPeopleGoFirstInPrompt() {
        val many = (1..40).map { "Человек$it Тестов" } + "Олег Петренко"
        val p = ip.prompt("позвони Олегу", now, many, "", "", null)
        assertTrue(p.contains("People: Олег Петренко"))
    }

    @Test fun replyLanguageFollowsPhrase() {
        assertEquals("Ukrainian", NoaInterpreter.langFor("що в мене завтра"))
        assertEquals("Russian", NoaInterpreter.langFor("что у меня завтра"))
        assertTrue(ip.prompt("Що в мене завтра", now, emptyList(), "", "", null).contains("Ukrainian, max 2 sentences"))
    }

    @Test fun durationParser() {
        val f = { s: String -> ip.durationSecs(s) }
        assertEquals(300, f("на 5 минут")); assertEquals(5400, f("1 час 30 минут")); assertEquals(1800, f("полчаса"))
        assertEquals(120, f("две минуты")); assertEquals(30, f("30 секунд")); assertEquals(3600, f("на час")); assertNull(f("привет"))
    }

    @Test fun mediaControlAndPlayUnchanged() {
        assertEquals(NoaIntent.Media(NoaMedia.Control.PAUSE), ip.fromJson("""{"action":"media","control":"pause"}""", "x", now)!!.intent)
    }
}
