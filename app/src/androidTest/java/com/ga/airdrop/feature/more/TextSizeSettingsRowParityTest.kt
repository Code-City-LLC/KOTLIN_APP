package com.ga.airdrop.feature.more

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.TextSizeController
import com.ga.airdrop.core.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PR92 review #24601: real routing + real editor proof.
 *
 * - Settings' Text Size row must NAVIGATE to Preferences (current Swift
 *   routing) — proven on the REAL SettingsScreen, not a detached row.
 * - Preferences owns the single controller-backed editor — proven by real
 *   PreferencesScreen selection, then a REAL-CONTEXT SharedPreferences
 *   re-initialization (the durable-persistence proof) and a Compose
 *   recreation restore (StateRestorationTester exercises saved-instance
 *   state only — it is NOT a process-death simulation; the re-init
 *   assertion covers durability).
 * - Test hygiene: the exact prior level is captured before and restored
 *   after EVERY test, fail-safely, so no global persisted state leaks.
 */
@RunWith(AndroidJUnit4::class)
class TextSizeSettingsRowParityTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var priorLevel: TextSizeController.Level

    @Before
    fun capturePriorLevel() {
        priorLevel = TextSizeController.level
        TextSizeController.set(TextSizeController.Level.STANDARD)
    }

    @After
    fun restorePriorLevel() {
        // Fail-safe: runs whether the test passed, failed, or threw.
        TextSizeController.set(priorLevel)
    }

    @Test
    fun settingsTextSizeRowNavigatesToPreferences() {
        var navigatedTo: String? = null
        compose.setContent {
            AirdropTheme {
                SettingsScreen(
                    onBack = {},
                    onNavigate = { navigatedTo = it },
                    onLoggedOut = {},
                )
            }
        }

        compose.onNodeWithText("Text Size").assertIsDisplayed()
        compose.onNodeWithText("Standard").assertIsDisplayed()

        compose.onNodeWithTag("${SettingsTags.TEXT_SIZE}-row").performClick()
        assertEquals(
            "Settings' Text Size row must route to Preferences — the editor lives there",
            Routes.PREFERENCES,
            navigatedTo,
        )
    }

    @Test
    fun preferencesOwnsTheEditorSelectionAppliesAndSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AirdropTheme {
                PreferencesScreen(onBack = {})
            }
        }

        // Real Preferences: the Text Size field shows the current level.
        compose.onNodeWithText("Text Size").assertIsDisplayed()
        compose.onNodeWithText("Standard").assertIsDisplayed()

        // Open the ONE controller-backed editor and pick Largest. The
        // clickable surface is the field box (its value text), not the
        // label above it.
        compose.onNodeWithText("Standard").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Largest").performClick()
        compose.waitForIdle()

        assertEquals(TextSizeController.Level.LARGEST, TextSizeController.level)
        compose.onNodeWithText("Largest").assertIsDisplayed()

        // Durable persistence: the UI selection above committed synchronously,
        // so re-initializing the controller from the REAL app SharedPreferences
        // (a fresh disk read that overwrites the in-memory value) must yield
        // Largest — had the commit not landed, this re-init would read the
        // @Before Standard and fail.
        TextSizeController.init(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        assertEquals(
            "real-context re-init must read back the committed selection",
            TextSizeController.Level.LARGEST,
            TextSizeController.level,
        )

        // Compose recreation (saved-instance state restore — NOT process
        // death): the screen still reflects Largest afterwards.
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("Largest").assertIsDisplayed()
        assertEquals(TextSizeController.Level.LARGEST, TextSizeController.level)
    }
}
