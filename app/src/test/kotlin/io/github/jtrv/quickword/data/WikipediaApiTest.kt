package io.github.jtrv.quickword.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric only because org.json is a stub in plain JVM unit tests.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WikipediaApiTest {
    @Test
    fun `standard summary parses title, extract and url`() {
        val summary =
            WikipediaApi.parse(
                """
                {"type":"standard","title":"Petrichor",
                 "extract":"Petrichor is the earthy scent produced when rain falls on dry soil.",
                 "content_urls":{"desktop":{"page":"https://en.wikipedia.org/wiki/Petrichor"}}}
                """.trimIndent(),
            )
        assertEquals("Petrichor", summary?.title)
        assertEquals("https://en.wikipedia.org/wiki/Petrichor", summary?.pageUrl)
    }

    @Test
    fun `disambiguation pages are rejected`() {
        assertNull(
            WikipediaApi.parse("""{"type":"disambiguation","title":"Mercury","extract":"may refer to:"}"""),
        )
    }

    @Test
    fun `blank extract is rejected`() {
        assertNull(WikipediaApi.parse("""{"type":"standard","title":"X","extract":""}"""))
    }
}
