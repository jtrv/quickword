package io.github.jtrv.quickword.lookup

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The trampoline's contract: selection in → notification out, activity gone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProcessTextActivityTest {
    private val timeoutMs = 10_000L

    private fun notificationManager(): NotificationManager =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSystemService(NotificationManager::class.java)

    private fun launch(intent: Intent) {
        val controller = Robolectric.buildActivity(ProcessTextActivity::class.java, intent).setup()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (notificationManager().activeNotifications.isEmpty() &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue("trampoline must finish itself", controller.get().isFinishing)
    }

    @Test
    fun `process_text selection posts a definition notification`() {
        launch(
            Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "Petrichor!"),
        )
        val active = notificationManager().activeNotifications
        assertEquals(1, active.size)
        val extras = active.first().notification.extras
        assertEquals("petrichor · noun", extras.getCharSequence("android.title").toString())
        assertTrue(
            extras.getCharSequence("android.bigText").toString().contains("earthy smell"),
        )
    }

    @Test
    fun `phrase lookup hits multi-word entry, not first word`() {
        launch(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "ice cream"),
        )
        val title =
            notificationManager()
                .activeNotifications
                .first()
                .notification.extras
                .getCharSequence("android.title")
                .toString()
        assertEquals("ice cream · noun", title)
    }

    @Test
    fun `unknown word posts a no-entry notification`() {
        launch(
            Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "zzgrumblefritz"),
        )
        val extras =
            notificationManager()
                .activeNotifications
                .first()
                .notification.extras
        assertTrue(
            extras.getCharSequence("android.text").toString().contains("No entry"),
        )
    }
}
