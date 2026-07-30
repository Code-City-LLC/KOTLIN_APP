package com.ga.airdrop.core.designsystem.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kemar 2026-07-30: "the CIF value is fallen off the page ... the blue on the
 * cif there is not correct as well on the dark theme."
 *
 * Renders the real CIF table content at a realistic row count in BOTH themes and
 * saves PNGs so the layout and the Total-row blue can actually be looked at
 * rather than reasoned about.
 */
@RunWith(AndroidJUnit4::class)
class CifValueSheetLookTest {

    @get:Rule
    val compose = createComposeRule()

    private val rows = listOf(
        CifRow("Item Value (FOB)", 640.00),
        CifRow("Freight", 48.75),
        CifRow("Insurance", 12.40),
        CifRow("Customs Duty", 96.32),
        CifRow("General Consumption Tax", 128.44),
        CifRow("Standard Compliance Fee", 3.00),
        CifRow("Environmental Levy", 1.50),
        CifRow("Customs Administrative Fee", 22.00),
    )

    private fun render(mode: ThemeController.Mode, file: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { ThemeController.set(mode) }
        compose.setContent {
            AirdropTheme {
                Box(Modifier.fillMaxSize().background(AirdropTheme.colors.gray150)) {
                    CifServicesTable(rows = rows, exchangeRate = 160.63)
                }
            }
        }
        compose.waitForIdle()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), file)
    }

    @Test
    fun cifTableLight() = render(ThemeController.Mode.LIGHT, "cif_light.png")

    @Test
    fun cifTableDark() = render(ThemeController.Mode.DARK, "cif_dark.png")

    private fun save(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "screenshots/cif").also { it.mkdirs() }
        FileOutputStream(File(dir, filename)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
