package com.ga.airdrop.feature.calculator

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
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
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DutyRateSearchParityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedCatalogueRetriesInLightTheme() = verifyFailure(ThemeController.Mode.LIGHT)
    @Test fun failedCatalogueRetriesInDarkTheme() = verifyFailure(ThemeController.Mode.DARK)
    @Test fun emptyCataloguePreservesDescriptionInLightTheme() = verifyEmpty(ThemeController.Mode.LIGHT)
    @Test fun emptyCataloguePreservesDescriptionInDarkTheme() = verifyEmpty(ThemeController.Mode.DARK)
    @Test fun allMatchesAreSelectableInLightTheme() = verifyAllMatches(ThemeController.Mode.LIGHT)
    @Test fun allMatchesAreSelectableInDarkTheme() = verifyAllMatches(ThemeController.Mode.DARK)

    private fun verifyFailure(mode: ThemeController.Mode) {
        val repository = SearchRepository { throw IOException("offline") }
        val model = showSearch(mode, repository)
        compose.waitUntil(5_000) { model.state.value.searchState == DutyRateSearchState.Failed }
        compose.onNodeWithText(FAILURE_TEXT).performScrollTo().assertIsDisplayed()
        assertNoText(EMPTY_TEXT)
        assertNoText("No products found")
        capture("failure_${mode.name.lowercase()}")

        compose.runOnIdle {
            repository.search = { listOf(CalcDutyRate(60, "BOOK", 37.0)) }
            model.onProductChange("books")
        }
        compose.waitUntil(5_000) { model.state.value.searchState is DutyRateSearchState.Results }
        compose.onNodeWithText("BOOK").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(60, model.state.value.selectedDutyRate?.id)
            assertEquals("BOOK", model.state.value.product)
            assertEquals(DutyRateSearchState.Hidden, model.state.value.searchState)
        }
        assertNoText(FAILURE_TEXT)
    }

    private fun verifyEmpty(mode: ThemeController.Mode) {
        val model = showSearch(mode, SearchRepository { emptyList() })
        compose.waitUntil(5_000) { model.state.value.searchState is DutyRateSearchState.Results }
        compose.onNodeWithText(EMPTY_TEXT).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals("book", model.state.value.product) }
        assertNoText(FAILURE_TEXT)
        assertNoText("No products found")
        capture("empty_${mode.name.lowercase()}")
    }

    private fun verifyAllMatches(mode: ThemeController.Mode) {
        val rows = (1..30).map { CalcDutyRate(it, "Book ${it.toString().padStart(2, '0')}", 37.0) }
        val model = showSearch(mode, SearchRepository { rows })
        compose.waitUntil(5_000) { model.state.value.searchState is DutyRateSearchState.Results }
        compose.onNodeWithText("Book 30").performScrollTo().assertIsDisplayed()
        capture("last_match_${mode.name.lowercase()}")
        compose.onNodeWithText("Book 30").performClick()
        compose.runOnIdle {
            assertEquals(30, model.state.value.selectedDutyRate?.id)
            assertEquals("Book 30", model.state.value.product)
            assertEquals(DutyRateSearchState.Hidden, model.state.value.searchState)
            model.onMethodSelected(ShippingMethod.STANDARD)
            model.onProductChange("custom description")
        }
        compose.onNodeWithText("custom description").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(DutyRateSearchState.Hidden, model.state.value.searchState) }
        assertNoText("Book 01")
    }

    private fun showSearch(mode: ThemeController.Mode, repository: SearchRepository): CalculatorViewModel {
        lateinit var model: CalculatorViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ThemeController.set(mode)
            model = CalculatorViewModel(repository)
            model.onMethodSelected(ShippingMethod.EXPRESS)
            model.onProductChange("book")
        }
        compose.setContent {
            AirdropTheme {
                Box(Modifier.width(320.dp).height(812.dp).background(AirdropTheme.colors.gray200)) {
                    CalculatorScreen(model, onBack = {}, onShowResults = {})
                }
            }
        }
        return model
    }

    private fun assertNoText(text: String) {
        assertTrue(compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty())
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "screenshots/duty_search").also { it.mkdirs() }
        FileOutputStream(File(directory, "$name.png")).use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private class SearchRepository(var search: suspend (String) -> List<CalcDutyRate>) : CalculatorRepository {
        override suspend fun searchDutyRates(query: String, limit: Int) = search(query)
        override suspend fun usdToJmdRate() = 162.0
        override suspend fun calculateShipment(
            shippingMethod: String, invoiceAmount: Double, weightLbs: Double?, numberOfPackages: Int,
            lengthInches: Double?, widthInches: Double?, heightInches: Double?, customDutyRateId: Int?,
        ): ShipmentCalculation = error("Search must not request a quote")
    }

    private companion object {
        const val FAILURE_TEXT = "Couldn't load the customs item list. Check your connection and try again."
        const val EMPTY_TEXT = "No matching customs item \u2014 keep your own description and calculate."
    }
}
