package com.ga.airdrop.feature.more2

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestrictedItemsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private var backClicks = 0

    @Test
    fun entryKeepsSwiftSearchableCategoryListLight() {
        setRestrictedItems(mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithText("Items that cannot be shipped through AirDrop")
            .assertIsDisplayed()
        compose.onNodeWithText(
            "* Item eligibility can change by carrier, customs, and government " +
                "agency approval. Search an item before shipping.",
        ).assertIsDisplayed()
        assertNoText("License-Required")
        assertNoText("Prohibited Articles")

        RestrictedCategory.entries.forEach { category ->
            compose.onNodeWithTag("restricted-category-${category.name}")
                .assertIsDisplayed()
        }

        assertSwiftListGeometry()
        saveRootScreenshot("restricted_items_entry_swift_light.png")
    }

    @Test
    fun searchResultsOpenTheirCategoryPage() {
        setRestrictedItems(mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag("restricted-search-input", useUnmergedTree = true)
            .performTextInput("lithium")
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Lithium batteries and magnetised materials")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        assertNoTag("restricted-category-PERMITTED")
        assertNoText("License-Required")
        compose.onNodeWithTag("restricted-search-result-0")
            .assertIsDisplayed()
        val result = bounds("restricted-search-result-0")
        val root = bounds("restricted-list-root")
        assertClose(20f, result.left.value, "Search result left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(result), "Search result width")
        saveRootScreenshot("restricted_items_search_results_swift_light.png")

        compose.onNodeWithTag("restricted-search-result-0").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("restricted-detail-root-RESTRICTED_COMMODITIES")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Restricted Commodities (Carrier/Dangerous Goods)")
            .assertIsDisplayed()
        saveRootScreenshot("restricted_items_restricted_detail_from_search_swift_light.png")
    }

    @Test
    fun detailKeepsSwiftCardStackAndNotesDark() {
        setRestrictedItems(mode = ThemeController.Mode.DARK)

        compose.onNodeWithTag("restricted-category-PERMITTED").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("restricted-detail-root-PERMITTED")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        compose.onNodeWithText("Permitted Commodities (General Goods)")
            .assertIsDisplayed()
        assertSwiftDetailGeometry()

        compose.onNodeWithTag("restricted-detail-scroll")
            .performScrollToNode(hasTestTag("restricted-note-BATTERY"))
        compose.onNodeWithTag("restricted-note-BATTERY").assertIsDisplayed()
        compose.onNodeWithText("Battery policy (Jamaica + IATA):")
            .assertIsDisplayed()
        compose.onNodeWithText(
            "Please note: All packages will be handled as Airdrop unless identified otherwise in address line 2.",
        ).assertIsDisplayed()

        val root = bounds("restricted-detail-root-PERMITTED")
        val note = bounds("restricted-note-BATTERY")
        assertClose(20f, note.left.value, "Note left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(note), "Note width")
        saveRootScreenshot("restricted_items_permitted_detail_swift_dark.png")
    }

    /**
     * Kemar 2026-10-17: the category page carries the "Restricted Items" title,
     * the info button, the item search 20dp under the header and the category
     * tab bar 20dp under that, with the content 20dp below. Figma 40001432:15045.
     */
    @Test
    fun categoryPageHasSearchAndTabBarUnderTheHeaderLight() {
        setRestrictedItems(mode = ThemeController.Mode.LIGHT)
        openCategory(RestrictedCategory.LICENSE_REQUIRED)

        compose.onNodeWithText("Restricted Items").assertIsDisplayed()
        compose.onNodeWithTag("restricted-info-button").assertIsDisplayed()
        assertNoText("License Required")

        val root = bounds("restricted-detail-root-LICENSE_REQUIRED")
        val header = bounds("more2-inner-header-back")
        val button = bounds("restricted-info-button")
        val icon = bounds("restricted-info-icon")
        val search = bounds("restricted-detail-search-row")
        val tabs = bounds("restricted-tabs")
        val license = bounds("restricted-tab-LICENSE_REQUIRED")
        val intro = bounds("restricted-detail-intro-card")

        assertClose(36f, boundsWidth(button), "Info button target")
        assertClose(12f, (root.right - button.right).value, "Info button trailing inset")
        assertClose(24f, boundsWidth(icon), "Info icon size")
        assertClose(20f, search.left.value, "Search left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(search), "Search width")
        assertClose(50f, boundsHeight(search), "Search height")
        assertTrue("Search sits under the header", search.top > header.bottom)
        assertClose(20f, (tabs.top - search.bottom).value, "Search-to-tabs gap")
        assertClose(40f, boundsHeight(license), "Tab height")
        assertClose(20f, license.left.value, "Active tab scrolled to the 20dp inset")
        assertClose(20f, (intro.top - tabs.bottom).value, "Tabs-to-content gap")
        assertClose(20f, intro.left.value, "Content left gutter")
        compose.onNodeWithTag("restricted-tab-LICENSE_REQUIRED").assertIsSelected()
        compose.onNodeWithTag("restricted-tab-PERMITTED").assertIsNotSelected()
        assertClose(24f, boundsWidth(bounds("restricted-tab-icon-LICENSE_REQUIRED")), "Tab icon slot")
        saveRootScreenshot("restricted_items_license_tabs_light.png")
    }

    @Test
    fun tabsAndSwipesChangeTheCategoryInPlace() {
        setRestrictedItems(mode = ThemeController.Mode.LIGHT)
        openCategory(RestrictedCategory.PERMITTED)

        compose.onNodeWithTag("restricted-tab-LICENSE_REQUIRED").performClick()
        waitForCategory(RestrictedCategory.LICENSE_REQUIRED)
        compose.onNodeWithText("License Required Categories").assertIsDisplayed()

        swipeContent(toNext = true)
        waitForCategory(RestrictedCategory.RESTRICTED_COMMODITIES)
        compose.onNodeWithText("Restricted Commodities (Carrier/Dangerous Goods)").assertIsDisplayed()
        compose.waitForIdle()
        assertClose(
            20f,
            bounds("restricted-tab-RESTRICTED_COMMODITIES").left.value,
            "Swiped-to tab scrolled to the 20dp inset",
        )

        swipeContent(toNext = false)
        waitForCategory(RestrictedCategory.LICENSE_REQUIRED)
        compose.waitForIdle()

        // A vertical drag on the content still scrolls the page and keeps the
        // category.
        val introTop = bounds("restricted-detail-intro-card").top
        compose.onNodeWithTag("restricted-detail-content").performTouchInput {
            swipe(
                start = Offset(centerX, top + 300f),
                end = Offset(centerX, top + 40f),
                durationMillis = 300,
            )
        }
        compose.waitForIdle()
        assertTrue(
            "A vertical drag scrolls the page",
            bounds("restricted-detail-intro-card").top < introTop,
        )
        assertTrue(
            compose.onAllNodesWithTag("restricted-detail-root-LICENSE_REQUIRED")
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    @Test
    fun categorySearchFiltersItemsAndShowsTheEmptyCard() {
        setRestrictedItems(mode = ThemeController.Mode.LIGHT)
        openCategory(RestrictedCategory.RESTRICTED_COMMODITIES)

        compose.onNodeWithTag("restricted-detail-search-input", useUnmergedTree = true)
            .performTextInput("POWER BANKS")
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("restricted-detail-item-card-1").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Lithium batteries and magnetised materials").assertIsDisplayed()
        compose.onNodeWithTag("restricted-detail-intro-card").assertIsDisplayed()

        compose.onNodeWithTag("restricted-detail-search-input", useUnmergedTree = true)
            .performTextClearance()
        compose.onNodeWithTag("restricted-detail-search-input", useUnmergedTree = true)
            .performTextInput("zzqx")
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("restricted-detail-search-empty").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No results found for \"zzqx\".").assertIsDisplayed()
        assertNoTag("restricted-detail-item-card-0")
        saveRootScreenshot("restricted_items_category_search_empty_light.png")
    }

    @Test
    fun categoryInfoButtonOpensInformationAndBackReturnsDark() {
        setRestrictedItems(mode = ThemeController.Mode.DARK)
        openCategory(RestrictedCategory.RESTRICTED_COMMODITIES)
        compose.onNodeWithTag("restricted-tab-RESTRICTED_COMMODITIES").assertIsSelected()
        saveRootScreenshot("restricted_items_restricted_tabs_dark.png")

        compose.onNodeWithTag("restricted-info-button").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("restricted-legal-root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Information").assertIsDisplayed()

        // Figma 40001432:14025 and Swift order the page title, introduction
        // and labeling card, Legal Notice, U.S. authorities, Jamaican authorities.
        val sections = listOf(
            "restricted-legal-title",
            "restricted-legal-labeling-card",
            "restricted-legal-notice",
            "restricted-legal-us-authorities",
            "restricted-legal-jm-authorities",
        ).map { bounds(it) }
        sections.zipWithNext().forEach { (above, below) ->
            assertClose(20f, (below.top - above.bottom).value, "Information section gap")
        }
        saveRootScreenshot("restricted_items_information_dark.png")

        compose.onNodeWithTag("more2-inner-header-back").performClick()
        waitForCategory(RestrictedCategory.RESTRICTED_COMMODITIES)
    }

    private fun openCategory(category: RestrictedCategory) {
        compose.onNodeWithTag("restricted-category-${category.name}").performClick()
        waitForCategory(category)
        compose.waitForIdle()
    }

    private fun waitForCategory(category: RestrictedCategory) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("restricted-detail-root-${category.name}")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /** A horizontal swipe across the top of the category's content: toward the next category or back. */
    private fun swipeContent(toNext: Boolean) {
        compose.onNodeWithTag("restricted-detail-content").performTouchInput {
            val y = top + 40f
            val from = Offset(if (toNext) right - 20f else left + 20f, y)
            val to = Offset(if (toNext) left + 20f else right - 20f, y)
            swipe(start = from, end = to, durationMillis = 300)
        }
    }

    private fun setRestrictedItems(mode: ThemeController.Mode) {
        backClicks = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    RestrictedItemsScreen(onBack = { backClicks += 1 })
                }
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("restricted-list-root").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun assertSwiftListGeometry() {
        val root = bounds("restricted-list-root")
        val search = bounds("restricted-search-row")
        val permitted = bounds("restricted-category-PERMITTED")
        val license = bounds("restricted-category-LICENSE_REQUIRED")
        val licenseCircle = bounds("restricted-category-icon-circle-LICENSE_REQUIRED")
        val licenseGlyph = bounds("restricted-category-icon-glyph-LICENSE_REQUIRED")
        val dangerousCircle = bounds("restricted-category-icon-circle-RESTRICTED_COMMODITIES")
        val dangerousGlyph = bounds("restricted-category-icon-glyph-RESTRICTED_COMMODITIES")

        assertClose(375f, boundsWidth(root), "Restricted Items frame width")
        assertClose(20f, search.left.value, "Search left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(search), "Search width")
        assertClose(50f, boundsHeight(search), "Search height")
        assertClose(20f, permitted.left.value, "Category left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(permitted), "Category width")
        assertClose(64f, boundsHeight(permitted), "Category row height")
        assertClose(20f, permitted.top.value - search.bottom.value, "Search-to-card gap")
        assertClose(15f, license.top.value - permitted.bottom.value, "Category stack gap")
        assertClose(36f, boundsWidth(licenseCircle), "License icon circle width")
        assertClose(36f, boundsHeight(licenseCircle), "License icon circle height")
        assertClose(22f, boundsWidth(licenseGlyph), "License Swift glyph width")
        assertClose(22f, boundsHeight(licenseGlyph), "License Swift glyph height")
        assertClose(36f, boundsWidth(dangerousCircle), "Dangerous goods icon circle width")
        assertClose(22f, boundsWidth(dangerousGlyph), "Dangerous goods Swift glyph width")
    }

    private fun assertSwiftDetailGeometry() {
        val root = bounds("restricted-detail-root-PERMITTED")
        val intro = bounds("restricted-detail-intro-card")
        val firstItem = bounds("restricted-detail-item-card-0")
        val firstBullet = bounds("restricted-detail-item-bullet-0")

        assertClose(20f, intro.left.value, "Intro left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(intro), "Intro width")
        assertClose(15f, firstItem.top.value - intro.bottom.value, "Intro-to-first-item gap")
        assertClose(boundsWidth(root) - 40f, boundsWidth(firstItem), "Item card width")
        assertClose(4f, boundsWidth(firstBullet), "Item bullet width")
        assertClose(4f, boundsHeight(firstBullet), "Item bullet height")
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        saveRootScreenshotToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/restricted_items",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/restricted_items")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        val outputStream = context.contentResolver.openOutputStream(uri) ?: return
        outputStream.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private fun assertNoText(text: String) {
        assertEquals(
            "$text should not render in the Swift-precedence Restricted Items flow",
            0,
            compose.onAllNodesWithText(text).fetchSemanticsNodes().size,
        )
    }

    private fun assertNoTag(tag: String) {
        assertTrue(
            "$tag should not exist",
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun bounds(tag: String): DpRect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value
}
