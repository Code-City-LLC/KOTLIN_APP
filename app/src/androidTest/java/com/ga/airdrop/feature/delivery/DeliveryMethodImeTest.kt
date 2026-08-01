package com.ga.airdrop.feature.delivery

import androidx.core.graphics.Insets
import android.view.WindowInsets as PlatformWindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.data.model.DeliveryLocation
import com.ga.airdrop.data.model.DeliveryPreference
import com.ga.airdrop.data.model.DeliverySettingsPayload
import com.ga.airdrop.data.model.DeliveryValidationResponse
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.data.model.ReverseGeocodeResult
import com.ga.airdrop.data.repo.DeliveryGateway
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The keyboard used to cover the delivery address field and the "Choose
 * Currency" CTA.
 *
 * MainActivity calls `enableEdgeToEdge()`, which stops the window resizing for
 * the IME — the manifest's `adjustResize` no longer does the work, so every
 * screen has to consume `WindowInsets.ime` itself. This one consumed only
 * `navigationBars`.
 *
 * ⚠️ WHY A SYNTHETIC INSET, AND NOT A REAL KEYBOARD.
 *
 * Three earlier versions of this test drove the real IME and all three passed
 * against the BROKEN screen — which is worse than having no test. Measured with
 * `adb`, the inset that actually arrived was 63px on a 2400px display: a
 * gesture-bar sliver, not a keyboard. Compose's `performTextInput` injects
 * through the test's own InputConnection, so the on-screen keyboard is never
 * summoned, and `windowInsetsController.show(ime())` has no focused platform
 * editor to attach to. With a 63px "keyboard" nothing is ever covered and the
 * assertion holds no matter what the screen does.
 *
 * Dispatching a WindowInsetsCompat carrying a real-sized ime() inset is
 * deterministic, needs no keyboard app, and — the point — FAILS when the fix is
 * reverted. That was verified by reverting it.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryMethodImeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private companion object {
        /** Roughly a real keyboard on this display; any large value works. */
        const val FAKE_IME_PX = 900
    }

    /**
     * Restores the inset this test dispatches.
     *
     * Hygiene, not a bug fix: dispatching a WindowInsetsCompat is global mutable
     * state on the activity's view tree, and anything that sets it should put it
     * back. In practice the activity is torn down between classes, so this is
     * belt-and-braces.
     *
     * ⚠️ It is explicitly NOT the fix for DropAlertConsigneeParityTest. I wrote
     * that claim here first and it was wrong: those two failures reproduce
     * IDENTICALLY on origin/main with the same package selection, so they are
     * pre-existing and unrelated to this file. Recorded so nobody re-derives the
     * dead end — DropAlert passes alone everywhere and fails only when the whole
     * `feature` package runs, on main and on any branch.
     */
    @After
    fun clearSyntheticIme() {
        instrumentation.runOnMainSync {
            runCatching {
                val cleared = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                    .setVisible(WindowInsetsCompat.Type.ime(), false)
                    .build()
                compose.activity.window.decorView.dispatchApplyWindowInsets(
                    cleared.toWindowInsets() ?: PlatformWindowInsets.CONSUMED,
                )
            }
        }
    }

    @Test
    fun theKeyboardInsetMustNotCoverTheCtaOrTheAddressField() {
        lateinit var viewModel: DeliveryMethodViewModel
        instrumentation.runOnMainSync {
            // Mirror MainActivity: edge-to-edge, so insets reach Compose instead
            // of being swallowed by a window resize.
            compose.activity.enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            viewModel = DeliveryMethodViewModel(repo = FakeGateway())
        }

        var imeBottomPx = 0
        compose.setContent {
            AirdropTheme {
                imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
                DeliveryMethodScreen(onBack = {}, viewModel = viewModel)
            }
        }

        // The address field only exists in Delivery mode.
        compose.onNodeWithTag("delivery-tile-delivery").performClick()
        compose.waitForIdle()

        instrumentation.runOnMainSync {
            val decor = compose.activity.window.decorView
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, FAKE_IME_PX))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            decor.dispatchApplyWindowInsets(
                insets.toWindowInsets() ?: PlatformWindowInsets.CONSUMED,
            )
        }
        compose.waitForIdle()

        // If the platform refused the synthetic inset, say so loudly rather than
        // asserting something meaningless. A vacuous pass is what this test is
        // specifically designed not to do.
        assumeTrue(
            "synthetic ime inset was not applied (got ${imeBottomPx}px) — test cannot prove anything",
            imeBottomPx >= FAKE_IME_PX / 2,
        )

        var windowHeightPx = 0
        instrumentation.runOnMainSync {
            windowHeightPx = compose.activity.window.decorView.height
        }
        val keyboardTopPx = windowHeightPx - imeBottomPx
        val ctaBottomPx = compose.onNodeWithTag("delivery-cta")
            .fetchSemanticsNode().boundsInWindow.bottom
        val fieldBottomPx = compose.onNodeWithTag("delivery-search")
            .fetchSemanticsNode().boundsInWindow.bottom

        compose.onNodeWithTag("delivery-cta").assertIsDisplayed()
        assertTrue(
            "keyboard top=$keyboardTopPx must not cover the CTA (bottom=$ctaBottomPx)",
            ctaBottomPx <= keyboardTopPx + 1f,
        )
        assertTrue(
            "keyboard top=$keyboardTopPx must not cover the address field (bottom=$fieldBottomPx)",
            fieldBottomPx <= keyboardTopPx + 1f,
        )
    }

    private class FakeGateway : DeliveryGateway {
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
        override suspend fun searchPlaces(query: String) = Result.success(emptyList<PlaceResult>())
    }
}
