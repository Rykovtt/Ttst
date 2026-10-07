package com.kartoteka.app.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.kartoteka.app.data.ServiceTemplate
import com.kartoteka.app.ui.services.ServiceEditor
import com.kartoteka.app.ui.theme.KartotekaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Редактор услуги: кнопка «Сохранить» на экране, поле названия редактируется и сохраняется. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "ru-w400dp-h860dp-xxhdpi")
class ServiceEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun saveButtonVisibleAndSaves() {
        var saved: ServiceTemplate? = null
        compose.setContent {
            KartotekaTheme { ServiceEditor(ServiceTemplate(id = 1, name = "Тату", durationMin = 240), {}, { saved = it }, {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Сохранить").assertIsDisplayed()
        compose.onAllNodesWithText("Тату")[0].performTextReplacement("Тату-сеанс")
        compose.waitForIdle()
        compose.onNodeWithText("Сохранить").assertIsDisplayed()
        @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
        com.github.takahirom.roborazzi.captureScreenRoboImage("screenshots/40_service_editor.png")
        compose.onNodeWithText("Сохранить").performClick()
        assertEquals("Тату-сеанс", saved?.name)
    }
}
