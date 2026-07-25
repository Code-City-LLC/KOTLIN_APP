package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.CifTablePalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * CIF Value explainer — Figma 40001761:29633 (bottom-sheet 40001761:29679).
 *
 * ⚠️ RULE (Kemar 2026-07-25): **every** "CIF Value" information affordance in
 * the app opens THIS sheet — never a one-line alert, never a bespoke copy.
 * It is also reachable from inside [CustomsNoticeSheet], because that notice
 * explains duties in terms of CIF. Add new CIF entry points by calling this
 * composable; do not fork it.
 *
 * Layout is Figma-exact: 100x6 grabber, 20dp side inset, Title (h5) at the top,
 * the shared component description + bullets, then the services table —
 * a 44dp header row over 32dp data rows, with the Total row in
 * Secondary/Blue (#e1f6ff fill, #0872a1 label).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CifValueSheet(
    rows: List<CifRow>,
    exchangeRate: Double,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.gray150,
        shape = RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.sm)
                    .size(width = 100.dp, height = 6.dp)
                    .background(colors.gray300, RoundedCornerShape(Radius.full))
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag(CifSheetTags.SHEET)
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.sm, bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = CustomsNoticeContent.CIF_TITLE,
                style = AirdropType.h5,
                color = colors.textDarkTitle,
            )
            // Same wording as the Customs Notice — one source of truth so the
            // two sheets can never drift apart.
            Text(
                text = CustomsNoticeContent.COMPONENTS_INTRO,
                style = AirdropType.body2,
                color = colors.textDarkTitle,
            )
            CustomsNoticeContent.bullets.forEach { (label, rest) ->
                Row {
                    Text(
                        text = "•",
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label ") }
                            append(rest)
                        },
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                    )
                }
            }

            CifServicesTable(rows = rows, exchangeRate = exchangeRate)
        }
    }
}

/** One line of the CIF breakdown. [usd] is null when the server omits it. */
internal data class CifRow(val label: String, val usd: Double?)

@Composable
private fun CifServicesTable(rows: List<CifRow>, exchangeRate: Double) {
    val colors = AirdropTheme.colors
    val shape = RoundedCornerShape(Radius.s)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm)
            .clip(shape)
            .border(1.dp, colors.iconShape, shape)
            .testTag(CifSheetTags.TABLE),
    ) {
        // Header — 44dp, gray150 fill, SubTitle 2.
        CifTableRow(
            left = "Services",
            right = "USD / JMD",
            height = 44.dp,
            background = colors.gray150,
            textColor = colors.textDarkTitle,
            style = AirdropType.subtitle2,
        )
        HairlineDivider()
        rows.forEachIndexed { index, row ->
            CifTableRow(
                left = row.label,
                right = formatUsdJmd(row.usd, exchangeRate),
                height = 32.dp,
                background = colors.gray100,
                textColor = colors.textDarkTitle,
                style = AirdropType.body3,
                testTag = CifSheetTags.row(row.label),
            )
            if (index != rows.lastIndex) HairlineDivider()
        }
        // Total — Figma Secondary/Blue: #e1f6ff fill, #0872a1 label.
        if (rows.isNotEmpty()) {
            HairlineDivider()
            // Fail closed: a CIF total is only meaningful when EVERY component
            // is known. Summing the rows we happen to have would understate the
            // landed cost the customer's duty is assessed on, so a partial
            // breakdown shows an em-dash instead of a confidently wrong number.
            val total = if (rows.any { it.usd == null }) null else rows.sumOf { it.usd ?: 0.0 }
            CifTableRow(
                left = "Total",
                right = formatUsdJmd(total, exchangeRate),
                height = 32.dp,
                background = CifTablePalette.TotalFill,
                textColor = CifTablePalette.TotalLabel,
                style = AirdropType.subtitle2,
                testTag = CifSheetTags.TOTAL,
            )
        }
    }
}

@Composable
private fun HairlineDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AirdropTheme.colors.iconShape)
    )
}

@Composable
private fun CifTableRow(
    left: String,
    right: String,
    height: androidx.compose.ui.unit.Dp,
    background: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle,
    testTag: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(background)
            .padding(horizontal = Spacing.sm)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = left,
            style = style,
            color = textColor,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = right,
            style = style,
            color = textColor,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
    }
}

/** "1.00 / 161.00" — an em-dash when the server did not supply the component. */
internal fun formatUsdJmd(usd: Double?, exchangeRate: Double): String =
    if (usd == null) "—" else "${ShipmentsFormat.money(usd)} / ${ShipmentsFormat.money(usd * exchangeRate)}"

internal object CifSheetTags {
    const val SHEET = "cif-value-sheet"
    const val TABLE = "cif-value-table"
    const val TOTAL = "cif-value-total"
    fun row(label: String) = "cif-value-row-" + label.lowercase().replace(" ", "-")
}
