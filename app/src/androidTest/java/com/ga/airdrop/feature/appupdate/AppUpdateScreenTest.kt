package com.ga.airdrop.feature.appupdate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the screen six routes already pointed at.
 *
 * The copy assertions are not decoration: they pin **parity with iOS
 * `FigmaAppUpdateViewController`**, which is the only reason both platforms say
 * the same thing to the same customer. If someone reworders one side, this goes
 * red rather than the two silently drifting — the failure mode already recorded
 * in [parity-claims-decay-silently].
 */
@RunWith(AndroidJUnit4::class)
class AppUpdateScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var updates = 0
    private var laters = 0
    private var backs = 0

    private fun render(availableVersionCode: Int?, releaseNotes: String?) {
        compose.setContent {
            AirdropTheme {
                AppUpdateScreen(
                    availableVersionCode = availableVersionCode,
                    installedVersionName = "3.1.3",
                    releaseNotes = releaseNotes,
                    onUpdate = { updates++ },
                    onLater = { laters++ },
                    onBack = { backs++ },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theScreenSaysExactlyWhatIosSays() {
        render(availableVersionCode = 26, releaseNotes = null)

        compose.onNodeWithText("Update Available").assertIsDisplayed()
        compose.onNodeWithText("A new version of AirDrop is ready").assertIsDisplayed()
        compose.onNodeWithText("Update Now").assertIsDisplayed()
        compose.onNodeWithText("Not Now").assertIsDisplayed()
    }

    @Test
    fun bothActionsFireAndAreDistinct() {
        render(availableVersionCode = 26, releaseNotes = null)

        compose.onNodeWithTag(AppUpdateTags.UPDATE).performClick()
        compose.waitForIdle()
        assertEquals("Update Now must fire onUpdate", 1, updates)
        assertEquals("Update Now must NOT count as declining", 0, laters)

        compose.onNodeWithTag(AppUpdateTags.LATER).performClick()
        compose.waitForIdle()
        assertEquals("Not Now must fire onLater", 1, laters)
        assertEquals(1, updates)
    }

    /**
     * ⚠️ The load-bearing one. Play gives us `availableVersionCode()` and
     * nothing else — no marketing version, no notes. When we do not have a
     * number we must print NO version line rather than "Build 0" or a guess.
     */
    @Test
    fun aMissingVersionPrintsNoVersionLineAtAll() {
        render(availableVersionCode = null, releaseNotes = null)

        compose.onNodeWithText("A new version of AirDrop is ready").assertIsDisplayed()
        assertEquals(
            "with no known version the build line must be absent entirely",
            0,
            compose.onAllNodesWithTag(AppUpdateTags.VERSION).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun aKnownVersionShowsBothTheNewBuildAndTheInstalledOne() {
        render(availableVersionCode = 26, releaseNotes = null)

        compose.onNodeWithTag(AppUpdateTags.VERSION).assertIsDisplayed()
        val text = compose.onNodeWithTag(AppUpdateTags.VERSION)
            .fetchSemanticsNode()
            .config
            .toString()
        assertTrue("must name the available build: $text", text.contains("26"))
        assertTrue("must name what the customer is on: $text", text.contains("3.1.3"))
    }

    /** Play never sends release notes; an empty card would be worse than none. */
    @Test
    fun whatsNewIsOmittedWhenThereAreNoNotesAndShownWhenThereAre() {
        render(availableVersionCode = 26, releaseNotes = null)
        assertEquals(
            "no notes must omit the whole What's New block",
            0,
            compose.onAllNodesWithTag(AppUpdateTags.NOTES).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun whatsNewRendersWhenAPushCarriedNotes() {
        render(availableVersionCode = 26, releaseNotes = "Checkout is fixed.")
        compose.onNodeWithTag(AppUpdateTags.NOTES).assertIsDisplayed()
        compose.onNodeWithText("What's New").assertIsDisplayed()
        compose.onNodeWithText("Checkout is fixed.").assertIsDisplayed()
    }
}
