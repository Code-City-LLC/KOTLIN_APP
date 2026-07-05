package com.ga.airdrop.feature.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes

/**
 * Documents — Figma node 40000975:7748, behavior from
 * FigmaDocumentsViewController: five document slots (title + info circle,
 * description, peach uploaded-file row with trash/eye, Download|Upload
 * split action bar). Upload uses the system document picker (pdf/images);
 * view/download routes through the shared invoice viewer.
 */
@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: DocumentsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var uploadSlot by remember { mutableStateOf<DocumentSlot?>(null) }
    var infoSlot by remember { mutableStateOf<DocumentSlot?>(null) }
    var deleteSlot by remember { mutableStateOf<DocumentSlot?>(null) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val slot = uploadSlot
        uploadSlot = null
        if (uri == null || slot == null) return@rememberLauncherForActivityResult
        // Read bytes + display name off the main thread (see ViewModel.upload).
        viewModel.upload(slot, uri, context.contentResolver)
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
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
                        uploading = state.uploadingType == slot.docType,
                        onInfo = { infoSlot = slot },
                        onDownload = { openFile(slot) },
                        onView = { openFile(slot) },
                        onDelete = { deleteSlot = slot },
                        onUpload = {
                            uploadSlot = slot
                            documentPicker.launch(
                                arrayOf("application/pdf", "image/png", "image/jpeg"),
                            )
                        },
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }

    infoSlot?.let { slot ->
        MoreAlertDialog(
            title = slot.title,
            message = slot.detailDescription,
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

/** Figma Component 37/41 — radius-10 card with split Download|Upload bar. */
@Composable
private fun DocumentCard(
    slot: DocumentSlot,
    file: MoreDocumentFile?,
    uploading: Boolean,
    onInfo: () -> Unit,
    onDownload: () -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit,
    onUpload: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val hasFile = file?.hasFile == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    Modifier.fillMaxWidth(),
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
                    Image(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = "${slot.title} info",
                        colorFilter = ColorFilter.tint(colors.iconSelected),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onInfo),
                    )
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
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp),
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
                    .fillMaxHeight()
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
    }
}

/** Peach uploaded-file row — pdf glyph, name + "PDF files", trash + eye (30dp gap). */
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
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.peachLight)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_pdf),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.fileName ?: fallbackName,
                    style = AirdropType.subtitle2,
                    color = colors.textDarkTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$extension files",
                    style = AirdropType.body3,
                    color = colors.textPlaceholder,
                    maxLines = 1,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Image(
                painter = painterResource(R.drawable.ic_trash),
                contentDescription = "Delete",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onDelete),
            )
            Image(
                painter = painterResource(R.drawable.ic_eye),
                contentDescription = "View",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onView),
            )
        }
    }
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
                modifier = Modifier.size(24.dp),
            )
        }
        Text(text = label, style = AirdropType.button, color = color)
    }
}
