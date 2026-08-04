package io.github.jtrv.quickword.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.github.jtrv.quickword.data.DictionaryDownloader
import io.github.jtrv.quickword.data.DictionaryRepository
import io.github.jtrv.quickword.data.HistoryEntry
import io.github.jtrv.quickword.data.HistoryStore
import io.github.jtrv.quickword.data.Suggestion
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
    downloader: DictionaryDownloader? = null,
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
    val lookup by produceState<LookupResult?>(null, openWord) {
        value = null
        val word = openWord ?: return@produceState
        val entries = repository.entriesFor(word)
        value =
            if (entries.isNotEmpty()) {
                history.recordLookup(entries.first().word)
                LookupResult.Entries(entries)
            } else {
                LookupResult.Wiki(WikipediaApi().summary(word))
            }
    }
    val favourite by produceState(false, openWord, historyVersion) {
        value = openWord?.let { history.isFavourite(it) } ?: false
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            // Channel-health requirement (PLAN.md refutation round 2): users can
            // silently mute the channel and the app cannot restore it — surface it.
            if (notificationsMuted) {
                MutedBanner(onFixNotifications)
            }
            if (downloader != null && !downloader.hasFullDictionary()) {
                DownloadBanner(downloader) {
                    repository.reopen()
                    dictVersion++
                }
            }
            val result = if (openWord == null) null else lookup
            val wikiSummary = (result as? LookupResult.Wiki)?.summary
            when {
                showAbout -> {
                    BackHandler { showAbout = false }
                    AboutScreen()
                }
                result is LookupResult.Entries -> {
                    BackHandler { openWord = null }
                    WordScreen(
                        entries = result.entries,
                        favourite = favourite,
                        onToggleFavourite = {
                            val word = result.entries.first().word
                            scope.launch {
                                history.setFavourite(word, !favourite)
                                historyVersion++
                            }
                        },
                        onSynonymClick = { openWord = it },
                    )
                }
                wikiSummary != null -> {
                    BackHandler { openWord = null }
                    WikiScreen(summary = wikiSummary)
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

@Composable
private fun DownloadBanner(
    downloader: DictionaryDownloader,
    onDownloaded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val running = progress
            Text(
                text =
                    when {
                        running != null && running >= 0f ->
                            stringResource(R.string.dict_downloading, (running * PERCENT).toInt())
                        running != null -> stringResource(R.string.dict_downloading_indeterminate)
                        failed -> stringResource(R.string.dict_failed)
                        else -> stringResource(R.string.dict_banner)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (progress == null) {
                TextButton(onClick = {
                    failed = false
                    progress = -1f
                    scope.launch {
                        val result = downloader.download { progress = it }
                        progress = null
                        if (result.isSuccess) onDownloaded() else failed = true
                    }
                }) {
                    Text(stringResource(R.string.dict_download))
                }
            }
        }
    }
}

private const val PERCENT = 100

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
