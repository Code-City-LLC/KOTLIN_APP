package com.ga.airdrop.feature.cart

import java.net.URI
import android.graphics.Bitmap
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
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val url = request?.url?.toString().orEmpty()
                                    if (isNcbCallback(url)) {
                                        finalize()
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    if (isNcbCallback(url.orEmpty())) finalize()
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

