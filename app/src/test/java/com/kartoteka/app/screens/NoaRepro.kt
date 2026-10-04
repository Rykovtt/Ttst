package com.kartoteka.app.screens

import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.assistant.Noa
import com.kartoteka.app.assistant.NoaParser
import com.kartoteka.app.i18n.I18n
import com.kartoteka.app.i18n.UiLang
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NoaRepro {
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()

    @Test fun reproduceDeviceCommands() = runBlocking {
        I18n.init(app, UiLang.UK)
        for (cmd in listOf("Зайди в рассылки", "набери Илья рыков", "Открой контакты", "позвони маме")) {
            val r = runCatching { Noa(app).handle(cmd) }
            println("CMD[$cmd] -> " + r.fold({ it.toString() }, { "THROW: ${it::class.simpleName}: ${it.message}" }))
            r.exceptionOrNull()?.printStackTrace()
        }
    }
}
