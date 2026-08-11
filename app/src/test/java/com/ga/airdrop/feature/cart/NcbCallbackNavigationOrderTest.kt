package com.ga.airdrop.feature.cart

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WHEN the NCB 3DS flow completes — the ordering nobody could test.
 *
 * ⚠️ THIS IS A P0 PAYMENT PATH AND IT SHIPPED WRONG.
 *
 * `NcbThreeDSScreen` used to do, inside an anonymous `WebViewClient` inside a
 * composable:
 *
 *     if (isNcbCallback(url)) { finalize(); return true }   // shouldOverrideUrlLoading
 *     if (isNcbCallback(url)) finalize()                    // onPageStarted
 *
 * `return true` tells the WebView "I handled it, do not load it" — so the
 * callback URL was **never fetched** and Laravel's POST may never have been
 * processed, while the client went on to report success. `onPageStarted`
 * completed before the page had even loaded, racing Laravel's own settlement.
 *
 * Two tests already covered this file and BOTH stayed green the whole time:
 * `NcbCallbackRecognizerTest` pins URL/origin recognition, and
 * `NcbCheckoutIdContractTest` pins the `checkout_id` wire shape. Neither could
 * see ordering, because the ordering lived where no test could call it. That is
 * why `NcbCallbackNavigationGate` was extracted — the fix and its guard had to
 * become reachable at the same time.
 *
 * Found by @Codex-CodexKotlinAudit (P0 #95203) against current source, accepted
 * as owner in #95219. iOS had already solved it in `FigmaCartViewController`
 * 5481-5530; `462ebdf8` turned this rail ON in Kotlin without it, which was
 * shipping half a parity.
 */
class NcbCallbackNavigationOrderTest {

    private val callback = "https://airdropja.com$NCB_CALLBACK_PATH"
    private val bank = "https://bank.example/3ds/step"
    private val landing = "https://airdropja.com/user/checkout"

    private fun gate() = NcbCallbackNavigationGate { url ->
        isNcbCallback(url, allowedHost = "airdropja.com")
    }

    // ── The defect itself ───────────────────────────────────────────────────

    @Test
    fun `the callback navigation is ALLOWED, never cancelled`() {
        // The whole bug in one assertion. `true` here means "WebView, don't
        // load it" — Laravel then never receives the POST that settles the
        // payment, and the customer's card is left authorised with nothing
        // recorded.
        assertFalse(
            "returning true cancels the navigation and Laravel never gets the callback",
            gate().shouldOverrideUrlLoading(callback),
        )
    }

    @Test
    fun `seeing the callback does NOT complete on its own`() {
        // Completion must not fire from the navigation hook: at that moment
        // Laravel has not processed the POST, so completing races settlement.
        val g = gate()
        g.shouldOverrideUrlLoading(callback)
        assertTrue("the sighting must be recorded", g.sawCallback)
        // Nothing here completes — completion is only decided in onPageFinished,
        // which the next tests cover. The gate exposing no "complete now" from
        // this path IS the fix.
    }

    // ── Completing at the right moment ──────────────────────────────────────

    @Test
    fun `completion fires once the callback page finishes loading`() {
        val g = gate()
        g.shouldOverrideUrlLoading(callback)
        assertTrue(g.shouldCompleteOnPageFinished(callback))
    }

    @Test
    fun `completion ALSO fires when the callback redirects us away`() {
        // ⚠️ NOT belt-and-braces — without this arm the flow never completes.
        //
        // PowerTranz expects RedirectData in an iframe, so Laravel's
        // ncb-callback.blade.php ends with
        //     window.parent.location = '/user/checkout'
        // We load RedirectData top-level, so window.parent IS window and the
        // callback page redirects the whole WebView. onPageFinished therefore
        // reports /user/checkout and NEVER the callback URL.
        //
        // iOS hit exactly this (@MagentaReef #89168): card authorised, app
        // sitting there doing nothing.
        val g = gate()
        g.shouldOverrideUrlLoading(callback)
        assertTrue(
            "the callback page navigates itself away; completing only on the " +
                "callback URL would never fire at all",
            g.shouldCompleteOnPageFinished(landing),
        )
    }

    // ── Not completing when we should not ───────────────────────────────────

    @Test
    fun `a page finishing before any callback does NOT complete`() {
        // The bank's own 3DS pages finish loading constantly. None of them may
        // trigger settlement.
        val g = gate()
        assertFalse(g.shouldCompleteOnPageFinished(bank))
        assertFalse(g.shouldCompleteOnPageFinished(landing))
    }

    @Test
    fun `walking the bank's pages never records a callback`() {
        val g = gate()
        g.shouldOverrideUrlLoading(bank)
        g.shouldOverrideUrlLoading("https://bank.example/3ds/otp")
        g.shouldOverrideUrlLoading("https://evil.example/?spi_token=x")
        assertFalse("only OUR origin+path counts", g.sawCallback)
        assertFalse(g.shouldCompleteOnPageFinished(landing))
    }

    @Test
    fun `every bank navigation is allowed through`() {
        // The gate must never cancel a navigation. The 3DS challenge only works
        // if the bank's pages actually load.
        val g = gate()
        assertFalse(g.shouldOverrideUrlLoading(bank))
        assertFalse(g.shouldOverrideUrlLoading(landing))
        assertFalse(g.shouldOverrideUrlLoading(callback))
    }

    // ── The latch ───────────────────────────────────────────────────────────

    @Test
    fun `the sighting latch never clears`() {
        // A later navigation must not un-see the callback, or a redirect chain
        // would strand an authorised charge.
        val g = gate()
        g.shouldOverrideUrlLoading(callback)
        g.shouldOverrideUrlLoading(bank)
        g.shouldOverrideUrlLoading(landing)
        assertTrue(g.sawCallback)
        assertTrue(g.shouldCompleteOnPageFinished(landing))
    }

    @Test
    fun `re-entering completion is the host's latch, not this gate's job`() {
        // This gate answers "may we complete now"; it deliberately does NOT
        // dedup. completeNcbPayment early-returns while busy and consumes the
        // spi token on success, so a repeated true here cannot double-charge.
        // Pinned so nobody "fixes" this by adding a second latch and quietly
        // breaks the retry path that the host's failure branch depends on.
        val g = gate()
        g.shouldOverrideUrlLoading(callback)
        assertTrue(g.shouldCompleteOnPageFinished(landing))
        assertTrue(g.shouldCompleteOnPageFinished(landing))
    }
}
