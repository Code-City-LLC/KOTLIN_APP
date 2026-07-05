package com.ga.airdrop.feature.more2

import android.graphics.Bitmap
import android.text.style.ForegroundColorSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegalContentParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun liveLegalHtmlColorsHeadingsLikeSwift() {
        val headingColor = 0xFF292929.toInt()
        val styled = colorLegalHeadings(
            HtmlCompat.fromHtml(
                prepareLegalHtml(
                    """
                    # Shipping Terms

                    Body copy stays muted.
                    """.trimIndent(),
                ),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            ),
            headingColor,
        )

        val headingStart = styled.indexOf("Shipping Terms")
        val bodyStart = styled.indexOf("Body copy")
        assertTrue("sample heading should be present", headingStart >= 0)
        assertTrue("sample body should be present", bodyStart >= 0)

        val headingSpans = styled.getSpans(
            headingStart,
            headingStart + "Shipping Terms".length,
            ForegroundColorSpan::class.java,
        )
        val bodySpans = styled.getSpans(
            bodyStart,
            bodyStart + "Body copy".length,
            ForegroundColorSpan::class.java,
        )

        assertTrue(
            "Swift recolors parsed heading runs to textDarkTitle",
            headingSpans.any { it.foregroundColor == headingColor },
        )
        assertFalse(
            "Swift leaves body runs on the TextView body color/textDescription path",
            bodySpans.any { it.foregroundColor == headingColor },
        )
    }

    @Test
    fun liveLegalHtmlStripsFrozenCmsColorsBeforeThemeRecoloring() {
        val prepared = prepareLegalHtml(
            """
            <h2 style="color:#111111;background-color:#ffffff">Overview</h2>
            <p><font color="#ff0000" bgcolor="#00ff00">Body</font></p>
            """.trimIndent(),
        )

        assertFalse(prepared.contains("color:", ignoreCase = true))
        assertFalse(prepared.contains("background-color", ignoreCase = true))
        assertFalse(prepared.contains(" color=", ignoreCase = true))
        assertFalse(prepared.contains(" bgcolor=", ignoreCase = true))
    }

    @Test
    fun legalAccordionDefaultGapStaysSwiftTermsPrivacyFiveDp() {
        setAccordionCard(titleEndGap = Spacing.xs, tag = "legal-default")

        val gap = compose.onNodeWithTag(
            "legal-default-title-chevron-gap",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()

        assertClose(5f, boundsWidth(gap), "Terms/Privacy title-to-chevron gap")
    }

    @Test
    fun faqScreenUsesSwiftQuestionChevronGap() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                FaqScreen(onBack = {})
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(
                "faq-1-title-chevron-gap",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }

        val gap = compose.onNodeWithTag(
            "faq-1-title-chevron-gap",
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()

        assertClose(10f, boundsWidth(gap), "FAQ question-to-chevron gap")
    }

    @Test
    fun captureLiveLegalHtmlLight() {
        setLegalHtmlContent(ThemeController.Mode.LIGHT)

        saveRootScreenshot("legal_live_html_light.png")
    }

    @Test
    fun captureLiveLegalHtmlDark() {
        setLegalHtmlContent(ThemeController.Mode.DARK)

        saveRootScreenshot("legal_live_html_dark.png")
    }

    private fun setAccordionCard(titleEndGap: androidx.compose.ui.unit.Dp, tag: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(ThemeController.Mode.LIGHT)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    modifier = Modifier
                        .width(335.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    AccordionCard(
                        title = "Overview",
                        expanded = false,
                        onToggle = {},
                        titleEndGap = titleEndGap,
                        testTagPrefix = tag,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun setLegalHtmlContent(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropTheme {
                Column(
                    modifier = Modifier
                        .width(335.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    More2OuterCard {
                        Column(Modifier.padding(Spacing.md)) {
                            LegalHtmlContent(
                                html = """
                                # Shipping Terms

                                Body copy stays muted while the parsed heading follows
                                Swift textDarkTitle in the active theme.
                                """.trimIndent(),
                                modifier = Modifier.testTag("legal-html"),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
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
        return File(context.getExternalFilesDir(null), "screenshots").also { it.mkdirs() }
    }

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
    }

    private fun boundsWidth(bounds: androidx.compose.ui.unit.DpRect): Float =
        (bounds.right - bounds.left).value
}
