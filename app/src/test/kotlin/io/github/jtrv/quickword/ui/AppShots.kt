package io.github.jtrv.quickword.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.jtrv.quickword.data.DictionaryRepository
import io.github.jtrv.quickword.data.HistoryStore
import io.github.jtrv.quickword.ui.theme.QuickWordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual review rig (kotlin-verify-loop §3): renders real screens against the
 * bundled fixture DB and writes PNGs to app/shots/. Every capture asserts
 * something that exists ONLY in the state its name claims, so a silent
 * wrong-state capture fails loudly instead of producing a lying PNG.
 * Run via `mise run shots`; excluded from `check`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AppShots {
    @get:Rule val compose = createComposeRule()

    private val timeoutMs = 10_000L

    private fun repo() = DictionaryRepository(ApplicationProvider.getApplicationContext())

    private fun history() = HistoryStore(ApplicationProvider.getApplicationContext())

    private fun searchShot(
        dark: Boolean,
        name: String,
    ) {
        compose.setContent {
            QuickWordTheme(darkTheme = dark) { QuickWordApp(repository = repo(), history = history()) }
        }
        compose.onNode(hasSetTextAction()).performTextInput("qu")
        // Proof of state: a suggestion row only present once the DB answered.
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText("quick").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().captureRoboImage("shots/$name.png")
    }

    private fun wordShot(
        dark: Boolean,
        name: String,
    ) {
        compose.setContent {
            QuickWordTheme(darkTheme = dark) {
                QuickWordApp(repository = repo(), history = history(), initialWord = "dog")
            }
        }
        // Proof of state: gloss text + a synonym chip exist only on the word page.
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText("hound").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("verb").assertExists()
        compose.onRoot().captureRoboImage("shots/$name.png")
    }

    @Test
    fun about() {
        compose.setContent {
            QuickWordTheme(darkTheme = false) { QuickWordApp(repository = repo(), history = history()) }
        }
        compose.onNodeWithContentDescription("About & licences").performClick()
        // Proof of state: verbatim OFL text exists only on the about screen.
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText("SIL OPEN FONT LICENSE", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().captureRoboImage("shots/about.png")
    }

    @Test fun searchLight() = searchShot(dark = false, name = "search_light")

    @Test fun searchDark() = searchShot(dark = true, name = "search_dark")

    @Test fun wordLight() = wordShot(dark = false, name = "word_light")

    @Test fun wordDark() = wordShot(dark = true, name = "word_dark")
}
