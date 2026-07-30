package io.github.jtrv.quickword.lookup

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.jtrv.quickword.data.Sense
import io.github.jtrv.quickword.data.WordEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LookupNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifier = LookupNotifier(context)

    private val entry =
        WordEntry(
            id = 1,
            word = "quick",
            pos = "adj",
            ipa = null,
            senses = listOf(Sense(1, "Moving with speed.", null)),
            synonyms = listOf("fast", "rapid", "swift"),
        )

    private fun posted() =
        context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .first()
            .notification

    @Test
    fun `entry with synonyms gets a Thesaurus action first`() {
        notifier.showEntries(listOf(entry))
        val actions = posted().actions.map { it.title.toString() }
        assertEquals(listOf("Thesaurus", "Open", "Share"), actions)
    }

    @Test
    fun `entry without synonyms gets no Thesaurus action`() {
        notifier.showEntries(listOf(entry.copy(synonyms = emptyList())))
        val actions = posted().actions.map { it.title.toString() }
        assertEquals(listOf("Open", "Share"), actions)
    }

    @Test
    fun `showSynonyms swaps content in place under the same id`() {
        notifier.showEntries(listOf(entry))
        notifier.showSynonyms("quick", listOf(entry))
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, manager.activeNotifications.size)
        val extras = posted().extras
        assertEquals("quick · synonyms", extras.getCharSequence("android.title").toString())
        assertTrue(extras.getCharSequence("android.bigText").toString().contains("fast · rapid · swift"))
    }
}
