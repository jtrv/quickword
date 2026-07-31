package io.github.jtrv.quickword

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import io.github.jtrv.quickword.data.DictionaryDownloader
import io.github.jtrv.quickword.data.DictionaryRepository
import io.github.jtrv.quickword.data.HistoryStore
import io.github.jtrv.quickword.lookup.LookupChannel
import io.github.jtrv.quickword.ui.QuickWordApp
import io.github.jtrv.quickword.ui.theme.QuickWordTheme

class MainActivity : ComponentActivity() {
    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshMuted() }
    private val channel by lazy { LookupChannel(applicationContext) }
    private val muted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        channel.ensure()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val repository = DictionaryRepository(applicationContext)
        val history = HistoryStore(applicationContext)
        val initialWord = intent.getStringExtra(EXTRA_WORD)
        setContent {
            QuickWordTheme {
                QuickWordApp(
                    repository = repository,
                    history = history,
                    downloader = DictionaryDownloader(applicationContext),
                    initialWord = initialWord,
                    notificationsMuted = muted.value,
                    onFixNotifications = ::openChannelSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMuted()
    }

    private fun refreshMuted() {
        muted.value = !channel.canNotify() || channel.degraded()
    }

    private fun openChannelSettings() {
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, LookupChannel.CHANNEL_ID),
        )
    }

    companion object {
        // Deep-link target for the notification's Open action (M2).
        const val EXTRA_WORD = io.github.jtrv.quickword.lookup.EXTRA_WORD
    }
}
