package io.github.jtrv.quickword.ui.about

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jtrv.quickword.BuildConfig
import io.github.jtrv.quickword.R

/**
 * Attribution and licences. Not decoration: CC BY-SA 4.0 (Wiktionary data,
 * Wikipedia summaries) requires attribution be shown to users, and the SIL OFL
 * requires the licence text travel with the bundled fonts — so the OFL files
 * ship as APK assets and are rendered verbatim here.
 */
@Composable
fun AboutScreen(
    dictionaryBytes: Long,
    onRemoveDictionary: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val assets = context.assets
    val licenceUrl = stringResource(R.string.licence_url)
    // ponytail: ~3 KB read on a screen the user deliberately opened.
    val licences =
        remember {
            OFL_FILES.map { name -> assets.open("licenses/$name").bufferedReader().use { it.readText() } }
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .widthIn(max = 640.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Section(R.string.about_dictionary_heading, R.string.about_dictionary)
        Section(R.string.about_wikipedia_heading, R.string.about_wikipedia)
        // The licence obligation is a link to the licence, not its name.
        TextButton(onClick = { uriHandler.openUri(licenceUrl) }) {
            Text(stringResource(R.string.about_licence_link))
        }
        Section(R.string.about_fonts_heading, R.string.about_fonts)
        Section(R.string.about_code_heading, R.string.about_code)
        TextButton(onClick = { uriHandler.openUri(SOURCE_URL) }) {
            Text(stringResource(R.string.about_source))
        }
        Storage(
            dictionaryBytes = dictionaryBytes,
            onRemoveDictionary = onRemoveDictionary,
            onClearHistory = onClearHistory,
        )
        licences.forEach { text ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The app can occupy ~120 MB of someone's phone and holds a record of every
 * word they looked up. Both need an exit that is not "uninstall the app".
 */
@Composable
private fun Storage(
    dictionaryBytes: Long,
    onRemoveDictionary: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.about_storage_heading),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 20.dp),
    )
    Text(
        text =
            if (dictionaryBytes > 0) {
                stringResource(
                    R.string.about_dictionary_full,
                    Formatter.formatShortFileSize(context, dictionaryBytes),
                )
            } else {
                stringResource(R.string.about_dictionary_starter)
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    // Re-downloadable, so it goes without a confirmation; the history does not.
    if (dictionaryBytes > 0) {
        TextButton(onClick = onRemoveDictionary) {
            Text(stringResource(R.string.about_remove_dictionary))
        }
    }
    TextButton(onClick = { confirmClear = true }) {
        Text(stringResource(R.string.about_clear_history))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.about_clear_confirm_title)) },
            text = { Text(stringResource(R.string.about_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                }) {
                    Text(stringResource(R.string.about_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun Section(
    @StringRes heading: Int,
    @StringRes body: Int,
) {
    Text(
        text = stringResource(heading),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 20.dp),
    )
    Text(
        text = stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private val OFL_FILES = listOf("OFL-Literata.txt", "OFL-Inter.txt")
private const val SOURCE_URL = "https://github.com/jtrv/quickword"
