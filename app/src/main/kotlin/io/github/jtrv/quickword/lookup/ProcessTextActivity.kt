package io.github.jtrv.quickword.lookup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.jtrv.quickword.MainActivity
import io.github.jtrv.quickword.data.DictionaryRepository
import io.github.jtrv.quickword.data.HistoryStore
import io.github.jtrv.quickword.data.WikiCorpus
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
        // Exported component: only the two actions the manifest advertises get
        // read, rather than treating anything at all as a share.
        val raw =
            when (intent.action) {
                Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                else -> null
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
            try {
                lookUp(repository, notifier, candidates)
            } finally {
                // This is the hot path — one launch per lookup, forever. Leaving
                // SQLite handles to the finaliser accumulates file descriptors.
                repository.close()
                finish()
            }
        }
    }

    private suspend fun lookUp(
        repository: DictionaryRepository,
        notifier: LookupNotifier,
        candidates: List<String>,
    ) {
        val entries = repository.lookup(candidates)
        if (entries.isNotEmpty()) {
            HistoryStore(applicationContext).use { it.recordLookup(entries.first().word) }
            notifier.showEntries(entries)
            return
        }
        // No dictionary hit: proper nouns and names live on Wikipedia. The
        // offline corpus answers without a network round trip when installed,
        // which also keeps the trampoline fast.
        val term = candidates.first()
        val wiki = WikiCorpus(applicationContext).summary(term) ?: WikipediaApi().summary(term)
        if (wiki != null) notifier.showWiki(wiki) else notifier.showNoEntry(term)
    }
}
