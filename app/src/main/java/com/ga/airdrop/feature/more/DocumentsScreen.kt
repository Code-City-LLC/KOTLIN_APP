package com.ga.airdrop.feature.more

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.common.AirdropUploadSourceConfig
import com.ga.airdrop.feature.common.AirdropUploadSourceSheet
import java.util.Locale

/**
 * Documents — Figma node 40000975:7748, behavior from
 * FigmaDocumentsViewController: five document slots (title + info circle,
 * description, peach uploaded-file row with trash/eye, Download|Upload
 * split action bar). Upload uses the shared file/photo/camera source sheet
 * and stages a pending file before explicit commit; view/download routes
 * through the shared invoice viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: DocumentsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    var uploadSlot by remember { mutableStateOf<DocumentSlot?>(null) }
    var infoSlot by remember { mutableStateOf<DocumentSlot?>(null) }
    var deleteSlot by remember { mutableStateOf<DocumentSlot?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val ptrState = rememberPullToRefreshState()

    DisposableEffect(lifecycleOwner, viewModel) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.load()
        }
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun openFile(slot: DocumentSlot) {
        val url = state.files[slot.docType]?.fileUrl
            ?.replaceFirst("http://", "https://")
        if (url.isNullOrBlank()) {
            viewModel.showAlert(
                "Not available",
                "No download link is available for ${slot.title} yet.",
            )
        } else {
            // Shared in-app viewer route (registered by the shipments group).
            onNavigate(Routes.invoiceViewer(url, slot.title))
        }
    }

    Box(Modifier.fillMaxSize().background(colors.gray100)) {
        Column(Modifier.fillMaxSize()) {
            MoreDetailHeader(title = "Documents", onBack = onBack)
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                state = ptrState,
                modifier = Modifier
                    .weight(1f)
                    .testTag("documents-pull-refresh"),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = ptrState,
                        isRefreshing = state.refreshing,
                        color = BrandPalette.OrangeMain,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(Spacing.md),
                    // Swift listStack.spacing = 12; Figma's older 20px gap loses to Swift.
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.loading && state.files.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(top = Spacing.xl), Alignment.Center) {
                            CircularProgressIndicator(color = BrandPalette.OrangeMain)
                        }
                    }
                    DOCUMENT_SLOTS.forEach { slot ->
                        DocumentCard(
                            slot = slot,
                            file = state.files[slot.docType],
                            pendingUpload = state.pendingUploads[slot.docType],
                            uploading = state.uploadingType == slot.docType,
                            onInfo = { infoSlot = slot },
                            onDownload = { openFile(slot) },
                            onView = { openFile(slot) },
                            onDelete = { deleteSlot = slot },
                            onUpload = { uploadSlot = slot },
                            onClearPendingUpload = { viewModel.clearPendingUpload(slot) },
                            onCommitPendingUpload = { viewModel.commitPendingUpload(slot) },
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                }
            }
        }
    }

    uploadSlot?.let { slot ->
        AirdropUploadSourceSheet(
            config = AirdropUploadSourceConfig(
                sheetTitle = "Upload ${slot.title}",
                allowedFileExtensions = AirdropUploadSourceConfig.userDocumentFileExtensions,
                allowsMultipleFileSelection = false,
                maxSelectionCount = 1,
            ),
            onPicked = { files ->
                files.firstOrNull()?.let { file ->
                    viewModel.stageUpload(
                        slot = slot,
                        fileName = file.fileName,
                        mimeType = file.mimeType,
                        bytes = file.bytes,
                    )
                }
            },
            onFailure = { message -> viewModel.showAlert("Upload failed", message) },
            onDismiss = { uploadSlot = null },
        )
    }
    infoSlot?.let { slot ->
        MoreAlertDialog(
            title = slot.title,
            message = slot.detailDescription,
            confirmLabel = "Got it",
            onDismiss = { infoSlot = null },
        )
    }
    deleteSlot?.let { slot ->
        MoreConfirmDialog(
            title = "Delete Document",
            message = "Are you sure you want to delete this document? This action cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(slot) },
            onDismiss = { deleteSlot = null },
        )
    }
    state.alert?.let { (title, message) ->
        MoreAlertDialog(title = title, message = message, onDismiss = viewModel::dismissAlert)
    }
}

/** Swift FigmaDocumentsViewController card — radius-15 with inset split actions row. */
@Composable
internal fun DocumentCard(
    slot: DocumentSlot,
    file: MoreDocumentFile?,
    pendingUpload: PendingDocumentUpload? = null,
    uploading: Boolean,
    onInfo: () -> Unit,
    onDownload: () -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit,
    onUpload: () -> Unit,
    onClearPendingUpload: () -> Unit = {},
    onCommitPendingUpload: () -> Unit = {},
) {
    val colors = AirdropTheme.colors
    val hasFile = file?.hasFile == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("documents-card-${slot.docType}")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = slot.title,
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(onClick = onInfo),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            // Swift FigmaDocumentsViewController:271 uses the
                            // circular infoCircle glyph; Figma 40000975:7748 +
                            // ledger C9 agree (ledger P3). ic_info is the squircle.
                            painter = painterResource(R.drawable.ic_calc_info_circle),
                            contentDescription = "${slot.title} info",
                            colorFilter = ColorFilter.tint(colors.textDarkTitle),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Text(
                    text = slot.description,
                    style = AirdropType.body2,
                    // Swift: slot descriptions are the gray textDescription.
                    color = colors.textDescription,
                )
            }
            if (hasFile) {
                UploadedFileRow(
                    file = file!!,
                    fallbackName = slot.title,
                    onDelete = onDelete,
                    onView = onView,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("documents-actions-${slot.docType}")
                    .clip(RoundedCornerShape(Radius.xs))
                    .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SplitAction(
                    iconRes = R.drawable.ic_download_file,
                    label = "Download",
                    enabled = !file?.fileUrl.isNullOrBlank(),
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(colors.iconShape),
                )
                SplitAction(
                    iconRes = R.drawable.ic_upload,
                    label = "Upload",
                    enabled = !uploading,
                    loading = uploading,
                    onClick = onUpload,
                    modifier = Modifier.weight(1f),
                )
            }
            pendingUpload?.let { pending ->
                PendingUploadSection(
                    file = pending,
                    uploading = uploading,
                    onClear = onClearPendingUpload,
                    onCommit = onCommitPendingUpload,
                    tagSuffix = slot.docType,
                )
            }
        }
    }
}

/** Peach uploaded-file row — Swift 56pt row, 28pt PDF, 18pt actions in 28pt buttons. */
@Composable
private fun UploadedFileRow(
    file: MoreDocumentFile,
    fallbackName: String,
    onDelete: () -> Unit,
    onView: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val extension = file.fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.uppercase()
        ?.takeIf { it.isNotEmpty() } ?: "PDF"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("documents-uploaded-file-row")
            .clip(RoundedCornerShape(8.dp))
            .background(colors.peachLight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_pdf),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.fileName ?: fallbackName,
                    style = AirdropType.body2,
                    color = colors.textDarkTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$extension files",
                    style = AirdropType.body3,
                    color = colors.textDescription,
                    maxLines = 1,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = "Delete",
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onView),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_eye),
                    contentDescription = "View",
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PendingUploadSection(
    file: PendingDocumentUpload,
    uploading: Boolean,
    onClear: () -> Unit,
    onCommit: () -> Unit,
    tagSuffix: String,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("documents-pending-upload-$tagSuffix"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(colors.peachLight)
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(
                    if (file.mimeType == "application/pdf") R.drawable.ic_pdf else R.drawable.ic_document_list,
                ),
                contentDescription = null,
                colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = AirdropType.body2,
                    color = colors.textDarkTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = uploadSummary(file.mimeType, file.bytes.size),
                    style = AirdropType.body3,
                    color = colors.textDescription,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.iconShape)
                    .clickable(enabled = !uploading, onClick = onClear)
                    .testTag("documents-clear-pending-$tagSuffix"),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_cross),
                    contentDescription = "Remove selected file",
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(if (uploading) colors.textPlaceholder else BrandPalette.OrangeMain)
                .clickable(enabled = !uploading, onClick = onCommit)
                .testTag("documents-commit-upload-$tagSuffix"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (uploading) "Uploading" else "Upload Document",
                style = AirdropType.button,
                color = Color.White,
            )
        }
        if (uploading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = BrandPalette.OrangeMain,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Uploading, please wait",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            }
        }
    }
}

private fun uploadSummary(mimeType: String, byteCount: Int): String {
    val type = when {
        mimeType.contains("pdf") -> "PDF"
        mimeType.contains("png") -> "PNG"
        mimeType.contains("jpeg") || mimeType.contains("jpg") -> "JPG"
        mimeType.contains("gif") -> "GIF"
        mimeType.contains("bmp") -> "BMP"
        mimeType.contains("webp") -> "WEBP"
        else -> "File"
    }
    val bytes = byteCount.toDouble()
    val size = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1fMB", bytes / (1024 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.0fKB", bytes / 1024)
        else -> "${byteCount}B"
    }
    return "$type files, $size"
}

@Composable
private fun SplitAction(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val colors = AirdropTheme.colors
    val color = if (enabled) colors.textDarkTitle else colors.textPlaceholder
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = BrandPalette.OrangeMain,
                strokeWidth = 2.dp,
            )
        } else {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(text = label, style = AirdropType.button, color = color)
    }
}
