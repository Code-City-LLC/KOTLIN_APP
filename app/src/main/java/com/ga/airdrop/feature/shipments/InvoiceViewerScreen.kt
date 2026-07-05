package com.ga.airdrop.feature.shipments

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.io.File
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val isLocalFile = remember(secureUrl) { secureUrl.startsWith("file://") }
    val isImage = remember(secureUrl) {
        val path = Uri.parse(secureUrl).path.orEmpty().lowercase(Locale.US)
        listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp").any { path.endsWith(it) }
    }
    val isPdf = remember(secureUrl) {
        Uri.parse(secureUrl).path.orEmpty().lowercase(Locale.US).endsWith(".pdf") || !isImage
    }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var localActionFile by remember(secureUrl) { mutableStateOf<File?>(null) }
    var actionFileError by remember(secureUrl) { mutableStateOf<String?>(null) }

    val fileName = remember(secureUrl, title) {
        Uri.parse(secureUrl).lastPathSegment?.takeIf { it.contains('.') }
            ?: (title.ifBlank { "Invoice" } + if (isPdf) ".pdf" else ".jpg")
    }

    LaunchedEffect(secureUrl, fileName) {
        localActionFile = null
        actionFileError = null
        if (secureUrl.isBlank()) return@LaunchedEffect
        runCatching {
            prepareInvoiceActionFile(context.applicationContext, secureUrl, fileName)
        }.onSuccess {
            localActionFile = it
        }.onFailure {
            actionFileError = it.localizedMessage ?: "Unable to prepare invoice file."
        }
    }

    // Swift FigmaInvoiceViewerScreenViewController.swift: view bg gray100 (:68),
    // preview contentContainer gray150 (:86). The Kotlin had these swapped.
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .testTag("invoice-viewer-root")
    ) {
        ShipmentsDetailHeader(title = "Invoice", onBack = onBack)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(Spacing.md)
                .clip(RoundedCornerShape(Radius.s))
                .background(colors.gray150)
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                .testTag("invoice-viewer-preview"),
        ) {
            when {
                secureUrl.isBlank() -> {
                    Text(
                        text = "No invoice URL provided.",
                        style = AirdropType.body1,
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
                                style = AirdropType.body1,
                                color = colors.textDescription,
                                textAlign = TextAlign.Center,
                            )
                        },
                    )
                }
                isLocalFile -> {
                    // Local file:// caches can't reach the gview embed; hand the
                    // file to the device's default viewer via a FileProvider URI.
                    LaunchedEffect(secureUrl) {
                        loading = false
                        runCatching {
                            val file = java.io.File(Uri.parse(secureUrl).path!!)
                            val authority = context.packageName + ".fileprovider"
                            val contentUri = FileProvider.getUriForFile(context, authority, file)
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(contentUri, "application/pdf")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(context, "No app to open this file", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Opening $fileName...",
                            style = AirdropType.body1,
                            color = colors.textDescription,
                            textAlign = TextAlign.Center,
                        )
                    }
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
                                style = AirdropType.body1,
                                color = colors.textDescription,
                            )
                        }
                    }
                    loadError?.let { message ->
                        Text(
                            text = "Couldn't download $fileName.\n$message",
                            style = AirdropType.body1,
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
        val actionsEnabled = localActionFile != null && loadError == null && actionFileError == null
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
                onClick = { localActionFile?.let { saveInvoice(context, it, fileName) } },
                modifier = Modifier.weight(1f),
                tag = "invoice-save-button",
            )
            InvoiceActionButton(
                text = "Share",
                iconRes = R.drawable.ic_upload,
                primary = true,
                enabled = actionsEnabled,
                onClick = { localActionFile?.let { shareInvoice(context, it, title) } },
                modifier = Modifier.weight(1f),
                tag = "invoice-share-button",
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
    tag: String,
) {
    val colors = AirdropTheme.colors
    val shape = RoundedCornerShape(Radius.xs)
    Row(
        modifier = modifier
            .height(52.dp)
            .testTag(tag)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .let {
                if (primary) {
                    // Swift primary action = flat orangeMain fill, not a gradient.
                    it.background(BrandPalette.OrangeMain)
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
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = AirdropType.button,
            color = if (primary) BrandPalette.White else colors.textDarkTitle,
        )
    }
}

private fun saveInvoice(context: Context, file: File, fileName: String) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, invoiceMimeType(fileName))
            }
            val dest = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create Downloads entry")
            val output = resolver.openOutputStream(dest)
                ?: error("Unable to write invoice file")
            output.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
        } else {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloads.mkdirs()
            file.copyTo(File(downloads, fileName), overwrite = true)
        }
        Toast.makeText(context, "Invoice saved to Downloads", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Couldn't save invoice", Toast.LENGTH_SHORT).show()
    }
}

private fun shareInvoice(context: Context, file: File, title: String) {
    runCatching {
        val intent = invoiceShareIntent(context, file, title)
        context.startActivity(Intent.createChooser(intent, "Share invoice"))
    }.onFailure {
        Toast.makeText(context, "Couldn't share invoice", Toast.LENGTH_SHORT).show()
    }
}

internal suspend fun prepareInvoiceActionFile(context: Context, url: String, fileName: String): File =
    withContext(Dispatchers.IO) {
        val uri = Uri.parse(url)
        when (uri.scheme?.lowercase(Locale.US)) {
            "file" -> File(requireNotNull(uri.path) { "Missing invoice file path" }).also {
                require(it.exists()) { "Invoice file does not exist" }
            }
            "content" -> copyInvoiceStreamToCache(
                context,
                fileName,
                context.contentResolver.openInputStream(uri)
                    ?: error("Unable to open invoice content"),
            )
            "http", "https" -> copyInvoiceStreamToCache(
                context,
                fileName,
                URL(url).openStream(),
            )
            else -> error("Unsupported invoice URL")
        }
    }

private fun copyInvoiceStreamToCache(
    context: Context,
    fileName: String,
    input: java.io.InputStream,
): File {
    val dir = File(context.cacheDir, "invoices").also { it.mkdirs() }
    val output = File(dir, safeInvoiceFileName(fileName))
    input.use { source ->
        output.outputStream().use { sink -> source.copyTo(sink) }
    }
    return output
}

internal fun invoiceShareIntent(context: Context, file: File, title: String): Intent {
    val authority = context.packageName + ".fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, file)
    return Intent(Intent.ACTION_SEND).apply {
        type = invoiceMimeType(file.name)
        putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Invoice" })
        putExtra(Intent.EXTRA_STREAM, contentUri)
        clipData = ClipData.newUri(context.contentResolver, file.name, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun invoiceMimeType(fileName: String): String =
    when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

private fun safeInvoiceFileName(fileName: String): String {
    val cleaned = fileName
        .ifBlank { "Invoice.pdf" }
        .replace(Regex("""[^\w.\- ]"""), "_")
        .trim()
    return cleaned.ifBlank { "Invoice.pdf" }
}
