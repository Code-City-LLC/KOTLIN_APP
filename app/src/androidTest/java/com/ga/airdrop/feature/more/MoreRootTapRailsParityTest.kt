package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.session.SessionStore
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreRootTapRailsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val navigatedRoutes = mutableListOf<String>()
    private var avatarEditCount = 0

    @Test
    fun rootRowsKeepSwiftFigmaGeometryLight() {
        setMoreRoot(mode = ThemeController.Mode.LIGHT)

        assertSwiftFigmaGeometry()
        assertMoreMenuIconsUseSwiftDuotone(iconSelected = DARK_ICON)
        saveRootScreenshot("more_root_swift_light.png")
    }

    @Test
    fun rootRowsKeepSwiftFigmaGeometryDark() {
        setMoreRoot(mode = ThemeController.Mode.DARK)

        assertSwiftFigmaGeometry()
        assertMoreMenuIconsUseSwiftDuotone(iconSelected = WHITE_ICON)
        saveRootScreenshot("more_root_swift_dark.png")
    }

    @Test
    fun aboutAirDropKeepsHeaderMatchedBottomClearance() {
        setMoreRoot(mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithTag(MoreRootTags.BOTTOM_CLEARANCE).performScrollTo()
        compose.waitForIdle()

        val root = bounds(MoreRootTags.ROOT)
        val about = bounds(MoreRootTags.ABOUT)
        val bottomClearance = bounds(MoreRootTags.BOTTOM_CLEARANCE)
        val visibleGapBelowAbout = (root.bottom - about.bottom).value

        assertClose(335f, boundsWidth(about), "About AirDrop row width")
        assertClose(59f, boundsHeight(about), "About AirDrop row height")
        assertClose(10f, verticalGap(about, bottomClearance), "About-to-bottom-clearance gap")
        assertTrue(
            "Bottom clearance should match the header clearance; visible gap was $visibleGapBelowAbout",
            visibleGapBelowAbout >= MORE_ROOT_HEADER_CLEARANCE_DP,
        )
        assertTrue(
            "Bottom spacer should preserve at least the fixed header clearance",
            boundsHeight(bottomClearance) >= MORE_ROOT_HEADER_CLEARANCE_DP,
        )
        saveRootScreenshot("more_root_about_bottom_clearance_light.png")
    }

    @Test
    fun menuRowsEmitSwiftRoutesInFigmaOrder() {
        setMoreRoot()

        listOf(
            MoreRootTags.PREFERENCES to Routes.PREFERENCES,
            MoreRootTags.PROMOTIONS to Routes.PROMOTIONS,
            MoreRootTags.SETTINGS to Routes.SETTINGS,
            MoreRootTags.DOCUMENTS to Routes.DOCUMENTS,
            MoreRootTags.USERS to Routes.AUTHORIZED_USERS,
            MoreRootTags.REFER_A_FRIEND to Routes.REFER_A_FRIEND,
            MoreRootTags.SHIPPING_RATES to Routes.SHIPPING_RATES,
            MoreRootTags.RESTRICTED_ITEMS to Routes.RESTRICTED_ITEMS,
            MoreRootTags.PAYMENT_METHODS to Routes.PAYMENT_METHODS,
            MoreRootTags.FAQS to Routes.FAQ,
            MoreRootTags.TERMS to Routes.TERMS,
            MoreRootTags.PRIVACY to Routes.PRIVACY,
            MoreRootTags.ABOUT to Routes.ABOUT,
        ).forEach { (tag, route) ->
            compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(route, navigatedRoutes.lastOrNull())
            }
        }
    }

    @Test
    fun profileAvatarAndHeaderEmitSwiftRoutes() {
        setMoreRoot()

        compose.onNodeWithTag(MoreRootTags.PROFILE_AVATAR, useUnmergedTree = true)
            .performClick()
        compose.runOnIdle {
            assertEquals(1, avatarEditCount)
            assertTrue("Avatar tap opens picker path, not Profile route", navigatedRoutes.isEmpty())
        }

        compose.onNodeWithTag(MoreRootTags.PROFILE_CARD).performClick()
        compose.runOnIdle {
            assertEquals(Routes.PROFILE, navigatedRoutes.lastOrNull())
        }

        clickSwiftHeaderText("Gold Standard")
        compose.runOnIdle {
            assertEquals(Routes.GOLD_PRIORITY, navigatedRoutes.lastOrNull())
        }

        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.runOnIdle {
            assertEquals(Routes.NOTIFICATIONS, navigatedRoutes.lastOrNull())
        }

        compose.onNodeWithContentDescription("Cart").performClick()
        compose.runOnIdle {
            assertEquals(Routes.CART, navigatedRoutes.lastOrNull())
        }

        clickSwiftHeaderContentDescription("AirCoins")
        compose.runOnIdle {
            assertEquals(Routes.AIRCOIN_HISTORY, navigatedRoutes.lastOrNull())
        }
    }

    private fun setMoreRoot(mode: ThemeController.Mode = ThemeController.Mode.LIGHT) {
        navigatedRoutes.clear()
        avatarEditCount = 0
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
                    MoreScreenContent(
                        state = MoreUiState(
                            name = "Chase Kamer",
                            account = "AIR1234",
                        ),
                        headerInfo = SessionStore.HeaderInfo(
                            greeting = "Good Morning",
                            firstName = "Chase",
                            tierName = "Gold Standard",
                            airCoins = "1,250",
                            cartCount = 2,
                        ),
                        onNavigate = navigatedRoutes::add,
                        onEditAvatar = { avatarEditCount += 1 },
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(MoreRootTags.PREFERENCES).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun assertSwiftFigmaGeometry() {
        val root = bounds(MoreRootTags.ROOT)
        val profile = bounds(MoreRootTags.PROFILE_CARD)
        val avatar = bounds(MoreRootTags.PROFILE_AVATAR)
        val preferences = bounds(MoreRootTags.PREFERENCES)

        assertClose(375f, boundsWidth(root), "More root Figma frame width")
        assertClose(335f, boundsWidth(profile), "Profile card width")
        assertClose(80f, boundsHeight(profile), "Profile card height")
        assertClose(48f, boundsWidth(avatar), "Profile avatar width")
        assertClose(48f, boundsHeight(avatar), "Profile avatar height")
        assertClose(335f, boundsWidth(preferences), "Menu row width")
        assertClose(59f, boundsHeight(preferences), "Menu row height")
        assertClose(10f, verticalGap(profile, preferences), "Profile-to-first-row gap")
    }

    private fun assertMoreMenuIconsUseSwiftDuotone(iconSelected: Int) {
        moreIconRowTags.forEach { rowTag ->
            compose.onNodeWithTag(rowTag).performScrollTo()
            val iconTag = "$rowTag-icon"
            assertIconContainsColor(iconTag, iconSelected, "$rowTag iconSelected role")
            assertIconContainsColor(iconTag, ORANGE, "$rowTag orange role")
        }
    }

    private fun clickSwiftHeaderText(text: String) {
        compose.onNode(
            hasClickAction() and (hasText(text) or hasAnyDescendant(hasText(text))),
            useUnmergedTree = true,
        ).performClick()
    }

    private fun clickSwiftHeaderContentDescription(description: String) {
        compose.onNode(
            hasClickAction() and hasContentOrDescendant(description),
            useUnmergedTree = true,
        ).performClick()
    }

    private fun hasContentOrDescendant(description: String): SemanticsMatcher =
        hasContentDescription(description) or hasAnyDescendant(hasContentDescription(description))

    private fun bounds(tag: String) = compose.onNodeWithTag(
        tag,
        useUnmergedTree = true,
    ).getUnclippedBoundsInRoot()

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun assertIconContainsColor(tag: String, target: Int, label: String) {
        val bitmap = iconBitmap(tag)
        assertTrue(label, bitmap.hasPixelNear(target))
    }

    private fun iconBitmap(tag: String): Bitmap =
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

    private fun Bitmap.hasPixelNear(target: Int): Boolean {
        val targetRed = (target shr 16) and 0xFF
        val targetGreen = (target shr 8) and 0xFF
        val targetBlue = target and 0xFF
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 180) continue
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (
                    kotlin.math.abs(red - targetRed) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(green - targetGreen) <= COLOR_TOLERANCE &&
                    kotlin.math.abs(blue - targetBlue) <= COLOR_TOLERANCE
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/more_root").also { it.mkdirs() }
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.bottom - bounds.top).value

    private fun verticalGap(
        upper: androidx.compose.ui.unit.DpRect,
        lower: androidx.compose.ui.unit.DpRect,
    ): Float = (lower.top - upper.bottom).value

    private companion object {
        private const val ORANGE = 0xFFF15114.toInt()
        private const val DARK_ICON = 0xFF292929.toInt()
        private const val WHITE_ICON = 0xFFFFFFFF.toInt()
        private const val COLOR_TOLERANCE = 12
        private const val MORE_ROOT_HEADER_CLEARANCE_DP = 76f

        private val moreIconRowTags = listOf(
            MoreRootTags.PREFERENCES,
            MoreRootTags.PROMOTIONS,
            MoreRootTags.SETTINGS,
            MoreRootTags.DOCUMENTS,
            MoreRootTags.USERS,
            MoreRootTags.REFER_A_FRIEND,
            MoreRootTags.SHIPPING_RATES,
            MoreRootTags.RESTRICTED_ITEMS,
            MoreRootTags.PAYMENT_METHODS,
            MoreRootTags.FAQS,
            MoreRootTags.TERMS,
            MoreRootTags.PRIVACY,
        )
    }
}
