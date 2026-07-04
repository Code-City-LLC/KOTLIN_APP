package com.ga.airdrop.feature.shipments

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.SubcomposeAsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.GradientPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.util.Locale

/**
 * Invoice viewer — behavior from FigmaInvoiceViewerScreenViewController
 * (no dedicated Figma node; modal viewer): renders the invoice URL (PDF via
 * an embedded viewer, images natively), with "Save to Files" (DownloadManager)
 * and "Share" actions.
 */
@Composable
fun InvoiceViewerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current

    // Swift upgrades http:// → https:// before loading.
    val secureUrl = remember(url) {
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
    }
    val isImage = remember(secureUrl) {
        val path = Uri.parse(secureUrl).path.orEmpty().lowercase(Locale.US)
        listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp").any { path.endsWith(it) }
    }
    val isPdf = remember(secureUrl) {
        Uri.parse(secureUrl).path.orEmpty().lowercase(Locale.US).endsWith(".pdf") || !isImage
    }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val fileName = remember(secureUrl, title) {
        Uri.parse(secureUrl).lastPathSegment?.takeIf { it.contains('.') }
            ?: (title.ifBlank { "Invoice" } + if (isPdf) ".pdf" else ".jpg")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
    ) {
        ShipmentsDetailHeader(title = "Invoice", onBack = onBack)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(Spacing.md)
                .clip(RoundedCornerShape(Radius.s))
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s)),
        ) {
            when {
                secureUrl.isBlank() -> {
                    Text(
                        text = "No invoice URL provided.",
                        style = AirdropType.body2,
                        color = colors.textDescription,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                isImage -> {
                    SubcomposeAsyncImage(
                        model = secureUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        loading = { ShipmentsLoadingIndicator(Modifier.fillMaxSize()) },
                        error = {
                            Text(
                                text = "Couldn't download $fileName.",
                                style = AirdropType.body2,
                                color = colors.textDescription,
                                textAlign = TextAlign.Center,
                            )
                        },
                    )
                }
                else -> {
                    // PDFs render through an embedded viewer page (WebView has
                    // no native PDF support); other docs load directly.
                    val target = if (isPdf) {
                        "https://docs.google.com/gview?embedded=true&url=" +
                            java.net.URLEncoder.encode(secureUrl, "UTF-8")
                    } else {
                        secureUrl
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: android.webkit.WebResourceError?,
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            loading = false
                                            loadError = error?.description?.toString()
                                        }
                                    }
                                }
                                loadUrl(target)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (loading && loadError == null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = BrandPalette.OrangeMain,
                                strokeWidth = 2.5.dp,
                            )
                            Text(
                                text = "Downloading $fileName...",
                                style = AirdropType.body2,
                                color = colors.textDescription,
                            )
                        }
                    }
                    loadError?.let { message ->
                        Text(
                            text = "Couldn't download $fileName.\n$message",
                            style = AirdropType.body2,
                            color = colors.textDescription,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(Spacing.md),
                        )
                    }
                }
            }
        }

        // Bottom actions — Save to Files (secondary) + Share (primary).
        val actionsEnabled = secureUrl.isNotBlank() && loadError == null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InvoiceActionButton(
                text = "Save to Files",
                iconRes = R.drawable.ic_download_file,
                primary = false,
                enabled = actionsEnabled,
                onClick = { saveInvoice(context, secureUrl, fileName) },
                modifier = Modifier.weight(1f),
            )
            InvoiceActionButton(
                text = "Share",
                iconRes = R.drawable.ic_upload,
                primary = true,
                enabled = actionsEnabled,
                onClick = { shareInvoice(context, secureUrl, title) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun InvoiceActionButton(
    text: String,
    iconRes: Int,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    val shape = RoundedCornerShape(Radius.xs)
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(shape)
            .let {
                if (primary) {
                    it.background(
                        if (enabled) Brush.verticalGradient(GradientPalette.SignInButton)
                        else Brush.verticalGradient(
                            listOf(BrandPalette.ButtonDisable, BrandPalette.ButtonDisable)
                        )
                    )
                } else {
                    it
                        .background(colors.gray150)
                        .border(1.dp, colors.iconShape, shape)
                }
            }
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (primary) BrandPalette.White else colors.iconSelected
            ),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(
            text = text,
            style = AirdropType.button,
            color = if (primary) BrandPalette.White else colors.textDarkTitle,
        )
    }
}

private fun saveInvoice(context: Context, url: String, fileName: String) {
    runCatching {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        manager.enqueue(request)
    }
}

private fun shareInvoice(context: Context, url: String, title: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Invoice" })
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(intent, "Share invoice"))
    }
}
