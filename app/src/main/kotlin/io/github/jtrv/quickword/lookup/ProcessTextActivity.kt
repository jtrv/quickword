package io.github.jtrv.quickword.lookup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.jtrv.quickword.MainActivity
import io.github.jtrv.quickword.data.DictionaryRepository
import io.github.jtrv.quickword.data.HistoryStore
import io.github.jtrv.quickword.data.WikipediaApi
import kotlinx.coroutines.launch

/**
 * The trampoline (PLAN.md M2): translucent, never draws, posts the definition
 * notification and finishes. Manifest gives it the PROCESS_TEXT + SEND
 * filters, a translucent theme, noHistory and excludeFromRecents — the host
 * app stays on screen throughout.
 */
class ProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw =
            if (intent.action == Intent.ACTION_PROCESS_TEXT) {
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            } else {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            }?.toString().orEmpty()

        val candidates = lookupCandidates(raw)
        if (candidates.isEmpty()) {
            finish()
            return
        }

        val notifier = LookupNotifier(applicationContext)
        if (!LookupChannel(applicationContext).canNotify()) {
            // Notifications blocked: honest fallback — open the app at the word.
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_WORD, candidates.first()),
            )
            finish()
            return
        }

        val repository = DictionaryRepository(applicationContext)
        lifecycleScope.launch {
            val entries = repository.lookup(candidates)
            when {
                entries.isNotEmpty() -> {
                    HistoryStore(applicationContext).recordLookup(entries.first().word)
                    notifier.showEntries(entries)
                }
                else -> {
                    // No dictionary hit: proper nouns and names live on Wikipedia.
                    val wiki = WikipediaApi().summary(candidates.first())
                    if (wiki != null) {
                        notifier.showWiki(wiki)
                    } else {
                        notifier.showNoEntry(candidates.first())
                    }
                }
            }
            finish()
        }
    }
}
