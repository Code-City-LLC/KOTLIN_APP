package com.ga.airdrop.feature.cart

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.BuildConfig
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
    viewModel: CartViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val redirectData = state.ncbRedirectData
    var completed by remember { mutableStateOf(false) }
    var showLeaveWarning by remember { mutableStateOf(false) }

    fun finalize() {
        if (!completed) {
            completed = true
            viewModel.completeNcbPayment()
        }
    }

    // The VM flips navToNcbSuccess once ncb-complete-payment returns the invoice.
    LaunchedEffect(state.navToNcbSuccess) {
        if (state.navToNcbSuccess) {
            viewModel.consumeNcbSuccessNav()
            onPaid()
        }
    }

    // Leaving mid-3DS may strand an already-authorized charge → warn first.
    BackHandler(enabled = !completed && !state.navToNcbSuccess) { showLeaveWarning = true }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
            // Manual fallback (Swift's "Complete Payment" button).
            GradientButton(
                text = if (completed || state.ncbBusy) "Confirming…" else "I've completed verification",
                onClick = { finalize() },
                loading = state.ncbBusy,
                enabled = !state.ncbBusy && !completed,
                modifier = Modifier
                    .padding(Spacing.md)
                    .fillMaxWidth(),
            )
            state.errorMessage?.let {
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

internal fun isNcbCallback(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("ncb-3ds-callback") ||
        lower.contains("ncb_3ds_callback") ||
        Regex("[?&]spi[_-]?token=").containsMatchIn(lower) ||
        lower.contains("spitoken")
}
