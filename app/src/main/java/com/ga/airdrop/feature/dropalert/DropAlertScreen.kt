package com.ga.airdrop.feature.dropalert

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
// Shared form chrome from the sibling calculator feature (same owner) —
// Figma Type Input Field, select field, blue info card, inner header.
import com.ga.airdrop.feature.calculator.BlueInfoCard
import com.ga.airdrop.feature.calculator.CalcInputField
import com.ga.airdrop.feature.calculator.CalcSelectField
import com.ga.airdrop.feature.calculator.DollarTrailing
import com.ga.airdrop.feature.calculator.FieldLabel
import com.ga.airdrop.feature.calculator.InnerScreenHeader
import com.ga.airdrop.feature.calculator.OptionPickerSheet
import com.ga.airdrop.feature.calculator.SimpleAlertDialog
import java.util.Locale

/**
 * Drop Alert — Figma 40001826:22497 (empty) + 40001836:22971 (filled, file
 * rows). Behavior from FigmaDropAlertViewController / RN DropAlertView:
 * consignee prefilled from the profile, insurance note, up to 3 invoice
 * files, typed multipart POST /drop-alerts.
 */
@Composable
fun DropAlertScreen(
    viewModel: DropAlertViewModel,
    onBack: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var methodPicker by remember { mutableStateOf(false) }
    var courierPicker by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            readInvoice(context, uri)?.let(viewModel::addInvoice)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .imePadding()
    ) {
        InnerScreenHeader(title = "Drop Alert", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            BlueInfoCard(
                title = "Create a DropAlert",
                text = "Let us know when your shipment is en route to our Florida warehouse. " +
                    "This ensures faster processing, minimizes customs delays, and prevents " +
                    "invoice-related overcharges.",
            )

            CalcInputField(
                label = "Courier Number",
                value = state.courierNumber,
                onValueChange = viewModel::onCourierNumberChange,
                placeholder = "e.g. 3498534580",
                required = true,
            )

            CalcSelectField(
                label = "Shipping Method",
                value = state.shippingMethod,
                placeholder = "Select",
                required = true,
                onClick = { methodPicker = true },
            )

            // Figma renders this as a dropdown, but Swift/RN take free text
            // ("e.g. Amazon") — the API's package_shipper is unconstrained.
            CalcInputField(
                label = "Shipper/Merchant",
                value = state.shipper,
                onValueChange = viewModel::onShipperChange,
                placeholder = "e.g. Amazon",
                required = true,
            )

            // Read-only, prefilled from the profile (Swift prefillConsignee).
            // Figma shows the filled value in Body 1 (16/26) on the disabled box.
            CalcInputField(
                label = "Consignee",
                value = state.consignee,
                onValueChange = {},
                required = true,
                enabled = false,
                textStyle = AirdropType.body1,
            )

            CalcInputField(
                label = "Package Value (USD)",
                value = state.packageValue,
                onValueChange = viewModel::onPackageValueChange,
                placeholder = "e.g. 84",
                required = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailing = { DollarTrailing() },
            )

            InsuranceInfoCard()

            // Not in the Figma frame, but required by the API
            // (package_store) — Swift/RN both collect it. See report.
            CalcSelectField(
                label = "Courier Company",
                value = state.courierCompany,
                placeholder = "Select",
                required = true,
                onClick = { courierPicker = true },
            )

            DescriptionField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
            )

            UploadSection(
                count = state.invoices.size,
                onUploadClick = {
                    if (viewModel.onUploadTapped()) {
                        filePicker.launch(arrayOf("application/pdf", "image/png", "image/jpeg"))
                    }
                },
            )

            Text(
                text = "You're allowed to upload a maximum of 3 files each with a size below 10 MB. " +
                    "Only the following formats are allowed: pdf, jpg, bmp, png, doc, docx html.",
                style = AirdropType.body2,
                color = colors.textDescription,
            )

            state.invoices.forEach { invoice ->
                InvoiceFileRow(
                    invoice = invoice,
                    onDelete = { viewModel.removeInvoice(invoice) },
                    onPreview = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(invoice.uri), invoice.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            )
                        }
                    },
                )
            }
        }

        // Footer — Figma "Button Type" (40001826:22528): Cancel + Drop Alert.
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.glassOverlay70)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider)
            )
            Row(
                Modifier
                    .padding(Spacing.md)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlineButton(text = "Cancel", onClick = onBack, modifier = Modifier.weight(1f))
                GradientButton(
                    text = if (state.submitting) "Submitting…" else "Drop Alert",
                    loading = state.submitting,
                    onClick = viewModel::submit,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (methodPicker) {
        OptionPickerSheet(
            options = DropAlertViewModel.SHIPPING_METHOD_OPTIONS,
            selected = state.shippingMethod.ifBlank { null },
            onSelect = viewModel::onShippingMethodSelected,
            onDismiss = { methodPicker = false },
        )
    }
    if (courierPicker) {
        OptionPickerSheet(
            options = DropAlertViewModel.COURIER_COMPANY_OPTIONS,
            selected = state.courierCompany.ifBlank { null },
            onSelect = viewModel::onCourierCompanySelected,
            onDismiss = { courierPicker = false },
        )
    }

    state.dialog?.let { dialog ->
        SimpleAlertDialog(title = dialog.title, message = dialog.message, onDismiss = viewModel::dismissDialog)
    }
}

/**
 * Insurance note — Swift FigmaDropAlertViewController.swift:613-619: tapping
 * "See details" shows an in-place "Terms and Conditions" alert, NOT a push to
 * the full Terms screen.
 */
@Composable
private fun InsuranceInfoCard() {
    val colors = AirdropTheme.colors
    var showTerms by remember { mutableStateOf(false) }
    BlueInfoCard(
        text = {
            val text = buildAnnotatedString {
                append("Your package is insured for the value entered. Please see our terms and conditions. ")
                pushStringAnnotation(tag = "link", annotation = "terms")
                withStyle(
                    SpanStyle(
                        color = BrandPalette.OrangeMain,
                        textDecoration = TextDecoration.Underline,
                    )
                ) {
                    append("See details")
                }
                pop()
            }
            ClickableText(
                text = text,
                style = AirdropType.body2.copy(color = colors.textDarkTitle),
                onClick = { offset ->
                    text.getStringAnnotations("link", offset, offset).firstOrNull()?.let { showTerms = true }
                },
            )
        },
    )
    if (showTerms) {
        SimpleAlertDialog(
            title = "Terms and Conditions",
            message = "Your package is insured for the value entered. Coverage is " +
                "subject to our terms and conditions.",
            onDismiss = { showTerms = false },
        )
    }
}

@Composable
private fun DescriptionField(value: String, onValueChange: (String) -> Unit) {
    val colors = AirdropTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FieldLabel(label = "Description", required = false)
        Box(
            Modifier
                .fillMaxWidth()
                .height(128.dp)
                .background(colors.gray150, RoundedCornerShape(Radius.xs))
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
                .padding(horizontal = Spacing.md, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
                cursorBrush = SolidColor(BrandPalette.OrangeMain),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Upload card — Figma "Attached File Type" (40001836:22938): section header
 * with (n/3) counter, bordered card with centered copy and the dashed
 * paper-download drop area ("Select File").
 */
@Composable
private fun UploadSection(count: Int, onUploadClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Upload Your Invoice",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "($count/${DropAlertViewModel.MAX_INVOICES})",
                style = AirdropType.body2,
                color = colors.textDescription,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100, RoundedCornerShape(Radius.xs))
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
                .clickable(onClick = onUploadClick)
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Upload Invoice",
                    style = AirdropType.title1,
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "PNG, JPG and PDF files are allowed",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                )
            }
            val dashColor = colors.gray400
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.gray150, RoundedCornerShape(Radius.xs))
                    .drawBehind {
                        drawRoundRect(
                            color = dashColor,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                                ),
                            ),
                            cornerRadius = CornerRadius(Radius.xs.toPx()),
                        )
                    }
                    .padding(vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_dropalert_paper_download),
                    contentDescription = "Upload invoice",
                    colorFilter = ColorFilter.tint(colors.gray500),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "Select File",
                    style = AirdropType.subtitle2,
                    color = colors.textDarkTitle,
                )
            }
        }
    }
}

/** Attached invoice row — Figma 40001836:22971 file cards (pdf/trash/eye). */
@Composable
private fun InvoiceFileRow(
    invoice: DropAlertInvoice,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.xs))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
            .padding(horizontal = Spacing.sm1, vertical = Spacing.sm),
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
                text = invoice.fileName.ifBlank { "Invoice" },
                style = AirdropType.subtitle2,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = fileSummary(invoice),
                style = AirdropType.body3,
                color = colors.textDescription,
                maxLines = 1,
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_trash),
            contentDescription = "Remove file",
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onDelete),
        )
        Image(
            painter = painterResource(R.drawable.ic_eye),
            contentDescription = "Preview file",
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onPreview),
        )
    }
}

/** "PDF files, 2.2MB" caption — type from the mime, size humanized. */
private fun fileSummary(invoice: DropAlertInvoice): String {
    val type = when {
        invoice.mimeType.contains("pdf") -> "PDF"
        invoice.mimeType.contains("png") -> "PNG"
        invoice.mimeType.contains("jpeg") || invoice.mimeType.contains("jpg") -> "JPG"
        else -> "File"
    }
    val bytes = invoice.bytes.size.toDouble()
    val size = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1fMB", bytes / (1024 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.0fKB", bytes / 1024)
        else -> "${bytes.toInt()}B"
    }
    return "$type files, $size"
}

/**
 * Load a picked document into memory — the SAF grant only lives for the
 * callback, so bytes are read immediately (Swift documentPicker parity).
 */
private fun readInvoice(context: android.content.Context, uri: Uri): DropAlertInvoice? {
    val resolver = context.contentResolver
    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null

    var name = "invoice"
    runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.let { name = it }
            }
        }
    }

    val mime = resolver.getType(uri) ?: when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }
    return DropAlertInvoice(fileName = name, mimeType = mime, bytes = bytes, uri = uri.toString())
}
