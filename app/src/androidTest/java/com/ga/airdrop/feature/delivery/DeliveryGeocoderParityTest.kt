package com.ga.airdrop.feature.delivery

import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropThemeProvider
import com.ga.airdrop.core.designsystem.theme.ThemeController
import com.ga.airdrop.data.model.DeliveryLocation
import com.ga.airdrop.data.model.DeliveryPreference
import com.ga.airdrop.data.model.DeliverySettingsPayload
import com.ga.airdrop.data.model.DeliveryValidationResponse
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.data.model.ReverseGeocodeResult
import com.ga.airdrop.data.repo.DeliveryGateway
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeliveryGeocoderParityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun pickupRejectsNonCooperativeLateDeliveryResults() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gateway = FakeGateway { query ->
            withContext(NonCancellable) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            listOf(PlaceResult(address = "$query result", latitude = 18.0, longitude = -76.8))
        }
        lateinit var viewModel: DeliveryMethodViewModel
        instrumentation.runOnMainSync {
            viewModel = DeliveryMethodViewModel(repo = gateway)
            viewModel.onModeSelected(DeliveryMode.Delivery)
            viewModel.onSearchQueryChange("Kingston")
        }
        assertTrue("search started", started.await(5, TimeUnit.SECONDS))

        instrumentation.runOnMainSync { viewModel.onModeSelected(DeliveryMode.Pickup) }
        release.countDown()
        Thread.sleep(250)

        assertEquals(DeliveryMode.Pickup, viewModel.state.value.mode)
        assertTrue(viewModel.state.value.searchResults.isEmpty())
    }

    @Test
    fun lateDeviceGeocodeCannotOverwriteCurrentMarker() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val gateway = FakeGateway { emptyList() }
        lateinit var viewModel: DeliveryMethodViewModel
        instrumentation.runOnMainSync {
            viewModel = DeliveryMethodViewModel(repo = gateway)
            viewModel.deviceGeocoder = { latitude, _ ->
                if (latitude == 18.0) {
                    firstStarted.countDown()
                    withContext(NonCancellable) { releaseFirst.await(5, TimeUnit.SECONDS) }
                    "Old Place"
                } else {
                    "Current Place"
                }
            }
            viewModel.onMapPointPicked(18.0, -77.0)
        }
        assertTrue("first geocode started", firstStarted.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { viewModel.onMapPointPicked(19.0, -76.0) }
        waitUntil { viewModel.state.value.validatedAddress == "Current Place" }
        releaseFirst.countDown()
        Thread.sleep(250)

        assertEquals(19.0 to -76.0, viewModel.state.value.markerCoord)
        assertEquals("Current Place", viewModel.state.value.validatedAddress)
    }

    @Test
    fun deliveryResultsRenderAtMostFiveInLightAndDark() {
        instrumentation.runOnMainSync { ThemeController.set(ThemeController.Mode.LIGHT) }
        compose.setContent {
            AirdropThemeProvider {
                DeliverySection(
                    state = DeliveryUiState(
                        mode = DeliveryMode.Delivery,
                        searchResults = (1..7).map { PlaceResult(address = "Result $it") },
                    ),
                    onSearchQueryChange = {},
                    onSubmitSearch = {},
                    onSearchResultPicked = {},
                    onMapPointPicked = { _, _ -> },
                    onUseCurrentLocation = {},
                )
            }
        }
        compose.onNodeWithTag("delivery-search-result-4").assertExists()
        compose.onNodeWithTag("delivery-search-result-5").assertDoesNotExist()

        instrumentation.runOnMainSync { ThemeController.set(ThemeController.Mode.DARK) }
        compose.waitForIdle()
        compose.onNodeWithTag("delivery-search-result-4").assertExists()
        compose.onNodeWithTag("delivery-search-result-5").assertDoesNotExist()
    }

    @Test
    fun rawFallbackCardIsVisibleThenReadableAddressReplacesIt() {
        lateinit var updateState: (DeliveryUiState) -> Unit
        val rawState = DeliveryUiState(
            mode = DeliveryMode.Delivery,
            markerCoord = 18.012 to -76.793,
            validatedAddress = "18.01200, -76.79300",
        )
        compose.setContent {
            AirdropThemeProvider {
                var state by remember { mutableStateOf(rawState) }
                updateState = { state = it }
                DeliverySection(
                    state = state,
                    onSearchQueryChange = {},
                    onSubmitSearch = {},
                    onSearchResultPicked = {},
                    onMapPointPicked = { _, _ -> },
                    onUseCurrentLocation = {},
                )
            }
        }
        compose.onNodeWithTag("delivery-selected-location").assertExists()
        compose.onNodeWithTag("delivery-selected-location-detail")
            .assertTextEquals("18.01200, -76.79300")

        compose.runOnIdle {
            updateState(rawState.copy(validatedAddress = "Devon House, Kingston, Jamaica"))
        }
        compose.onNodeWithTag("delivery-selected-location-detail")
            .assertTextEquals("Devon House, Kingston, Jamaica")
    }

    @Test
    fun selectedCardUsesTextDarkTitleInLightTheme() {
        assertSelectedCardColors(ThemeController.Mode.LIGHT)
    }

    @Test
    fun selectedCardUsesTextDarkTitleInDarkTheme() {
        assertSelectedCardColors(ThemeController.Mode.DARK)
    }

    private fun assertSelectedCardColors(mode: ThemeController.Mode) {
        instrumentation.runOnMainSync { ThemeController.set(mode) }
        var expected = 0
        compose.setContent {
            AirdropThemeProvider {
                expected = com.ga.airdrop.core.designsystem.theme.AirdropTheme.colors.textDarkTitle.toArgb()
                DeliverySection(
                    state = DeliveryUiState(
                        mode = DeliveryMode.Delivery,
                        markerCoord = 18.012 to -76.793,
                        validatedAddress = "Devon House, Kingston",
                    ),
                    onSearchQueryChange = {},
                    onSubmitSearch = {},
                    onSearchResultPicked = {},
                    onMapPointPicked = { _, _ -> },
                    onUseCurrentLocation = {},
                )
            }
        }
        listOf(
            "delivery-selected-location-pin",
            "delivery-selected-location-title",
            "delivery-selected-location-detail",
        ).forEach { tag ->
            val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true)
                .captureToImage().asAndroidBitmap()
            assertTrue("$tag must render textDarkTitle in $mode", bitmap.hasPixelNear(expected))
        }
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition timed out", predicate())
    }

    private fun Bitmap.hasPixelNear(target: Int, tolerance: Int = 28): Boolean {
        val tr = (target shr 16) and 0xff
        val tg = (target shr 8) and 0xff
        val tb = target and 0xff
        for (y in 0 until height) for (x in 0 until width) {
            val pixel = getPixel(x, y)
            if (kotlin.math.abs(((pixel shr 16) and 0xff) - tr) <= tolerance &&
                kotlin.math.abs(((pixel shr 8) and 0xff) - tg) <= tolerance &&
                kotlin.math.abs((pixel and 0xff) - tb) <= tolerance
            ) return true
        }
        return false
    }

    private class FakeGateway(
        private val search: suspend (String) -> List<PlaceResult>,
    ) : DeliveryGateway {
        override suspend fun deliverySettings() = Result.success(DeliverySettingsPayload())
        override suspend fun validateLocation(
            latitude: Double,
            longitude: Double,
            address: String?,
            totalWeightKg: Double?,
        ) = Result.success(DeliveryValidationResponse(valid = true))
        override suspend fun savePickupPreference(pickupLocation: String?) = Result.success(DeliveryPreference())
        override suspend fun saveDeliveryPreference(location: DeliveryLocation, totalWeightKg: Double?) =
            Result.success(DeliveryPreference())
        override suspend fun preference() = Result.failure<DeliveryPreference>(IllegalStateException("unset"))
        override suspend fun reverseGeocode(latitude: Double, longitude: Double) =
            Result.failure<ReverseGeocodeResult>(IllegalStateException("use device"))
        override suspend fun searchPlaces(query: String) = Result.success(search(query))
    }
}
