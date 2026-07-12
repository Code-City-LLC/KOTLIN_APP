package com.ga.airdrop.feature.homedetails

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
                    tierState = GoldPriorityUiState(catalogStatus = TierCatalogStatus.Failed),
                    onRetryBenefits = { retries.incrementAndGet() },
                )
            }
        }
        compose.onNodeWithText("Tier benefits are unavailable.").assertIsDisplayed()
        compose.onNodeWithTag("gold-priority-benefits-retry").performClick()
        compose.runOnIdle { assertEquals(1, retries.get()) }
        assertEquals(0, compose.onAllNodesWithText("3-5% discounted shipping rates.").fetchSemanticsNodes().size)
    }

    @Test
    fun currentTierUpgradeFlowOpensConfirmationAndRequestsServerOffer() {
        val requested = java.util.concurrent.atomic.AtomicReference<String?>()
        setTierFlowContent(onRequestChange = requested::set)

        compose.onNodeWithTag("tier-change-cta").assertIsDisplayed().performClick()
        compose.onNodeWithTag("your-tier-sheet").assertIsDisplayed()
        compose.onNodeWithTag("your-tier-upgrade").assertIsDisplayed().performClick()
        compose.onNodeWithTag("tier-change-sheet").assertIsDisplayed()
        compose.onNodeWithTag("tier-sheet-confirm").assertIsDisplayed().performClick()

        compose.runOnIdle { assertEquals("GOLD", requested.get()) }
    }

    @Test
    fun awaitingConfirmationSheetOffersRetryGetAndNoSecondPatch() {
        val state = mutableStateOf(tierFlowState())
        val retries = java.util.concurrent.atomic.AtomicInteger()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(Modifier.width(375.dp).height(812.dp)) {
                    GoldPriorityContent(
                        onBack = {},
                        tierState = state.value,
                        onRetryConfirmation = { retries.incrementAndGet() },
                    )
                }
            }
        }
        compose.onNodeWithTag("tier-change-cta").performClick()
        compose.onNodeWithTag("your-tier-upgrade").performClick()

        compose.runOnIdle {
            state.value = state.value.copy(
                awaitingConfirmation = true,
                awaitingCode = "GOLD",
                changeError = "Couldn't confirm the requested tier — retry.",
            )
        }
        compose.onNodeWithTag("tier-sheet-retry-confirmation").assertIsDisplayed().performClick()
        compose.onNodeWithTag("tier-sheet-confirm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries.get()) }
    }

    @Test
    fun serverUpgradeDirectionOverridesPageOrderInComposeCta() {
        val requested = java.util.concurrent.atomic.AtomicReference<String?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(Modifier.width(375.dp).height(812.dp)) {
                    GoldPriorityContent(
                        onBack = {},
                        tierState = tierFlowState().copy(
                            offers = listOf(
                                TierOffer("SAVR", "Sapphire Saver", 99, "upgrade"),
                            ),
                        ),
                        onRequestChange = requested::set,
                    )
                }
            }
        }

        compose.onNodeWithTag("gold-priority-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("tier-change-cta")
            .assertIsDisplayed()
            .assertTextEquals("Upgrade to Sapphire Saver")
            .performClick()
        compose.onNodeWithTag("tier-sheet-confirm").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("SAVR", requested.get()) }
    }

    @Test
    fun tierVisualEvidenceMatrixLight() {
        captureTierVisualEvidence(ThemeController.Mode.LIGHT, "light")
    }

    @Test
    fun tierVisualEvidenceMatrixDark() {
        captureTierVisualEvidence(ThemeController.Mode.DARK, "dark")
    }

    private fun captureTierVisualEvidence(mode: ThemeController.Mode, suffix: String) {
        val requested = java.util.concurrent.atomic.AtomicReference<String?>()
        val rubyIndex = tierPages.indexOfFirst { it.apiCode == "RUBY" }
        val state = mutableStateOf(evidenceState())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(Modifier.fillMaxSize()) {
                    GoldPriorityContent(
                        onBack = {},
                        initialPage = rubyIndex,
                        tierState = state.value,
                        onRequestChange = requested::set,
                    )
                }
            }
        }

        var visibleIndex = rubyIndex
        fun navigateTo(targetIndex: Int) {
            while (visibleIndex < targetIndex) {
                compose.onNodeWithTag("gold-priority-pager").performTouchInput { swipeLeft() }
                visibleIndex++
                compose.waitForIdle()
            }
            while (visibleIndex > targetIndex) {
                compose.onNodeWithTag("gold-priority-pager").performTouchInput { swipeRight() }
                visibleIndex--
                compose.waitForIdle()
            }
        }

        tierPages.forEachIndexed { index, page ->
            navigateTo(index)
            compose.onNodeWithText(page.name).assertIsDisplayed()
            when (page.apiCode) {
                "DIAM" -> compose.onNodeWithTag("tier-change-cta")
                    .assertTextEquals("Upgrade to Diamond Elite")
                "PLAT" -> compose.onNodeWithTag("tier-change-cta")
                    .assertTextEquals("Upgrade to Platinum Priority")
                "GOLD" -> compose.onNodeWithTag("tier-change-cta")
                    .assertTextEquals("Upgrade to Gold Standard")
                "RUBY" -> compose.onNodeWithTag("tier-change-cta")
                    .assertTextEquals("Your Tier")
                else -> compose.onNodeWithTag("tier-change-cta").assertDoesNotExist()
            }
            saveRootScreenshot("tier_${page.id}_$suffix.png")
        }

        navigateTo(rubyIndex)
        compose.onNodeWithTag("tier-change-cta").performClick()
        saveTaggedComposeScreenshot("your-tier-sheet", "tier_current_breakdown_$suffix.png")
        compose.onNodeWithTag("your-tier-upgrade").performClick()
        compose.onNodeWithTag("tier-change-sheet").assertIsDisplayed()
        saveTaggedComposeScreenshot("tier-change-sheet-content", "tier_upgrade_top_$suffix.png")
        compose.onNodeWithTag("tier-sheet-confirm").performScrollTo().assertIsDisplayed()
        saveTaggedComposeScreenshot("tier-change-actions", "tier_upgrade_actions_$suffix.png")

        compose.runOnIdle {
            state.value = state.value.copy(
                awaitingConfirmation = true,
                awaitingCode = "GOLD",
                changeError = "Couldn't confirm the requested tier — retry.",
            )
        }
        compose.onNodeWithTag("tier-sheet-retry-confirmation").performScrollTo().assertIsDisplayed()
        saveTaggedComposeScreenshot("tier-change-sheet-content", "tier_confirmation_retry_actions_$suffix.png")

        compose.runOnIdle {
            state.value = state.value.copy(
                awaitingConfirmation = false,
                awaitingCode = null,
                changeError = null,
                changeSuccessName = "Gold Standard",
            )
        }
        compose.onNodeWithTag("tier-sheet-done").assertIsDisplayed()
        saveTaggedComposeScreenshot("tier-change-sheet-content", "tier_change_success_$suffix.png")
        compose.onNodeWithTag("tier-sheet-done").performClick()

        compose.runOnIdle {
            state.value = evidenceState()
        }
        compose.onNodeWithTag("tier-change-cta").performClick()
        compose.onNodeWithTag("your-tier-downgrade").performClick()
        compose.onNodeWithTag("tier-change-sheet").assertIsDisplayed()
        saveTaggedComposeScreenshot("tier-change-sheet-content", "tier_downgrade_top_$suffix.png")
        compose.onNodeWithTag("tier-sheet-confirm").performScrollTo().assertIsDisplayed()
        saveTaggedComposeScreenshot("tier-change-actions", "tier_downgrade_actions_$suffix.png")
        compose.onNodeWithTag("tier-sheet-confirm").performClick()
        compose.runOnIdle { assertEquals("SAVR", requested.get()) }
    }

    private fun setTierFlowContent(onRequestChange: (String) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropThemeProvider {
                Box(Modifier.width(375.dp).height(812.dp)) {
                    GoldPriorityContent(
                        onBack = {},
                        tierState = tierFlowState(),
                        onRequestChange = onRequestChange,
                    )
                }
            }
        }
    }

    private fun tierFlowState(): GoldPriorityUiState {
        val ruby = tierPages.indexOfFirst { it.apiCode == "RUBY" }
        return GoldPriorityUiState(
            resolvedTierIndex = ruby,
            tierConfirmed = true,
            currentTierCode = "RUBY",
            canChange = true,
            offers = listOf(
                TierOffer("GOLD", "Gold Standard", 3, "upgrade"),
                TierOffer("SAVR", "Sapphire Saver", 1, "downgrade"),
            ),
            benefitRowsByCode = mapOf(
                "RUBY" to listOf("Standard 2–3 business day processing."),
                "GOLD" to listOf("Free storage for 30 days on all incoming packages."),
                "SAVR" to listOf("Basic processing (3–5 business days)."),
            ),
            catalogStatus = TierCatalogStatus.Ready,
        )
    }

    private fun evidenceState(): GoldPriorityUiState {
        val index = tierPages.indexOfFirst { it.apiCode == "RUBY" }
        return GoldPriorityUiState(
            resolvedTierIndex = index.takeIf { it >= 0 },
            tierConfirmed = true,
            currentTierCode = "RUBY",
            canChange = true,
            offers = listOf(
                TierOffer("DIAM", "Diamond Elite", 5, "upgrade"),
                TierOffer("PLAT", "Platinum Priority", 4, "upgrade"),
                TierOffer("GOLD", "Gold Standard", 3, "upgrade"),
                TierOffer("SAVR", "Sapphire Saver", 1, "downgrade"),
            ),
            benefitRowsByCode = evidenceBenefitRows,
            catalogStatus = TierCatalogStatus.Ready,
        )
    }

    private val evidenceBenefitRows = mapOf(
        "DIAM" to listOf(
            "Next-day shipping on all packages.",
            "Priority logging, warehouse handling, and customs clearance.",
            "Dedicated WhatsApp VIP line for real-time assistance.",
            "Exclusive discounts and AirCoins multipliers on every shipment.",
            "Free storage for up to 60 days.",
            "Early access to clearance events, auctions, and flash sales.",
            "Personalized account concierge for dispute or issue resolution.",
            "Surprise appreciation gifts for milestone achievements (e.g. 100th shipment, 1-year VIP anniversary).",
            "Insurance required on every shipment",
        ),
        "PLAT" to listOf(
            "Expedited 24-hour processing for all cleared packages.",
            "Free storage for up to 45 days.",
            "Premium customer support queue with faster handling.",
            "Double AirCoins events and random loyalty gifts.",
            "Priority in pre-auction and sales events.",
            "Access to affiliate and referral bonuses.",
            "Complimentary upgrade offers during seasonal promotions.",
            "Insurance required on every shipment",
        ),
        "GOLD" to listOf(
            "Free storage for 30 days on all incoming packages.",
            "Processing within 24-48 hours of package clearance.",
            "Standard loyalty rewards plus double-points promotions during AirDrop events.",
            "Early notifications for sales, warehouse auctions, and holiday offers.",
            "General support line priority over standard-tier members.",
            "Eligibility for seasonal upgrade offers.",
            "Insurance required on every shipment",
        ),
        "RUBY" to listOf(
            "Standard 2–3 business day processing.",
            "Free storage for up to 15 days.",
            "Competitive base shipping rates.",
            "Exclusive partner coupons and limited-time promos.",
            "Access to standard customer support channels during business hours.",
            "Auto-upgrade eligibility after 12 months of consistent activity.",
            "Insurance required on every shipment",
        ),
        "SAVR" to listOf(
            "Basic processing (3–5 business days).",
            "Standard shipping rates.",
            "Free storage for up to 7 days.",
            "Access to limited promotions and onboarding discounts.",
            "Eligibility for early upgrade upon meeting spend thresholds.",
            "Welcome emails and loyalty guidance to familiarize them with benefits.",
            "Insurance optional — select or decline at checkout",
        ),
    )

    private fun setGoldPriorityContent(
        mode: ThemeController.Mode,
        initialPage: Int = goldIndex,
        widthDp: Int,
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
                        tierState = GoldPriorityUiState(
                            benefitRowsByCode = mapOf(
                                "PLAT" to listOf("Insurance required on every shipment."),
                                "GOLD" to listOf(
                                    "24-48 hour target after clearance",
                                    "Insurance required on every shipment.",
                                ),
                            ),
                            catalogStatus = TierCatalogStatus.Ready,
                        ),
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

        assertClose(70f, boundsWidth(badge), "Swift tier badge width")
        assertClose(64f, boundsHeight(badge), "Swift tier badge height")
        assertClose(12f, boundsLeft(name) - boundsRight(badge), "Swift badge/name gap")
        assertTrue(
            "Tier name should fit inside title row, nameRight=${boundsRight(name)} rowRight=${boundsRight(row)}",
            boundsRight(name) <= boundsRight(row) + 0.75f,
        )
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onNodeWithTag("gold-priority-root").captureToImage().asAndroidBitmap()
        saveScreenshot(bitmap, filename)
    }

    private fun saveTaggedComposeScreenshot(tag: String, filename: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(tag)
            .assertIsDisplayed()
            .captureToImage()
            .asAndroidBitmap()
        saveScreenshot(bitmap, filename)
    }

    private fun saveScreenshot(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/tier_server_copy/"
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME}=?",
            arrayOf(filename),
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
}
