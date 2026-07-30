package io.github.jtrv.quickword.lookup

import org.junit.Assert.assertEquals
import org.junit.Test

class WordNormalizerTest {
    @Test fun `single word lowercased, one candidate`() =
        assertEquals(
            listOf("petrichor"),
            lookupCandidates("Petrichor"),
        )

    @Test fun `surrounding punctuation and quotes stripped`() =
        assertEquals(listOf("petrichor"), lookupCandidates("“petrichor!”"))

    @Test fun `phrase first, then first word`() =
        assertEquals(
            listOf("ice cream", "ice"),
            lookupCandidates("Ice cream"),
        )

    @Test fun `longer selection keeps full phrase as first candidate`() =
        assertEquals(listOf("petrichor rising from", "petrichor"), lookupCandidates("petrichor rising from,"))

    @Test fun `curly apostrophe normalized, internal apostrophe kept`() =
        assertEquals(listOf("don't"), lookupCandidates("Don’t"))

    @Test fun `hyphenated word kept whole`() = assertEquals(listOf("well-being"), lookupCandidates("well-being,"))

    @Test fun `whitespace-only selection yields no candidates`() =
        assertEquals(emptyList<String>(), lookupCandidates("  \n "))

    @Test fun `per-token edge punctuation stripped inside phrase`() =
        assertEquals(listOf("bona fide", "bona"), lookupCandidates("(bona fide)"))
}
