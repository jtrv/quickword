package io.github.jtrv.quickword.ui

import io.github.jtrv.quickword.data.WikiSummary
import io.github.jtrv.quickword.data.WordEntry

sealed interface LookupResult {
    data class Entries(
        val entries: List<WordEntry>,
    ) : LookupResult

    data class Wiki(
        val summary: WikiSummary?,
    ) : LookupResult
}
