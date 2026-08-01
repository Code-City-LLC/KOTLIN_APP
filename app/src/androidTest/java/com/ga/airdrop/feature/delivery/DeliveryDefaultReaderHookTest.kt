package com.ga.airdrop.feature.delivery

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.prefs.DeliveryDefaultsStore
import com.ga.airdrop.core.session.FakeAuthenticatedSessionBoundary
import com.ga.airdrop.data.model.DeliveryLocation
import com.ga.airdrop.data.model.DeliveryPreference
import com.ga.airdrop.data.model.DeliverySettingsPayload
import com.ga.airdrop.data.model.DeliveryValidationResponse
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.data.model.ReverseGeocodeResult
import com.ga.airdrop.data.repo.DeliveryGateway
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The About screen's "Default delivery method" was a setting that did nothing.
 *
 * It wrote [DeliveryDefaultsStore.preferredMethod]; About read it back to
 * display itself; logout cleared it — and NOTHING else ever looked at it. The
 * customer chose "Home delivery", went to checkout, and got Pickup.
 *
 * Swift has read it since FigmaDeliveryMethodViewController viewDidLoad:333,
 * which even calls itself "the reader hook its header promised". The store's own
 * KDoc claimed Swift had "no consumer yet either" — that claim was false and is
 * why this sat dead.
 *
 * Instrumented rather than a JVM unit test because the store is backed by
 * SharedPreferences: without a real Context its writes no-op and its reads
 * return null, so a JVM test of this would silently assert nothing.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryDefaultReaderHookTest {

    @Before
    fun setUp() {
        DeliveryDefaultsStore.init(InstrumentationRegistry.getInstrumentation().targetContext)
        DeliveryDefaultsStore.clearAll()
    }

    @After
    fun tearDown() {
        DeliveryDefaultsStore.clearAll()
    }

    @Test
    fun aLocalHomeDeliveryDefaultPreSelectsDeliveryMode() {
        DeliveryDefaultsStore.preferredMethod = DeliveryDefaultsStore.Method.HOME

        val viewModel = DeliveryMethodViewModel(repo = FakeGateway())

        assertEquals(
            "the About screen's default must reach the checkout tile",
            DeliveryMode.Delivery,
            viewModel.state.value.mode,
        )
    }

    @Test
    fun aLocalPickupDefaultPreSelectsPickupMode() {
        DeliveryDefaultsStore.preferredMethod = DeliveryDefaultsStore.Method.PICKUP

        val viewModel = DeliveryMethodViewModel(repo = FakeGateway())

        assertEquals(DeliveryMode.Pickup, viewModel.state.value.mode)
    }

    /**
     * The half that is easy to get wrong.
     *
     * Laravel's getPreference cannot distinguish "never chose" from "pickup" —
     * it defaults to pickup — so if the server answer were allowed to win, a
     * customer who deliberately chose Home delivery would be silently flipped
     * back on every visit. Swift guards this at :1522; so does Kotlin now.
     */
    @Test
    fun anExplicitLocalDefaultOutranksTheServerPreference() = runBlocking {
        DeliveryDefaultsStore.preferredMethod = DeliveryDefaultsStore.Method.HOME

        // ⚠️ sessionBoundary is REQUIRED here. Without it capture() returns null,
        // loadPreference() returns before it ever reads the server answer, and
        // this test passes for the wrong reason — the local default "wins"
        // only because nothing ever competed with it.
        val viewModel = DeliveryMethodViewModel(
            repo = FakeGateway(preferenceMode = "pickup"),
            sessionBoundary = FakeAuthenticatedSessionBoundary(),
        )
        viewModel.loadPreference()
        // loadPreference is a coroutine on viewModelScope; give it a beat.
        // Let loadPreference land. return@repeat would only skip one iteration,
        // so poll plainly instead.
        repeat(20) { kotlinx.coroutines.delay(25) }

        assertEquals(
            "a server 'pickup' default must not overwrite an explicit local Home delivery",
            DeliveryMode.Delivery,
            viewModel.state.value.mode,
        )
    }

    /** With no local default, the server preference is authoritative as before. */
    @Test
    fun withNoLocalDefaultTheServerPreferenceStillWins() = runBlocking {
        val viewModel = DeliveryMethodViewModel(
            repo = FakeGateway(preferenceMode = "delivery"),
            sessionBoundary = FakeAuthenticatedSessionBoundary(),
        )
        viewModel.loadPreference()
        repeat(40) { kotlinx.coroutines.delay(25) }

        assertEquals(DeliveryMode.Delivery, viewModel.state.value.mode)
    }

    private class FakeGateway(private val preferenceMode: String? = null) : DeliveryGateway {
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
        override suspend fun preference() =
            if (preferenceMode == null) {
                Result.failure<DeliveryPreference>(IllegalStateException("unset"))
            } else {
                Result.success(DeliveryPreference(deliveryMode = preferenceMode))
            }
        override suspend fun reverseGeocode(latitude: Double, longitude: Double) =
            Result.failure<ReverseGeocodeResult>(IllegalStateException("use device"))
        override suspend fun searchPlaces(query: String) = Result.success(emptyList<PlaceResult>())
    }
}
