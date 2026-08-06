package io.github.jtrv.quickword.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.Deflater

/**
 * The corpus format is the part that can silently produce garbage: rows are raw
 * DEFLATE against a shared preset dictionary, and raw streams never announce
 * that they need one. This builds a corpus exactly the way etl/build_wiki.py
 * does and reads it back through the real class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikiCorpusTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val corpusFile get() = File(context.noBackupFilesDir, WikiCorpus.CORPUS_NAME)

    private val zdict = "the of and in a to is city capital largest article Wikipedia ".toByteArray()

    private fun deflate(text: String): ByteArray {
        val deflater = Deflater(9, true) // raw: no zlib header, mirrors the ETL
        deflater.setDictionary(zdict)
        deflater.setInput(text.toByteArray())
        deflater.finish()
        val out = ByteArray(4096)
        val n = deflater.deflate(out)
        deflater.end()
        return out.copyOf(n)
    }

    private fun writeCorpus(
        articles: List<Pair<String, String>>,
        aliases: List<Pair<String, Int>>,
    ) {
        corpusFile.delete()
        SQLiteDatabase.openOrCreateDatabase(corpusFile, null).use { db ->
            db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("CREATE TABLE article(id INTEGER PRIMARY KEY, title TEXT NOT NULL, intro BLOB NOT NULL)")
            db.execSQL("CREATE TABLE alias(name TEXT PRIMARY KEY, target INTEGER NOT NULL)")
            db.execSQL(
                "INSERT INTO meta VALUES('zdict', ?)",
                arrayOf(Base64.encodeToString(zdict, Base64.DEFAULT)),
            )
            articles.forEachIndexed { index, (title, text) ->
                db.execSQL(
                    "INSERT INTO article VALUES(?,?,?)",
                    arrayOf<Any>(index + 1, title, deflate(text)),
                )
            }
            aliases.forEach { (name, target) ->
                db.execSQL("INSERT INTO alias VALUES(?,?)", arrayOf<Any>(name, target))
            }
        }
    }

    @After
    fun cleanUp() {
        corpusFile.delete()
    }

    @Test
    fun `reads a compressed lead paragraph back through the shared dictionary`() {
        writeCorpus(
            articles = listOf("Nairobi" to "Nairobi is the capital and largest city of Kenya."),
            aliases = emptyList(),
        )

        val summary = runBlocking { WikiCorpus(context).summary("Nairobi") }

        assertEquals("Nairobi", summary?.title)
        assertEquals("Nairobi is the capital and largest city of Kenya.", summary?.extract)
        assertEquals("https://en.wikipedia.org/wiki/Nairobi", summary?.pageUrl)
    }

    /** "Ice Cube" and "ice cube" are both articles; case must not pick the wrong one. */
    @Test
    fun `exact case wins over a case-insensitive match`() {
        writeCorpus(
            articles =
                listOf(
                    "Ice Cube" to "Ice Cube is an American rapper and actor from Los Angeles.",
                    "Ice cube" to "An ice cube is a small piece of ice used to cool drinks.",
                ),
            aliases = emptyList(),
        )

        val rapper = runBlocking { WikiCorpus(context).summary("Ice Cube") }
        val frozen = runBlocking { WikiCorpus(context).summary("Ice cube") }

        assertTrue(rapper!!.extract.contains("rapper"))
        assertTrue(frozen!!.extract.contains("cool drinks"))
    }

    @Test
    fun `an alias resolves to the article it redirects to`() {
        writeCorpus(
            articles = listOf("United States" to "The United States is a country in North America."),
            aliases = listOf("USA" to 1),
        )

        val summary = runBlocking { WikiCorpus(context).summary("USA") }

        assertEquals("United States", summary?.title)
        assertTrue(summary!!.extract.contains("North America"))
    }

    @Test
    fun `a missing corpus is not an error, it is simply no answer`() {
        corpusFile.delete()

        val corpus = WikiCorpus(context)

        assertTrue(!corpus.isInstalled)
        assertNull(runBlocking { corpus.summary("Nairobi") })
    }

    @Test
    fun `an unknown title yields nothing rather than a wrong article`() {
        writeCorpus(articles = listOf("Nairobi" to "Nairobi is the capital of Kenya."), aliases = emptyList())

        assertNull(runBlocking { WikiCorpus(context).summary("Reykjavik") })
    }
}
