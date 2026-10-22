package io.github.jtrv.quickword.ui.word

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Makes [content] selectable, with QuickWord's own selection-toolbar entry
 * opening the selection in place.
 *
 * Compose lists every PROCESS_TEXT activity in that toolbar — QuickWord's
 * included — and launches the chosen one through [LocalContext]. Left alone,
 * ours would post a notification about text already on screen.
 */
@Composable
fun SelectableLookup(
    onLookup: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val currentOnLookup by rememberUpdatedState(onLookup)
    val routed = remember(context) { InAppLookupContext(context) { currentOnLookup(it) } }
    CompositionLocalProvider(LocalContext provides routed) {
        SelectionContainer { content() }
    }
}

internal class InAppLookupContext(
    base: Context,
    private val onLookup: (String) -> Unit,
) : ContextWrapper(base) {
    override fun startActivity(intent: Intent) {
        val ownProcessText =
            intent.action == Intent.ACTION_PROCESS_TEXT &&
                intent.component?.packageName == packageName
        if (!ownProcessText) {
            super.startActivity(intent)
            return
        }
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.let { onLookup(it.toString()) }
    }
}
