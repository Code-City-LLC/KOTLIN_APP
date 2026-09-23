package com.ga.airdrop.feature.shipments

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageProofOfDeliveryParityTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var signatureFile: File

    @Before
    fun writeSignatureImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        signatureFile = File(context.cacheDir, "proof-of-delivery-test.png")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        FileOutputStream(signatureFile).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }

    @After
    fun deleteSignatureImage() {
        if (::signatureFile.isInitialized) signatureFile.delete()
    }

    @Test
    fun signedForPanelUsesTheSameWhitePlateInLightTheme() {
        render(ThemeController.Mode.LIGHT)
        assertSignedForPlate()
    }

    @Test
    fun signedForPanelUsesTheSameWhitePlateInDarkTheme() {
        render(ThemeController.Mode.DARK)
        assertSignedForPlate()
    }

    private fun render(mode: ThemeController.Mode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
        }
        compose.setContent {
            AirdropThemeProvider {
                SignedForPanel(
                    presentation = SignedForPresentation(
                        imageUrl = "https://airdropja.com/proof.png",
                        meta = "Received by D. Grant \u00B7 10 Aug 2026",
                    ),
                    signatureFile = signatureFile,
                )
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("package-details-section-signed-for")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertSignedForPlate() {
        compose.onNodeWithTag("package-details-section-signed-for").assertIsDisplayed()
        compose.onNodeWithTag("package-details-signed-for-plate").assertIsDisplayed()
        compose.onNodeWithText("Received by D. Grant \u00B7 10 Aug 2026").assertIsDisplayed()

        val plate = compose.onNodeWithTag("package-details-signed-for-plate")
            .captureToImage()
            .asAndroidBitmap()
        // The ink image is centered; this left inset is intentionally the fixed
        // white plate surface in both modes.
        assertEquals(Color.WHITE, plate.getPixel(20, 20))
    }
}
