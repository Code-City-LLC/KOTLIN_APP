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
 * `NcbCallbackRecognizerTest` and `NcbCheckoutIdContractTest` both stayed green
 * throughout: one pins URL/origin recognition, the other the `checkout_id` wire
 * shape. Neither could see ordering, because the ordering lived in an anonymous
 * object inside a composable where no test could call it. `NcbCallbackNavigationGate`
 * exists so the fix and its guard became reachable together.
 *
 * Found by @Codex-CodexKotlinAudit (P0 #95203), accepted in #95219.
 *
 * ⚠️ TWICE CORRECTED, AND BOTH CORRECTIONS CAME FROM THE VERIFIER.
 *
 * My first repair added a `sawCallback` latch recorded in
 * `shouldOverrideUrlLoading`, and completed on any later page once armed.
 * #95230 then #95239 established that it was unproven and had to go:
 *
 *  - The canonical callback is a **POST**, and Android does not call
 *    `shouldOverrideUrlLoading` for POST requests. Laravel's route is POST-only
 *    (`routes/modules/customer.php:322`), so the latch could never arm on the
 *    shipping path. My tests "proved" it only by hand-calling a hook Android
 *    never invokes — a false green over a sequence that cannot occur.
 *  - Android may call that hook for **subframes**, so the latch could arm from
 *    a frame that is not the callback at all.
 *  - The redirect it was meant to catch does not happen to us. Deployed
 *    `origin/pre_staging` `5705a8cd` `redirectEmbeddedParent` returns early
 *    when `window.parent === window`, deliberately PRESERVING the callback URL
 *    so native `onPageFinished(callback)` can fire.
 *
 * What remains is the whole contract, and it is stateless:
 *   1. every navigation is ALLOWED (`false`);
 *   2. completion fires ONLY on the EXACT callback URL in `onPageFinished`.
 *
 * If a provider variant ever violates that, the user-visible manual
 * confirmation button is the fallback — not a stateful guess.
 */
class NcbCallbackNavigationOrderTest {

    private val callback = "https://airdropja.com$NCB_CALLBACK_PATH"
    private val bank = "https://bank.example/3ds/step"
    private val landing = "https://airdropja.com/user/checkout"

    private fun gate() = NcbCallbackNavigationGate { url ->
        isNcbCallback(url, allowedHost = "airdropja.com")
    }

    // ── 1. Navigation is always allowed ─────────────────────────────────────

    @Test
    fun `the callback navigation is ALLOWED, never cancelled`() {
        // The original P0 in one assertion. `true` means "WebView, don't load
        // it" — Laravel then never receives the POST that settles the payment,
        // and the customer's card is left authorised with nothing recorded.
        assertFalse(
            "returning true cancels the navigation and Laravel never gets the callback",
            gate().shouldOverrideUrlLoading(callback),
        )
    }

    @Test
    fun `every navigation is allowed through, whatever it is`() {
        // The 3DS challenge only works if the bank's own pages actually load.
        // Nothing this gate sees may ever be cancelled.
        val g = gate()
        assertFalse(g.shouldOverrideUrlLoading(bank))
        assertFalse(g.shouldOverrideUrlLoading("https://bank.example/3ds/otp"))
        assertFalse(g.shouldOverrideUrlLoading(landing))
        assertFalse(g.shouldOverrideUrlLoading(callback))
        assertFalse(g.shouldOverrideUrlLoading(""))
    }

    // ── 2. Completion, and ONLY on the exact callback ───────────────────────

    @Test
    fun `CANONICAL — a POST callback completes with NO shouldOverride call at all`() {
        // ⚠️ THE SEQUENCE THAT REALLY SHIPS, and the one my first repair failed
        // to model. Android does not call shouldOverrideUrlLoading for POST,
        // and Laravel's callback route is POST-only — so that hook never runs.
        // Completion must come from onPageFinished alone.
        //
        // Laravel preserves the callback URL for exactly this (deployed
        // 5705a8cd: `if (window.parent === window) return false`).
        val g = gate()
        // No shouldOverrideUrlLoading call. None. That is the point.
        assertTrue(
            "the canonical POST callback must complete from onPageFinished alone",
            g.shouldCompleteOnPageFinished(callback),
        )
    }

    @Test
    fun `completion needs no prior navigation callback of any kind`() {
        // Guards statelessness directly: if someone reintroduces a latch and
        // makes completion depend on it, the shipping path silently stops
        // completing and the card is authorised with nothing shown.
        assertTrue(gate().shouldCompleteOnPageFinished(callback))
    }

    @Test
    fun `a page finishing that is NOT the callback does not complete`() {
        // The bank's pages and our own checkout landing finish loading
        // constantly. None of them may trigger settlement.
        val g = gate()
        assertFalse(g.shouldCompleteOnPageFinished(bank))
        assertFalse(
            "the post-callback landing page is not itself a completion trigger",
            g.shouldCompleteOnPageFinished(landing),
        )
        assertFalse(g.shouldCompleteOnPageFinished(""))
    }

    @Test
    fun `recognition stays exact — a look-alike never completes`() {
        // Origin and path both matter; this is the strict recognizer from #231.
        val g = gate()
        assertFalse(g.shouldCompleteOnPageFinished("https://evil.example$NCB_CALLBACK_PATH"))
        assertFalse(g.shouldCompleteOnPageFinished("https://airdropja.com.evil.com$NCB_CALLBACK_PATH"))
        assertFalse(g.shouldCompleteOnPageFinished("https://airdropja.com/?spi_token=x"))
        assertFalse("cleartext is never ours", g.shouldCompleteOnPageFinished("http://airdropja.com$NCB_CALLBACK_PATH"))
    }

    @Test
    fun `a query or trailing slash on the callback still completes`() {
        val g = gate()
        assertTrue(g.shouldCompleteOnPageFinished("$callback?spi_token=abc"))
        assertTrue(g.shouldCompleteOnPageFinished("$callback/"))
    }

    // ── Re-entry is the host's job, not this gate's ─────────────────────────

    @Test
    fun `the gate does not dedup — that latch belongs to the host`() {
        // This answers "may we complete now"; it deliberately does NOT dedup.
        // completeNcbPayment early-returns while busy and consumes the spi
        // token on success. A second latch here would break the retry path the
        // host's failure branch depends on, turning a recoverable decline into
        // a permanent lockout. Pinned so nobody "hardens" it that way.
        val g = gate()
        assertTrue(g.shouldCompleteOnPageFinished(callback))
        assertTrue(g.shouldCompleteOnPageFinished(callback))
    }
}
