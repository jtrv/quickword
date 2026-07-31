package io.github.jtrv.quickword.lookup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.jtrv.quickword.data.HistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The notification's ★ button: mark the word favourite, no UI. */
class FavouriteActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val word = intent.getStringExtra(EXTRA_WORD) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HistoryStore(context.applicationContext).setFavourite(word, true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "io.github.jtrv.quickword.action.FAVOURITE"
        const val EXTRA_WORD = io.github.jtrv.quickword.lookup.EXTRA_WORD
    }
}
