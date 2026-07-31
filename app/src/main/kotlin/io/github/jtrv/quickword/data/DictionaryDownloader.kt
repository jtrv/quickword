package io.github.jtrv.quickword.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * First-run download of the full dictionary (M6). Streams the gzipped release
 * asset, verifies it is a sane dictionary, then atomically swaps it in.
 * The bundled fixture keeps the app working before/without it.
 */
class DictionaryDownloader(
    private val context: Context,
    private val minWords: Long = MIN_WORDS, // injectable so tests cover the success path
) {
    /** @param onProgress fraction of compressed bytes read, 0..1, or -1 when length unknown. */
    suspend fun download(
        url: String = RELEASE_URL,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(context.noBackupFilesDir, FULL_DB_NAME)
                val tmp = File(context.noBackupFilesDir, "$FULL_DB_NAME.tmp")
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.instanceFollowRedirects = true
                    check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                        "HTTP ${connection.responseCode} for $url"
                    }
                    val total = connection.contentLengthLong
                    var read = 0L
                    connection.inputStream.use { raw ->
                        val counting =
                            object : java.io.FilterInputStream(raw) {
                                override fun read(
                                    b: ByteArray,
                                    off: Int,
                                    len: Int,
                                ): Int =
                                    super.read(b, off, len).also {
                                        if (it > 0) {
                                            read += it
                                            onProgress(if (total > 0) read.toFloat() / total else -1f)
                                        }
                                    }
                            }
                        GZIPInputStream(counting).use { gz ->
                            tmp.outputStream().use { gz.copyTo(it) }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                verify(tmp)
                check(tmp.renameTo(target)) { "rename failed" }
            }.onFailure {
                File(context.noBackupFilesDir, "$FULL_DB_NAME.tmp").delete()
            }
        }

    fun hasFullDictionary(): Boolean = File(context.noBackupFilesDir, FULL_DB_NAME).exists()

    private fun verify(db: File) {
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use {
            val words =
                it
                    .rawQuery("SELECT value FROM meta WHERE key='words'", null)
                    .use { c -> if (c.moveToFirst()) c.getString(0).toLong() else 0L }
            check(words >= minWords) { "downloaded DB has only $words words" }
        }
    }

    companion object {
        const val FULL_DB_NAME = "quickword-en-full.db"
        const val RELEASE_URL =
            "https://github.com/jtrv/quickword/releases/download/db-en-v1/quickword-en.db.gz"
        private const val MIN_WORDS = 100_000L
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
