package com.kartoteka.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.kartoteka.app.ui.components.drawOrb
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Сфера Ноа в нескольких фазах движения — для сверки с референсом. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w800dp-h300dp-xhdpi")
class OrbPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun orbPhases() {
        compose.setContent {
            Row(Modifier.background(Color(0xFF070914))) {
                listOf(0f, 3f, 7f, 12f).forEach { t ->
                    Canvas(Modifier.size(200.dp)) { drawOrb(t, if (t == 7f) 0.6f else 0f, Color(0xFFFFCB8E)) }
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/30_orb.png")
    }
}
