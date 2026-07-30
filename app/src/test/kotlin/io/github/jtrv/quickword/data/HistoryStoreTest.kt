package io.github.jtrv.quickword.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryStoreTest {
    private val store = HistoryStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `lookups dedupe by word and favourites sort first`() =
        runBlocking {
            store.recordLookup("petrichor")
            store.recordLookup("quick")
            store.recordLookup("petrichor")
            store.setFavourite("dog", true)
            val recent = store.recent()
            assertEquals(3, recent.size)
            assertEquals("dog", recent.first().word)
            assertTrue(recent.first().favourite)
        }

    @Test
    fun `favourite toggles off`() =
        runBlocking {
            store.setFavourite("dog", true)
            store.setFavourite("dog", false)
            assertFalse(store.isFavourite("dog"))
        }
}
