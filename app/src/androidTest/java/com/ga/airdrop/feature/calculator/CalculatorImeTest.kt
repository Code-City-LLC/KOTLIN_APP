package com.ga.airdrop.feature.calculator

import android.view.WindowInsets as PlatformWindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The calculator already consumes the IME inset and its content can scroll.
 * The remaining short-screen defect was narrower: focus belonged to the inner
 * BasicTextField, so Compose revealed the cursor but left the bottom of the
 * decorated field underneath the keyboard.
 *
 * A synthetic inset is deliberate. Compose test text injection does not open a
 * real keyboard reliably; asserting against the gesture-bar-sized inset would
 * pass even when the field is covered. This dispatches a keyboard-sized inset
 * and measures the complete outer field against the resulting keyboard top.
 */
@RunWith(AndroidJUnit4::class)
class CalculatorImeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun useShortScreen() {
        // Matches the bounded profile that reproduced the defect on device.
        // 1904px keeps the inner editor visible while clipping the decorated
        // field, making this test fail for the precise pre-fix behavior.
        shell("wm size 1080x1904")
    }

    @After
    fun clearSyntheticIme() {
        instrumentation.runOnMainSync {
            val cleared = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.ime(), false)
                .build()
            compose.activity.window.decorView.dispatchApplyWindowInsets(
                cleared.toWindowInsets() ?: PlatformWindowInsets.CONSUMED,
            )
        }
        shell("wm size reset")
    }

    @Test
    fun focusingInvoiceKeepsTheEntireDecoratedFieldAboveTheKeyboardOnAShortScreen() {
        lateinit var viewModel: CalculatorViewModel
        instrumentation.runOnMainSync {
            compose.activity.enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            viewModel = CalculatorViewModel(FakeCalculatorRepository())
        }

        var imeBottomPx = 0
        compose.setContent {
            AirdropTheme {
                imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
                Box(Modifier.fillMaxSize()) {
                    CalculatorScreen(
                        viewModel = viewModel,
                        onBack = {},
                        onShowResults = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("calculator-invoice-input", useUnmergedTree = true).performClick()

        var windowHeightPx = 0
        instrumentation.runOnMainSync {
            windowHeightPx = compose.activity.window.decorView.height
            val fakeImePx = windowHeightPx / 2
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, fakeImePx))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            compose.activity.window.decorView.dispatchApplyWindowInsets(
                insets.toWindowInsets() ?: PlatformWindowInsets.CONSUMED,
            )
        }
        compose.waitForIdle()

        assumeTrue(
            "synthetic IME inset was not applied (got $imeBottomPx px)",
            imeBottomPx >= windowHeightPx / 4,
        )

        val keyboardTopPx = windowHeightPx - imeBottomPx
        val fieldBottomPx = compose
            .onNodeWithTag("calculator-invoice-field", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInWindow
            .bottom

        assertTrue(
            "keyboard top=$keyboardTopPx must not cover the full invoice field (bottom=$fieldBottomPx)",
            fieldBottomPx <= keyboardTopPx + 1f,
        )
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
            customDutyRateId: Int?,
        ): ShipmentCalculation = throw AssertionError("Unused in CalculatorImeTest")

        override suspend fun searchDutyRates(query: String, limit: Int): List<CalcDutyRate> = emptyList()

        override suspend fun usdToJmdRate(): Double = 156.0
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
        Thread.sleep(500)
    }
}
