package io.github.jtrv.quickword.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jtrv.quickword.R
import io.github.jtrv.quickword.data.CorpusDownloader
import io.github.jtrv.quickword.data.DictionaryRepository
import io.github.jtrv.quickword.data.HistoryEntry
import io.github.jtrv.quickword.data.HistoryStore
import io.github.jtrv.quickword.data.Suggestion
import io.github.jtrv.quickword.data.WikiCorpus
import io.github.jtrv.quickword.data.WikipediaApi
import io.github.jtrv.quickword.ui.about.AboutScreen
import io.github.jtrv.quickword.ui.search.SearchScreen
import io.github.jtrv.quickword.ui.word.WikiScreen
import io.github.jtrv.quickword.ui.word.WordScreen
import kotlinx.coroutines.launch

// ponytail: three screens, hoisted state, no navigation library — each is a
// leaf reached from search and dismissed with Back. Revisit if any destination
// ever needs to stack on another.
@Composable
fun QuickWordApp(
    repository: DictionaryRepository,
    history: HistoryStore,
    downloader: CorpusDownloader? = null,
    wikiDownloader: CorpusDownloader? = null,
    wikiCorpus: WikiCorpus? = null,
    initialWord: String? = null,
    notificationsMuted: Boolean = false,
    onFixNotifications: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var openWord by rememberSaveable { mutableStateOf(initialWord) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var historyVersion by rememberSaveable { mutableStateOf(0) }
    var dictVersion by rememberSaveable { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val suggestions by produceState(emptyList<Suggestion>(), query, dictVersion) {
        value = repository.suggest(query)
    }
    val recents by produceState(emptyList<HistoryEntry>(), openWord, historyVersion) {
        value = history.recent()
    }
    // Dictionary first; Wikipedia only after a confirmed no-hit (proper nouns).
    // null means still resolving — the Wikipedia leg is a network round trip.
    // Keyed on dictVersion too: a word shown as missing before the full
    // dictionary installed must re-resolve against it, not stay wrong.
    val lookup by produceState<LookupResult?>(null, openWord, dictVersion) {
        value = null
        val word = openWord ?: return@produceState
        val entries = repository.entriesFor(word)
        value =
            if (entries.isNotEmpty()) {
                history.recordLookup(entries.first().word)
                LookupResult.Entries(entries)
            } else {
                // Offline corpus first when installed — same answer, no network.
                val wiki = wikiCorpus?.summary(word) ?: WikipediaApi().summary(word)
                wiki?.let(LookupResult::Wiki) ?: LookupResult.None
            }
    }
    val favourite by produceState(false, openWord, historyVersion) {
        value = openWord?.let { history.isFavourite(it) } ?: false
    }

    // Insets, hard-won on a device (2026-08-05): the window resizes for the
    // keyboard, so the IME must NOT be padded for as well — doing so strands the
    // bottom search field a keyboard's height above the keyboard. But that same
    // resize drops the status-bar inset Scaffold would otherwise report, letting
    // the results list run under the clock — statusBars reports 0 while the
    // keyboard is up. So Scaffold contributes nothing and the top inset comes
    // from statusBarsIgnoringVisibility, which does not collapse.
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
            // Channel-health requirement (PLAN.md refutation round 2): users can
            // silently mute the channel and the app cannot restore it — surface it.
            if (notificationsMuted) {
                MutedBanner(onFixNotifications)
            }
            if (downloader != null && !downloader.isInstalled) {
                DownloadBanner(downloader) {
                    repository.reopen()
                    dictVersion++
                }
            }
            val word = openWord
            when {
                showAbout -> {
                    BackHandler { showAbout = false }
                    AboutDestination(
                        dictionary = downloader,
                        wikiDownloader = wikiDownloader,
                        wikiCorpus = wikiCorpus,
                        dictVersion = dictVersion,
                        onCorpusChanged = {
                            repository.reopen()
                            dictVersion++
                        },
                        onClearHistory = {
                            scope.launch {
                                history.clear()
                                historyVersion++
                            }
                        },
                    )
                }
                word != null -> {
                    BackHandler { openWord = null }
                    OpenWord(
                        word = word,
                        lookup = lookup,
                        favourite = favourite,
                        onToggleFavourite = { headword ->
                            scope.launch {
                                history.setFavourite(headword, !favourite)
                                historyVersion++
                            }
                        },
                        onSynonymClick = { openWord = it },
                    )
                }
                else ->
                    SearchScreen(
                        query = query,
                        suggestions = suggestions,
                        recents = recents,
                        onQueryChange = { query = it },
                        onWordSelected = { openWord = it },
                        onAbout = { showAbout = true },
                    )
            }
        }
    }
}

/** Keeps the nullable-corpus plumbing out of [QuickWordApp]'s own branching. */
@Composable
private fun AboutDestination(
    dictionary: CorpusDownloader?,
    wikiDownloader: CorpusDownloader?,
    wikiCorpus: WikiCorpus?,
    dictVersion: Int,
    onCorpusChanged: () -> Unit,
    onClearHistory: () -> Unit,
) {
    AboutScreen(
        // Keyed on dictVersion so installing or removing a corpus re-reads the
        // size instead of lying about it.
        dictionaryBytes = remember(dictVersion) { dictionary?.installedBytes ?: 0L },
        wikiBytes = remember(dictVersion) { wikiCorpus?.bytes ?: 0L },
        wikiDownloader = wikiDownloader,
        onRemoveDictionary = {
            dictionary?.removeInstalled()
            onCorpusChanged()
        },
        onWikiChanged = {
            wikiCorpus?.reopen()
            onCorpusChanged()
        },
        onRemoveWiki = {
            wikiCorpus?.remove()
            onCorpusChanged()
        },
        onClearHistory = onClearHistory,
    )
}

/**
 * Every outcome of an open word — including "no such word" and "still asking
 * Wikipedia" — is a screen of its own. Falling through to search instead, as
 * this once did, stranded anyone who tapped a synonym chip that is not itself
 * a headword: the tap appeared to do nothing at all.
 */
@Composable
private fun OpenWord(
    word: String,
    lookup: LookupResult?,
    favourite: Boolean,
    onToggleFavourite: (String) -> Unit,
    onSynonymClick: (String) -> Unit,
) {
    when (lookup) {
        null -> Resolving()
        is LookupResult.Entries ->
            WordScreen(
                entries = lookup.entries,
                favourite = favourite,
                onToggleFavourite = { onToggleFavourite(lookup.entries.first().word) },
                onSynonymClick = onSynonymClick,
            )
        is LookupResult.Wiki -> WikiScreen(summary = lookup.summary)
        LookupResult.None -> NoEntry(word)
    }
}

/** Dictionary hits resolve in a frame; only the Wikipedia leg is ever slow. */
@Composable
private fun Resolving() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoEntry(word: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.no_results, word),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(R.string.no_entry_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MutedBanner(onFix: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.notifications_muted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.notifications_muted_fix))
            }
        }
    }
}
