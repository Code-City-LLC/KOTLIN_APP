package com.ga.airdrop.feature.cart

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ga.airdrop.BuildConfig
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.feature.shop.ShopInnerHeader

/**
 * NCB 3-D Secure challenge — Swift FigmaNcbThreeDSViewController parity:
 * "Card Authentication" header, the PowerTranz `redirect_data` HTML in a
 * WebView (JS on — the ACS page is a form/script app), and a bottom
 * "Complete Payment" CTA. Completion fires two ways, exactly like Swift:
 * automatically when the WebView reaches the merchant callback URL
 * ([isNcbThreeDsCallback], with Swift's 700 ms grace so the ACS finishes
 * posting), or manually via the CTA after the bank challenge.
 *
 * Leaving is an explicit choice (money-limbo guard): the card may already
 * be AUTHORIZED once the page is up; silent back would strand the charge
 * with no completed invoice.
 */
@Composable
fun NcbThreeDsScreen(
    redirectData: String,
    completing: Boolean,
    onComplete: () -> Unit,
    onLeave: () -> Unit,
    errorTitle: String? = null,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
) {
    val colors = AirdropTheme.colors
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentCompleting by rememberUpdatedState(completing)
    var callbackSeen by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (!currentCompleting) showLeaveDialog = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150),
    ) {
        ShopInnerHeader(
            title = "Card Authentication",
            onBack = { if (!currentCompleting) showLeaveDialog = true },
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("ncb-3ds-web"),
            factory = { context ->
                WebView(context).apply {
                    // ACS challenge pages are JS form apps; both required.
                    @Suppress("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.WHITE)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            if (isNcbThreeDsCallback(request.url?.toString())) {
                                callbackSeen = true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            if (isNcbThreeDsCallback(url)) {
                                callbackSeen = true
                                // Swift waits 700 ms so the ACS result post
                                // lands server-side before completion runs.
                                view.postDelayed({
                                    if (!currentCompleting) currentOnComplete()
                                }, 700)
                            }
                        }
                    }
                    loadDataWithBaseURL(
                        ncbWebBaseUrl(BuildConfig.API_BASE_URL),
                        redirectData,
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            onRelease = { it.destroy() },
        )
        CheckoutSolidButton(
            text = if (completing || callbackSeen && completing) {
                "Completing..."
            } else {
                "Complete Payment"
            },
            onClick = onComplete,
            enabled = !completing,
            loading = completing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp)
                .testTag("ncb-3ds-complete"),
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = colors.gray100,
            title = {
                Text(
                    "Leave card authentication?",
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                )
            },
            text = {
                Text(
                    "Your payment may already be in progress. If you leave now it will " +
                        "not be completed and you may need to contact support before retrying.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Stay", style = AirdropType.subtitle2, color = BrandPalette.OrangeMain)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        onLeave()
                    },
                    modifier = Modifier.testTag("ncb-3ds-leave"),
                ) {
                    Text("Leave", style = AirdropType.subtitle2, color = colors.textDescription)
                }
            },
        )
    }

    if (errorTitle != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            containerColor = colors.gray100,
            title = { Text(errorTitle, style = AirdropType.title2, color = colors.textDarkTitle) },
            text = {
                Text(errorMessage.orEmpty(), style = AirdropType.body2, color = colors.textDescription)
            },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text("OK", style = AirdropType.subtitle2, color = BrandPalette.OrangeMain)
                }
            },
        )
    }
}
