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
 * leading/trailing non-letters per token, and try the selection both as typed
 * and lowercased.
 *
 * Both bounds and both cases matter, and neither is theoretical:
 *  - The input is whatever an arbitrary app puts in a PROCESS_TEXT extra. A
 *    "select all" on a long article would otherwise be tokenised in full, used
 *    as a SQL parameter, and pasted into a Wikipedia URL.
 *  - SQLite's NOCASE folds ASCII only, so a lowercased "Übermensch" never
 *    matches the stored headword. Keeping the as-typed form as a candidate
 *    rescues every non-ASCII entry the user selected with its own capital.
 */
fun lookupCandidates(raw: String): List<String> {
    val tokens =
        raw
            .take(MAX_INPUT_CHARS)
            .replace('’', '\'')
            .split(Regex("\\s+"))
            .map { it.trim { c -> !c.isLetter() } }
            .filter { it.isNotEmpty() }
    val phrase = tokens.joinToString(" ")
    val firstWord = tokens.firstOrNull().orEmpty()
    return listOf(phrase, phrase.lowercase(), firstWord, firstWord.lowercase())
        .filter { it.isNotEmpty() && it.length <= MAX_CANDIDATE_CHARS }
        .distinct()
}

/** A selection longer than this is not a word anyone is looking up. */
private const val MAX_INPUT_CHARS = 512

/** Longest headword worth querying; also bounds the Wikipedia URL. */
private const val MAX_CANDIDATE_CHARS = 128
