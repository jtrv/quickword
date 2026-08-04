package io.github.jtrv.quickword.ui.about

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
fun AboutScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val assets = LocalContext.current.assets
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
        Section(R.string.about_fonts_heading, R.string.about_fonts)
        Section(R.string.about_code_heading, R.string.about_code)
        TextButton(onClick = { uriHandler.openUri(SOURCE_URL) }) {
            Text(stringResource(R.string.about_source))
        }
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
