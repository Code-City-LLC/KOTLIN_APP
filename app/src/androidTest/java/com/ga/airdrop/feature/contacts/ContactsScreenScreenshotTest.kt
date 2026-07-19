package com.ga.airdrop.feature.contacts

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.core.navigation.Routes
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactsScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureHelpLight() {
        captureHelp(
            mode = ThemeController.Mode.LIGHT,
            topFilename = "help_swift_top_light.png",
            socialFilename = "help_swift_social_light.png",
        )
    }

    @Test
    fun captureHelpDark() {
        captureHelp(
            mode = ThemeController.Mode.DARK,
            topFilename = "help_swift_top_dark.png",
            socialFilename = "help_swift_social_dark.png",
        )
    }

    @Test
    fun helpUsesSwiftSeparateCardsWithLiveChat() {
        setHelpContent(ThemeController.Mode.LIGHT)

        // Swift makeLiveChatCard is the first card (re-added 2026-07-11).
        compose.onNodeWithTag("contacts-card-live-chat").assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("Live Chat").fetchSemanticsNodes().size)
        compose.onNodeWithTag("contacts-card-contact-number").assertIsDisplayed()
        compose.onNodeWithTag("contacts-card-whatsapp").assertIsDisplayed()
        compose.onNodeWithTag("contacts-card-email").assertIsDisplayed()
        // Live Chat is a nav card with no copy affordance — count stays 11.
        assertEquals(11, compose.onAllNodesWithContentDescription("Copy").fetchSemanticsNodes().size)
        compose.onNodeWithText("Monday-Friday: 9am-6pm\nSaturday: 10am-4pm\nSunday: Closed")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(
            0,
            compose.onAllNodesWithText("Monday - Friday: 9 AM - 6 PM\nSaturday: 10 AM - 4 PM\nSunday: Closed")
                .fetchSemanticsNodes()
                .size,
        )

        val contact = compose.onNodeWithTag("contacts-card-contact-number").getUnclippedBoundsInRoot()
        val whatsapp = compose.onNodeWithTag("contacts-card-whatsapp").getUnclippedBoundsInRoot()
        val email = compose.onNodeWithTag("contacts-card-email").getUnclippedBoundsInRoot()
        assertClose(20f, boundsTop(whatsapp) - boundsBottom(contact), "Swift card gap contact/whatsapp")
        assertClose(20f, boundsTop(email) - boundsBottom(whatsapp), "Swift card gap whatsapp/email")
        assertTrue(boundsWidth(contact) > 300f)
        assertClose(boundsWidth(contact), boundsWidth(whatsapp), "Swift card widths match")
    }

    @Test
    fun rowsUseSwiftOutboundUris() {
        val opened = mutableListOf<String>()
        setHelpContent(
            mode = ThemeController.Mode.LIGHT,
            openExternal = {
                opened += it
                true
            },
        )

        compose.onNodeWithText("+876-676-6999").performClick()
        compose.onNodeWithText("support@airdropja.com").performClick()
        compose.onNodeWithText("Instagram: @airdrop.ja").performScrollTo()
        compose.onNodeWithText("Instagram: @airdrop.ja").performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(
                    "tel:8766766999",
                    "mailto:support@airdropja.com?subject=Contact%20from%20App",
                    "https://www.instagram.com/airdrop.ja/",
                ),
                opened,
            )
        }
    }

    @Test
    fun whatsAppPrefersNativeAppWhenAvailable() {
        val opened = mutableListOf<String>()
        setHelpContent(
            mode = ThemeController.Mode.LIGHT,
            openExternal = {
                opened += it
                true
            },
        )

        compose.onNodeWithText("+876-566-9339").performClick()

        compose.runOnIdle {
            assertEquals(
                listOf("whatsapp://send?phone=8765669339"),
                opened,
            )
        }
    }

    @Test
    fun whatsAppFallsBackToWaMeWhenNativeAppUnavailable() {
        val opened = mutableListOf<String>()
        setHelpContent(
            mode = ThemeController.Mode.LIGHT,
            openExternal = {
                opened += it
                it != "whatsapp://send?phone=8765669339"
            },
        )

        compose.onNodeWithText("+876-566-9339").performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(
                    "whatsapp://send?phone=8765669339",
                    "https://wa.me/8765669339",
                ),
                opened,
            )
        }
    }

    @Test
    fun liveChatCardNavigatesToLiveAgentChat() {
        val routes = mutableListOf<String>()
        setHelpContent(mode = ThemeController.Mode.LIGHT, onNavigate = { routes += it })

        compose.onNodeWithTag("contacts-card-live-chat").performClick()

        compose.runOnIdle {
            assertEquals(listOf(Routes.LIVE_CHAT), routes)
        }
    }

    @Test
    fun tiktokPrefersAppSchemeWhenAvailable() {
        val opened = mutableListOf<String>()
        setHelpContent(
            mode = ThemeController.Mode.LIGHT,
            openExternal = {
                opened += it
                true
            },
        )

        compose.onNodeWithText("Tiktok: @airdropja").performScrollTo()
        compose.onNodeWithText("Tiktok: @airdropja").performClick()

        compose.runOnIdle {
            assertEquals(listOf("tiktok://user?unique_id=@airdropja"), opened)
        }
    }

    @Test
    fun tiktokFallsBackToWebWhenAppUnavailable() {
        val opened = mutableListOf<String>()
        setHelpContent(
            mode = ThemeController.Mode.LIGHT,
            openExternal = {
                opened += it
                it != "tiktok://user?unique_id=@airdropja"
            },
        )

        compose.onNodeWithText("Tiktok: @airdropja").performScrollTo()
        compose.onNodeWithText("Tiktok: @airdropja").performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(
                    "tiktok://user?unique_id=@airdropja",
                    "https://www.tiktok.com/@airdropja",
                ),
                opened,
            )
        }
    }

    @Test
    fun copyButtonShowsCopiedToast() {
        setHelpContent(ThemeController.Mode.DARK)

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Copy").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(11, compose.onAllNodesWithContentDescription("Copy").fetchSemanticsNodes().size)
        compose.onAllNodesWithContentDescription("Copy")[0].performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("All the information is copied").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun captureHelp(
        mode: ThemeController.Mode,
        topFilename: String,
        socialFilename: String,
    ) {
        setHelpContent(mode)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Contact Number").fetchSemanticsNodes().isNotEmpty()
        }
        saveRootScreenshot(topFilename)

        compose.onNodeWithText("Tiktok: @airdropja").performScrollTo()
        compose.waitForIdle()
        saveRootScreenshot(socialFilename)
    }

    private fun setHelpContent(
        mode: ThemeController.Mode,
        onNavigate: (String) -> Unit = {},
        openExternal: ((String) -> Boolean)? = null,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }

        compose.setContent {
            AirdropTheme {
                ContactsScreen(
                    onNavigate = onNavigate,
                    openExternal = openExternal,
                )
            }
        }
    }

    private fun saveRootScreenshot(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun screenshotDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "screenshots/help_contacts_swift").also { it.mkdirs() }
    }

    private fun boundsTop(rect: DpRect): Float = rect.top.value

    private fun boundsBottom(rect: DpRect): Float = rect.bottom.value

    private fun boundsWidth(rect: DpRect): Float = (rect.right - rect.left).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }
}
