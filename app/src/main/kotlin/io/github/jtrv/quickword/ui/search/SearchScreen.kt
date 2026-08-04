package io.github.jtrv.quickword.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jtrv.quickword.R
import io.github.jtrv.quickword.data.HistoryEntry
import io.github.jtrv.quickword.data.Suggestion

@Composable
fun SearchScreen(
    query: String,
    suggestions: List<Suggestion>,
    recents: List<HistoryEntry>,
    onQueryChange: (String) -> Unit,
    onWordSelected: (String) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            // ponytail: no top bar exists; the field's trailing slot is the one
            // always-visible affordance, and licences must stay reachable.
            trailingIcon = {
                IconButton(onClick = onAbout) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about))
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
        )
        if (query.isNotBlank() && suggestions.isEmpty()) {
            Text(
                text = stringResource(R.string.no_results, query.trim()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        if (query.isBlank() && recents.isNotEmpty()) {
            Text(
                text = stringResource(R.string.recent_heading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            LazyColumn {
                items(recents, key = { it.word }) { entry ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onWordSelected(entry.word) }
                                .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = entry.word,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (entry.favourite) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        LazyColumn {
            items(suggestions, key = { it.word }) { suggestion ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onWordSelected(suggestion.word) }
                            .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = suggestion.word,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = suggestion.firstGloss,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
