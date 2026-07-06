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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
class CalculatorResultsParityTest {

    @get:Rule
    val compose = createComposeRule()

    private val backClicks = AtomicInteger()
    private val dropAlertClicks = AtomicInteger()
    private val makePaymentClicks = AtomicInteger()
    private val governmentChargesClicks = AtomicInteger()

    @Test
    fun standardResultsUsesSwiftFirstLayoutLight() {
        setStandardResults(mode = ThemeController.Mode.LIGHT)

        compose.onNodeWithText("Results").assertIsDisplayed()
        compose.onNodeWithText("Total Weight").assertIsDisplayed()
        compose.onNodeWithText("123.00 lbs").assertIsDisplayed()
        compose.onNodeWithText("Invoice Amount (Declared Value/Cost)").assertIsDisplayed()
        compose.onNodeWithText("\$123.00").assertIsDisplayed()
        compose.onNodeWithText("CIF Value").assertIsDisplayed()
        compose.onNodeWithText("Charges").assertIsDisplayed()
        compose.onNodeWithText("(USD)").assertIsDisplayed()
        compose.onNodeWithText("Insurance").assertIsDisplayed()
        compose.onNodeWithText("Freight").assertIsDisplayed()
        compose.onNodeWithText("Fuel").assertIsDisplayed()
        compose.onNodeWithText("Total Airdrop Charges").assertIsDisplayed()
        assertTextExists("Total with Duty")
        assertNoText("Calculate")

        assertSwiftGeometry()
        saveRootScreenshot("calculator_results_standard_swift_light_top.png")

        compose.onNodeWithTag("calculator-results-disclaimer-card", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Click the link", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        saveRootScreenshot("calculator_results_standard_swift_light_disclaimer.png")
    }

    @Test
    fun standardResultsActionsUseSwiftRailsDark() {
        setStandardResults(mode = ThemeController.Mode.DARK)

        compose.onNodeWithTag("calculator-results-drop-alert-button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithTag("calculator-results-make-payment-button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        assertEquals("Swift Drop Alert footer CTA should dispatch once", 1, dropAlertClicks.get())
        assertEquals("Swift Make Payment footer CTA should dispatch once", 1, makePaymentClicks.get())

        compose.onNodeWithTag("calculator-results-disclaimer-link-text", useUnmergedTree = true)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(
            "Figma Government Charges disclaimer rail should dispatch once",
            1,
            governmentChargesClicks.get(),
        )
        saveRootScreenshot("calculator_results_standard_swift_dark.png")

        compose.onNodeWithTag("calculator-results-cif-info", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("The CIF value represents the total landed cost", substring = true)
            .assertIsDisplayed()
    }

    private fun setStandardResults(mode: ThemeController.Mode) {
        backClicks.set(0)
        dropAlertClicks.set(0)
        makePaymentClicks.set(0)
        governmentChargesClicks.set(0)

        lateinit var viewModel: CalculatorViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = CalculatorViewModel(FakeCalculatorRepository())
            viewModel.onInvoiceChange("123")
            viewModel.onActualWeightChange("123")
            viewModel.calculate()
        }

        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200)
                ) {
                    CalculatorResultsScreen(
                        viewModel = viewModel,
                        onBack = { backClicks.incrementAndGet() },
                        onDropAlert = { dropAlertClicks.incrementAndGet() },
                        onMakePayment = { makePaymentClicks.incrementAndGet() },
                        onGovernmentCharges = { governmentChargesClicks.incrementAndGet() },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertSwiftGeometry() {
        val root = bounds("calculator-results-root")
        val footer = bounds("calculator-results-footer")
        val dropAlert = bounds("calculator-results-drop-alert-button")
        val makePayment = bounds("calculator-results-make-payment-button")
        val cifInfo = bounds("calculator-results-cif-info")

        assertClose(375f, boundsWidth(root), "Results frame width")
        assertClose(0f, footer.left.value, "Swift footer left")
        assertClose(boundsWidth(root), boundsWidth(footer), "Swift footer full width")
        assertClose(20f, dropAlert.left.value, "Swift Drop Alert left gutter")
        assertClose(52f, boundsHeight(dropAlert), "Swift Drop Alert height")
        assertClose(12f, makePayment.left.value - dropAlert.right.value, "Swift footer CTA gap")
        assertClose(boundsWidth(dropAlert), boundsWidth(makePayment), "Swift footer CTA equal width")
        assertClose(28f, boundsWidth(cifInfo), "Swift CIF info button rail width")
        assertClose(28f, boundsHeight(cifInfo), "Swift CIF info button rail height")

        compose.onNodeWithContentDescription("What is this?", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "$text should not exist in Swift Calculator Results footer",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun assertTextExists(text: String) {
        assertTrue(
            "$text should exist in Swift Calculator Results",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
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
            "screenshots/calculator_results",
        ).also { it.mkdirs() }

    private fun saveRootScreenshotToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/kotlin_ui_proof/calculator_results")
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
            lengthInches: Double?,
            widthInches: Double?,
            heightInches: Double?,
        ): ShipmentCalculation =
            throw AssertionError("Unused in CalculatorResultsParityTest")

        override suspend fun searchProducts(query: String, limit: Int): List<CalcProduct> = emptyList()

        override suspend fun usdToJmdRate(): Double = 156.0
    }
}
