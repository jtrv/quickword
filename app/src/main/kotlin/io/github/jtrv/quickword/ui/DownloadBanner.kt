package io.github.jtrv.quickword.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jtrv.quickword.R
import io.github.jtrv.quickword.data.CorpusDownloader
import kotlinx.coroutines.delay

private const val PERCENT = 100
private const val POLL_MS = 500L
private const val IDLE_POLL_MS = 5_000L

/**
 * Drives a corpus download — the dictionary, or the offline Wikipedia corpus. DownloadManager owns the transfer, so
 * this polls its status rather than holding progress itself — which is what lets
 * the download survive leaving the app, and lets the banner pick a download back
 * up on the next launch instead of offering to start a second one.
 */
@Composable
fun DownloadBanner(
    downloader: CorpusDownloader,
    @StringRes idleText: Int = R.string.dict_banner,
    onInstalled: () -> Unit,
) {
    var state by remember { mutableStateOf<CorpusDownloader.State>(CorpusDownloader.State.Absent) }
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var confirmMetered by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        failed = false
        while (true) {
            val current = downloader.state()
            state = current
            if (current is CorpusDownloader.State.Ready) {
                val installed = downloader.install().isSuccess
                if (installed) {
                    onInstalled()
                } else {
                    // A corrupt or truncated archive is not worth retrying on a
                    // timer; drop it so the button offers a clean start.
                    downloader.cancel()
                    failed = true
                }
                return@LaunchedEffect
            }
            if (current is CorpusDownloader.State.Failed) {
                downloader.cancel()
                failed = true
                return@LaunchedEffect
            }
            // Idle is the common case — most sessions never start a download and
            // must not pay a 2 Hz timer for the privilege. Polling only tightens
            // once there is progress to report.
            delay(if (current is CorpusDownloader.State.Downloading) POLL_MS else IDLE_POLL_MS)
        }
    }

    val start = { allowMetered: Boolean ->
        downloader.enqueue(allowMetered = allowMetered)
        attempt++
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bannerText(state, failed, idleText),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            BannerAction(
                state = state,
                failed = failed,
                onRetry = { start(false) },
                onUseMobile = { start(true) },
                onStart = { if (downloader.onMeteredNetwork()) confirmMetered = true else start(false) },
            )
        }
    }

    MeteredDialog(
        visible = confirmMetered,
        onDismiss = { confirmMetered = false },
        onDownloadNow = {
            confirmMetered = false
            start(true)
        },
        onWaitForWifi = {
            confirmMetered = false
            start(false)
        },
    )
}

@Composable
private fun bannerText(
    state: CorpusDownloader.State,
    failed: Boolean,
    @StringRes idleText: Int,
): String =
    when {
        failed -> stringResource(R.string.dict_failed)
        state is CorpusDownloader.State.Ready -> stringResource(R.string.dict_installing)
        state !is CorpusDownloader.State.Downloading -> stringResource(idleText)
        state.waitingForNetwork -> stringResource(R.string.dict_waiting_wifi)
        state.fraction >= 0f ->
            stringResource(R.string.dict_downloading, (state.fraction * PERCENT).toInt())
        else -> stringResource(R.string.dict_downloading_indeterminate)
    }

@Composable
private fun BannerAction(
    state: CorpusDownloader.State,
    failed: Boolean,
    onRetry: () -> Unit,
    onUseMobile: () -> Unit,
    onStart: () -> Unit,
) {
    when {
        failed -> TextButton(onRetry) { Text(stringResource(R.string.dict_retry)) }
        state is CorpusDownloader.State.Downloading && state.waitingForNetwork ->
            TextButton(onUseMobile) { Text(stringResource(R.string.dict_use_mobile)) }
        state is CorpusDownloader.State.Absent ->
            TextButton(onStart) { Text(stringResource(R.string.dict_download)) }
        // Running or installing: nothing useful for a button to do.
        else -> Unit
    }
}

@Composable
private fun MeteredDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onDownloadNow: () -> Unit,
    onWaitForWifi: () -> Unit,
) {
    if (visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dict_metered_title)) },
            text = { Text(stringResource(R.string.dict_metered_body)) },
            confirmButton = {
                TextButton(onDownloadNow) { Text(stringResource(R.string.dict_metered_confirm)) }
            },
            // Queuing it Wi-Fi-only is a real answer, not a cancel: the download
            // starts by itself the next time they are on Wi-Fi.
            dismissButton = {
                TextButton(onWaitForWifi) { Text(stringResource(R.string.dict_metered_wifi)) }
            },
        )
    }
}
