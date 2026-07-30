package io.github.jtrv.quickword.lookup

import android.app.Notification
import android.app.NotificationChannel
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

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_description)
                setSound(null, null)
            },
        )
    }

    /** True when notifications will actually surface (permission + channel not muted by user). */
    fun canNotify(): Boolean {
        val channelImportance = manager.getNotificationChannel(CHANNEL_ID)?.importance
        return manager.areNotificationsEnabled() &&
            channelImportance != NotificationManager.IMPORTANCE_NONE
    }

    /** Channel exists but the user downgraded it below heads-up level. */
    fun channelDegraded(): Boolean {
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return false
        return channel.importance in 1 until NotificationManager.IMPORTANCE_HIGH
    }

    fun showEntries(entries: List<WordEntry>) {
        ensureChannel()
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
        post(head.word, "${head.word} · ${head.pos}", definition, withThesaurus = hasSynonyms)
    }

    fun showNoEntry(query: String) {
        ensureChannel()
        post(query, query, context.getString(R.string.no_results, query))
    }

    /** In-place swap to synonyms — the Thesaurus action's target state. */
    fun showSynonyms(
        word: String,
        entries: List<WordEntry>,
    ) {
        ensureChannel()
        val synonyms = entries.flatMap { it.synonyms }.distinct()
        val text =
            if (synonyms.isEmpty()) {
                context.getString(R.string.no_synonyms, word)
            } else {
                synonyms.joinToString(" · ")
            }
        post(word, context.getString(R.string.synonyms_title, word), text)
    }

    private fun post(
        word: String,
        title: String,
        text: String,
        withThesaurus: Boolean = false,
    ) {
        val openIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_WORD, word)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val shareIntent =
            PendingIntent.getActivity(
                context,
                1,
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

        val builder =
            Notification
                .Builder(context, CHANNEL_ID)
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
                    2,
                    Intent(context, ThesaurusActionReceiver::class.java)
                        .setAction(ThesaurusActionReceiver.ACTION)
                        .putExtra(ThesaurusActionReceiver.EXTRA_WORD, word),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder.addAction(actionOf(R.string.action_thesaurus, thesaurusIntent))
        }
        builder
            .addAction(actionOf(R.string.action_open, openIntent))
            .addAction(actionOf(R.string.action_share, shareIntent))
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun actionOf(
        label: Int,
        intent: PendingIntent,
    ): Notification.Action = Notification.Action.Builder(null, context.getString(label), intent).build()

    companion object {
        const val CHANNEL_ID = "lookup"
        const val NOTIFICATION_ID = 1
        private const val MAX_GLOSSES = 2
        private const val TIMEOUT_MS = 30_000L
    }
}
