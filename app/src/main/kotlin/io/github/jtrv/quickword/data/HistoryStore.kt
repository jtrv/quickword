package io.github.jtrv.quickword.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HistoryEntry(
    val word: String,
    val favourite: Boolean,
)

/**
 * App-owned lookup history + favourites. Separate DB from the read-only
 * dictionary so dictionary swaps never migrate user data (PLAN.md).
 * ponytail: SQLiteOpenHelper over Room — one table, three queries.
 */
class HistoryStore(
    context: Context,
) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE history(
              word TEXT PRIMARY KEY,
              last_at INTEGER NOT NULL,
              favourite INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) = Unit

    suspend fun recordLookup(word: String) =
        withContext(Dispatchers.IO) {
            writableDatabase.execSQL(
                "INSERT INTO history(word, last_at) VALUES(?, ?) " +
                    "ON CONFLICT(word) DO UPDATE SET last_at = excluded.last_at",
                arrayOf<Any>(word, System.currentTimeMillis()),
            )
        }

    suspend fun setFavourite(
        word: String,
        favourite: Boolean,
    ) = withContext(Dispatchers.IO) {
        val values =
            ContentValues().apply {
                put("word", word)
                put("last_at", System.currentTimeMillis())
                put("favourite", if (favourite) 1 else 0)
            }
        writableDatabase.insertWithOnConflict(
            "history",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    suspend fun isFavourite(word: String): Boolean =
        withContext(Dispatchers.IO) {
            readableDatabase
                .rawQuery("SELECT favourite FROM history WHERE word = ?", arrayOf(word))
                .use { it.moveToFirst() && it.getInt(0) == 1 }
        }

    suspend fun recent(limit: Int = RECENT_LIMIT): List<HistoryEntry> =
        withContext(Dispatchers.IO) {
            readableDatabase
                .rawQuery(
                    "SELECT word, favourite FROM history ORDER BY favourite DESC, last_at DESC LIMIT ?",
                    arrayOf(limit.toString()),
                ).use { c ->
                    buildList {
                        while (c.moveToNext()) add(HistoryEntry(c.getString(0), c.getInt(1) == 1))
                    }
                }
        }

    companion object {
        private const val DB_NAME = "history.db"
        private const val DB_VERSION = 1
        private const val RECENT_LIMIT = 30
    }
}
