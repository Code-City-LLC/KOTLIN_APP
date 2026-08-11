package com.ga.airdrop.feature.cart

import java.net.URI
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * NCB 3-D Secure challenge — Swift FigmaNcbThreeDSViewController parity.
 * redirect_data is an HTML blob (not a URL), loaded via loadDataWithBaseURL with
 * the web base URL. Completion is detected when the WebView navigates to a URL
 * containing "ncb-3ds-callback" (or a query key normalizing to "spitoken"); then
 * we finalize via ncb-complete-payment. A manual button is the fallback, and a
 * leave-mid-3DS warning avoids stranding an authorized charge (money-limbo).
 */
@Composable
fun NcbThreeDSScreen(
    onBack: () -> Unit,
    onPaid: () -> Unit,
    host: NcbCheckoutHost,
) {
    val colors = AirdropTheme.colors
    val ui by host.ncbUi.collectAsState()
    val redirectData = ui.redirectData
    var showLeaveWarning by remember { mutableStateOf(false) }

    // Idempotent: dedup of the near-simultaneous WebView callbacks AND the
    // post-success guard both live in the host — completeNcbPayment early-returns
    // while busy, and it consumes the spi token on success so a late callback
    // can't re-POST. On FAILURE busy clears and the token survives, so the
    // manual button below re-enables and a retry works (no permanent lockout).
    fun finalize() {
        host.completeNcbPayment()
    }

    /**
     * Owns WHEN the 3DS flow may complete. Deliberately NOT Compose state and
     * NOT inline in the WebViewClient: the ordering defect in #95203 survived
     * precisely because it lived in an anonymous object inside a composable,
     * where no unit test could reach it. The two existing tests pin URL/origin
     * recognition and the checkout_id wire shape; neither could see ordering.
     */
    val navGate = remember { NcbCallbackNavigationGate(::isNcbCallback) }

    // The host flips navToSuccess once ncb-complete-payment returns the invoice.
    LaunchedEffect(ui.navToSuccess) {
        if (ui.navToSuccess) {
            host.consumeNcbSuccessNav()
            onPaid()
        }
    }

    // Leaving mid-3DS may strand an already-authorized charge → warn first.
    // Disabled only once we've succeeded and are navigating away.
    BackHandler(enabled = !ui.navToSuccess) { showLeaveWarning = true }

    fun requestLeave() {
        if (!ui.navToSuccess) showLeaveWarning = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray150),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = ::requestLeave),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_more2_back_chevron),
                        contentDescription = "Back",
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "Card Authentication",
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(40.dp))
            }

            if (redirectData.isNullOrBlank()) {
                Text(
                    "Preparing secure verification…",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    modifier = Modifier.padding(Spacing.md),
                )
            } else {
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            @Suppress("SetJavaScriptEnabled")
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                /**
                                 * ⚠️ RECORD THE CALLBACK — DO NOT COMPLETE, AND DO NOT
                                 * CANCEL THE NAVIGATION.
                                 *
                                 * This used to `finalize(); return true`. Returning true
                                 * tells the WebView "I handled it, don't load it" — so the
                                 * callback URL was NEVER FETCHED and Laravel's POST may
                                 * never have been processed, while the client went on to
                                 * report success. `onPageStarted` completed too, before the
                                 * page had even loaded, racing Laravel's own settlement.
                                 * Neither existing test caught it: they pin URL/origin
                                 * recognition and the checkout_id wire shape, not ordering.
                                 *
                                 * iOS resolved this in `decidePolicyFor` (FigmaCartViewController
                                 * 5481-5530): record here, `.allow` the navigation, complete
                                 * only after the load lands. This is the Android equivalent —
                                 * `false` means "WebView, you load it".
                                 *
                                 * Confirmed by @Codex-CodexKotlinAudit P0 #95203 and accepted
                                 * as owner in #95219. `462ebdf8` turned this rail ON without
                                 * this fix; that was shipping half a parity.
                                 */
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean =
                                    navGate.shouldOverrideUrlLoading(
                                        request?.url?.toString().orEmpty(),
                                    )

                                /**
                                 * COMPLETE HERE, AND ONLY ON THE EXACT CALLBACK URL.
                                 *
                                 * Laravel PRESERVES the top-level callback URL. Deployed
                                 * `origin/pre_staging` `5705a8cd`,
                                 * `resources/views/checkout/ncb-callback.blade.php`
                                 * `redirectEmbeddedParent`:
                                 *
                                 *     if (window.parent === window) { return false; }
                                 *     window.parent.location = '/user/checkout';
                                 *
                                 * with Laravel's own comment: "Swift and Android load the
                                 * same RedirectData as a top-level WebView document and
                                 * detect this exact callback URL after the POST finishes.
                                 * Redirecting a top-level WebView here races that native
                                 * completion signal."
                                 *
                                 * We load RedirectData top-level, so the early return
                                 * applies to us: the page does NOT navigate away, and this
                                 * callback reports the callback URL itself. Exact
                                 * recognition after the load lands is therefore the whole
                                 * trigger — the POST has been processed by then.
                                 *
                                 * There is deliberately NO latch and no second arm. The
                                 * canonical callback is a POST, which Android never routes
                                 * through `shouldOverrideUrlLoading`, and that hook may also
                                 * fire for subframes — so any recorded state would be dead
                                 * on the shipping path and armable from a frame that is not
                                 * the callback. Verifier decision @Codex-CodexKotlinAudit
                                 * #95239.
                                 *
                                 * If a provider variant ever violates that contract, the
                                 * user-visible manual confirmation button on this screen is
                                 * the fallback — not a stateful guess in here.
                                 *
                                 * Safe because completion trusts this page for NOTHING: it
                                 * asks our own authenticated server for the outcome with the
                                 * server-issued spi_token plus checkout_id, and
                                 * completeNcbPayment is latched once-only. The page is a
                                 * trigger, never a source of truth.
                                 */
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (navGate.shouldCompleteOnPageFinished(url.orEmpty())) {
                                        finalize()
                                    }
                                }
                            }
                            loadDataWithBaseURL(
                                BuildConfig.WEB_BASE_URL,
                                redirectData,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                )
            }
            // Manual fallback (Swift's "Complete Payment" button). Re-enables
            // after a failed confirm so the user can retry the still-valid token.
            GradientButton(
                text = if (ui.busy) "Confirming…" else "I've completed verification",
                onClick = { finalize() },
                loading = ui.busy,
                enabled = !ui.busy,
                modifier = Modifier
                    .padding(Spacing.md)
                    .fillMaxWidth(),
            )
            ui.errorMessage?.let {
                Text(
                    it,
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }

    if (showLeaveWarning) {
        AlertDialog(
            onDismissRequest = { showLeaveWarning = false },
            title = { Text("Leave payment?", style = AirdropType.title1) },
            text = {
                Text(
                    "This payment may already be authorized by your bank. If you leave now and it went through, check Shipments before paying again.",
                    style = AirdropType.body2,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLeaveWarning = false; onBack() }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveWarning = false }) { Text("Stay") }
            },
        )
    }
}

/** Laravel route `user.checkout.ncb-3ds-callback`, exact case (ORC 88696). */
internal const val NCB_CALLBACK_PATH = "/user/checkout/ncb-3ds-callback"

/**
 * Is this WebView navigation OUR 3DS callback?
 *
 * ## What this replaced, and why it was a hole
 *
 * Three bare `contains` checks plus a query-parameter regex, with **no host,
 * scheme or path check at all**. During 3DS the WebView sits on **the bank's
 * domain**, which we do not control, and every URL it walked through was
 * measured against substrings. All of these returned TRUE before this change:
 *
 * ```
 * https://bank.example/step?next=%2Fuser%2Fcheckout%2Fncb-3ds-callback  (query VALUE)
 * https://spitoken.example.com/anything                                  (in the HOST)
 * https://anything.example/x#spitoken                                    (FRAGMENT)
 * http://<ourhost>/user/checkout/ncb-3ds-callback                        (cleartext)
 * https://evil.example/?spi_token=whatever                               (any host)
 * ```
 *
 * I audited this in ORC 88258 and reported *"host-agnostic, so the apex move
 * cannot break it — no change needed."* That was wrong, and it is the worst
 * call I made on this codebase. Host-agnosticism is not a feature; it is a
 * missing origin check on a payment callback.
 *
 * ## What it does now — exact origin + path, nothing else
 *
 *  - **https only.** A cleartext callback is never ours.
 *  - **Exact host equality** against the ACTIVE BUILD's [BuildConfig.WEB_BASE_URL].
 *    Not `endsWith`, not `contains` — a suffix test is how `airdropja.com.evil.com`
 *    gets in. This also makes a prod build reject a staging callback, and the reverse.
 *  - **Exact path** [NCB_CALLBACK_PATH]; a trailing slash is tolerated.
 *  - **Query and fragment are NOT triggers.** The `spi_token` arm is gone: a
 *    token in a query string says nothing about origin.
 *
 * ## Arrival is NOT success
 *
 * ORC 88696: the route is POST-only, a live GET returns 405, and an invalid
 * empty POST returns **HTTP 200 with `success=false`**. Reaching this URL proves
 * the bank redirected us and nothing more. This function answers "is this our
 * callback", never "did the payment succeed" — only the Laravel completion
 * response decides that.
 */
internal fun isNcbCallback(
    url: String,
    allowedHost: String? = runCatching { URI(BuildConfig.WEB_BASE_URL).host }.getOrNull(),
): Boolean {
    if (allowedHost.isNullOrBlank()) return false
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    if (!"https".equals(uri.scheme, ignoreCase = true)) return false
    if (!allowedHost.equals(uri.host, ignoreCase = true)) return false
    return uri.path?.lowercase()?.trimEnd('/').orEmpty() == NCB_CALLBACK_PATH
}

/**
 * WHEN the NCB 3DS flow may complete — extracted so it can be unit-tested.
 *
 * ⚠️ THIS EXISTS BECAUSE THE ORDERING BUG WAS UNREACHABLE BY TESTS.
 *
 * The defect (@Codex-CodexKotlinAudit P0 #95203, accepted in #95219) lived in
 * an anonymous `WebViewClient` inside a composable:
 *
 *     if (isNcbCallback(url)) { finalize(); return true }   // and onPageStarted too
 *
 * `return true` tells the WebView "I handled it, don't load it", so the
 * callback URL was never fetched and Laravel's POST may never have been
 * processed — while the client reported success. `onPageStarted` completed
 * before the page had even loaded, racing Laravel's settlement.
 *
 * `NcbCallbackRecognizerTest` and `NcbCheckoutIdContractTest` both passed
 * throughout: one pins URL/origin recognition, the other the `checkout_id`
 * wire shape. Neither could see ordering, because there was nothing to call.
 *
 * THE RULE — two decisions, no state:
 *  1. EVERY navigation is allowed (`false`). Cancelling is what stopped the
 *     callback from ever reaching Laravel.
 *  2. Completion fires ONLY on the EXACT callback URL in `onPageFinished` —
 *     after the load lands, so Laravel has already processed the POST.
 *
 * ⚠️ THERE IS DELIBERATELY NO LATCH, AND THE ONE I ADDED WAS REMOVED ON
 * VERIFIER DECISION @Codex-CodexKotlinAudit #95239.
 *
 * My first repair recorded a `sawCallback` flag in `shouldOverrideUrlLoading`
 * and also completed on any later page once armed. Three things were wrong
 * with it:
 *
 *  - The canonical callback is a POST, and Android does not call
 *    `shouldOverrideUrlLoading` for POST requests. Laravel's route is POST-only
 *    (`routes/modules/customer.php:322`), so the flag could never arm on the
 *    shipping path — my tests "proved" it only by hand-calling a hook Android
 *    never invokes.
 *  - Android may call that hook for SUBFRAMES, so the flag could arm from a
 *    frame that is not the callback at all, giving completion a second trigger
 *    with no evidence behind it.
 *  - The redirect it was meant to catch does not happen to us. I claimed
 *    `ncb-callback.blade.php` redirects a top-level WebView unconditionally,
 *    carrying iOS's historical finding (@MagentaReef #89168) forward as current
 *    fact. Verified against deployed `origin/pre_staging` `5705a8cd`,
 *    `redirectEmbeddedParent`:
 *
 *        if (window.parent === window) { return false; }
 *        window.parent.location = '/user/checkout';
 *
 *    Laravel's own comment: "Swift and Android load the same RedirectData as a
 *    top-level WebView document and detect this exact callback URL after the
 *    POST finishes. Redirecting a top-level WebView here races that native
 *    completion signal." The server PRESERVES the URL on purpose so exact
 *    recognition is sufficient.
 *
 * If a provider variant ever violates that contract, the user-visible manual
 * confirmation button on this screen is the fallback — not a stateful guess.
 *
 * Trusting the page for nothing is what makes this safe — completion asks our
 * own authenticated server for the outcome with the server-issued spi_token and
 * checkout_id, and `completeNcbPayment` is latched once-only. The page is a
 * trigger, never a source of truth.
 */
internal class NcbCallbackNavigationGate(
    private val isCallback: (String) -> Boolean,
) {
    /**
     * ALWAYS false — never cancel a navigation.
     *
     * `true` here is the original P0: it tells the WebView "I handled it, do
     * not load it", so the callback URL was never fetched and Laravel's POST
     * may never have been processed while the client reported success.
     *
     * Nothing is recorded here. The canonical callback is a POST and Android
     * does not call this hook for POST requests, so any state set here would be
     * dead on the shipping path — and Android may call it for SUBFRAMES, so a
     * latch could arm from a frame that is not the callback at all.
     */
    fun shouldOverrideUrlLoading(url: String): Boolean = false

    /**
     * Complete ONLY on the exact callback URL.
     *
     * Laravel deliberately preserves that URL for a top-level WebView
     * (`5705a8cd`, `ncb-callback.blade.php` `redirectEmbeddedParent`:
     * `if (window.parent === window) return false`), precisely so this fires.
     * Exact recognition is therefore sufficient, and it is the whole trigger.
     *
     * If a provider variant ever violates that contract, the user-visible
     * manual confirmation button on this screen is the fallback — not a
     * stateful guess in here.
     */
    fun shouldCompleteOnPageFinished(url: String): Boolean = isCallback(url)
}
