package io.github.jtrv.quickword.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.jtrv.quickword.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * One downloadable data set: the dictionary, or the offline Wikipedia corpus.
 *
 * Everything that used to be a constant is here, because it turned out the
 * downloader was single-corpus in four separate places — one preferences key,
 * one destination name, one installed-file check, and an `enqueue()` that
 * cancelled whatever was already running (PLAN.md refutation round 4). A second
 * download would have silently killed the first.
 */
data class Corpus(
    val id: String,
    val url: String,
    val fileName: String,
    /** Tables that must each hold at least [minRows] for the file to be believed. */
    val tables: List<String>,
    val minRows: Long,
) {
    val gzName: String get() = "$id.db.gz"
    val prefsName: String get() = "download_$id"

    companion object {
        val DICTIONARY =
            Corpus(
                id = "dictionary",
                url = "https://github.com/jtrv/quickword/releases/download/db-en-v1/quickword-en.db.gz",
                fileName = "quickword-en-full.db",
                tables = listOf("words", "senses"),
                minRows = 100_000,
            )
        val WIKIPEDIA =
            Corpus(
                id = "wikipedia",
                url = "https://github.com/jtrv/quickword/releases/download/wiki-en-top-v1/quickword-wiki.db.gz",
                fileName = WikiCorpus.CORPUS_NAME,
                tables = listOf("article"),
                minRows = 40_000,
            )
    }
}

/**
 * Downloads a [Corpus], handing the transfer to the platform's
 * [DownloadManager]: it resumes a dropped connection instead of restarting
 * hundreds of megabytes, honours a metered-network policy, and keeps going with
 * a system progress notification while the app is closed.
 *
 * What stays ours is everything after the bytes land — gunzip, verify the file
 * really is what it claims, swap it in atomically. That is [installFrom], and it
 * is where the tests live.
 */
class CorpusDownloader(
    private val context: Context,
    val corpus: Corpus,
    private val minRows: Long = corpus.minRows, // injectable so tests cover the success path
) {
    sealed interface State {
        /** Nothing installed, nothing queued. */
        data object Absent : State

        /** @param fraction 0..1, or -1 when the server sent no length. */
        data class Downloading(
            val fraction: Float,
            val waitingForNetwork: Boolean,
        ) : State

        /** Bytes are down; the gunzip-and-verify step has not run yet. */
        data object Ready : State

        data object Installed : State

        data object Failed : State
    }

    private val manager get() = context.getSystemService(DownloadManager::class.java)

    private val prefs get() = context.getSharedPreferences(corpus.prefsName, Context.MODE_PRIVATE)

    /** True when the active connection would bill the user for the transfer. */
    fun onMeteredNetwork(): Boolean = context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    fun enqueue(
        allowMetered: Boolean,
        url: String = corpus.url,
    ): Long {
        cancel() // one download per corpus; a retry replaces its predecessor
        val request =
            DownloadManager
                .Request(url.toUri())
                .setTitle(context.getString(R.string.dict_notification_title))
                .setAllowedOverMetered(allowMetered)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, null, corpus.gzName)
        return manager.enqueue(request).also { id -> prefs.edit { putLong(KEY_ID, id) } }
    }

    fun state(): State =
        when {
            // Reconcile first: install() renames before it clears the record, so
            // dying in that window would otherwise strand the archive on disk
            // with nothing left to ever delete it.
            isInstalled -> State.Installed.also { if (prefs.getLong(KEY_ID, NO_ID) != NO_ID) cancel() }
            else -> prefs.getLong(KEY_ID, NO_ID).let { id -> if (id == NO_ID) State.Absent else queryState(id) }
        }

    private fun queryState(id: Long): State =
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) {
                // The user cleared it from the system downloads UI.
                State.Absent
            } else {
                when (val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> State.Ready
                    DownloadManager.STATUS_FAILED -> State.Failed
                    else -> downloadingState(cursor, status)
                }
            }
        }

    /** Gunzip, verify and swap in the completed download. */
    suspend fun install(): Result<Unit> {
        val id = prefs.getLong(KEY_ID, NO_ID)
        val gz = if (id == NO_ID) null else downloadedFile(id)
        return if (gz == null) {
            Result.failure(IllegalStateException("no completed download to install"))
        } else {
            installFrom(gz).onSuccess {
                manager.remove(id)
                prefs.edit { remove(KEY_ID) }
            }
        }
    }

    /**
     * The part worth testing: a downloaded archive only becomes the corpus if it
     * gunzips, opens as SQLite, and really holds the rows it claims. The rename
     * is last so a half-written file is never visible as the real thing.
     */
    internal suspend fun installFrom(gz: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            val target = installedFile
            val tmp = File(context.noBackupFilesDir, "${corpus.fileName}.tmp")
            runCatching {
                GZIPInputStream(gz.inputStream().buffered()).use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                verify(tmp, corpus.tables, minRows)
                check(tmp.renameTo(target)) { "rename failed" }
                gz.delete()
                Unit
            }.onFailure { tmp.delete() }
        }

    fun cancel() {
        val id = prefs.getLong(KEY_ID, NO_ID)
        if (id != NO_ID) manager.remove(id)
        prefs.edit { remove(KEY_ID) }
    }

    private val installedFile get() = File(context.noBackupFilesDir, corpus.fileName)

    val isInstalled: Boolean get() = installedFile.exists()

    /** Bytes on disk, 0 when this corpus is not installed. */
    val installedBytes: Long get() = installedFile.length()

    fun removeInstalled(): Boolean = installedFile.delete()

    private fun downloadedFile(id: Long): File? =
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                cursor
                    .getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    ?.let { it.toUri().path }
                    ?.let(::File)
            }
        }

    companion object {
        const val UNKNOWN_PROGRESS = -1f
        private const val KEY_ID = "id"
        private const val NO_ID = -1L
    }
}

private val WIFI_WAIT_REASONS =
    setOf(DownloadManager.PAUSED_WAITING_FOR_NETWORK, DownloadManager.PAUSED_QUEUED_FOR_WIFI)

private fun downloadingState(
    cursor: Cursor,
    status: Int,
): CorpusDownloader.State.Downloading {
    val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
    return CorpusDownloader.State.Downloading(
        fraction = if (total > 0) soFar.toFloat() / total else CorpusDownloader.UNKNOWN_PROGRESS,
        // Distinct from "slow": the user asked for Wi-Fi only and there is none,
        // so nothing happens until one of those two facts changes. QUEUED_FOR_WIFI
        // is the one a large transfer actually hits.
        waitingForNetwork = status == DownloadManager.STATUS_PAUSED && reason in WIFI_WAIT_REASONS,
    )
}

/**
 * A file is the corpus only if it opens as SQLite and really contains the rows.
 * Counted from the tables rather than read out of `meta`, because `meta` is a
 * claim the file makes about itself.
 */
private fun verify(
    db: File,
    tables: List<String>,
    minRows: Long,
) {
    SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
        tables.forEach { table ->
            val rows = database.countRows(table)
            check(rows >= minRows) { "downloaded DB has only $rows rows in $table" }
        }
    }
}

private fun SQLiteDatabase.countRows(table: String): Long =
    rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
