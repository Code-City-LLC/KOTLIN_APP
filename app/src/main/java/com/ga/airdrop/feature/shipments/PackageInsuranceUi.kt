package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.InsuranceOptions
import com.ga.airdrop.data.model.InsuranceSelection
import com.ga.airdrop.feature.homedetails.GlassPrimaryButton
import com.ga.airdrop.feature.homedetails.GlassSecondaryButton

/*
 * Package insurance — the joint Swift/Kotlin flow for the Tier API's explicit
 * select/decline contract (POST /packages/{id}/insurance-selection):
 *
 *  - An "Insurance" row on Package Details (same card language as the CIF
 *    row) shows what the backend recorded: Choose / Covered / Declined.
 *  - Tapping opens a dark glass sheet (same recipe as the tier-change sheet:
 *    #1B1B1B container, white-glass panels, 14dp-radius buttons) with the
 *    backend's premium quote rendered verbatim — the app never computes money.
 *  - "Decline coverage" is offered ONLY when the quote says can_decline
 *    (SAVR today). A refused decline (INSURANCE_MANDATORY) snaps the sheet
 *    back to the covered option — the error_code pact behaviour.
 *
 * Kotlin is the reference implementation of this flow; Swift mirrors it in
 * FigmaPackageDetailsViewController (see Tier-System-Handoff-for-Swift.md).
 */

/** The Insurance row — sits with the CIF row on Package Details. */
@Composable
internal fun PackageInsuranceRow(
    selection: InsuranceSelection?,
    onClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val status = insuranceStatusLabel(selection)
    val statusColor = when (status) {
        "Covered" -> Color(0xFF2E9E5B)
        "Declined" -> colors.textDescription
        else -> com.ga.airdrop.core.designsystem.theme.BrandPalette.OrangeMain
    }
    val statusText = when {
        selection?.selected == true && selection.premium > 0 ->
            "Covered · $${ShipmentsFormat.money(selection.premium)}"
        else -> status
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("package-details-insurance-row")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Insurance", style = AirdropType.subtitle1, color = colors.textDarkTitle)
        Text(
            text = statusText,
            style = AirdropType.subtitle1,
            color = statusColor,
            modifier = Modifier.testTag("package-details-insurance-status"),
        )
    }
}

/**
 * The select/decline glass sheet. All figures come from GET /insurance/options
 * and are shown exactly as returned.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PackageInsuranceSheet(
    quote: InsuranceOptions?,
    quoteError: String?,
    busy: Boolean,
    error: String?,
    declineRefused: Boolean,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1B1B1B),
        dragHandle = null,
        modifier = Modifier.testTag("package-insurance-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 28.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 16.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            )

            // Hero — the shipment-blue gradient keeps the sheet in the same
            // family as the tier sheets without borrowing a tier's identity.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0A96D4), Color(0xFF004B6C)),
                            start = Offset(Float.POSITIVE_INFINITY, 0f),
                            end = Offset(0f, Float.POSITIVE_INFINITY),
                        ),
                    )
                    .padding(horizontal = 22.dp, vertical = 24.dp),
            ) {
                Text(
                    text = "PROTECT THIS PACKAGE",
                    style = AirdropType.button.copy(fontSize = 12.sp, letterSpacing = 1.5.sp),
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Package Insurance",
                    style = AirdropType.h5.copy(fontSize = 28.sp, lineHeight = 34.sp),
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(22.dp))

            when {
                quote == null && quoteError == null -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        Modifier.size(26.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }

                quote == null -> Text(
                    text = quoteError ?: "Couldn't load insurance options.",
                    style = AirdropType.body2,
                    color = Color(0xFFF17A72),
                )

                else -> {
                    // Backend quote, verbatim — glass panel like the tier pills.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InsuranceQuoteRow("Declared value", "$" + ShipmentsFormat.money(quote.insuredValue))
                        InsuranceQuoteRow(
                            "Premium",
                            "$" + ShipmentsFormat.money(quote.premium),
                            caption = "$${ShipmentsFormat.money(quote.ratePer100)} per $${quote.blockSize} of value",
                        )
                        InsuranceQuoteRow("Covered value", "$" + ShipmentsFormat.money(quote.coveredValue))
                    }

                    if (!quote.canDecline) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Insurance is required for your tier and can't be declined.",
                            style = AirdropType.body2,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error,
                    style = AirdropType.body2,
                    color = Color(0xFFF17A72),
                    modifier = Modifier.testTag("package-insurance-error"),
                )
            }

            Spacer(Modifier.height(24.dp))

            GlassPrimaryButton(
                text = quote?.let { "Add Insurance — $" + ShipmentsFormat.money(it.premium) }
                    ?: "Add Insurance",
                loading = busy,
                onClick = onConfirm,
                fill = Color.White,
                content = Color(0xFF1B1B1B),
                enabled = quote != null,
            )

            // Decline exists only while the backend allows it: SAVR quotes say
            // can_decline; a refused decline (INSURANCE_MANDATORY) removes it.
            if (quote?.canDecline == true && !declineRefused) {
                Spacer(Modifier.height(10.dp))
                GlassSecondaryButton(
                    text = "Decline coverage",
                    onClick = onDecline,
                    enabled = !busy,
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun InsuranceQuoteRow(label: String, value: String, caption: String? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = AirdropType.body2, color = Color.White.copy(alpha = 0.7f))
            if (caption != null) {
                Text(text = caption, style = AirdropType.body3, color = Color.White.copy(alpha = 0.45f))
            }
        }
        Text(text = value, style = AirdropType.subtitle1, color = Color.White)
    }
}
