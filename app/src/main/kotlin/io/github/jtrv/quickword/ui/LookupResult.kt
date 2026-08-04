package io.github.jtrv.quickword.ui

import io.github.jtrv.quickword.data.WikiSummary
import io.github.jtrv.quickword.data.WordEntry

sealed interface LookupResult {
    data class Entries(
        val entries: List<WordEntry>,
    ) : LookupResult

    data class Wiki(
        val summary: WikiSummary,
    ) : LookupResult

    /**
     * Dictionary missed and Wikipedia had nothing — or the network was gone.
     * A distinct state, not a null Wiki: the trampoline already tells the user
     * this (LookupNotifier.showNoEntry) and the app must not stay quieter than
     * its own notification.
     */
    data object None : LookupResult
}
