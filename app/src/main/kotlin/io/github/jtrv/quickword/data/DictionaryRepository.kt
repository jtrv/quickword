package io.github.jtrv.quickword.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Sense(
    val senseNo: Int,
    val gloss: String,
    val example: String?,
)

data class WordEntry(
    val id: Long,
    val word: String,
    val pos: String,
    val ipa: String?,
    val senses: List<Sense>,
    val synonyms: List<String>,
)

data class Suggestion(
    val word: String,
    val firstGloss: String,
)

/**
 * Read-only access to the prebuilt dictionary DB (schema: etl/build_db.py).
 * Plain SQLite by design — see PLAN.md M1 deviation note.
 */
class DictionaryRepository(
    private val context: Context,
) {
    @Volatile private var db: SQLiteDatabase? = null

    private fun open(): SQLiteDatabase =
        db ?: synchronized(this) {
            db ?: SQLiteDatabase
                .openDatabase(resolveDb().path, null, SQLiteDatabase.OPEN_READONLY)
                .also { db = it }
        }

    // Full downloaded dictionary wins; bundled fixture is the fallback tier.
    private fun resolveDb(): File {
        val full = File(context.noBackupFilesDir, DictionaryDownloader.FULL_DB_NAME)
        return if (full.exists()) full else ensureExtracted()
    }

    /** Close so the next query re-resolves (called after a full-DB download). */
    fun reopen() {
        synchronized(this) {
            db?.close()
            db = null
        }
    }

    // The asset is the source of truth; re-extract when its size changes
    // (cheap proxy for a new dictionary version — real versioning at M6).
    private fun ensureExtracted(): File {
        val target = File(context.noBackupFilesDir, DB_FILE_NAME)
        val assetSize = context.assets.openFd(ASSET_PATH).use { it.length }
        if (!target.exists() || target.length() != assetSize) {
            context.assets.open(ASSET_PATH).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        return target
    }

    suspend fun suggest(
        prefix: String,
        limit: Int = 20,
    ): List<Suggestion> =
        withContext(Dispatchers.IO) {
            if (prefix.isBlank()) return@withContext emptyList()
            val escaped =
                prefix
                    .trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_")
            open()
                .rawQuery(
                    """
                    SELECT w.word, MIN(s.gloss)
                    FROM words w JOIN senses s ON s.word_id = w.id AND s.sense_no = 1
                    WHERE w.word LIKE ? ESCAPE '\'
                    GROUP BY w.word
                    ORDER BY LENGTH(w.word), w.word
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf("$escaped%", limit.toString()),
                ).use { c ->
                    buildList {
                        while (c.moveToNext()) add(Suggestion(c.getString(0), c.getString(1)))
                    }
                }
        }

    /** All entries (one per part of speech) for an exact word, case-insensitive. */
    suspend fun entriesFor(word: String): List<WordEntry> =
        withContext(Dispatchers.IO) {
            val database = open()
            val entries =
                database
                    .rawQuery(
                        "SELECT id, word, pos, ipa FROM words WHERE word = ? COLLATE NOCASE ORDER BY id",
                        arrayOf(word),
                    ).use { c ->
                        val ipaCol = c.getColumnIndexOrThrow("ipa")
                        buildList {
                            while (c.moveToNext()) {
                                add(
                                    WordEntry(
                                        id = c.getLong(0),
                                        word = c.getString(1),
                                        pos = c.getString(2),
                                        ipa = if (c.isNull(ipaCol)) null else c.getString(ipaCol),
                                        senses = emptyList(),
                                        synonyms = emptyList(),
                                    ),
                                )
                            }
                        }
                    }
            entries.map { entry ->
                entry.copy(
                    senses =
                        database
                            .rawQuery(
                                "SELECT sense_no, gloss, example FROM senses WHERE word_id = ? ORDER BY sense_no",
                                arrayOf(entry.id.toString()),
                            ).use { c ->
                                buildList {
                                    while (c.moveToNext()) {
                                        val example = if (c.isNull(2)) null else c.getString(2)
                                        add(Sense(c.getInt(0), c.getString(1), example))
                                    }
                                }
                            },
                    synonyms =
                        database
                            .rawQuery(
                                "SELECT synonym FROM synonyms WHERE word_id = ? ORDER BY synonym",
                                arrayOf(entry.id.toString()),
                            ).use { c ->
                                buildList { while (c.moveToNext()) add(c.getString(0)) }
                            },
                )
            }
        }

    /** First candidate (see [io.github.jtrv.quickword.lookup.lookupCandidates]) with entries. */
    suspend fun lookup(candidates: List<String>): List<WordEntry> {
        for (candidate in candidates) {
            val entries = entriesFor(candidate)
            if (entries.isNotEmpty()) return entries
        }
        return emptyList()
    }

    companion object {
        const val ASSET_PATH = "dictionary/quickword-en.db"
        const val DB_FILE_NAME = "quickword-en.db"
    }
}
