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
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** Device-rendered parity proof for the Airdrop TierQuote result path. */
@RunWith(AndroidJUnit4::class)
class TierQuoteCalculatorParityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tierQuoteShowsServerLinesAndInsuranceGateInLightTheme() {
        val paymentRequests = AtomicInteger()
        val viewModel = setResults(ThemeController.Mode.LIGHT, paymentRequests)

        compose.onNodeWithText("Service Tier").assertIsDisplayed()
        compose.onNodeWithText("Base shipping").assertIsDisplayed()
        compose.onNodeWithText("Fuel surcharge").assertIsDisplayed()
        compose.onNodeWithText("Total Due").assertIsDisplayed()
        compose.onNodeWithText("Q-7H2K9M4P6R8T", substring = true).assertExists()
        assertNoText("Customs Duty")

        compose.onNodeWithText("Make Payment").performClick()
        compose.onNodeWithText("Insurance choice required").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModel.dismissAlert() }

        compose.onNodeWithText("Decline").performScrollTo().assertHasClickAction().performClick()
        compose.onNodeWithText("Insurance declined").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Make Payment").performClick()
        assertEquals(1, paymentRequests.get())

        capture("calculator_tier_quote_light.png")
    }

    @Test
    fun tierQuoteRendersTheSameServerRowsInDarkTheme() {
        val paymentRequests = AtomicInteger()
        setResults(ThemeController.Mode.DARK, paymentRequests)

        compose.onNodeWithText("Service Tier").assertIsDisplayed()
        compose.onNodeWithText("Base shipping").assertIsDisplayed()
        compose.onNodeWithText("Total Due").assertIsDisplayed()
        compose.onNodeWithText("Insurance (optional for your tier)").assertExists()
        assertNoText("Customs Duty")

        capture("calculator_tier_quote_dark.png")
    }

    private fun setResults(
        mode: ThemeController.Mode,
        paymentRequests: AtomicInteger,
    ): CalculatorViewModel {
        lateinit var viewModel: CalculatorViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            viewModel = CalculatorViewModel(FakeTierQuoteRepository())
            viewModel.onPackagesChange("1")
            viewModel.onInvoiceChange("150")
            viewModel.onActualWeightChange("5.5")
            viewModel.onProductChange("Laptop")
            viewModel.calculate()
        }
        compose.setContent {
            AirdropTheme {
                Box(
                    Modifier
                        .width(375.dp)
                        .height(812.dp)
                        .background(AirdropTheme.colors.gray200),
                ) {
                    CalculatorResultsScreen(
                        viewModel = viewModel,
                        onBack = {},
                        onDropAlert = {},
                        onMakePayment = { paymentRequests.incrementAndGet() },
                        onGovernmentCharges = {},
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.result.value?.tierQuote != null }
        compose.waitForIdle()
        return viewModel
    }

    private fun assertNoText(text: String) {
        assertTrue(compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
    }

    private fun capture(filename: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File(screenshotDir(), filename)
        FileOutputStream(output).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        saveToMediaStore(bitmap, filename)
    }

    private fun screenshotDir(): File =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "screenshots/calculator_tier_quote",
        ).also { it.mkdirs() }

    private fun saveToMediaStore(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relativePath = "Pictures/kotlin_ui_proof/calculator_tier_quote/${context.packageName}/"
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
            arrayOf(filename, relativePath),
        )
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    private class FakeTierQuoteRepository : CalculatorRepository {
        override suspend fun quoteShipment(request: TierQuoteRequest): TierQuote = QUOTE

        override suspend fun calculateShipment(
            shippingMethod: String,
            invoiceAmount: Double,
            weightLbs: Double?,
            numberOfPackages: Int,
            lengthInches: Double?,
            widthInches: Double?,
            heightInches: Double?,
            customDutyRateId: Int?,
        ): ShipmentCalculation = error("Airdrop must not use legacy calculate")

        override suspend fun searchDutyRates(query: String, limit: Int): List<CalcDutyRate> = emptyList()

        override suspend fun usdToJmdRate(): Double = 162.0
    }

    private companion object {
        val QUOTE = TierQuote(
            quoteReference = "Q-7H2K9M4P6R8T",
            customerTier = "SAVR",
            method = "AIR",
            destination = "JM",
            currency = "USD",
            lineItems = listOf(
                TierLineItem("base_shipping", "Base shipping", 20.0),
                TierLineItem("fuel_surcharge", "Fuel surcharge", 2.0),
                TierLineItem("insurance", "Insurance", 1.5),
                TierLineItem("aircoins_credit", "AirCoins credit", 0.0),
            ),
            subtotal = 23.5,
            totalDue = 23.5,
            status = "active",
            isExpired = false,
            expiresAt = "2099-01-02T03:04:05Z",
            insuranceOptions = TierInsuranceOptions(
                insuredValue = 150.0,
                ratePer100 = 1.0,
                blockSize = 100,
                blocks = 2,
                premium = 1.5,
                maxCoverage = null,
                coveredValue = 150.0,
                canDecline = true,
                mandatory = false,
                explicitRequired = true,
            ),
            insuranceChoiceRequired = true,
            aircoinsEarned = 4.0,
        )
    }
}
