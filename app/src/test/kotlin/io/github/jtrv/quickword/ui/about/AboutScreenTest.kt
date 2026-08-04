package io.github.jtrv.quickword.ui.about

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.jtrv.quickword.data.DictionaryRepository
import io.github.jtrv.quickword.data.HistoryStore
import io.github.jtrv.quickword.ui.QuickWordApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Licence-compliance contract: CC BY-SA attribution and the verbatim OFL text
 * must be reachable from the app's first screen and must actually be inside the
 * APK. Guards the two ways this silently breaks — the entry point disappearing
 * in a UI refactor, and the asset being dropped by resource shrinking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AboutScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aboutIsReachableAndCarriesLicenceTexts() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            QuickWordApp(repository = DictionaryRepository(context), history = HistoryStore(context))
        }

        compose.onNodeWithContentDescription("About & licences").performClick()

        listOf(
            "CC BY-SA 4.0", // Wiktionary attribution the licence requires
            "SIL OPEN FONT LICENSE Version 1.1", // verbatim OFL, from assets
            "The Literata Project Authors",
            "The Inter Project Authors",
        ).forEach { needle ->
            compose.waitUntil(TIMEOUT_MS) {
                compose.onAllNodesWithText(needle, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}

private const val TIMEOUT_MS = 5_000L
