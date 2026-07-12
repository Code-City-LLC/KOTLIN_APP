package com.ga.airdrop.feature.more2

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
class FaqParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun heroMatchesSwiftCopyInLight() {
        setFaq(ThemeController.Mode.LIGHT)
        assertSwiftHero()
        saveHeroScreenshot("faq_hero_light.png")
    }

    @Test
    fun heroMatchesSwiftCopyInDark() {
        setFaq(ThemeController.Mode.DARK)
        assertSwiftHero()
        saveHeroScreenshot("faq_hero_dark.png")
    }

    @Test
    fun noMatchKeepsSwiftHintAndContactSupportRoute() {
        var route: String? = null
        setFaq(ThemeController.Mode.LIGHT, onNavigate = { route = it })

        compose.onNode(hasSetTextAction(), useUnmergedTree = true)
            .performTextInput("zzz-no-faq-match")
        compose.onNodeWithText("No matches for “zzz-no-faq-match”")
            .assertIsDisplayed()
        compose.onNodeWithText("Try different keywords, or tap Contact Support below.")
            .assertIsDisplayed()

        compose.onNodeWithText("Contact Support").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Routes.CONTACTS, route) }
    }

    private fun assertSwiftHero() {
        compose.onNodeWithText("Frequently Asked").assertIsDisplayed()
        compose.onNodeWithText(
            "Quick answers to the questions our customers ask most. " +
                "Tap any card to see the full answer.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("faq-search").assertIsDisplayed()

        val card = bounds("faq-hero")
        val accent = bounds("faq-hero-accent")
        val monogram = bounds("faq-hero-monogram")
        val title = bounds("faq-hero-title")
        val subtitle = bounds("faq-hero-subtitle")

        assertClose(140f, (accent.right - accent.left).value, "Swift accent width")
        assertClose(140f, (accent.bottom - accent.top).value, "Swift accent height")
        assertClose(64f, (monogram.right - monogram.left).value, "Swift monogram width")
        assertClose(64f, (monogram.bottom - monogram.top).value, "Swift monogram height")
        assertClose(20f, (title.left - card.left).value, "Swift title leading inset")
        assertClose(22f, (title.top - card.top).value, "Swift title top inset")
        assertClose(20f, (subtitle.left - card.left).value, "Swift subtitle leading inset")
        assertClose(20f, (card.right - subtitle.right).value, "Swift subtitle trailing inset")
        assertClose(22f, (card.bottom - subtitle.bottom).value, "Swift subtitle bottom inset")
        assertTrue("Title must remain left of the monogram", title.right <= monogram.left)
        assertClose(0f, (accent.right - card.right).value, "Clipped accent trailing edge")
        assertClose(0f, (accent.top - card.top).value, "Clipped accent top edge")
    }

    private fun bounds(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun saveHeroScreenshot(name: String) {
        val bitmap = compose.onNodeWithTag("faq-hero").captureToImage().asAndroidBitmap()
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/faq/$name",
        )
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun setFaq(
        mode: ThemeController.Mode,
        onNavigate: (String) -> Unit = {},
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                FaqScreen(onBack = {}, onNavigate = onNavigate)
            }
        }
    }
}
