package io.github.jtrv.quickword.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.github.jtrv.quickword.data.DictionaryRepository
import io.github.jtrv.quickword.data.HistoryStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A word that is in neither the dictionary nor Wikipedia used to fall through
 * to the search screen — so tapping a synonym chip that is not itself a
 * headword, or opening the app from the blocked-notification fallback, silently
 * dumped you back where you started with no explanation. The trampoline had
 * always said so out loud (LookupNotifier.showNoEntry); the app had not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickWordAppTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun deadEndWordExplainsItselfInsteadOfSilentlyReturningToSearch() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            QuickWordApp(
                repository = DictionaryRepository(context),
                history = HistoryStore(context),
                // Not a headword, and Wikipedia is unreachable under Robolectric —
                // exactly the state that used to render nothing at all.
                initialWord = MISSING_WORD,
            )
        }

        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(MISSING_WORD, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No entry for", substring = true).assertExists()
        // Proof it is not the search screen wearing a "no results" label: the
        // search field exists only there.
        compose.onNodeWithText("Search the dictionary").assertDoesNotExist()
    }
}

private const val MISSING_WORD = "zzzznotaword"
private const val TIMEOUT_MS = 5_000L
