package com.ga.airdrop.feature.cart

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ga.airdrop.MainActivity
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.ThemeController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NcbLargeScreenChallengeTest {

    /**
     * Android 16 ignores a fixed portrait request on displays whose smallest
     * width is at least 600dp. The production activity owns those configuration
     * changes so Compose keeps the live WebView (and its bank-owned DOM) instead
     * of replaying the one-shot RedirectData in a new Activity.
     */
    @Test
    fun largeScreenRotationKeepsLiveChallengeDomAndDoesNotReplayRedirectData() {
        var scenario: ActivityScenario<MainActivity>? = null
        val previousTheme = ThemeController.mode

        try {
            // Exercise the explicit-theme attachBaseContext path that previously
            // pinned every configuration qualifier while forcing uiMode.
            ThemeController.set(ThemeController.Mode.DARK)
            shell("wm size 1200x1920")
            shell("wm density 240")
            shell("wm user-rotation lock 0")

            val loadKey = "ncb_redirect_loads_${System.nanoTime()}"
            val host = TestNcbHost(challengeHtml(loadKey))
            scenario = launchMainActivity()
            scenario.onActivity { activity ->
                activity.setContent {
                    AirdropTheme {
                        NcbThreeDSScreen(onBack = {}, onPaid = {}, host = host)
                    }
                }
            }

            val firstWebView = waitForWebView(scenario)
            val firstActivity = AtomicReference<MainActivity>()
            scenario.onActivity { activity ->
                firstActivity.set(activity)
                assertTrue(
                    "test display is not a non-vacuous >=600dp profile",
                    activity.resources.configuration.smallestScreenWidthDp >= 600,
                )
                assertMainActivityOwnsAdaptiveChanges(activity)
                assertDarkQualifier(activity)
            }
            waitForJavascript(
                scenario,
                "Boolean(document.getElementById('otp')).toString()",
                "\"true\"",
            )
            evaluate(scenario, "document.getElementById('otp').value='846291'; 'ready'")
            assertEquals(
                "\"846291\"",
                evaluate(scenario, "document.getElementById('otp').value"),
            )
            assertEquals("\"1\"", evaluate(scenario, "window.__redirectLoads.toString()"))

            // UiDevice.setOrientationLeft() is ignored by the headless API 36
            // emulator used in GitHub Actions. Drive WindowManager directly;
            // the display rotation then produces the real Configuration change.
            shell("wm user-rotation lock 1")
            waitForOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE)

            scenario.onActivity { activity ->
                assertSame(
                    "MainActivity was recreated and destroyed the live bank DOM",
                    firstActivity.get(),
                    activity,
                )
                assertSame(
                    "the NCB WebView was replaced during a handled configuration change",
                    firstWebView,
                    requireNotNull(activity.window.decorView.findWebView()),
                )
                assertDarkQualifier(activity)
            }
            assertEquals(
                "\"846291\"",
                evaluate(scenario, "document.getElementById('otp').value"),
            )
            assertEquals(
                "the one-shot redirect page executed more than once",
                "\"1\"",
                evaluate(scenario, "window.__redirectLoads.toString()"),
            )
        } finally {
            scenario?.close()
            shell("wm user-rotation lock 0")
            shell("wm user-rotation free")
            shell("wm size reset")
            shell("wm density reset")
            ThemeController.set(previousTheme)
        }
    }

    private fun launchMainActivity(): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    private fun assertMainActivityOwnsAdaptiveChanges(activity: MainActivity) {
        val info = activity.packageManager.getActivityInfo(activity.componentName, 0)
        val required = ActivityInfo.CONFIG_SCREEN_LAYOUT or
            ActivityInfo.CONFIG_ORIENTATION or
            ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_KEYBOARD or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE
        assertEquals(required, info.configChanges and required)
    }

    private fun assertDarkQualifier(activity: MainActivity) {
        assertEquals(
            Configuration.UI_MODE_NIGHT_YES,
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
        )
    }

    private fun waitForOrientation(
        scenario: ActivityScenario<MainActivity>,
        expected: Int,
    ) {
        val deadline = System.currentTimeMillis() + 10_000
        var orientation = Configuration.ORIENTATION_UNDEFINED
        while (orientation != expected && System.currentTimeMillis() < deadline) {
            scenario.onActivity { orientation = it.resources.configuration.orientation }
            if (orientation != expected) Thread.sleep(100)
        }
        assertEquals("display did not reach the expected orientation", expected, orientation)
    }

    private fun waitForWebView(scenario: ActivityScenario<MainActivity>): WebView {
        val deadline = System.currentTimeMillis() + 10_000
        var found: WebView? = null
        while (found == null && System.currentTimeMillis() < deadline) {
            scenario.onActivity { found = it.window.decorView.findWebView() }
            if (found == null) Thread.sleep(100)
        }
        return requireNotNull(found) { "NCB WebView was not rendered" }
    }

    private fun evaluate(scenario: ActivityScenario<MainActivity>, script: String): String {
        val result = AtomicReference<String>()
        val callback = CountDownLatch(1)
        scenario.onActivity { activity ->
            requireNotNull(activity.window.decorView.findWebView())
                .evaluateJavascript(script) {
                    result.set(it)
                    callback.countDown()
                }
        }
        assertTrue("JavaScript result timed out", callback.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun waitForJavascript(
        scenario: ActivityScenario<MainActivity>,
        script: String,
        expected: String,
    ) {
        val deadline = System.currentTimeMillis() + 10_000
        var value: String? = null
        while (value != expected && System.currentTimeMillis() < deadline) {
            value = evaluate(scenario, script)
            if (value != expected) Thread.sleep(100)
        }
        assertEquals("bank challenge DOM did not become ready", expected, value)
    }

    private fun challengeHtml(loadKey: String) = """
        <html>
          <head><title>Synthetic bank challenge</title></head>
          <body><input id="otp" value=""></body>
          <script>
            const key = '$loadKey';
            window.__redirectLoads = Number(localStorage.getItem(key) || '0') + 1;
            localStorage.setItem(key, window.__redirectLoads.toString());
            history.replaceState(null, '', '/synthetic-bank');
          </script>
        </html>
    """.trimIndent()

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .close()
        Thread.sleep(500)
    }
}

private class TestNcbHost(
    redirectData: String,
) : NcbCheckoutHost {
    private val mutableUi = MutableStateFlow(
        NcbUiModel(
            redirectData = redirectData,
            navTo3DS = true,
        ),
    )
    override val ncbUi: StateFlow<NcbUiModel> = mutableUi

    override fun updateNcbForm(transform: (CartBillingForm) -> CartBillingForm) {
        mutableUi.value = mutableUi.value.copy(form = transform(mutableUi.value.form))
    }

    override fun createNcbSession(
        cardName: String,
        cardNumber: String,
        cardMonth: String,
        cardYear: String,
        cardCvv: String,
    ) = Unit

    override fun completeNcbPayment() = Unit
    override fun consumeNcb3DSNav() = Unit
    override fun consumeNcbSuccessNav() = Unit
}

private fun View.findWebView(): WebView? = when (this) {
    is WebView -> this
    is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findWebView() }
    else -> null
}
