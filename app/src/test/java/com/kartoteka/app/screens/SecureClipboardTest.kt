package com.kartoteka.app.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.test.core.app.ApplicationProvider
import com.kartoteka.app.security.SecureClipboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class SecureClipboardTest {
    private val context get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val cm get() = context.getSystemService(ClipboardManager::class.java)

    private fun clipText() = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()

    @Test fun copiedNumberIsSensitiveAndDisappearsAfter30Seconds() {
        SecureClipboard.copy(context, "+380671234567")
        assertEquals("+380671234567", clipText())
        assertTrue(cm.primaryClip!!.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(29))
        assertEquals("+380671234567", clipText())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertFalse(cm.hasPrimaryClip() && clipText().isNotEmpty())
    }

    @Test fun somethingCopiedLaterIsNotErased() {
        SecureClipboard.copy(context, "+380671234567")
        cm.setPrimaryClip(ClipData.newPlainText("other", "список покупок"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertEquals("список покупок", clipText())
    }
}
