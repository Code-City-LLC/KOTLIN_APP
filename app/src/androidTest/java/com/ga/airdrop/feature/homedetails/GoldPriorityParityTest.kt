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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
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
import androidx.test.espresso.Espresso.pressBack
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
    fun lowerTierHasNoCtaButKeepsTheScreenLevelFade() {
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
        compose.onNodeWithTag("gold-priority-fade-sapphire").assertIsDisplayed()
        saveRootScreenshot("gold_priority_sapphire_cta_hidden_with_fade.png")
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
        val root = compose.onNodeWithTag("gold-priority-root").getUnclippedBoundsInRoot()
        val fade = compose.onNodeWithTag("gold-priority-fade-ruby")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val cta = compose.onNodeWithTag("gold-priority-cta").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertClose(316f, boundsWidth(cta), "Swift glass CTA width")
        assertClose(50f, boundsHeight(cta), "Swift glass CTA height")

        // The fade is a bottom-only sibling behind the CTA. It starts well
        // below the page top and finishes opaque at the physical screen edge.
        assertTrue(
            "bottom fade must not cover the page top",
            boundsTop(fade) > boundsTop(root),
        )
        assertClose(boundsBottom(root), boundsBottom(fade), "fade reaches page bottom")
        assertClose(
            TierFadeHeight.value + 12f,
            boundsTop(cta) - boundsTop(fade),
            "fade starts 64dp above the CTA lane",
        )
        val navigationInset = boundsBottom(root) - boundsBottom(cta) - 12f
        assertTrue("navigation inset cannot be negative", navigationInset >= -0.75f)
        assertClose(
            TierFadeHeight.value + TierCtaClearance.value + navigationInset.coerceAtLeast(0f),
            boundsHeight(fade),
            "fade covers dissolve, CTA, and navigation lanes",
        )

        val rootBitmap = compose.onNodeWithTag("gold-priority-root")
            .captureToImage()
            .asAndroidBitmap()
        val expectedTop = tierPages[rubyIndex].gradientTop.toArgb()
        val topPixel = rootBitmap.getPixel(4, 4)
        assertTrue(
            "tier header must not apply a top fade; " +
                "actual=${topPixel.toUInt().toString(16)} " +
                "expected=${expectedTop.toUInt().toString(16)}",
            topPixel.isNear(expectedTop, tolerance = 12),
        )
        val expectedBottom = tierPages[rubyIndex].gradientBottom.toArgb()
        val bottomPixel = rootBitmap.getPixel(4, rootBitmap.height - 2)
        assertTrue(
            "fade must finish opaque in the active tier bottom color; " +
                "actual=${bottomPixel.toUInt().toString(16)} " +
                "expected=${expectedBottom.toUInt().toString(16)}",
            bottomPixel.isNear(expectedBottom),
        )
        saveRootScreenshot(screenshot)

        // The overlay must not intercept the real CTA tap.
        compose.onNodeWithTag("gold-priority-cta").performClick()
        compose.onNodeWithTag("tier-breakdown-sheet").assertIsDisplayed()
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
                    benefitRowsByCode = mapOf(
                        "RUBY" to listOf(
                            "Shared benefit",
                            "Ruby-only priority support",
                            "Ruby account support for shipment questions and delivery updates.",
                            "Ruby tracking updates across each package milestone.",
                            "Ruby eligibility for seasonal account offers.",
                            "Ruby warehouse processing guidance.",
                        ),
                        "GOLD" to listOf(
                            "Shared benefit",
                            "Gold faster processing",
                            "Gold storage for all incoming packages.",
                            "Gold loyalty rewards during AirDrop events.",
                            "Gold early sale notifications.",
                            "Gold support-line priority over standard tiers.",
                        ),
                        "SAVR" to listOf(
                            "Shared benefit",
                            "Sapphire basic processing",
                            "Sapphire standard shipping rates.",
                            "Sapphire limited promotions and onboarding discounts.",
                            "Sapphire loyalty guidance for tier benefits.",
                        ),
                    ),
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
        compose.onNodeWithTag("tier-breakdown-downgrade").performClick()
        compose.waitForIdle()

        // Swift downgrade confirmation: explicit loss disclosure, safe choice
        // first, destructive action second, both pinned outside the scroll.
        val keepTopBeforeScroll = boundsTop(
            compose.onNodeWithTag("tier-change-cancel").assertIsDisplayed()
                .getUnclippedBoundsInRoot(),
        )
        val downgradeTopBeforeScroll = boundsTop(
            compose.onNodeWithTag("tier-change-confirm").assertIsDisplayed()
                .getUnclippedBoundsInRoot(),
        )
        compose.onNodeWithText("Downgrade to Sapphire Saver?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Are you sure? Here's what you'd be giving up:")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tier-change-lost-benefits").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tier-change-lost-benefits-row-0")
            .assertTextEquals("Ruby-only priority support")
        compose.onNodeWithText("What you'll have on Sapphire Saver")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sapphire basic processing").performScrollTo().assertIsDisplayed()
        val keep = compose.onNodeWithTag("tier-change-cancel")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val downgrade = compose.onNodeWithTag("tier-change-confirm")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        assertClose(52f, boundsHeight(keep), "Swift safe downgrade button height")
        assertClose(52f, boundsHeight(downgrade), "Swift destructive button height")
        assertClose(keepTopBeforeScroll, boundsTop(keep), "Pinned safe action top")
        assertClose(downgradeTopBeforeScroll, boundsTop(downgrade), "Pinned destructive action top")
        assertTrue("Safe action must precede destructive action", boundsTop(keep) < boundsTop(downgrade))
        compose.onNodeWithTag("tier-change-cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("tier-change-sheet").fetchSemanticsNodes().isEmpty()
        }

        // Swipe to Gold (one tier above): CTA flips to "Upgrade to <tier>".
        compose.onNodeWithTag("gold-priority-root").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(
            1,
            compose.onAllNodesWithText("Upgrade to Gold Standard").fetchSemanticsNodes().size,
        )
        compose.onNodeWithTag("gold-priority-fade-gold").assertIsDisplayed()
        compose.onNodeWithTag("gold-priority-cta").performClick()
        compose.waitForIdle()

        // Swift upgrade confirmation: no question mark, exact copy, frosted
        // benefit panel, and actions pinned outside the scrolling content.
        val upgradeTopBeforeScroll = boundsTop(
            compose.onNodeWithTag("tier-change-confirm").assertIsDisplayed()
                .getUnclippedBoundsInRoot(),
        )
        val notNowTopBeforeScroll = boundsTop(
            compose.onNodeWithTag("tier-change-cancel").assertIsDisplayed()
                .getUnclippedBoundsInRoot(),
        )
        compose.onNodeWithTag("tier-change-title").performScrollTo().assertIsDisplayed()
            .assertTextEquals("Upgrade to Gold Standard")
        compose.onNodeWithText("Upgrade to Gold Standard?").assertDoesNotExist()
        compose.onNodeWithTag("tier-change-subtitle").performScrollTo().assertIsDisplayed()
            .assertTextEquals("Changes apply immediately. Here's what you'll get:")
        compose.onNodeWithText("What you'll get").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tier-change-benefits-row-1")
            .assertTextEquals("Gold faster processing")
        compose.onNodeWithTag("tier-change-footnote").performScrollTo().assertIsDisplayed()
            .assertTextEquals(
                "Tier changes are applied by AirDrop and take effect immediately. " +
                    "Any open shipment quote must be refreshed.",
            )
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        assertClose(
            64f,
            boundsHeight(compose.onNodeWithTag("tier-change-glyph").getUnclippedBoundsInRoot()),
            "Swift tier glyph height",
        )
        val upgrade = compose.onNodeWithTag("tier-change-confirm")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val notNow = compose.onNodeWithTag("tier-change-cancel")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        assertClose(52f, boundsHeight(upgrade), "Swift upgrade button height")
        assertClose(44f, boundsHeight(notNow), "Swift Not Now button height")
        assertClose(upgradeTopBeforeScroll, boundsTop(upgrade), "Pinned upgrade action top")
        assertClose(notNowTopBeforeScroll, boundsTop(notNow), "Pinned Not Now action top")
        assertTrue("Upgrade action must precede Not Now", boundsTop(upgrade) < boundsTop(notNow))
        compose.onNodeWithTag("tier-change-cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("tier-change-sheet").fetchSemanticsNodes().isEmpty()
        }

        // Swipe down to Sapphire (below the customer): accepted Swift hides
        // the CTA but keeps the shared screen-level bottom fade.
        compose.onNodeWithTag("gold-priority-root").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("gold-priority-root").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(
            0,
            compose.onAllNodesWithTag("gold-priority-cta").fetchSemanticsNodes().size,
        )
        compose.onNodeWithTag("gold-priority-fade-sapphire").assertIsDisplayed()
    }

    @Test
    fun tierChangeErrorWorkingAndSuccessMatchSwiftStateMachine() {
        val phase = mutableStateOf(TierChangePhase.Error)
        var confirmCalls = 0
        var doneCalls = 0
        var dismissCalls = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                TierChangeSheet(
                    target = tierPages[goldIndex],
                    current = tierPages[rubyIndex],
                    targetBenefits = listOf("Gold faster processing"),
                    currentBenefits = listOf("Ruby standard processing"),
                    isUpgrade = true,
                    phase = phase.value,
                    successName = "Gold Standard",
                    successMessage = "Gold Standard is now active.",
                    error = "We couldn't confirm the change.",
                    onConfirm = {
                        confirmCalls++
                        phase.value = TierChangePhase.Working
                    },
                    onDone = { doneCalls++ },
                    onDismiss = { dismissCalls++ },
                )
            }
        }
        compose.waitForIdle()

        val confirmationFrameHeight = boundsHeight(
            compose.onNodeWithTag("tier-change-sheet-content").getUnclippedBoundsInRoot(),
        )
        compose.onNodeWithTag("tier-change-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Upgrade Now").assertIsDisplayed()
        compose.onNodeWithText("Not Now").assertIsDisplayed()
        compose.onNodeWithText("Try Again").assertDoesNotExist()
        compose.onNodeWithTag("tier-change-confirm").performClick()
        compose.waitForIdle()

        compose.runOnIdle { assertEquals(1, confirmCalls) }
        compose.onNodeWithTag("tier-change-spinner").assertIsDisplayed()
        compose.onNodeWithTag("tier-change-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("tier-change-cancel").assertIsNotEnabled()
        pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("tier-change-sheet").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissCalls) }

        compose.runOnIdle { phase.value = TierChangePhase.Success }
        compose.waitForIdle()
        compose.onNodeWithTag("tier-change-success-icon").assertIsDisplayed()
        compose.onNodeWithText("Welcome to Gold Standard").assertIsDisplayed()
        compose.onNodeWithText("Gold Standard is now active.").assertIsDisplayed()
        val successFrame = compose.onNodeWithTag("tier-change-success-frame")
            .getUnclippedBoundsInRoot()
        val successContent = compose.onNodeWithTag("tier-change-success-content")
            .getUnclippedBoundsInRoot()
        assertClose(
            confirmationFrameHeight,
            boundsHeight(compose.onNodeWithTag("tier-change-sheet-content").getUnclippedBoundsInRoot()),
            "Swift success preserves confirmation card height",
        )
        assertClose(
            boundsCenterY(successFrame),
            boundsCenterY(successContent),
            "Swift success content is vertically centered",
        )
        val done = compose.onNodeWithTag("tier-change-done").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertClose(200f, boundsWidth(done), "Swift Done button width")
        assertClose(52f, boundsHeight(done), "Swift Done button height")
        compose.onNodeWithTag("tier-change-done").performClick()
        compose.runOnIdle { assertEquals(1, doneCalls) }
    }

    @Test
    fun downgradeFailsClosedWhenBenefitDisclosureIsMissing() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                TierChangeSheet(
                    target = tierPages[rubyIndex],
                    current = tierPages[goldIndex],
                    targetBenefits = null,
                    currentBenefits = listOf("Gold faster processing"),
                    isUpgrade = false,
                    phase = TierChangePhase.Idle,
                    successName = null,
                    successMessage = null,
                    error = null,
                    onConfirm = {},
                    onDone = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Tier benefits are unavailable.").assertIsDisplayed()
        compose.onNodeWithTag("tier-change-lost-benefits").assertDoesNotExist()
        compose.onNodeWithTag("tier-change-benefits").assertDoesNotExist()
        compose.onNodeWithTag("tier-change-confirm").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("tier-change-cancel").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun authenticatedSessionReplacementClosesOpenChangeSheet() {
        val sessionEpoch = mutableStateOf(0L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                GoldPriorityContent(
                    onBack = {},
                    sessionEpoch = sessionEpoch.value,
                    initialPage = rubyIndex,
                    resolvedTierIndex = rubyIndex,
                    benefitRowsByCode = mapOf(
                        "RUBY" to listOf("Ruby processing"),
                        "GOLD" to listOf("Gold processing"),
                    ),
                    catalogStatus = TierCatalogStatus.Ready,
                    canChange = true,
                    changeOffers = listOf(
                        com.ga.airdrop.data.model.TierChangeOption(
                            code = "GOLD",
                            name = "Gold Standard",
                            laneRank = 3,
                            isCurrent = false,
                            direction = "upgrade",
                        ),
                    ),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("gold-priority-root").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("gold-priority-cta").performClick()
        compose.onNodeWithTag("tier-change-sheet").assertIsDisplayed()

        compose.runOnIdle { sessionEpoch.value++ }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("tier-change-sheet").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("tier-change-sheet").assertDoesNotExist()
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
        // Staging and production cannot replace each other's scoped MediaStore rows.
        val relativePath = "Pictures/kotlin_ui_proof/tier_server_copy/${context.packageName}/"
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
            arrayOf(filename, relativePath),
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

    private fun boundsTop(rect: DpRect): Float = rect.top.value

    private fun boundsBottom(rect: DpRect): Float = rect.bottom.value

    private fun boundsWidth(rect: DpRect): Float = (rect.right - rect.left).value

    private fun boundsHeight(rect: DpRect): Float = (rect.bottom - rect.top).value

    private fun boundsCenterY(rect: DpRect): Float = ((rect.top + rect.bottom) / 2f).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun Int.isNear(target: Int, tolerance: Int = 6): Boolean {
        return kotlin.math.abs(((this shr 16) and 0xFF) - ((target shr 16) and 0xFF)) <= tolerance &&
            kotlin.math.abs(((this shr 8) and 0xFF) - ((target shr 8) and 0xFF)) <= tolerance &&
            kotlin.math.abs((this and 0xFF) - (target and 0xFF)) <= tolerance
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
