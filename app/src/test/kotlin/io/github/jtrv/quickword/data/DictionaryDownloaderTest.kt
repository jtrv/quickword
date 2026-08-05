package io.github.jtrv.quickword.data

import android.app.DownloadManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * DownloadManager owns the transfer, so what is left to test is what we do with
 * the bytes: a downloaded archive becomes the dictionary only if it gunzips,
 * opens as SQLite and holds a plausible number of words — and a failure at any
 * of those steps leaves nothing behind to be mistaken for a dictionary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryDownloaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun gzFile(body: ByteArray): File =
        File.createTempFile("quickword", ".gz").apply {
            GZIPOutputStream(outputStream()).use { it.write(body) }
        }

    /** The bundled fixture DB is a real ETL product — gzip it as the "release asset". */
    private fun fixtureGz(): File = gzFile(context.assets.open(DictionaryRepository.ASSET_PATH).readBytes())

    @After
    fun cleanUp() {
        File(context.noBackupFilesDir, DictionaryDownloader.FULL_DB_NAME).delete()
        DictionaryDownloader(context).cancel()
    }

    @Test
    fun `gunzip, verify and swap in`() {
        val downloader = DictionaryDownloader(context, minWords = 1)
        val archive = fixtureGz()

        val result = runBlocking { downloader.installFrom(archive) }

        assertTrue(result.exceptionOrNull()?.toString().orEmpty(), result.isSuccess)
        assertTrue(downloader.hasFullDictionary)
        assertTrue(downloader.fullDictionaryBytes > 0)
        assertFalse("archive should be reclaimed once installed", archive.exists())
        assertFalse(File(context.noBackupFilesDir, "${DictionaryDownloader.FULL_DB_NAME}.tmp").exists())
    }

    @Test
    fun `too-small dictionary is rejected by verification`() {
        val downloader = DictionaryDownloader(context) // production MIN_WORDS

        val result = runBlocking { downloader.installFrom(fixtureGz()) }

        assertTrue(result.isFailure)
        assertFalse(downloader.hasFullDictionary)
    }

    @Test
    fun `corrupt payload fails without leaving artifacts`() {
        val corrupt = File.createTempFile("quickword", ".gz").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val downloader = DictionaryDownloader(context)

        val result = runBlocking { downloader.installFrom(corrupt) }

        assertTrue(result.isFailure)
        assertFalse(downloader.hasFullDictionary)
        assertFalse(File(context.noBackupFilesDir, "${DictionaryDownloader.FULL_DB_NAME}.tmp").exists())
    }

    @Test
    fun `not a dictionary at all is rejected`() {
        val downloader = DictionaryDownloader(context, minWords = 1)

        val result = runBlocking { downloader.installFrom(gzFile("this is not sqlite".toByteArray())) }

        assertTrue(result.isFailure)
        assertFalse(downloader.hasFullDictionary)
    }

    @Test
    fun `install without a queued download fails instead of throwing`() {
        val result = runBlocking { DictionaryDownloader(context).install() }

        assertTrue(result.isFailure)
    }

    @Test
    fun `enqueue queues exactly one download and replaces its predecessor`() {
        val downloader = DictionaryDownloader(context)
        val manager = context.getSystemService(DownloadManager::class.java)
        assertEquals(DictionaryDownloader.State.Absent, downloader.state())

        downloader.enqueue(allowMetered = false)
        downloader.enqueue(allowMetered = true)

        // A retry must not leave the previous transfer running alongside it —
        // two live downloads of 120 MB is the failure mode worth guarding.
        manager.query(DownloadManager.Query()).use { assertEquals(1, it.count) }
    }
}
