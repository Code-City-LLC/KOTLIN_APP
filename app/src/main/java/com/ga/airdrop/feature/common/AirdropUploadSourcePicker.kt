package com.ga.airdrop.feature.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AirdropPickedUploadFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class AirdropUploadSourceConfig(
    val sheetTitle: String = "Add a File",
    val allowedMimeTypes: List<String> = listOf("application/pdf", "image/*"),
    val allowedFileExtensions: Set<String> = invoiceFileExtensions,
    val allowsMultipleFileSelection: Boolean = false,
    val maxSelectionCount: Int = 1,
    val maxFileBytes: Int? = null,
    val imageMaxDimension: Int = 2_000,
    val imageCompressionQuality: Int = 80,
    /**
     * The destination accepts only PDF (Laravel `mimes:pdf` reads the content,
     * so a renamed JPEG is refused): a photo, camera shot or image file is
     * handed back as a one-page PDF instead.
     */
    val imagesAsPdf: Boolean = false,
) {
    companion object {
        val invoiceFileExtensions = setOf("pdf", "jpg", "jpeg", "png", "gif", "bmp", "webp")
        val userDocumentFileExtensions = setOf("pdf", "jpg", "jpeg", "png")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirdropUploadSourceSheet(
    config: AirdropUploadSourceConfig,
    onPicked: (List<AirdropPickedUploadFile>) -> Unit,
    onFailure: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun handleFiles(block: suspend () -> List<AirdropPickedUploadFile>) {
        scope.launch {
            val result = runCatching { block() }
            onDismiss()
            result
                .onSuccess { files -> if (files.isNotEmpty()) onPicked(files) }
                .onFailure { onFailure(it.message ?: "Upload failed") }
        }
    }

    val singleFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult onDismiss()
        handleFiles {
            listOf(readUploadFile(context, uri, config, fallbackName = "upload"))
        }
    }
    val multiFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult onDismiss()
        handleFiles {
            uris
                .take(config.maxSelectionCount.coerceAtLeast(1))
                .map { readUploadFile(context, it, config, fallbackName = "upload") }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult onDismiss()
        handleFiles {
            listOf(readImageUpload(context, uri, config))
        }
    }
    val cameraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult onDismiss()
        handleFiles {
            listOf(imageUploadFile(bitmap, config))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AirdropTheme.colors.gray100,
        modifier = Modifier.testTag("upload-source-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.md)
                .padding(top = 4.dp, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = config.sheetTitle,
                style = AirdropType.title2,
                color = AirdropTheme.colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .testTag("upload-source-title"),
            )
            UploadSourceAction(
                label = "Select File",
                iconRes = R.drawable.ic_document_list,
                tag = "upload-source-file",
                onClick = {
                    val mimeTypes = config.allowedMimeTypes.toTypedArray()
                    if (config.allowsMultipleFileSelection) {
                        multiFilePicker.launch(mimeTypes)
                    } else {
                        singleFilePicker.launch(mimeTypes)
                    }
                },
            )
            UploadSourceAction(
                label = "Select Photo",
                iconRes = R.drawable.ic_download_file,
                tag = "upload-source-photo",
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            UploadSourceAction(
                label = "Take Photo",
                iconRes = R.drawable.ic_upload,
                tag = "upload-source-camera",
                onClick = { cameraPicker.launch(null) },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(BrandPalette.OrangeMain)
                    .clickable(onClick = onDismiss)
                    .testTag("upload-source-cancel"),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Cancel", style = AirdropType.button, color = Color.White)
            }
        }
    }
}

@Composable
private fun UploadSourceAction(
    label: String,
    iconRes: Int,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(tag)
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDarkTitle),
            modifier = Modifier.size(24.dp),
        )
        Text(text = label, style = AirdropType.subtitle1, color = colors.textDarkTitle)
    }
}

private suspend fun readUploadFile(
    context: Context,
    uri: Uri,
    config: AirdropUploadSourceConfig,
    fallbackName: String,
): AirdropPickedUploadFile = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw UploadPickerError("Could not read the selected file.")

    val resolverMime = resolver.getType(uri)
    val displayName = displayName(context, uri) ?: fallbackName
    val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)
        .ifBlank { extensionForMime(resolverMime).orEmpty() }
    val mimeType = mimeTypeForExtension(extension) ?: resolverMime
    if (extension !in config.allowedFileExtensions || mimeType == null) {
        throw UploadPickerError("Please select a supported PDF or image file.")
    }
    // An image becomes a PDF here, so the size that counts is the PDF's.
    if (config.imagesAsPdf && mimeType.startsWith("image/")) {
        val bitmap = decodeImageForPdf(bytes)
            ?: throw UploadPickerError("The selected image could not be prepared for upload.")
        return@withContext imagePdfUploadFile(bitmap, config)
    }
    validateSize(bytes, config)
    val fileName = if (displayName.contains('.')) displayName else "$displayName.$extension"
    AirdropPickedUploadFile(fileName = fileName, mimeType = mimeType, bytes = bytes)
}

private suspend fun readImageUpload(
    context: Context,
    uri: Uri,
    config: AirdropUploadSourceConfig,
): AirdropPickedUploadFile = withContext(Dispatchers.IO) {
    val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        ?: throw UploadPickerError("The selected image could not be prepared for upload.")
    imageUploadFile(bitmap, config)
}

internal fun imageUploadFile(
    bitmap: Bitmap,
    config: AirdropUploadSourceConfig,
): AirdropPickedUploadFile {
    if (config.imagesAsPdf) return imagePdfUploadFile(bitmap, config)
    val scaled = resizeBitmap(bitmap, config.imageMaxDimension)
    var quality = config.imageCompressionQuality.coerceIn(35, 100)
    var bytes = compressJpeg(scaled, quality)
    while (config.maxFileBytes != null && bytes.size > config.maxFileBytes && quality > 35) {
        quality = (quality - 15).coerceAtLeast(35)
        bytes = compressJpeg(scaled, quality)
    }
    validateSize(bytes, config)
    val timestamp = System.currentTimeMillis() / 1000L
    return AirdropPickedUploadFile(
        fileName = "photo-$timestamp.jpg",
        mimeType = "image/jpeg",
        bytes = bytes,
    )
}

/**
 * The image as the only page of an A4 PDF, fitted to the page in its own
 * orientation. PdfDocument stores pixels losslessly, so the image is capped at
 * [PDF_IMAGE_MAX_DIMENSION]: even an incompressible 1600 x 1600 image is
 * 7.7 MB of pixels, inside the 10 MB document limit (about 190 dpi on A4).
 */
private fun imagePdfUploadFile(
    bitmap: Bitmap,
    config: AirdropUploadSourceConfig,
): AirdropPickedUploadFile {
    val image = resizeBitmap(bitmap, minOf(config.imageMaxDimension, PDF_IMAGE_MAX_DIMENSION))
    val landscape = image.width > image.height
    val pageWidth = if (landscape) A4_LONG_SIDE_PT else A4_SHORT_SIDE_PT
    val pageHeight = if (landscape) A4_SHORT_SIDE_PT else A4_LONG_SIDE_PT
    val scale = minOf(pageWidth.toFloat() / image.width, pageHeight.toFloat() / image.height)
    val left = (pageWidth - image.width * scale) / 2f
    val top = (pageHeight - image.height * scale) / 2f
    val document = PdfDocument()
    val bytes = try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        page.canvas.drawColor(android.graphics.Color.WHITE)
        page.canvas.drawBitmap(
            image,
            null,
            RectF(left, top, left + image.width * scale, top + image.height * scale),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        document.finishPage(page)
        ByteArrayOutputStream().use { out ->
            document.writeTo(out)
            out.toByteArray()
        }
    } finally {
        document.close()
    }
    validateSize(bytes, config)
    val timestamp = System.currentTimeMillis() / 1000L
    return AirdropPickedUploadFile(
        fileName = "photo-$timestamp.pdf",
        mimeType = "application/pdf",
        bytes = bytes,
    )
}

/** Decodes at the smallest power-of-two sample that still covers the PDF cap. */
internal fun decodeImageForPdf(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PDF_IMAGE_MAX_DIMENSION) sample *= 2
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

private const val PDF_IMAGE_MAX_DIMENSION = 1_600
private const val A4_SHORT_SIDE_PT = 595
private const val A4_LONG_SIDE_PT = 842

private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (maxDimension <= 0 || longest <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / longest.toFloat()
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray =
    ByteArrayOutputStream().use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            throw UploadPickerError("The selected image could not be prepared for upload.")
        }
        out.toByteArray()
    }

private fun validateSize(bytes: ByteArray, config: AirdropUploadSourceConfig) {
    val maxBytes = config.maxFileBytes ?: return
    if (bytes.size > maxBytes) {
        throw UploadPickerError("Please select a file smaller than ${maxOf(1, maxBytes / 1_048_576)} MB.")
    }
}

private fun displayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

private fun extensionForMime(mime: String?): String? = when (mime?.lowercase(Locale.US)) {
    "application/pdf" -> "pdf"
    "image/jpeg", "image/jpg" -> "jpg"
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/bmp", "image/x-ms-bmp" -> "bmp"
    "image/webp" -> "webp"
    else -> null
}

private fun mimeTypeForExtension(extension: String): String? = when (extension) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    "webp" -> "image/webp"
    else -> null
}

private class UploadPickerError(message: String) : IllegalArgumentException(message)
