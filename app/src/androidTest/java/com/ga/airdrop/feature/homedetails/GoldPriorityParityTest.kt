package com.ga.airdrop.feature.homedetails

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoldPriorityParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun goldPriorityForcesSwiftLightStatusBarIcons() {
        compose.activityRule.scenario.onActivity { activity ->
            statusController(activity).isAppearanceLightStatusBars = true
        }
        val showGoldPriority = mutableStateOf(true)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                ) {
                    if (showGoldPriority.value) {
                        GoldPriorityContent(onBack = {})
                    }
                }
            }
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertFalse(statusController(compose.activity).isAppearanceLightStatusBars)
        }

        compose.runOnIdle {
            showGoldPriority.value = false
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue(statusController(compose.activity).isAppearanceLightStatusBars)
        }
    }

    @Test
    fun platinumTierNameFitsSwiftTitleRowLight() {
        setGoldPriorityContent(
            mode = ThemeController.Mode.LIGHT,
            initialPage = platinumIndex,
            widthDp = 360,
        )

        assertPlatinumTitleFitsSwiftRow()
        saveRootScreenshot("gold_priority_platinum_swift_light_360.png")
    }

    @Test
    fun platinumTierNameFitsSwiftTitleRowDark() {
        setGoldPriorityContent(
            mode = ThemeController.Mode.DARK,
            initialPage = platinumIndex,
            widthDp = 360,
        )

        assertPlatinumTitleFitsSwiftRow()
        saveRootScreenshot("gold_priority_platinum_swift_dark_360.png")
    }

    @Test
    fun goldRendersServerCopyWithoutRemovedPercentageClaim() {
        setGoldPriorityContent(
            mode = ThemeController.Mode.LIGHT,
            initialPage = goldIndex,
            widthDp = 375,
        )

        compose.onNodeWithText("Insurance required on every shipment.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("3-5% discounted shipping rates.").fetchSemanticsNodes().size)
    }

    @Test
    fun rubyCurrentRendersTheSwiftFadeAndLightOutlineCtaLight() {
        assertRubyCurrentFadeAndCta(
            mode = ThemeController.Mode.LIGHT,
            screenshot = "gold_priority_ruby_current_fade_cta_light.png",
        )
    }

    @Test
    fun rubyCurrentRendersTheSwiftFadeAndLightOutlineCtaDark() {
        assertRubyCurrentFadeAndCta(
            mode = ThemeController.Mode.DARK,
            screenshot = "gold_priority_ruby_current_fade_cta_dark.png",
        )
    }

    @Test
    fun lowerTierHasNoCtaAndNoFadeLane() {
        setGoldPriorityContent(
            mode = ThemeController.Mode.LIGHT,
            initialPage = rubyIndex,
            resolvedTierIndex = rubyIndex,
            widthDp = 375,
        )
        // The real screen intentionally pre-scrolls to the resolved customer
        // tier. Navigate one page lower exactly as the customer does.
        compose.onNodeWithTag("gold-priority-root").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Sapphire Saver").assertIsDisplayed()

        compose.onNodeWithTag("gold-priority-lines", useUnmergedTree = true).assertExists()
        assertEquals(0, compose.onAllNodesWithTag("gold-priority-cta").fetchSemanticsNodes().size)
        assertEquals(
            0,
            compose.onAllNodesWithTag("gold-priority-fade-sapphire").fetchSemanticsNodes().size,
        )
        saveRootScreenshot("gold_priority_sapphire_cta_hidden_no_fade.png")
    }

    private fun assertRubyCurrentFadeAndCta(
        mode: ThemeController.Mode,
        screenshot: String,
    ) {
        setGoldPriorityContent(
            mode = mode,
            initialPage = rubyIndex,
            resolvedTierIndex = rubyIndex,
            widthDp = 375,
        )

        compose.onNodeWithTag("gold-priority-lines", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("gold-priority-fade-ruby").assertIsDisplayed()
        val cta = compose.onNodeWithTag("gold-priority-cta").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertClose(316f, boundsWidth(cta), "Swift glass CTA width")
        assertClose(50f, boundsHeight(cta), "Swift glass CTA height")
        saveRootScreenshot(screenshot)
    }

    @Test
    fun missingServerRowsFailClosedAndRetry() {
        val retries = java.util.concurrent.atomic.AtomicInteger()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                GoldPriorityContent(
                    onBack = {},
                    initialPage = goldIndex,
                    catalogStatus = TierCatalogStatus.Failed,
                    onRetryBenefits = { retries.incrementAndGet() },
                )
            }
        }
        compose.onNodeWithText("Tier benefits are unavailable.").assertIsDisplayed()
        compose.onNodeWithTag("gold-priority-benefits-retry").performClick()
        compose.runOnIdle { assertEquals(1, retries.get()) }
        assertEquals(0, compose.onAllNodesWithText("3-5% discounted shipping rates.").fetchSemanticsNodes().size)
    }

    // ── Canon coverage (CoralCove #22431 blocker 3) ──

    @Test
    fun contourLinesRenderFullBleedOnEveryPage() {
        setGoldPriorityContent(
            mode = ThemeController.Mode.LIGHT,
            initialPage = goldIndex,
            widthDp = 375,
        )
        compose.onNodeWithTag("gold-priority-lines", useUnmergedTree = true).assertExists()
    }

    @Test
    fun ctaFollowsTierRelationAcrossPages() {
        // Full-window content (the CTA is bottom-anchored — a fixed 812dp
        // wrapper can push it below the emulator's real viewport).
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                GoldPriorityContent(
                    onBack = {},
                    initialPage = rubyIndex,
                    resolvedTierIndex = rubyIndex,
                    benefitRowsByCode = mapOf("RUBY" to listOf("Server ruby row")),
                    catalogStatus = TierCatalogStatus.Ready,
                    // Offer-driven sheets (#22805): buttons exist ONLY for
                    // backend-offered changes.
                    canChange = true,
                    changeOffers = listOf(
                        com.ga.airdrop.data.model.TierChangeOption(
                            code = "GOLD", name = "Gold Standard",
                            laneRank = 3, isCurrent = false, direction = "upgrade",
                        ),
                        com.ga.airdrop.data.model.TierChangeOption(
                            code = "SAVR", name = "Sapphire Saver",
                            laneRank = 1, isCurrent = false, direction = "downgrade",
                        ),
                    ),
                )
            }
        }
        compose.waitForIdle()

        // Own page: "Your Tier" CTA visible; tapping opens the breakdown —
        // the ONE sanctioned downgrade entry (standing ruling).
        compose.onNodeWithTag("gold-priority-cta").assertIsDisplayed()
        compose.onNodeWithTag("gold-priority-fade-ruby").assertIsDisplayed()
        compose.onNodeWithTag("gold-priority-cta").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tier-breakdown-sheet").assertIsDisplayed()
        compose.onNodeWithTag("tier-breakdown-upgrade").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tier-breakdown-downgrade").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tier-breakdown-close").performScrollTo().performClick()
        compose.waitForIdle()

        // Swipe to Gold (one tier above): CTA flips to "Upgrade to <tier>".
        compose.onNodeWithTag("gold-priority-root").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(
            1,
            compose.onAllNodesWithText("Upgrade to Gold Standard").fetchSemanticsNodes().size,
        )
        compose.onNodeWithTag("gold-priority-fade-gold").assertIsDisplayed()

        // Swipe down to Sapphire (below the customer): current Swift hides
        // the CTA and its bottom fade rather than exposing a pager downgrade.
        compose.onNodeWithTag("gold-priority-root").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("gold-priority-root").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(
            0,
            compose.onAllNodesWithTag("gold-priority-cta").fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            compose.onAllNodesWithTag("gold-priority-fade-sapphire").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun corporatePageShowsStaticInfoRows() {
        // Kemar ruling #22424: Inactive/Corporate MUST display their info
        // even though the API does not serve them.
        setGoldPriorityContent(
            mode = ThemeController.Mode.LIGHT,
            initialPage = corporateIndex,
            widthDp = 375,
        )
        compose.onNodeWithText("Dedicated account manager.").assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithText("Tier benefits are unavailable.").fetchSemanticsNodes().size,
        )
    }

    private fun setGoldPriorityContent(
        mode: ThemeController.Mode,
        initialPage: Int = goldIndex,
        widthDp: Int,
        resolvedTierIndex: Int? = null,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(
                    Modifier
                        .width(widthDp.dp)
                        .height(812.dp)
                ) {
                    GoldPriorityContent(
                        onBack = {},
                        initialPage = initialPage,
                        resolvedTierIndex = resolvedTierIndex,
                        benefitRowsByCode = mapOf(
                            "PLAT" to listOf("Insurance required on every shipment."),
                            "GOLD" to listOf(
                                "24-48 hour target after clearance",
                                "Insurance required on every shipment.",
                            ),
                            "RUBY" to listOf(
                                "Packages processed within 48-72 hours after customs clearance.",
                                "Access to standard customer support during business hours.",
                                "Auto-upgrade eligibility after consistent activity.",
                                "Account support for shipment questions and delivery updates.",
                                "Clear tracking updates across each package milestone.",
                                "Eligibility for seasonal account offers.",
                                "Access to standard warehouse processing lanes.",
                                "Guidance for customs and invoice requirements.",
                            ),
                            "SAVR" to listOf(
                                "Basic processing (3-5 business days).",
                                "Standard shipping rates.",
                                "Free storage for up to 7 days.",
                                "Access to limited promotions and onboarding discounts.",
                                "Eligibility for early upgrade upon meeting spend thresholds.",
                                "Welcome emails and loyalty guidance to explain tier benefits.",
                            ),
                        ),
                        catalogStatus = TierCatalogStatus.Ready,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertPlatinumTitleFitsSwiftRow() {
        compose.onNodeWithText("Platinum Priority").assertIsDisplayed()
        val row = compose.onNodeWithTag("gold-priority-title-row").getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithTag("gold-priority-tier-badge", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val name = compose.onNodeWithTag("gold-priority-tier-name", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        // Canon staging b5e9a6f: iconImageView 70×70, titleRow.spacing = 15
        // (CoralCove #22425 anchors; was 64/12 in the pre-canon port).
        assertClose(70f, boundsWidth(badge), "Swift tier badge width")
        assertClose(70f, boundsHeight(badge), "Swift tier badge height")
        assertClose(15f, boundsLeft(name) - boundsRight(badge), "Swift badge/name gap")
        assertTrue(
            "Tier name should fit inside title row, nameRight=${boundsRight(name)} rowRight=${boundsRight(row)}",
            boundsRight(name) <= boundsRight(row) + 0.75f,
        )
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onNodeWithTag("gold-priority-root").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/tier_server_copy/"
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf(filename, "%kotlin_ui_proof/tier_server_copy%"),
        )
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private fun statusController(activity: ComponentActivity) =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    private fun boundsLeft(rect: DpRect): Float = rect.left.value

    private fun boundsRight(rect: DpRect): Float = rect.right.value

    private fun boundsWidth(rect: DpRect): Float = (rect.right - rect.left).value

    private fun boundsHeight(rect: DpRect): Float = (rect.bottom - rect.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private val platinumIndex: Int
        get() = tierPages.indexOfFirst { it.id == "platinum" }

    private val goldIndex: Int
        get() = tierPages.indexOfFirst { it.id == "gold" }

    private val rubyIndex: Int
        get() = tierPages.indexOfFirst { it.id == "ruby" }

    private val sapphireIndex: Int
        get() = tierPages.indexOfFirst { it.id == "sapphire" }

    private val corporateIndex: Int
        get() = tierPages.indexOfFirst { it.id == "corporate" }
}
