package io.github.jtrv.quickword.ui.word

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.jtrv.quickword.lookup.ProcessTextActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Selecting text on a word page and tapping QuickWord in the toolbar must open
 * the word in the app, not bounce through the trampoline into a notification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SelectableLookupTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val looked = mutableListOf<String>()
    private val context = InAppLookupContext(app) { looked += it }

    // The shape Compose's toolbar item builds for a PROCESS_TEXT activity.
    private fun processText(
        packageName: String,
        className: String,
    ) = Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .setClassName(packageName, className)
        .putExtra(Intent.EXTRA_PROCESS_TEXT, "serendipity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Test
    fun ownProcessTextOpensInPlace() {
        context.startActivity(processText(app.packageName, ProcessTextActivity::class.java.name))

        assertEquals(listOf("serendipity"), looked)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun otherAppsProcessTextStillLaunches() {
        context.startActivity(processText("com.example.translate", "com.example.translate.Translate"))

        assertTrue(looked.isEmpty())
        assertEquals("com.example.translate", shadowOf(app).nextStartedActivity.component?.packageName)
    }
}
