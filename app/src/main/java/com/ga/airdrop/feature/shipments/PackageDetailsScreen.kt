package com.ga.airdrop.feature.shipments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.CifValueSheet
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.common.AirdropUploadSourceConfig
import com.ga.airdrop.feature.common.AirdropUploadSourceSheet
import com.ga.airdrop.feature.delivery.TrackJourney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    var showInvoiceSourcePicker by remember { mutableStateOf(false) }

    val damagePhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val files = uris.mapNotNull { uri ->
                readPickedPackageFile(context, uri, fallbackName = "damage-photo")?.let {
                    DamageReportUploadFile(
                        fileName = it.fileName,
                        mimeType = damagePhotoMime(it.fileName, it.mimeType),
                        bytes = it.bytes,
                    )
                }
            }
            viewModel.addDamageReportPhotos(files)
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
                            onPickFiles = { showInvoiceSourcePicker = true },
                            onViewInvoice = { doc ->
                                doc.fullUrl?.let { url ->
                                    onNavigate(Routes.invoiceViewer(url, doc.fileName ?: "Invoice"))
                                }
                            },
                            onDeleteInvoice = viewModel::requestDeleteInvoice,
                            onCifInfo = { viewModel.showCifInfo(true) },
                            onAddToCart = viewModel::addToCart,
                            onReportDamage = { viewModel.showReportDamageSheet(true) },
                            onContactUs = { onNavigate(Routes.CONTACTS) },
                            onRetryTimeline = { viewModel.refresh() },
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

        if (showInvoiceSourcePicker) {
            val existingCount = detail?.invoices?.size ?: 0
            AirdropUploadSourceSheet(
                config = AirdropUploadSourceConfig(
                    sheetTitle = "Upload Invoice",
                    allowedFileExtensions = AirdropUploadSourceConfig.invoiceFileExtensions,
                    allowsMultipleFileSelection = true,
                    maxSelectionCount = (3 - existingCount).coerceAtLeast(1),
                    maxFileBytes = 10 * 1024 * 1024,
                ),
                onPicked = { files ->
                    viewModel.uploadInvoices(
                        files.map {
                            InvoiceUploadFile(
                                fileName = it.fileName,
                                mimeType = it.mimeType,
                                bytes = it.bytes,
                            )
                        },
                    )
                },
                onFailure = viewModel::showTransientMessage,
                onDismiss = { showInvoiceSourcePicker = false },
            )
        }

        if (state.showCifInfo) {
            // ⚠️ RULE (Kemar 2026-07-25): every CIF Value affordance opens the
            // Figma CIF sheet (40001761:29633) — never a one-line alert.
            CifValueSheet(
                rows = state.cifRows,
                // effectiveRate, NOT exchangeRate — the rule this file already
                // states at the StorageFeeCard below, which this call was the
                // single violation of. `exchangeRate` is the app-wide rate and
                // falls back to the HARDCODED ExchangeRateStore.DEFAULT_USD_TO_JMD
                // (160.625) whenever /exchange-rates has not answered — and that
                // fetch is onSuccess-only (PackageDetailsViewModel:176-179), so a
                // failure leaves the constant in place with nothing surfaced.
                // Meanwhile the package payload always carries a live server rate.
                //
                // The visible symptom: Breakdown, Exchange Rate and Total convert
                // at the package's real rate while the CIF sheet converted at the
                // constant — one screen, two different JMD figures for the same
                // money, one of them not from the server at all.
                //
                // The sibling screen already had it right
                // (PaymentPackageDetailsScreen.kt:111).
                exchangeRate = state.effectiveRate,
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
        if (state.showDamageReportSubmitted) {
            ShipmentsAlertDialog(
                title = "Report received",
                message = "Thanks — we'll review the damage photos and reach out within 24 hours.",
                confirmText = "OK",
                onConfirm = viewModel::dismissDamageReportSubmitted,
                onDismiss = viewModel::dismissDamageReportSubmitted,
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
                title = state.transientTitle ?: "Invoice",
                message = message,
                confirmText = "OK",
                onConfirm = viewModel::consumeTransientMessage,
                onDismiss = viewModel::consumeTransientMessage,
            )
        }
        if (state.showReportDamageSheet) {
            ReportDamageSheet(
                description = state.damageReportDescription,
                photos = state.damageReportPhotos,
                submitting = state.submittingDamageReport,
                error = state.damageReportError,
                onDescriptionChange = viewModel::onDamageReportDescription,
                onAddPhoto = { damagePhotoPicker.launch(arrayOf("image/png", "image/jpeg")) },
                onRemovePhoto = viewModel::removeDamageReportPhoto,
                onSubmit = viewModel::submitDamageReport,
                onDismiss = { viewModel.showReportDamageSheet(false) },
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
    onReportDamage: () -> Unit,
    /** Opens Contact us from a timeline row that has gone wrong. */
    onContactUs: () -> Unit,
    /**
     * Re-requests the canonical timeline. Real, not decorative: the error state
     * previously told customers to "pull to refresh" on a screen with no
     * pull-to-refresh at all (BrightHarbor #80372).
     */
    onRetryTimeline: () -> Unit,
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
            // Swift orders Status immediately after Shipping Method
            // (FigmaPackageDetailsViewController:1332-1333, and the placeholder
            // table at :566-567). Kotlin omitted it entirely, so the one field
            // that says where the package actually is was missing from the
            // summary — on a screen whose timeline can also be empty.
            DetailRow("Status", ShipmentStatusCatalog.displayName(detail.statusName, detail.status))
            DetailRow("Merchant/Shipper", detail.store ?: "—")
            DetailRow("Courier Tracking", courier)
            DetailRow("Description", detail.description?.ifBlank { "—" } ?: "—")
            DetailRow(
                "Weight/Volume",
                ShipmentsFormat.weight(detail.weightLbs, detail.weightKg, detail.weight),
            )
            DetailRow("Number of Pieces", (detail.numberOfPieces ?: 1).toString())
        }

        // Shipment Timeline — the package's REAL recorded history.
        //
        // ⚠️ WHAT THIS REPLACED. This section used to render
        // PackageTimelineProgression, a fixed seven-rung ladder (Drop Alerted,
        // Shipment Received, Port of Departure MIA, Arrived at Port JAM, Ready
        // for Pickup, Paid and Ready for Pick Up, Delivered) derived from the
        // package's CURRENT status. It ignored `history` for the row list and
        // only consulted it to look up a date. Two consequences, both seen on
        // pre-staging:
        //   • It INVENTED events. Package 153897 has status 20 and ZERO
        //     history rows; the screen printed all seven rungs anyway, because
        //     an unrecognised status fell through to `return visibleStatuses`.
        //   • It DELETED events. The ladder has no entry for Released From
        //     Customs (5) or Processing at our Warehouse (6), so package
        //     153907 — which really passed through both — showed neither.
        // A fixed list may ORDER a rail; it must never author or filter it
        // (SwiftHawk #79761 §1).
        //
        // Kemar 2026-07-26: *"once we go into details on packages, we would
        // see the full thing as well, the full tracking history as well, which
        // could be up to twelve statuses if needs be or even more."* The
        // Packages LIST stays a one-line status; DETAILS is the expanded rail.
        DetailSectionCard(
            title = "Shipment Timeline",
            tag = "package-details-section-timeline",
            titleContentGap = 14.dp,
            contentSpacing = 12.dp,
        ) {
            val rows = TrackJourney.rows(state.timeline)
            // ⚠️ THREE DISTINCT SITUATIONS, THREE DIFFERENT ANSWERS.
            //
            //   FAILED                        -> we could not read it. Say so.
            //   LOADED + raw payload empty    -> genuinely no recorded events.
            //   LOADED + raw entries present
            //     but all dropped as invalid  -> we read something we could not
            //                                    understand. NOT "no history".
            //
            // The third is the subtle one, and it is why this gates on the RAW
            // payload rather than on the mapped rows: TrackJourney drops any
            // entry with a blank label, so a malformed-but-successful response
            // maps to zero rows and would otherwise be presented as confirmed
            // zero history. BrightHarbor #80372.
            val readFailed = state.timelineOutcome == TimelineOutcome.FAILED
            val payloadUnusable = state.timelineOutcome == TimelineOutcome.LOADED &&
                state.timeline.isNotEmpty() && rows.isEmpty()
            if (rows.isEmpty() && (readFailed || payloadUnusable)) {
                // ⚠️ A FAILED READ IS NOT AN EMPTY JOURNEY.
                //
                // `packageTimeline(...).getOrNull().orEmpty()` collapses every
                // failure — network, 401, 5xx, decode — onto the same empty
                // list an event-less package produces. Branching on emptiness
                // alone would show a package with a full recorded history as a
                // single current-status row the moment one request dropped,
                // and would present "we could not read this" as "nothing has
                // happened". Two different facts; the customer is told which.
                //
                // Caught by BrightHarbor reviewing #178 before it merged.
                Text(
                    text = "Tracking updates couldn't be loaded.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    modifier = Modifier.testTag("package-details-timeline-unavailable"),
                )
                // ⚠️ This used to read "Pull to refresh to try again" — on a
                // screen that is a plain verticalScroll with no PullToRefresh
                // and no onRefresh anywhere. It instructed customers to perform
                // a gesture that does nothing. Telling someone to retry with no
                // way to retry is worse than showing the error alone.
                // BrightHarbor #80372.
                Text(
                    text = "Try again",
                    style = AirdropType.body2,
                    color = colors.orangeMain,
                    modifier = Modifier
                        .clickable(onClick = onRetryTimeline)
                        .testTag("package-details-timeline-retry"),
                )
            } else if (rows.isEmpty() && state.timelineOutcome == TimelineOutcome.LOADED) {
                // ⚠️ ZERO RECORDED HISTORY. Until now this card rendered its
                // title with nothing under it — a blank box — for any package
                // whose history has not been written yet. Real case, not
                // hypothetical: every package on the QA fixture set has zero
                // `package_change_history` rows, and pre-staging 153897 is a
                // live status-20 package with none.
                //
                // The fix must not become the bug it replaces. This is ONE row
                // stating where the package is NOW — undated, not completed, no
                // connector, no predecessors. It is a present-state marker, not
                // a claim that a transition was recorded. That distinction is
                // the whole reason `/packages/journeys` is on hold: it answers
                // this same situation by inventing "done" stages with null
                // timestamps for events that never happened.
                //
                // Swift parity, read from FigmaPackageDetailsViewController
                // :1359-1367 — `d.history.isEmpty` adds exactly one row with
                // `date: nil`, `isCompleted: false`, `isLast: true`, falling
                // back to "Pending" when the name resolves to an em dash.
                val currentStatusId = detail.status?.trim()?.toIntOrNull()
                val currentName = ShipmentStatusCatalog
                    .displayName(detail.statusName, detail.status)
                    .takeIf { it.isNotBlank() && it != "—" }
                    ?: "Pending"
                TimelineIconRow(
                    statusName = currentName,
                    statusCode = currentStatusId,
                    // No server glyph key exists off the timeline; the status
                    // catalogue resolves the icon from the id.
                    iconKey = null,
                    // Current, never AlertPalette.Completed — nothing here has
                    // been recorded as done.
                    color = colors.orangeMain,
                    date = null,
                    showConnector = false,
                    // Kemar's rule still applies to a present state: a package
                    // sitting in Detained/Uncollected/Dangerous/Returned needs a
                    // way out whether or not its history was ever written. Ten
                    // of the QA fixtures are status 19, Returned to Merchant.
                    onContactUs = if (TrackJourney.needsHelp(currentStatusId)) onContactUs else null,
                    tag = "package-details-timeline-current-only",
                )
            }
            rows.forEachIndexed { index, row ->
                TimelineIconRow(
                    statusName = row.label,
                    statusCode = row.statusId,
                    iconKey = row.iconKey,
                    // Everything on this rail has already happened, so it is
                    // all "reached". The last row is where the package is now.
                    color = if (index == rows.lastIndex) {
                        colors.orangeMain
                    } else {
                        AlertPalette.Completed
                    },
                    // The server preformats the timestamp.
                    date = row.at,
                    showConnector = index != rows.lastIndex,
                    // Kemar: a status that has gone wrong carries its way out.
                    onContactUs = if (row.needsHelp) onContactUs else null,
                    tag = "package-details-timeline-row-${row.statusId ?: row.label}",
                )
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
                    "Only the following formats are allowed: pdf, jpg, jpeg, png, gif, bmp, webp.",
                // Swift origin/main corrects the stale Figma/RN doc/docx/html copy.
                style = AirdropType.body3,
                color = colors.textDescription,
            )
            detail.invoices.forEach { doc ->
                InvoiceFileRow(
                    doc = doc,
                    canDelete = canDeleteInvoices,
                    deleting = state.deletingInvoiceId == doc.id,
                    onView = { onViewInvoice(doc) },
                    onDelete = { onDeleteInvoice(doc.id) },
                )
            }
        }

        // CIF Value info row — Figma 40001753:21889 (verified via Figma MCP:
        // the instance measures 335x59 with the 24dp Info Circle at y=17.5).
        // White surface, 1px #e5e5e5 hairline, 15px radius, label = SubTitle 1
        // (Cairo SemiBold 16/26 #292929), trailing 24dp Info Circle.
        //
        // 59dp is a MINIMUM, not a fixed box: it pins the default rendering to
        // Figma while still letting the row grow when the user scales text
        // size. A hard height would clip the label at large text sizes.
        // (The previous 48dp was a Swift-era value that matched neither Figma
        // nor the rendered row; PackageDetailsParityTest asserts the 59.)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("package-details-cif-row")
                .clip(RoundedCornerShape(Radius.s))
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                .clickable(onClick = onCifInfo)
                .defaultMinSize(minHeight = 59.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "CIF Value", style = AirdropType.subtitle1, color = colors.textDarkTitle)
            Image(
                // Figma embeds Info Circle node 40000643:20582 here — that is
                // ic_calc_info_circle (recorded as the verbatim SVG path). The
                // previous ic_info is a rounded-SQUARE glyph, not the circle.
                // It is already @color/icon_duotone, so it themes itself.
                painter = painterResource(R.drawable.ic_calc_info_circle),
                contentDescription = "CIF info",
                modifier = Modifier.size(24.dp),
            )
        }

        // Storage fees — Laravel's server-owned `storage` block. Rendered
        // whenever the server says the package is eligible, independently of
        // the charges gate: a customer accruing storage needs to see it even
        // in states where the Breakdown card is hidden.
        detail?.storage?.takeIf { it.eligible }?.let { storage ->
            // effectiveRate, NOT exchangeRate: every other money row on this
            // screen (Breakdown, Exchange Rate, Total) uses effectiveRate,
            // and showing the same charge at two different JMD figures on
            // one screen is worse than showing none.
            StorageFeeCard(storage = storage)
        }

        // Breakdown of Charges + Add to Cart — Swift showCharges gate
        // (statusInt == 7 || == 18), not >= 7. Swift (:834-890) ALWAYS renders
        // the Breakdown card (header + Subtotal) in this state, then a plain
        // Exchange Rate row and a plain Total row (orange value) — no orange pill box.
        if (state.showChargesAndCart) {
            // Customs Notice sheet — Swift figma.packageDetails.customsDutyInfo
            // (Figma 40008798:29642) opens from the Customs Duty charge row.
            val showCustomsNotice = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(false)
            }
            ChargesCard(
                state = state,
                detail = detail,
                onCustomsInfo = { showCustomsNotice.value = true },
            )
            if (showCustomsNotice.value) {
                CustomsNoticeSheet(
                    onDismiss = { showCustomsNotice.value = false },
                    // Kemar: tapping "CIF value" inside the notice generates the
                    // CIF page too.
                    onCifValue = {
                        showCustomsNotice.value = false
                        onCifInfo()
                    },
                )
            }
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
            if (state.canAddToCart) {
                GradientButton(text = "Add to Cart", onClick = onAddToCart)
            }
        }

        if (state.showReportDamageCta) {
            ReportDamageButton(onClick = onReportDamage)
        }

        Spacer(Modifier.height(Spacing.md))
    }
}

private data class PickedPackageFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

private fun readPickedPackageFile(context: Context, uri: Uri, fallbackName: String): PickedPackageFile? =
    runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var name = fallbackName
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
        }
        PickedPackageFile(fileName = name, mimeType = mime, bytes = bytes)
    }.getOrNull()

private fun damagePhotoMime(fileName: String, mimeType: String): String {
    val lowerMime = mimeType.lowercase()
    val lowerName = fileName.lowercase()
    return when {
        lowerMime == "image/png" || lowerName.endsWith(".png") -> "image/png"
        lowerMime == "image/jpeg" || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
        else -> lowerMime
    }
}

@Composable
private fun ReportDamageButton(onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("package-details-report-damage")
            .clip(RoundedCornerShape(14.dp))
            .background(colors.gray100)
            .border(1.dp, BrandPalette.OrangeMain, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Report damage", style = AirdropType.button, color = BrandPalette.OrangeMain)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDamageSheet(
    description: String,
    photos: List<DamageReportUploadFile>,
    submitting: Boolean,
    error: String?,
    onDescriptionChange: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.gray100,
        shape = RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s),
        modifier = Modifier.testTag("package-details-report-damage-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // ime ∪ navigationBars, same reason as the Delivery Method CTA:
                // enableEdgeToEdge() means adjustResize no longer moves anything,
                // so this sheet has to lift itself. Without it the keyboard sat on
                // top of the damage-description field the customer is asked to
                // type into, and on the Submit button under it.
                // union() takes the larger inset per side, so with the keyboard
                // closed this is exactly the old navigationBarsPadding().
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(text = "Report damage", style = AirdropType.h6, color = colors.textDarkTitle)
            Text(
                text = "Add photos of the damage. We'll review and reach out to you within 24 hours.",
                style = AirdropType.body2,
                color = colors.textDescription,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                photos.forEachIndexed { index, photo ->
                    DamagePhotoTile(photo = photo, index = index, onRemove = { onRemovePhoto(index) })
                }
                AddDamagePhotoTile(enabled = !submitting && photos.size < 5, onClick = onAddPhoto)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = "Description", style = AirdropType.subtitle2, color = colors.textDarkTitle)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.gray150)
                        .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
                        .padding(horizontal = Spacing.md, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
                        cursorBrush = SolidColor(BrandPalette.OrangeMain),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("package-details-report-damage-description"),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxSize()) {
                                if (description.isBlank()) {
                                    Text(
                                        text = "Describe the damage (optional)",
                                        style = AirdropType.body2,
                                        color = colors.textPlaceholder,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }
            error?.let {
                Text(
                    text = it,
                    style = AirdropType.body3,
                    color = androidx.compose.ui.graphics.Color(0xFFDC2626),
                    modifier = Modifier.testTag("package-details-report-damage-error"),
                )
            }
            GradientButton(
                text = "Submit",
                onClick = onSubmit,
                enabled = !submitting,
                loading = submitting,
                modifier = Modifier.testTag("package-details-report-damage-submit"),
            )
        }
    }
}

@Composable
private fun AddDamagePhotoTile(enabled: Boolean, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Box(
        modifier = Modifier
            .size(width = 92.dp, height = 84.dp)
            .testTag("package-details-report-damage-add-photo")
            .clip(RoundedCornerShape(12.dp))
            .background(colors.gray150)
            .border(1.dp, BrandPalette.OrangeMain, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "+", style = AirdropType.h6, color = BrandPalette.OrangeMain)
            Text(text = "Add", style = AirdropType.body3, color = BrandPalette.OrangeMain)
        }
    }
}

@Composable
private fun DamagePhotoTile(photo: DamageReportUploadFile, index: Int, onRemove: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .size(width = 116.dp, height = 84.dp)
            .testTag("package-details-report-damage-photo-$index")
            .clip(RoundedCornerShape(12.dp))
            .background(colors.gray150)
            .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = photo.fileName,
            style = AirdropType.body3,
            color = colors.textDarkTitle,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            text = "Remove",
            style = AirdropType.body3,
            color = BrandPalette.OrangeMain,
            modifier = Modifier
                .testTag("package-details-report-damage-remove-$index")
                .clickable(onClick = onRemove),
        )
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
            .testTag("package-details-upload-invoice-zone")
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
                text = "PDF and image files (JPG, PNG, GIF, BMP, WEBP) are allowed",
                // Swift origin/main states the actual picker-enforced file family.
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
    deleting: Boolean,
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
            Box(
                modifier = Modifier
                    .testTag(
                        if (deleting) {
                            "package-details-invoice-deleting-${doc.id}"
                        } else {
                            "package-details-invoice-delete-${doc.id}"
                        }
                    )
                    .size(28.dp)
                    .then(if (deleting) Modifier else Modifier.clickable(onClick = onDelete)),
                contentAlignment = Alignment.Center,
            ) {
                if (deleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BrandPalette.OrangeMain,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = "Delete invoice",
                        colorFilter = ColorFilter.tint(colors.iconSelected),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .testTag("package-details-invoice-view-${doc.id}")
                .size(28.dp)
                .clickable(onClick = onView),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_eye),
                contentDescription = "View invoice",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ─── Breakdown of Charges ───────────────────────────────────────────────────

@Composable
private fun ChargesCard(
    state: PackageDetailsUiState,
    detail: ShipmentPackageDetail,
    onCustomsInfo: () -> Unit,
) {
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
                    // Swift makeChargesRow: Customs Duty rows carry an
                    // orange info circle → Customs Notice sheet.
                    onInfo = if (isCustomsDutyCharge(name)) onCustomsInfo else null,
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
    onInfo: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onInfo != null) {
            // Swift makeCustomsDutyChargesButton: body2 label + 18pt orange
            // infoCircle, whole cell tappable → Customs Notice.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onInfo)
                    .testTag("package-details-customs-duty-info"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = name, style = AirdropType.body2, color = nameColor)
                Image(
                    painter = painterResource(R.drawable.ic_calc_info_circle),
                    contentDescription = "$name info",
                    colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Text(text = name, style = AirdropType.body2, color = nameColor, modifier = Modifier.weight(1f))
        }
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
    /** The server's glyph key for this row. */
    iconKey: String? = null,
    color: androidx.compose.ui.graphics.Color,
    date: String?,
    showConnector: Boolean,
    /**
     * Non-null on a status that has gone wrong. See TrackJourney.needsHelp.
     */
    onContactUs: (() -> Unit)? = null,
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
                painter = painterResource(TrackJourney.iconRes(iconKey, statusCode, colors.isDark)),
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
            // `package_comment` is NOT customer-safe: the same column carries
            // internal strings ("Status updated from batch process"), payment
            // internals ("Package paid via NCB PowerTranz. Invoice ID: …") and
            // driver-supplied delivery notes. BrightHarbor #79824 ruled it out
            // of the mobile projection; it is now out of this screen too.
            if (!date.isNullOrBlank()) {
                Text(text = date, style = AirdropType.body3, color = colors.textDescription)
            }
            onContactUs?.let { contact ->
                Text(
                    text = "Contact us about this",
                    style = AirdropType.subtitle1,
                    color = colors.orangeMain,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(onClick = contact),
                )
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
