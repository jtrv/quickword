package io.github.jtrv.quickword.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.util.zip.Inflater

/**
 * Offline Wikipedia lead paragraphs (etl/build_wiki.py). Optional: absent until
 * the user downloads it, and the live [WikipediaApi] remains the fallback's
 * fallback.
 *
 * Rows are raw-DEFLATE compressed against one preset dictionary shared by the
 * whole corpus, which is why a million-word archive fits in tens of megabytes
 * while every row stays independently decodable.
 */
class WikiCorpus(
    private val context: Context,
) {
    @Volatile private var db: SQLiteDatabase? = null

    @Volatile private var zdict: ByteArray? = null

    val isInstalled: Boolean get() = corpusFile.exists()

    private val corpusFile get() = File(context.noBackupFilesDir, CORPUS_NAME)

    /** Drop the handle so the next lookup re-resolves (after download or removal). */
    fun reopen() {
        synchronized(this) {
            db?.close()
            db = null
            zdict = null
        }
    }

    fun remove(): Boolean {
        reopen()
        return corpusFile.delete()
    }

    val bytes: Long get() = corpusFile.length()

    private fun open(): SQLiteDatabase? =
        db ?: synchronized(this) {
            db ?: runCatching {
                SQLiteDatabase
                    .openDatabase(corpusFile.path, null, SQLiteDatabase.OPEN_READONLY)
                    .also { opened ->
                        zdict = opened.readDictionary()
                        db = opened
                    }
            }.getOrNull()
        }

    private fun SQLiteDatabase.readDictionary(): ByteArray =
        rawQuery("SELECT value FROM meta WHERE key = 'zdict'", null).use { cursor ->
            if (cursor.moveToFirst()) Base64.decode(cursor.getString(0), Base64.DEFAULT) else ByteArray(0)
        }

    suspend fun summary(title: String): WikiSummary? =
        withContext(Dispatchers.IO) {
            val database = open() ?: return@withContext null
            val dictionary = zdict ?: return@withContext null
            val row = findArticle(database, title) ?: return@withContext null
            val (found, blob) = row
            WikiSummary(
                title = found,
                extract = inflate(blob, dictionary) ?: return@withContext null,
                pageUrl = ARTICLE_BASE + URLEncoder.encode(found.replace(' ', '_'), "UTF-8"),
            )
        }

    /**
     * Exact case wins before case-insensitive: "Ice Cube" and "ice cube" are
     * both articles, and so are "LaTeX" and "Latex". Aliases (Kiwix's redirects)
     * are tried last so a real article always beats a redirect to another one.
     */
    private fun findArticle(
        database: SQLiteDatabase,
        title: String,
    ): Pair<String, ByteArray>? {
        val direct =
            database.query(
                "SELECT title, intro FROM article WHERE title = ? " +
                    "UNION ALL SELECT title, intro FROM article WHERE title = ? COLLATE NOCASE " +
                    "LIMIT 1",
                arrayOf(title, title),
            )
        if (direct != null) return direct
        return database.query(
            "SELECT a.title, a.intro FROM alias x JOIN article a ON a.id = x.target " +
                "WHERE x.name = ? COLLATE NOCASE LIMIT 1",
            arrayOf(title),
        )
    }

    private fun SQLiteDatabase.query(
        sql: String,
        args: Array<String>,
    ): Pair<String, ByteArray>? =
        rawQuery(sql, args).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) to cursor.getBlob(1) else null
        }

    companion object {
        const val CORPUS_NAME = "quickword-wiki.db"
        private const val ARTICLE_BASE = "https://en.wikipedia.org/wiki/"
        private const val BUFFER = 8192

        /** Lead paragraphs compress to roughly a third; size the sink for that. */
        private const val INFLATE_GROWTH = 3
    }

    /**
     * Raw DEFLATE never reports [Inflater.needsDictionary], so the dictionary
     * has to be applied up front — waiting for the callback the documentation
     * describes fails with "invalid distance too far back" (PLAN.md round 4).
     */
    private fun inflate(
        blob: ByteArray,
        dictionary: ByteArray,
    ): String? =
        runCatching {
            val inflater = Inflater(true)
            try {
                inflater.setDictionary(dictionary)
                inflater.setInput(blob)
                val out = ByteArrayOutputStream(blob.size * INFLATE_GROWTH)
                val buffer = ByteArray(BUFFER)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    if (n == 0) break
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8").takeIf { it.isNotEmpty() }
            } finally {
                inflater.end()
            }
        }.getOrNull()
}
