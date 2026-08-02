package com.ga.airdrop.feature.cart

import com.ga.airdrop.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * The 3DS callback recognizer must match ONLY our own origin and path.
 *
 * During 3DS the WebView is on **the bank's domain**, which we do not control.
 * The old recognizer was three bare `contains` checks plus a query-parameter
 * regex, with **no host, scheme or path check at all**, so every URL the bank
 * walked through was measured against substrings.
 *
 * I audited that function in ORC 88258 and reported *"host-agnostic, so the
 * apex move cannot break it — no change needed."* That was wrong. It is the
 * worst call I made on this codebase, and these negatives exist because of it.
 *
 * Canonical contract from Laravel (ORC 88696):
 * ```
 * route  user.checkout.ncb-3ds-callback
 * path   /user/checkout/ncb-3ds-callback     exact case
 * prod   https://airdropja.com/user/checkout/ncb-3ds-callback
 * stage  https://pre-staging.airdropja.com/user/checkout/ncb-3ds-callback
 * ```
 */
class NcbCallbackRecognizerTest {

    private val host = "airdropja.com"
    private val ok = "https://$host$NCB_CALLBACK_PATH"

    private fun match(url: String) = isNcbCallback(url, allowedHost = host)

    // ── Positives ───────────────────────────────────────────────────────────

    @Test
    fun `the canonical callback matches`() {
        assertTrue(match(ok))
        assertTrue("a trailing slash is the same route", match("$ok/"))
        assertTrue("a query does not change the route", match("$ok?spi_token=abc"))
        assertTrue("a fragment does not change the route", match("$ok#done"))
        assertTrue("host comparison is case-insensitive", match("https://AIRDROPJA.COM$NCB_CALLBACK_PATH"))
    }

    // ── The five that were TRUE before, and are the reason this exists ──────

    @Test
    fun `the path appearing in a QUERY VALUE on a foreign host is not our callback`() {
        assertFalse(match("https://bank.example/step?next=%2Fuser%2Fcheckout%2Fncb-3ds-callback"))
        assertFalse(match("https://bank.example/step?next=/user/checkout/ncb-3ds-callback"))
    }

    @Test
    fun `a token-looking string in the HOST is not our callback`() {
        assertFalse(match("https://spitoken.example.com/anything"))
        assertFalse(match("https://ncb-3ds-callback.evil.com/"))
    }

    @Test
    fun `a token-looking string in the FRAGMENT is not our callback`() {
        assertFalse(match("https://anything.example/x#spitoken"))
        assertFalse(match("https://anything.example/x#/user/checkout/ncb-3ds-callback"))
    }

    @Test
    fun `cleartext is never our callback`() {
        assertFalse("http:// must never be accepted", match("http://$host$NCB_CALLBACK_PATH"))
    }

    @Test
    fun `an spi_token query parameter on ANY host is not a trigger`() {
        // The old regex matched the parameter NAME with no host constraint.
        assertFalse(match("https://evil.example/?spi_token=whatever"))
        assertFalse(match("https://evil.example/?spitoken=whatever"))
        assertFalse("not even on OUR host at the wrong path", match("https://$host/?spi_token=x"))
    }

    // ── Origin strictness ───────────────────────────────────────────────────

    @Test
    fun `a suffix look-alike host is refused`() {
        // endsWith(".airdropja.com") would let this through.
        assertFalse(match("https://airdropja.com.evil.com$NCB_CALLBACK_PATH"))
        assertFalse(match("https://evilairdropja.com$NCB_CALLBACK_PATH"))
    }

    @Test
    fun `the retired host is refused even at the correct path`() {
        assertFalse(match("https://app.$host$NCB_CALLBACK_PATH"))
    }

    @Test
    fun `a sibling environment is refused — a prod build must not accept a staging callback`() {
        assertFalse(match("https://pre-staging.$host$NCB_CALLBACK_PATH"))
        assertFalse(isNcbCallback(ok, allowedHost = "pre-staging.$host"))
    }

    // ── Path strictness ─────────────────────────────────────────────────────

    @Test
    fun `the right host at the wrong path is refused`() {
        assertFalse(match("https://$host/"))
        assertFalse(match("https://$host/user/checkout"))
        assertFalse(match("https://$host/user/checkout/ncb-3ds-callback-extra"))
        assertFalse(match("https://$host/prefix/user/checkout/ncb-3ds-callback"))
    }

    // ── Degenerate input ────────────────────────────────────────────────────

    @Test
    fun `malformed and empty input never matches`() {
        assertFalse(match(""))
        assertFalse(match("not a url"))
        assertFalse(match("://"))
        assertFalse(match("javascript:alert(1)"))
    }

    @Test
    fun `an unknown allowed host fails CLOSED rather than open`() {
        assertFalse(isNcbCallback(ok, allowedHost = null))
        assertFalse(isNcbCallback(ok, allowedHost = ""))
    }

    // ── The build actually ships a usable host ──────────────────────────────

    @Test
    fun `the active build resolves a real host, so the default argument is not silently null`() {
        val h = URI(BuildConfig.WEB_BASE_URL).host
        assertTrue("WEB_BASE_URL has no host: ${BuildConfig.WEB_BASE_URL}", !h.isNullOrBlank())
        assertTrue(
            "the recognizer must accept THIS build's own callback",
            isNcbCallback("https://$h$NCB_CALLBACK_PATH"),
        )
    }
}
