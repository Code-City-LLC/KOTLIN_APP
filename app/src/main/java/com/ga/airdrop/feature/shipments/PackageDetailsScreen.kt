package com.ga.airdrop.feature.shipments

import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes

/**
 * Package details — Figma node 40001753:15716, behavior from
 * FigmaPackageDetailsViewController: method hero + circular badge, Summary
 * card, Shipment Timeline (status-icon rows), invoice upload zone (multipart POST
 * /packages/{id}/invoices) + list + pre-ready delete, CIF info, Breakdown of
 * Charges and Add to Cart (status >= 7).
 */
@Composable
fun PackageDetailsScreen(
    packageId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: PackageDetailsViewModel = viewModel(key = "packageDetails/$packageId") {
        PackageDetailsViewModel(packageId)
    },
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val detail = state.detail
    val method = ShipmentMethodUi.from(detail?.shippingMethod)
    val detailBrandTitle = packageDetailsBrandTitle(method)

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        // Read each selected file off the main thread to avoid an ANR; the
        // resolver + Uris are safe to touch from IO. uploadInvoices does its
        // own viewModelScope.launch + thread-safe StateFlow.update.
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val files = uris.mapNotNull { uri ->
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@mapNotNull null
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    var name = "invoice"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
                    }
                    InvoiceUploadFile(fileName = name, mimeType = mime, bytes = bytes)
                }.getOrNull()
            }
            viewModel.uploadInvoices(files)
        }
    }

    // Swift FigmaPackageDetailsViewController.swift:72 — page is gray200.
    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero — method image, 240dp visible under the glass header.
            Box(Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(method.heroRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (-20).dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .testTag("package-details-sheet")
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        // Swift :110 — rounded body card is gray100 on the gray200 page.
                        .background(colors.gray100)
                        .padding(top = Spacing.xl),
                ) {
                    // Method name — H6 centered, divider below.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xl)
                            .padding(bottom = Spacing.md),
                    ) {
                        Text(
                            text = detailBrandTitle,
                            style = AirdropType.h6,
                            color = method.tint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.gray300)
                    )

                    when {
                        state.loading -> ShipmentsLoadingIndicator(Modifier.padding(Spacing.xl))
                        detail == null -> ShipmentsEmptyLabel(state.error ?: "Package not found")
                        else -> PackageDetailsContent(
                            state = state,
                            detail = detail,
                            onPickFiles = {
                                filePicker.launch(arrayOf("application/pdf", "image/*"))
                            },
                            onViewInvoice = { doc ->
                                doc.fullUrl?.let { url ->
                                    onNavigate(Routes.invoiceViewer(url, doc.fileName ?: "Invoice"))
                                }
                            },
                            onDeleteInvoice = viewModel::requestDeleteInvoice,
                            onCifInfo = { viewModel.showCifInfo(true) },
                            onAddToCart = viewModel::addToCart,
                        )
                    }
                }

                // 90dp circular method badge centered on the sheet edge.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-45).dp)
                        .size(90.dp)
                        .background(colors.gray100, CircleShape)
                        .border(1.dp, colors.gray300, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(method.iconRes),
                        contentDescription = detailBrandTitle,
                        colorFilter = ColorFilter.tint(method.tint),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("package-details-hero-icon"),
                    )
                }
            }
        }

        ShipmentsDetailHeader(
            title = "Packages Details",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (state.showCifInfo) {
            ShipmentsAlertDialog(
                title = "CIF Value",
                message = "CIF = Cost + Insurance + Freight — the package value used to compute customs charges.",
                confirmText = "OK",
                onConfirm = { viewModel.showCifInfo(false) },
                onDismiss = { viewModel.showCifInfo(false) },
            )
        }
        if (state.showAddedToCart) {
            ShipmentsAlertDialog(
                title = "Success",
                message = "Package added to cart",
                confirmText = "View Cart",
                dismissText = "Keep Browsing",
                onConfirm = {
                    viewModel.dismissAddedToCart()
                    onNavigate(Routes.CART)
                },
                onDismiss = viewModel::dismissAddedToCart,
            )
        }
        if (state.confirmDeleteInvoiceId != null) {
            ShipmentsAlertDialog(
                title = "Delete invoice",
                message = "Are you sure you want to delete this invoice?",
                confirmText = "Delete",
                dismissText = "Cancel",
                onConfirm = viewModel::confirmDeleteInvoice,
                onDismiss = viewModel::dismissDeleteInvoice,
            )
        }
        state.transientMessage?.let { message ->
            ShipmentsAlertDialog(
                title = "Upload Invoice",
                message = message,
                confirmText = "OK",
                onConfirm = viewModel::consumeTransientMessage,
                onDismiss = viewModel::consumeTransientMessage,
            )
        }
    }
}

@Composable
private fun PackageDetailsContent(
    state: PackageDetailsUiState,
    detail: ShipmentPackageDetail,
    onPickFiles: () -> Unit,
    onViewInvoice: (PackageInvoiceDoc) -> Unit,
    onDeleteInvoice: (Int) -> Unit,
    onCifInfo: () -> Unit,
    onAddToCart: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Summary — Swift makeSummaryPanel (:321-398): inline Title2 header +
        // 14pt chevron INSIDE a plain gray100 card, rows spaced 10, Title2
        // values, NO banded header, NO dividers.
        DetailSectionCard(
            title = "Summary",
            trailingChevron = true,
            tag = "package-details-section-summary",
            titleContentGap = 12.dp,
            contentSpacing = Spacing.sm,
        ) {
            val method = ShipmentMethodUi.from(detail.shippingMethod)
            val courier = listOfNotNull(
                detail.shipper?.takeIf { it.isNotBlank() },
                detail.courierNumber?.takeIf { it.isNotBlank() },
            ).joinToString(" ").ifBlank { "—" }
            DetailRow("Drop Number", detail.trackingCode ?: "—")
            DetailRow("Shipping Method", detail.shippingMethod ?: method.title)
            DetailRow("Merchant/Shipper", detail.store ?: "—")
            DetailRow("Courier Tracking", courier)
            DetailRow("Description", detail.description?.ifBlank { "—" } ?: "—")
            DetailRow(
                "Weight/Volume",
                ShipmentsFormat.weight(detail.weightLbs, detail.weightKg, detail.weight),
            )
            DetailRow("Number of Pieces", (detail.numberOfPieces ?: 1).toString())
        }

        // Shipment Timeline — Swift PackageTimelineProgression: canonical
        // customer-visible statuses through the current package status.
        DetailSectionCard(
            title = "Shipment Timeline",
            tag = "package-details-section-timeline",
            titleContentGap = 14.dp,
            contentSpacing = 12.dp,
        ) {
            val inlineRows = PackageTimelineProgression.inlineRows(detail)
            if (inlineRows.isNotEmpty()) {
                val currentStatus = detail.status?.trim()?.toIntOrNull()
                inlineRows.forEachIndexed { index, row ->
                    TimelineIconRow(
                        statusName = row.status.label,
                        statusCode = row.status.id,
                        color = PackageTimelineProgression.colorFor(
                            statusId = row.status.id,
                            currentStatus = currentStatus,
                            placeholder = colors.textPlaceholder,
                        ),
                        comment = row.history?.comment?.takeIf { it.isNotBlank() },
                        date = ShipmentsFormat.timelineDate(row.history?.changedDate).takeIf { it != "N/A" },
                        showConnector = index != inlineRows.lastIndex,
                        tag = "package-details-timeline-row-${row.status.id}",
                    )
                }
            } else {
                val steps = detail.history.ifEmpty {
                    listOf(
                        PackageHistoryItem(
                            status = detail.status?.toIntOrNull(),
                            statusName = detail.statusName,
                            changedDate = null,
                        )
                    )
                }
                steps.forEachIndexed { index, item ->
                    val statusName = item.statusName ?: "—"
                    val statusCode = item.status ?: ShipmentStatusCatalog.idFor(statusName)
                    TimelineIconRow(
                        statusName = statusName,
                        statusCode = statusCode,
                        color = timelineStatusColor(statusName),
                        comment = item.comment?.takeIf { it.isNotBlank() },
                        date = ShipmentsFormat.timelineDate(item.changedDate).takeIf { it != "N/A" },
                        showConnector = index != steps.lastIndex,
                        tag = "package-details-timeline-row-${item.status ?: statusName}",
                    )
                }
            }
        }

        // Upload Your Invoice
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val canDeleteInvoices = state.canDeleteInvoices
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Upload Your Invoice",
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                )
                Text(
                    text = "(${detail.invoices.size}/3)",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            }
            UploadInvoiceZone(uploading = state.uploading, onClick = onPickFiles)
            Text(
                text = "You're allowed to upload a maximum of 3 files each with a size below 10 MB. " +
                    "Only the following formats are allowed: pdf, jpg, bmp, png, doc, docx html.",
                // Swift FigmaPackageDetailsViewController.swift:612-613 — Body3.
                style = AirdropType.body3,
                color = colors.textDescription,
            )
            detail.invoices.forEach { doc ->
                InvoiceFileRow(
                    doc = doc,
                    canDelete = canDeleteInvoices,
                    onView = { onViewInvoice(doc) },
                    onDelete = { onDeleteInvoice(doc.id) },
                )
            }
        }

        // CIF Value info row — Figma 40001753:21889
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("package-details-cif-row")
                .clip(RoundedCornerShape(Radius.s))
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                .clickable(onClick = onCifInfo)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "CIF Value", style = AirdropType.subtitle1, color = colors.textDarkTitle)
            Image(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = "CIF info",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(20.dp),
            )
        }

        // Breakdown of Charges + Add to Cart — Swift showCharges gate
        // (statusInt == 7 || == 18), not >= 7. Swift (:834-890) ALWAYS renders
        // the Breakdown card (header + Subtotal) in this state, then a plain
        // Exchange Rate row and a plain Total row (orange value) — no orange pill box.
        if (state.showChargesAndCart) {
            ChargesCard(state = state, detail = detail)
            val rate = state.effectiveRate
            DetailKeyValueRow(
                "Exchange Rate",
                "1 USD = ${ShipmentsFormat.money(rate)} JMD",
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Total", style = AirdropType.title2, color = colors.textDarkTitle)
                Text(
                    text = ShipmentsFormat.usdJmd(state.chargesTotal ?: 0.0, rate),
                    style = AirdropType.title2,
                    color = BrandPalette.OrangeMain,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("package-details-total-value"),
                )
            }
            GradientButton(text = "Add to Cart", onClick = onAddToCart)
        }

        Spacer(Modifier.height(Spacing.md))
    }
}

// ─── Upload zone — Figma "Attached File Type" 40000643:22987 ───────────────

@Composable
private fun UploadInvoiceZone(uploading: Boolean, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
            .clickable(enabled = !uploading, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "Upload Invoice",
                // Swift FigmaPackageDetailsViewController.swift:526-530 — Title2.
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "PNG, JPG and PDF files are allowed",
                // Swift FigmaPackageDetailsViewController.swift:533-537 — Body3.
                style = AirdropType.body3,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Dashed drop zone.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val dashColor = colors.gray300
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    color = dashColor,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 2.dp.toPx()),
                            0f,
                        ),
                    ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(colors.gray150)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                if (uploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = BrandPalette.OrangeMain,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_upload),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = "Drag and drop or browse to choose a file",
                    // Swift FigmaPackageDetailsViewController.swift:570-573 —
                    // Body3 in textDescription.
                    style = AirdropType.body3,
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun InvoiceFileRow(
    doc: PackageInvoiceDoc,
    canDelete: Boolean,
    onView: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("package-details-invoice-row-${doc.id}")
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
            .padding(horizontal = 16.dp),
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
                text = doc.fileName ?: "Invoice",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(text = "PDF File", style = AirdropType.body3, color = colors.textPlaceholder)
        }
        if (canDelete) {
            Image(
                painter = painterResource(R.drawable.ic_trash),
                contentDescription = "Delete invoice",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier
                    .testTag("package-details-invoice-delete-${doc.id}")
                    .size(24.dp)
                    .clickable(onClick = onDelete),
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_eye),
            contentDescription = "View invoice",
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier
                .testTag("package-details-invoice-view-${doc.id}")
                .size(24.dp)
                .clickable(onClick = onView),
        )
    }
}

// ─── Breakdown of Charges ───────────────────────────────────────────────────

@Composable
private fun ChargesCard(state: PackageDetailsUiState, detail: ShipmentPackageDetail) {
    val colors = AirdropTheme.colors
    val rate = state.effectiveRate
    DetailSectionCard(
        title = "Breakdown of Charges",
        tag = "package-details-section-charges",
        titleContentGap = 12.dp,
        contentSpacing = Spacing.sm,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ChargeRow(
                name = "Services",
                usd = "USD",
                jmd = "Local (JMD)",
                nameColor = colors.textDescription,
                valueColor = colors.textDescription,
            )
            detail.additionalCharges.entries.sortedBy { it.key }.forEach { (name, amount) ->
                ChargeRow(
                    name = name,
                    usd = "$" + ShipmentsFormat.money(amount),
                    jmd = "$" + ShipmentsFormat.money(amount * rate),
                    nameColor = colors.textDarkTitle,
                    valueColor = colors.textDarkTitle,
                )
            }
            val subtotal = state.chargesTotal ?: 0.0
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = "Subtotal",
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$" + ShipmentsFormat.money(subtotal),
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                )
                Spacer(Modifier.size(Spacing.md))
                Text(
                    text = "$" + ShipmentsFormat.money(subtotal * rate),
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                )
            }
        }
    }
}

@Composable
private fun ChargeRow(
    name: String,
    usd: String,
    jmd: String,
    nameColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(text = name, style = AirdropType.body2, color = nameColor, modifier = Modifier.weight(1f))
        Text(text = usd, style = AirdropType.body2, color = valueColor)
        Spacer(Modifier.size(Spacing.md))
        Text(text = jmd, style = AirdropType.body2, color = valueColor)
    }
}

// ─── Local inline section chrome — Swift makeSectionCard/makeSummaryRow.
// Plain gray100 card (radius 15, iconShape border, 16dp padding), inline
// Title2 header + optional 14dp chevron, rows spaced 10, no dividers. This is
// distinct from the shared ShipmentsSectionCard (banded header) which the
// filter sheet correctly uses — keep both. ───────────────────────────────

@Composable
private fun DetailSectionCard(
    title: String,
    trailingChevron: Boolean = false,
    tag: String? = null,
    titleContentGap: androidx.compose.ui.unit.Dp = Spacing.sm,
    contentSpacing: androidx.compose.ui.unit.Dp = Spacing.sm,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(titleContentGap),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
            )
            if (trailingChevron) {
                Image(
                    painter = painterResource(R.drawable.ic_small_arrow_down),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content,
        )
    }
}

/** Swift makeSummaryRow — label subtitle2 textDescription over Title2 value, no divider. */
@Composable
private fun DetailRow(label: String, value: String) {
    val colors = AirdropTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(text = label, style = AirdropType.subtitle2, color = colors.textDescription)
        Text(text = value, style = AirdropType.title2, color = colors.textDarkTitle)
    }
}

/** Swift makeTotalAndCTAPanel exchange row — horizontal key/value pair. */
@Composable
private fun DetailKeyValueRow(label: String, value: String) {
    val colors = AirdropTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AirdropType.subtitle2, color = colors.textDescription)
        Text(
            text = value,
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            textAlign = TextAlign.End,
        )
    }
}

/** Swift makeTimelineRow — 24dp status icon + 1dp connector + subtitle1 text. */
@Composable
private fun TimelineIconRow(
    statusName: String,
    statusCode: Int?,
    color: androidx.compose.ui.graphics.Color,
    comment: String?,
    date: String?,
    showConnector: Boolean,
    tag: String? = null,
) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(ShipmentStatusCatalog.iconRes(statusCode ?: 0, dark = colors.isDark)),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier = Modifier
                    .size(24.dp)
                    .testTag("package-details-timeline-icon-${statusCode ?: statusName}"),
            )
            if (showConnector) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .width(1.dp)
                        .height(34.dp)
                        .background(color)
                        .testTag("package-details-timeline-connector-${statusCode ?: statusName}")
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(text = statusName, style = AirdropType.subtitle1, color = color)
            if (!comment.isNullOrBlank()) {
                Text(text = comment, style = AirdropType.body3, color = colors.textDescription)
            }
            if (!date.isNullOrBlank()) {
                Text(text = date, style = AirdropType.body3, color = colors.textDescription)
            }
        }
    }
}

private fun packageDetailsBrandTitle(method: ShipmentMethodUi): String =
    when (method) {
        ShipmentMethodUi.Standard -> "AirDrop"
        ShipmentMethodUi.Express -> "Express"
        ShipmentMethodUi.SeaDrop -> "SeaDrop"
    }
