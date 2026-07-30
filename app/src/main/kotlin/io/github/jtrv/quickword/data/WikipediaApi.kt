package io.github.jtrv.quickword.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WikiSummary(
    val title: String,
    val extract: String,
    val pageUrl: String,
)

/**
 * Wikipedia REST summary endpoint — the no-hit fallback layer (proper nouns,
 * places, people). Card contract: Page Previews / Specs Summary 1.2.0.
 * ponytail: HttpURLConnection + org.json, no HTTP client dependency for one
 * GET; revisit if a second endpoint ever appears.
 */
class WikipediaApi {
    suspend fun summary(term: String): WikiSummary? =
        withContext(Dispatchers.IO) {
            runCatching { fetch(term) }.getOrNull()
        }

    private fun fetch(term: String): WikiSummary? {
        val encoded = URLEncoder.encode(term.replace(' ', '_'), "UTF-8")
        val connection =
            URL("$BASE/$encoded?redirect=true").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // Wikimedia asks for a descriptive UA with contact info.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            parse(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://en.wikipedia.org/api/rest_v1/page/summary"
        private const val TIMEOUT_MS = 3_000
        private const val USER_AGENT = "QuickWord/0.1 (https://github.com/jtrv/quickword)"

        /** Pure and testable: summary JSON -> WikiSummary, null for disambiguation/no-extract. */
        fun parse(body: String): WikiSummary? {
            val json = JSONObject(body)
            val standard = json.optString("type") in setOf("standard", "")
            val extract = json.optString("extract")
            return if (!standard || extract.isBlank()) {
                null
            } else {
                WikiSummary(
                    title = json.optString("title"),
                    extract = extract,
                    pageUrl =
                        json
                            .optJSONObject("content_urls")
                            ?.optJSONObject("desktop")
                            ?.optString("page")
                            .orEmpty(),
                )
            }
        }
    }
}
