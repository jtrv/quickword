package io.github.jtrv.quickword.lookup

import org.junit.Assert.assertEquals
import org.junit.Test

class WordNormalizerTest {
    @Test fun `single word tried as typed, then lowercased`() =
        assertEquals(
            listOf("Petrichor", "petrichor"),
            lookupCandidates("Petrichor"),
        )

    @Test fun `surrounding punctuation and quotes stripped`() =
        assertEquals(listOf("petrichor"), lookupCandidates("“petrichor!”"))

    @Test fun `phrase first, then first word`() =
        assertEquals(
            listOf("Ice cream", "ice cream", "Ice", "ice"),
            lookupCandidates("Ice cream"),
        )

    @Test fun `longer selection keeps full phrase as first candidate`() =
        assertEquals(listOf("petrichor rising from", "petrichor"), lookupCandidates("petrichor rising from,"))

    @Test fun `curly apostrophe normalized, internal apostrophe kept`() =
        assertEquals(listOf("Don't", "don't"), lookupCandidates("Don’t"))

    @Test fun `hyphenated word kept whole`() = assertEquals(listOf("well-being"), lookupCandidates("well-being,"))

    @Test fun `whitespace-only selection yields no candidates`() =
        assertEquals(emptyList<String>(), lookupCandidates("  \n "))

    @Test fun `per-token edge punctuation stripped inside phrase`() =
        assertEquals(listOf("bona fide", "bona"), lookupCandidates("(bona fide)"))

    /**
     * SQLite NOCASE folds ASCII only, so the as-typed form is the only way a
     * capitalised non-ASCII headword is ever found.
     */
    @Test fun `non-ASCII capital survives as a candidate`() =
        assertEquals(listOf("Übermensch", "übermensch"), lookupCandidates("Übermensch"))

    @Test fun `a whole article selected is bounded, not tokenised in full`() {
        val article = "lorem ipsum ".repeat(500) // ~6000 chars, as a "select all" would be

        // The phrase candidate is far too long to be a headword and is dropped
        // entirely, so nothing oversized reaches SQLite or a Wikipedia URL.
        assertEquals(listOf("lorem"), lookupCandidates(article))
    }
}
