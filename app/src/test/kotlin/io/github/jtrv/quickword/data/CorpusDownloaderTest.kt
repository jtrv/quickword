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
class CorpusDownloaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun gzFile(body: ByteArray): File =
        File.createTempFile("quickword", ".gz").apply {
            GZIPOutputStream(outputStream()).use { it.write(body) }
        }

    /** The bundled fixture DB is a real ETL product — gzip it as the "release asset". */
    private fun fixtureGz(): File = gzFile(context.assets.open(DictionaryRepository.ASSET_PATH).readBytes())

    @After
    fun cleanUp() {
        File(context.noBackupFilesDir, Corpus.DICTIONARY.fileName).delete()
        CorpusDownloader(context, Corpus.DICTIONARY).cancel()
    }

    @Test
    fun `gunzip, verify and swap in`() {
        val downloader = CorpusDownloader(context, Corpus.DICTIONARY, minRows = 1)
        val archive = fixtureGz()

        val result = runBlocking { downloader.installFrom(archive) }

        assertTrue(result.exceptionOrNull()?.toString().orEmpty(), result.isSuccess)
        assertTrue(downloader.isInstalled)
        assertTrue(downloader.installedBytes > 0)
        assertFalse("archive should be reclaimed once installed", archive.exists())
        assertFalse(File(context.noBackupFilesDir, "${Corpus.DICTIONARY.fileName}.tmp").exists())
    }

    @Test
    fun `too-small dictionary is rejected by verification`() {
        val downloader = CorpusDownloader(context, Corpus.DICTIONARY) // production MIN_WORDS

        val result = runBlocking { downloader.installFrom(fixtureGz()) }

        assertTrue(result.isFailure)
        assertFalse(downloader.isInstalled)
    }

    @Test
    fun `corrupt payload fails without leaving artifacts`() {
        val corrupt = File.createTempFile("quickword", ".gz").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val downloader = CorpusDownloader(context, Corpus.DICTIONARY)

        val result = runBlocking { downloader.installFrom(corrupt) }

        assertTrue(result.isFailure)
        assertFalse(downloader.isInstalled)
        assertFalse(File(context.noBackupFilesDir, "${Corpus.DICTIONARY.fileName}.tmp").exists())
    }

    @Test
    fun `not a dictionary at all is rejected`() {
        val downloader = CorpusDownloader(context, Corpus.DICTIONARY, minRows = 1)

        val result = runBlocking { downloader.installFrom(gzFile("this is not sqlite".toByteArray())) }

        assertTrue(result.isFailure)
        assertFalse(downloader.isInstalled)
    }

    @Test
    fun `install without a queued download fails instead of throwing`() {
        val result = runBlocking { CorpusDownloader(context, Corpus.DICTIONARY).install() }

        assertTrue(result.isFailure)
    }

    @Test
    fun `a download installed just before the process died is reconciled`() {
        val downloader = CorpusDownloader(context, Corpus.DICTIONARY)
        val manager = context.getSystemService(DownloadManager::class.java)
        downloader.enqueue(allowMetered = true)
        // install() renames the database before it clears the download record.
        // This is the process dying in that window.
        File(context.noBackupFilesDir, Corpus.DICTIONARY.fileName).writeBytes(byteArrayOf(1))

        assertEquals(CorpusDownloader.State.Installed, downloader.state())

        // Otherwise the ~120 MB archive sits there with nothing left to delete it.
        manager.query(DownloadManager.Query()).use { assertEquals(0, it.count) }
    }

    @Test
    fun `enqueue queues exactly one download and replaces its predecessor`() {
        val downloader = CorpusDownloader(context, Corpus.DICTIONARY)
        val manager = context.getSystemService(DownloadManager::class.java)
        assertEquals(CorpusDownloader.State.Absent, downloader.state())

        downloader.enqueue(allowMetered = false)
        downloader.enqueue(allowMetered = true)

        // A retry must not leave the previous transfer running alongside it —
        // two live downloads of 120 MB is the failure mode worth guarding.
        manager.query(DownloadManager.Query()).use { assertEquals(1, it.count) }
    }
}
