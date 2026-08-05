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
 * First-run download of the full dictionary, handed to the platform's
 * [DownloadManager] rather than streamed by us.
 *
 * The transfer is ~120 MB, which makes the two things DownloadManager provides
 * for free the whole point: it resumes a dropped connection instead of starting
 * over, and it obeys a metered-network policy instead of quietly spending
 * someone's data plan. It also keeps downloading — with a system progress
 * notification — while the app is closed.
 *
 * What stays ours is everything after the bytes land: gunzip, verify the file
 * really is a dictionary, and swap it in atomically. That is [installFrom], and
 * it is where the tests live.
 */
class DictionaryDownloader(
    private val context: Context,
    private val minWords: Long = MIN_WORDS, // injectable so tests cover the success path
) {
    sealed interface State {
        /** Starter dictionary only, nothing queued. */
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

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when the active connection would bill the user for ~120 MB. */
    fun onMeteredNetwork(): Boolean = context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    fun enqueue(
        allowMetered: Boolean,
        url: String = RELEASE_URL,
    ): Long {
        cancel() // one download at a time; a retry replaces its predecessor
        val request =
            DownloadManager
                .Request(url.toUri())
                .setTitle(context.getString(R.string.dict_notification_title))
                .setAllowedOverMetered(allowMetered)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, null, GZ_NAME)
        return manager.enqueue(request).also { id -> prefs.edit { putLong(KEY_ID, id) } }
    }

    fun state(): State =
        when {
            hasFullDictionary -> State.Installed
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
     * The part worth testing: a downloaded archive only becomes the dictionary
     * if it gunzips, opens as SQLite, and holds a plausible number of words.
     * The rename is last so a half-written file is never visible as the DB.
     */
    internal suspend fun installFrom(gz: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            val target = File(context.noBackupFilesDir, FULL_DB_NAME)
            val tmp = File(context.noBackupFilesDir, "$FULL_DB_NAME.tmp")
            runCatching {
                GZIPInputStream(gz.inputStream().buffered()).use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                verify(tmp, minWords)
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

    private val fullDb get() = File(context.noBackupFilesDir, FULL_DB_NAME)

    val hasFullDictionary: Boolean get() = fullDb.exists()

    /** Bytes on disk, 0 when only the bundled starter dictionary is present. */
    val fullDictionaryBytes: Long get() = fullDb.length()

    /** Reclaim the download; the bundled starter keeps the app working. */
    fun removeFullDictionary(): Boolean = fullDb.delete()

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
        const val FULL_DB_NAME = "quickword-en-full.db"
        const val RELEASE_URL =
            "https://github.com/jtrv/quickword/releases/download/db-en-v1/quickword-en.db.gz"
        const val UNKNOWN_PROGRESS = -1f
        private const val GZ_NAME = "quickword-en.db.gz"
        private const val PREFS = "dictionary_download"
        private const val KEY_ID = "id"
        private const val NO_ID = -1L
        private const val MIN_WORDS = 100_000L
    }
}

private fun downloadingState(
    cursor: Cursor,
    status: Int,
): DictionaryDownloader.State.Downloading {
    val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
    return DictionaryDownloader.State.Downloading(
        fraction = if (total > 0) soFar.toFloat() / total else DictionaryDownloader.UNKNOWN_PROGRESS,
        // Distinct from "slow": the user asked for Wi-Fi only and there is none,
        // so nothing happens until one of those two facts changes.
        waitingForNetwork =
            status == DownloadManager.STATUS_PAUSED &&
                reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK,
    )
}

/** A file is the dictionary only if it opens as SQLite and holds enough words. */
private fun verify(
    db: File,
    minWords: Long,
) {
    SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use {
        val words =
            it
                .rawQuery("SELECT value FROM meta WHERE key='words'", null)
                .use { c -> if (c.moveToFirst()) c.getString(0).toLong() else 0L }
        check(words >= minWords) { "downloaded DB has only $words words" }
    }
}
