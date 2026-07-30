package io.github.jtrv.quickword.lookup

/**
 * Turns raw selected text into ordered lookup candidates.
 *
 * Policy (user decision 2026-07-29): try the FULL normalized phrase first —
 * catches multi-word entries ("ice cream", "bona fide") — then fall back to
 * the first word. The trampoline queries candidates in order and shows the
 * first hit.
 *
 * Normalization: collapse whitespace, straighten curly apostrophes, strip
 * leading/trailing non-letters per token, lowercase (locale-neutral).
 */
fun lookupCandidates(raw: String): List<String> {
    val tokens =
        raw
            .replace('’', '\'')
            .split(Regex("\\s+"))
            .map { it.trim { c -> !c.isLetter() }.lowercase() }
            .filter { it.isNotEmpty() }
    val phrase = tokens.joinToString(" ")
    val firstWord = tokens.firstOrNull().orEmpty()
    return listOf(phrase, firstWord).filter { it.isNotEmpty() }.distinct()
}
