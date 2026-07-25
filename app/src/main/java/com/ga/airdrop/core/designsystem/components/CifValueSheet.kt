package com.ga.airdrop.core.designsystem.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.util.Locale

/**
 * CIF Value explainer — Figma 40001761:29633 (bottom-sheet 40001761:29679).
 *
 * ⚠️ RULE (Kemar 2026-07-25): **every** "CIF Value" information affordance in
 * the app opens THIS sheet — never a one-line alert, never a bespoke copy.
 * That is why it lives in the design system rather than in one feature: the
 * app had three CIF explainers (Package Details, Payment Package Details,
 * Calculator/Government Charges) that had already drifted apart in copy,
 * row striping and number formatting. Add new CIF entry points by calling
 * this composable; do not fork it.
 *
 * It is also reachable from inside the Customs Notice, because that notice
 * explains duties in terms of CIF.
 *
 * Layout is Figma-exact: 100x6 grabber, 20dp side inset, Title (h5), the
 * component description + bullets, then the services table — a 44dp header
 * row over 32dp data rows, with the Total row in Secondary/Blue
 * (BlueAccentTertiary4 fill, BlueAccentTertiary1 label).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CifValueSheet(
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
                text = CifCopy.TITLE,
                style = AirdropType.h5,
                color = colors.textDarkTitle,
            )
            Text(
                text = CifCopy.COMPONENTS_INTRO,
                style = AirdropType.body2,
                color = colors.textDarkTitle,
            )
            CifCopy.bullets.forEach { (label, rest) ->
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
data class CifRow(val label: String, val usd: Double?)

/**
 * The CIF explainer copy, shared with the Customs Notice so the two sheets
 * cannot drift. Verbatim from Figma 40001761:29633.
 */
object CifCopy {
    const val TITLE = "CIF Value"

    const val COMPONENTS_INTRO = "The CIF value represents the total landed cost of an item " +
        "and is made up of three key components:"

    val bullets = listOf(
        "Cost:" to "The item's purchase price, declared value, or invoice amount.",
        "Insurance:" to "The cost of insuring the item during transport.",
        "Freight:" to "The shipping and handling cost to the destination port.",
    )
}

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
        // Total — Figma Secondary/Blue.
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
                background = BrandPalette.BlueAccentTertiary4,
                textColor = BrandPalette.BlueAccentTertiary1,
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
    height: Dp,
    background: Color,
    textColor: Color,
    style: TextStyle,
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

/**
 * "1.00 / 161.00" — an em-dash when the server did not supply the component.
 * Grouped to 2 decimals, matching ShipmentsFormat.money and the calculator's
 * formatDecimal (both "%,.2f"), so the same figure reads identically wherever
 * the sheet is opened from.
 */
fun formatUsdJmd(usd: Double?, exchangeRate: Double): String =
    if (usd == null) {
        "—"
    } else {
        String.format(Locale.US, "%,.2f / %,.2f", usd, usd * exchangeRate)
    }

object CifSheetTags {
    const val SHEET = "cif-value-sheet"
    const val TABLE = "cif-value-table"
    const val TOTAL = "cif-value-total"
    fun row(label: String) = "cif-value-row-" + label.lowercase().replace(" ", "-")
}
