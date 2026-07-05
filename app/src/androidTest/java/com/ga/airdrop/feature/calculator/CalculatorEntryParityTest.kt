package com.ga.airdrop.feature.calculator

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorEntryParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val backClicks = AtomicInteger()
    private val resultClicks = AtomicInteger()

    @Test
    fun standardEntryUsesSwiftRowsAndScrolledButtonLight() {
        setCalculator(mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithText("Shipping Calculator").assertIsDisplayed()
        compose.onNodeWithText("Shipping Method").assertIsDisplayed()
        compose.onNodeWithText("Invoice Amount USD").assertIsDisplayed()
        compose.onNodeWithText("Actual Weight (lbs)").assertIsDisplayed()
        assertNoText("Select Unit")
        assertNoText("Total Weight")

        assertSwiftHeaderGeometry()
        assertSwiftStandardGeometry()
        saveRootScreenshot("calculator_standard_swift_light.png")

        compose.onNodeWithTag("calculator-inner-header-back", useUnmergedTree = true).performClick()
        assertEquals("Swift calculator back rail should dispatch once", 1, backClicks.get())
    }

    @Test
    fun standardEntryUsesSwiftRowsAndScrolledButtonDark() {
        setCalculator(mode = ThemeController.Mode.DARK)

        assertNoText("Select Unit")
        assertNoText("Total Weight")
        assertSwiftHeaderGeometry()
        assertSwiftStandardGeometry()
        saveRootScreenshot("calculator_standard_swift_dark.png")
    }

    private fun setCalculator(mode: ThemeController.Mode) {
        backClicks.set(0)
        resultClicks.set(0)
        lateinit var viewModel: CalculatorViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = CalculatorViewModel(FakeCalculatorRepository())
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    CalculatorScreen(
                        viewModel = viewModel,
                        onBack = { backClicks.incrementAndGet() },
                        onShowResults = { resultClicks.incrementAndGet() },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSwiftHeaderGeometry() {
        val back = bounds("calculator-inner-header-back")
        val chevron = bounds("calculator-inner-header-back-chevron")

        assertClose(16f, back.left.value, "Swift calculator back rail left")
        assertClose(32f, boundsWidth(back), "Swift calculator back rail width")
        assertClose(32f, boundsHeight(back), "Swift calculator back rail height")
        assertClose(24f, boundsWidth(chevron), "Swift calculator chevron width")
        assertClose(24f, boundsHeight(chevron), "Swift calculator chevron height")
    }

    private fun assertSwiftStandardGeometry() {
        val root = bounds("calculator-root")
        val invoice = bounds("calculator-invoice-field")
        val actualWeight = bounds("calculator-actual-weight-field")
        val button = bounds("calculator-calculate-button")

        assertClose(375f, boundsWidth(root), "Calculator frame width")
        assertClose(20f, invoice.left.value, "Swift invoice left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(invoice), "Swift invoice full width")
        assertClose(invoice.left.value, actualWeight.left.value, "Swift actual weight aligns to invoice")
        assertClose(boundsWidth(invoice), boundsWidth(actualWeight), "Swift actual weight full width")
        assertClose(20f, button.left.value, "Swift calculate left gutter")
        assertClose(boundsWidth(root) - 40f, boundsWidth(button), "Swift calculate button width")
        assertClose(52f, boundsHeight(button), "Swift calculate button height")
        assertClose(28f, button.top.value - actualWeight.bottom.value, "Swift field-to-button gap")
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "$text should not exist in Swift Standard calculator entry",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun bounds(tag: String): DpRect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun boundsWidth(bounds: DpRect): Float = (bounds.right - bounds.left).value

    private fun boundsHeight(bounds: DpRect): Float = (bounds.bottom - bounds.top).value

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertEquals(label, expected, actual, 0.75f)
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
            "screenshots/calculator_entry",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/calculator_entry")
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

    private class FakeCalculatorRepository : CalculatorRepository {
        override suspend fun calculateShipment(
            shippingMethod: String,
            invoiceAmount: Double,
            weightLbs: Double?,
            numberOfPackages: Int,
        ): ShipmentCalculation =
            throw AssertionError("Unused in CalculatorEntryParityTest")

        override suspend fun searchProducts(query: String, limit: Int): List<CalcProduct> = emptyList()

        override suspend fun usdToJmdRate(): Double = 156.0
    }
}
