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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jtrv.quickword.R
import io.github.jtrv.quickword.data.DictionaryRepository
import io.github.jtrv.quickword.data.Suggestion
import io.github.jtrv.quickword.data.WordEntry
import io.github.jtrv.quickword.ui.search.SearchScreen
import io.github.jtrv.quickword.ui.word.WordScreen

// ponytail: two screens, hoisted state, no navigation library. Revisit if a
// third destination appears (M5 history/settings).
@Composable
fun QuickWordApp(
    repository: DictionaryRepository,
    initialWord: String? = null,
    notificationsMuted: Boolean = false,
    onFixNotifications: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var openWord by rememberSaveable { mutableStateOf(initialWord) }

    val suggestions by produceState(emptyList<Suggestion>(), query) {
        value = repository.suggest(query)
    }
    val entries by produceState(emptyList<WordEntry>(), openWord) {
        value = openWord?.let { repository.entriesFor(it) } ?: emptyList()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            // Channel-health requirement (PLAN.md refutation round 2): users can
            // silently mute the channel and the app cannot restore it — surface it.
            if (notificationsMuted) {
                MutedBanner(onFixNotifications)
            }
            val word = openWord
            if (word == null || entries.isEmpty()) {
                SearchScreen(
                    query = query,
                    suggestions = suggestions,
                    onQueryChange = { query = it },
                    onWordSelected = { openWord = it },
                )
            } else {
                BackHandler { openWord = null }
                WordScreen(
                    entries = entries,
                    onSynonymClick = { openWord = it },
                )
            }
        }
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
