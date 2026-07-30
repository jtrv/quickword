package io.github.jtrv.quickword.lookup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.jtrv.quickword.data.DictionaryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The notification's Thesaurus button (M4): swaps the notification content to
 * synonyms in place — no app launch, no UI. goAsync() keeps the process alive
 * for the DB read.
 */
class ThesaurusActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val word = intent.getStringExtra(EXTRA_WORD) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entries = DictionaryRepository(context.applicationContext).entriesFor(word)
                LookupNotifier(context.applicationContext).showSynonyms(word, entries)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "io.github.jtrv.quickword.action.THESAURUS"
        const val EXTRA_WORD = "io.github.jtrv.quickword.extra.WORD"
    }
}
