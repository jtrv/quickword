package io.github.jtrv.quickword.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryDownloaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var server: HttpServer

    private fun gzippedFixture(): ByteArray {
        // The bundled fixture DB is a real ETL product — gzip it as the "release asset".
        val db = context.assets.open(DictionaryRepository.ASSET_PATH).readBytes()
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(db) }
        return out.toByteArray()
    }

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
        File(context.noBackupFilesDir, DictionaryDownloader.FULL_DB_NAME).delete()
    }

    private fun serve(
        path: String,
        body: ByteArray,
    ) {
        server.createContext(path) { exchange ->
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    @Test
    fun `download, gunzip, verify and swap in`() {
        serve("/db.gz", gzippedFixture())
        val downloader = DictionaryDownloader(context, minWords = 1)
        var lastProgress = 0f
        val result =
            runBlocking {
                downloader.download("http://localhost:${server.address.port}/db.gz") {
                    lastProgress = it
                }
            }
        assertTrue(result.exceptionOrNull()?.toString().orEmpty(), result.isSuccess)
        assertTrue(downloader.hasFullDictionary())
        assertTrue("progress callback should have fired", lastProgress > 0f)
        assertFalse(File(context.noBackupFilesDir, "${DictionaryDownloader.FULL_DB_NAME}.tmp").exists())
    }

    @Test
    fun `too-small dictionary is rejected by verification`() {
        serve("/db.gz", gzippedFixture())
        val downloader = DictionaryDownloader(context) // production MIN_WORDS
        val result =
            runBlocking { downloader.download("http://localhost:${server.address.port}/db.gz") }
        assertTrue(result.isFailure)
        assertFalse(downloader.hasFullDictionary())
    }

    @Test
    fun `corrupt payload fails without leaving artifacts`() {
        serve("/bad.gz", byteArrayOf(1, 2, 3, 4))
        val downloader = DictionaryDownloader(context)
        val result =
            runBlocking { downloader.download("http://localhost:${server.address.port}/bad.gz") }
        assertTrue(result.isFailure)
        assertFalse(downloader.hasFullDictionary())
    }

    @Test
    fun `http error fails cleanly`() {
        val downloader = DictionaryDownloader(context)
        val result =
            runBlocking { downloader.download("http://localhost:${server.address.port}/missing") }
        assertTrue(result.isFailure)
        assertFalse(downloader.hasFullDictionary())
    }
}
