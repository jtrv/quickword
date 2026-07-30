package io.github.jtrv.quickword.lookup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.jtrv.quickword.R

/**
 * The lookup channel's lifecycle and health. Separate from posting because
 * channel importance is user-owned state the app can only observe (PLAN.md
 * refutation round 2) — MainActivity surfaces it, the trampoline gates on it.
 */
class LookupChannel(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensure() {
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
    fun degraded(): Boolean {
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return false
        return channel.importance in 1 until NotificationManager.IMPORTANCE_HIGH
    }

    companion object {
        const val CHANNEL_ID = "lookup"
    }
}
