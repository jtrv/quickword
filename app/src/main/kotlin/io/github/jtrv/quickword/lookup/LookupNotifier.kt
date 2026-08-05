package io.github.jtrv.quickword.lookup

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.jtrv.quickword.MainActivity
import io.github.jtrv.quickword.R
import io.github.jtrv.quickword.data.WordEntry

/**
 * Builds and posts the lookup notification — the product's core surface.
 * One fixed notification ID: a new lookup replaces the previous one.
 */
class LookupNotifier(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val channel = LookupChannel(context)

    fun showEntries(entries: List<WordEntry>) {
        channel.ensure()
        val head = entries.first()
        // Prefix each gloss with its POS only when several POS compete —
        // otherwise the title ("word · noun") already says it.
        val multiPos = entries.size > 1
        val definition =
            entries
                .flatMap { entry ->
                    entry.senses.map { if (multiPos) "${entry.pos} · ${it.gloss}" else it.gloss }
                }.take(MAX_GLOSSES)
                .joinToString("\n")
        val hasSynonyms = entries.any { it.synonyms.isNotEmpty() }
        post(
            head.word,
            "${head.word} · ${head.pos}",
            definition,
            withThesaurus = hasSynonyms,
            withFavourite = true,
        )
    }

    fun showNoEntry(query: String) {
        channel.ensure()
        post(query, query, context.getString(R.string.no_results, query))
    }

    /**
     * Wikipedia fallback (no dictionary hit). The attribution travels with the
     * extract: this notification redistributes CC BY-SA text, and it is often
     * the only place the user ever sees it — the app may never be opened.
     */
    fun showWiki(summary: io.github.jtrv.quickword.data.WikiSummary) {
        channel.ensure()
        post(
            summary.title,
            context.getString(R.string.wiki_title, summary.title),
            summary.extract + "\n\n" + context.getString(R.string.wiki_attribution),
        )
    }

    /** In-place swap to synonyms — the Thesaurus action's target state. */
    fun showSynonyms(
        word: String,
        entries: List<WordEntry>,
    ) {
        channel.ensure()
        val synonyms = entries.flatMap { it.synonyms }.distinct()
        val text =
            if (synonyms.isEmpty()) {
                context.getString(R.string.no_synonyms, word)
            } else {
                synonyms.joinToString(" · ")
            }
        post(word, context.getString(R.string.synonyms_title, word), text)
    }

    private fun openIntent(word: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQ_OPEN,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_WORD, word)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun shareIntent(
        word: String,
        text: String,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQ_SHARE,
            Intent
                .createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, word)
                        .putExtra(Intent.EXTRA_TEXT, "$word\n\n$text"),
                    null,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun post(
        word: String,
        title: String,
        text: String,
        withThesaurus: Boolean = false,
        withFavourite: Boolean = false,
    ) {
        val openIntent = openIntent(word)
        val builder =
            Notification
                .Builder(context, LookupChannel.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text.lineSequence().first())
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(TIMEOUT_MS)
        if (withThesaurus) {
            val thesaurusIntent =
                PendingIntent.getBroadcast(
                    context,
                    REQ_THESAURUS,
                    Intent(context, ThesaurusActionReceiver::class.java)
                        .setAction(ThesaurusActionReceiver.ACTION)
                        .putExtra(ThesaurusActionReceiver.EXTRA_WORD, word),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder.addAction(actionOf(R.string.action_thesaurus, thesaurusIntent))
        }
        builder.addAction(actionOf(R.string.action_open, openIntent))
        if (withFavourite) {
            // DESIGN.md action budget: [Thesaurus] [Open] [★]; Share lives in-app.
            val favIntent =
                PendingIntent.getBroadcast(
                    context,
                    REQ_FAVOURITE,
                    Intent(context, FavouriteActionReceiver::class.java)
                        .setAction(FavouriteActionReceiver.ACTION)
                        .putExtra(FavouriteActionReceiver.EXTRA_WORD, word),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder.addAction(actionOf(R.string.action_favourite, favIntent))
        } else {
            builder.addAction(actionOf(R.string.action_share, shareIntent(word, text)))
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun actionOf(
        label: Int,
        intent: PendingIntent,
    ): Notification.Action = Notification.Action.Builder(null, context.getString(label), intent).build()

    companion object {
        const val NOTIFICATION_ID = 1
        private const val MAX_GLOSSES = 2
        private const val TIMEOUT_MS = 30_000L
        private const val REQ_OPEN = 0
        private const val REQ_SHARE = 1
        private const val REQ_THESAURUS = 2
        private const val REQ_FAVOURITE = 3
    }
}
