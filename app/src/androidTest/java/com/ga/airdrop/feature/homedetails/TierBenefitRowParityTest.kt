package com.ga.airdrop.feature.homedetails

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.TierChangeOption
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeaderTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TierBenefitRowParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allSevenPagesWrapLongCopyWithFixedPageTicks() {
        setLightTheme()
        val apiRows = tierPages
            .mapNotNull { page -> page.apiCode?.let { it to longRows } }
            .toMap()

        compose.setContent {
            AirdropThemeProvider {
                GoldPriorityContent(
                    onBack = {},
                    initialPage = 0,
                    benefitRowsByCode = apiRows,
                    catalogStatus = TierCatalogStatus.Ready,
                )
            }
        }
        compose.waitForIdle()

        tierPages.forEachIndexed { index, tier ->
            val wrapIndex = when (tier.id) {
                "inactive" -> 1
                "corporate" -> 2
                else -> 1
            }
            val firstTextTag = "tier-page-${tier.id}-benefit-text-0"
            val wrappedTextTag = "tier-page-${tier.id}-benefit-text-$wrapIndex"
            val firstIconTag = "tier-page-${tier.id}-benefit-icon-0"
            val wrappedIconTag = "tier-page-${tier.id}-benefit-icon-$wrapIndex"

            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag(wrappedTextTag).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag(wrappedTextTag).performScrollTo().assertIsDisplayed()
            assertFixedSize(firstIconTag, 24f)
            assertFixedSize(wrappedIconTag, 24f)
            assertWrapsPastIcon(wrappedTextTag, wrappedIconTag)
            assertClose(
                boundsLeft(compose.onNodeWithTag(firstTextTag).getUnclippedBoundsInRoot()),
                boundsLeft(compose.onNodeWithTag(wrappedTextTag).getUnclippedBoundsInRoot()),
                "${tier.name} text leading column",
            )

            if (tier.apiCode != null) {
                compose.onNodeWithTag("tier-page-${tier.id}-benefit-text-0")
                    .assertTextEquals(LongLoyaltyCopy)
                compose.onNodeWithTag("tier-page-${tier.id}-benefit-text-1")
                    .assertTextEquals(LongSaleCopy)
                compose.onNodeWithTag("tier-page-${tier.id}-benefit-text-2")
                    .assertTextEquals(LongAutoUpgradeCopy)
            }

            saveScreenshot(
                nodeTag = "gold-priority-root",
                filename = "page-${tier.id}-${proofSuffix()}.png",
            )

            if (index < tierPages.lastIndex) {
                compose.onNodeWithTag("gold-priority-root").performTouchInput { swipeLeft() }
                compose.waitForIdle()
            }
        }
    }

    @Test
    fun breakdownUpgradeAndDowngradeScrollWrapAndReturn() {
        setLightTheme()
        var pageBackCalls = 0
        val ruby = tierPages.indexOfFirst { it.id == "ruby" }
        val gold = tierPages.indexOfFirst { it.id == "gold" }
        val sapphire = tierPages.indexOfFirst { it.id == "sapphire" }
        val rowsByCode = mapOf(
            "RUBY" to longRows,
            "GOLD" to listOf(LongLoyaltyCopy, LongSaleCopy, LongAutoUpgradeCopy),
            "SAVR" to listOf(LongAutoUpgradeCopy, "Standard shipping rates."),
        )

        compose.setContent {
            AirdropThemeProvider {
                GoldPriorityContent(
                    onBack = { pageBackCalls++ },
                    initialPage = ruby,
                    resolvedTierIndex = ruby,
                    benefitRowsByCode = rowsByCode,
                    catalogStatus = TierCatalogStatus.Ready,
                    canChange = true,
                    changeOffers = listOf(
                        TierChangeOption(
                            code = tierPages[gold].apiCode.orEmpty(),
                            name = tierPages[gold].name,
                            laneRank = 3,
                            isCurrent = false,
                            direction = "upgrade",
                        ),
                        TierChangeOption(
                            code = tierPages[sapphire].apiCode.orEmpty(),
                            name = tierPages[sapphire].name,
                            laneRank = 1,
                            isCurrent = false,
                            direction = "downgrade",
                        ),
                    ),
                )
            }
        }
        compose.waitForIdle()

        openBreakdown()
        compose.onNodeWithTag("tier-breakdown-benefit-text-1")
            .performScrollTo()
            .assertTextEquals(LongSaleCopy)
        assertFixedSize("tier-breakdown-benefit-icon-1", 18f)
        assertWrapsPastIcon("tier-breakdown-benefit-text-1", "tier-breakdown-benefit-icon-1")
        saveScreenshot("tier-breakdown-sheet", "breakdown-${proofSuffix()}.png")
        compose.onNodeWithTag("tier-breakdown-close").performScrollTo().performClick()
        waitForSheetToClose("tier-breakdown-sheet")

        openBreakdown()
        compose.onNodeWithTag("tier-breakdown-upgrade").performScrollTo().performClick()
        compose.onNodeWithTag("tier-change-benefits-row-1")
            .performScrollTo()
            .assertTextEquals(LongSaleCopy)
        assertFixedSize("tier-change-benefits-icon-1", 18f)
        assertWrapsPastIcon("tier-change-benefits-row-1", "tier-change-benefits-icon-1")
        compose.onNodeWithTag("tier-change-footnote").performScrollTo().assertIsDisplayed()
        assertFixedHeight("tier-change-confirm", 52f)
        assertFixedHeight("tier-change-cancel", 44f)
        saveScreenshot("tier-change-sheet-content", "upgrade-${proofSuffix()}.png")
        compose.onNodeWithTag("tier-change-cancel").performClick()
        waitForSheetToClose("tier-change-sheet")

        openBreakdown()
        compose.onNodeWithTag("tier-breakdown-downgrade").performScrollTo().performClick()
        compose.onNodeWithTag("tier-change-lost-benefits-row-1")
            .performScrollTo()
            .assertTextEquals(LongSaleCopy)
        assertFixedSize("tier-change-lost-benefits-icon-1", 18f)
        assertWrapsPastIcon(
            "tier-change-lost-benefits-row-1",
            "tier-change-lost-benefits-icon-1",
        )
        compose.onNodeWithTag("tier-change-benefits-row-0")
            .performScrollTo()
            .assertTextEquals(LongAutoUpgradeCopy)
        assertFixedSize("tier-change-benefits-icon-0", 18f)
        compose.onNodeWithTag("tier-change-footnote").performScrollTo().assertIsDisplayed()
        assertFixedHeight("tier-change-cancel", 52f)
        assertFixedHeight("tier-change-confirm", 52f)
        saveScreenshot("tier-change-sheet-content", "downgrade-${proofSuffix()}.png")
        compose.onNodeWithTag("tier-change-cancel").performClick()
        waitForSheetToClose("tier-change-sheet")

        openBreakdown()
        assertTrue(
            "System back must return from the breakdown sheet",
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK,
            ),
        )
        waitForSheetToClose("tier-breakdown-sheet")
        compose.onNodeWithTag(HomeDetailsHeaderTags.BACK).performClick()
        compose.runOnIdle { assertEquals(1, pageBackCalls) }
    }

    private fun openBreakdown() {
        compose.onNodeWithTag("gold-priority-cta").assertIsDisplayed().performClick()
        compose.onNodeWithTag("tier-breakdown-sheet").assertIsDisplayed()
    }

    private fun waitForSheetToClose(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("gold-priority-root").assertIsDisplayed()
    }

    private fun assertFixedSize(tag: String, expected: Float) {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertClose(expected, boundsWidth(bounds), "$tag width")
        assertClose(expected, boundsHeight(bounds), "$tag height")
    }

    private fun assertFixedHeight(tag: String, expected: Float) {
        val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertClose(expected, boundsHeight(bounds), "$tag height")
    }

    private fun assertWrapsPastIcon(textTag: String, iconTag: String) {
        val text = compose.onNodeWithTag(textTag).getUnclippedBoundsInRoot()
        val icon = compose.onNodeWithTag(iconTag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "$textTag must grow taller than one fixed icon; text=${boundsHeight(text)} icon=${boundsHeight(icon)}",
            boundsHeight(text) > boundsHeight(icon) * 1.5f,
        )
        assertTrue(
            "$textTag must start after $iconTag without overlap",
            boundsLeft(text) > boundsRight(icon),
        )
    }

    private fun saveScreenshot(nodeTag: String, filename: String) {
        val bitmap = compose.onNodeWithTag(nodeTag).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/tier_row_wrap/${context.packageName}/"
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

    private fun proofSuffix(): String {
        val args = InstrumentationRegistry.getArguments()
        val profile = args.getString("proofProfile") ?: "device"
        val pass = args.getString("proofPass") ?: "1"
        return "$profile-pass$pass"
    }

    private fun setLightTheme() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
    }

    private fun boundsLeft(rect: DpRect): Float = rect.left.value

    private fun boundsRight(rect: DpRect): Float = rect.right.value

    private fun boundsWidth(rect: DpRect): Float = (rect.right - rect.left).value

    private fun boundsHeight(rect: DpRect): Float = (rect.bottom - rect.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private companion object {
        const val LongLoyaltyCopy =
            "Standard loyalty rewards plus double-points promotions."
        const val LongSaleCopy =
            "Early notifications for warehouse/holiday/limited-time sales."
        const val LongAutoUpgradeCopy =
            "Auto-upgrade eligibility after 12 months."

        val longRows = listOf(LongLoyaltyCopy, LongSaleCopy, LongAutoUpgradeCopy)
    }
}
