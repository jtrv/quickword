package io.github.jtrv.quickword.ui.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.jtrv.quickword.data.WordEntry

private const val MAX_CHIPS = 6

@Composable
fun WordScreen(
    entries: List<WordEntry>,
    onSynonymClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val headword = entries.firstOrNull() ?: return
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .widthIn(max = 640.dp),
    ) {
        Text(
            text = headword.word,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp),
        )
        headword.ipa?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        entries.forEach { entry -> PosGroup(entry, onSynonymClick) }
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun PosGroup(
    entry: WordEntry,
    onSynonymClick: (String) -> Unit,
) {
    Text(
        text = entry.pos,
        style = MaterialTheme.typography.titleSmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
    entry.senses.forEach { sense ->
        Row(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = "${sense.senseNo}.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = sense.gloss,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                sense.example?.let {
                    Text(
                        text = "“$it”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
    if (entry.synonyms.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.synonyms.take(MAX_CHIPS).forEach { synonym ->
                SuggestionChip(
                    onClick = { onSynonymClick(synonym) },
                    label = { Text(synonym) },
                    colors =
                        SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    border = null,
                )
            }
            val overflow = entry.synonyms.size - MAX_CHIPS
            if (overflow > 0) {
                SuggestionChip(onClick = {}, label = { Text("+$overflow") })
            }
        }
    }
}
