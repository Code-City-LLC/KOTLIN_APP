package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.core.designsystem.components.CifCopy
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Customs Notice — Swift FigmaCustomsNoticeViewController, a pixel port of
 * Figma "Customs Notice" (node 40008798:29642). Opened from the Customs Duty
 * charge row in Package Details (Swift makeCustomsDutyChargesButton,
 * figma.packageDetails.customsDutyInfo → present(over:) as a glass sheet).
 * Copy is verbatim from the Swift/Figma source.
 */
internal object CustomsNoticeContent {
    const val TITLE = "Customs Notice"
    val CIF_TITLE = CifCopy.TITLE

    // The lead is split around the CIF phrase so that phrase can be an inline
    // tap target (Kemar: "even inside of that, if they click on CIF value, it
    // needs to generate that page") WITHOUT adding any element Figma does not
    // have — the rendered paragraph is still character-identical to the design.
    const val LEAD_PREFIX = "Jamaica Customs calculates import duties not on the declared " +
        "value or invoice amount, but on the "
    const val LEAD_CIF_PHRASE = "CIF (Cost, Insurance, and Freight)"
    const val LEAD_SUFFIX = " value of the shipment."
    const val LEAD = LEAD_PREFIX + LEAD_CIF_PHRASE + LEAD_SUFFIX

    // Shared with the CIF sheet — one source of truth so the notice and the
    // CIF explainer can never drift apart.
    val COMPONENTS_INTRO = CifCopy.COMPONENTS_INTRO
    val bullets = CifCopy.bullets

    val closingParagraphs = listOf(
        "Customs duties are applied in accordance with the Jamaica Customs Tariff, which " +
            "outlines the specific duty rate applicable to each item category according to " +
            "its tariff code (HS Code).",
        "Items with a declared or invoice value exceeding $100 USD may attract customs " +
            "duties ranging from 20% to 50% of the CIF value, depending on the item " +
            "classification and corresponding tariff rate.",
        "All duties and assessments are determined by the Jamaica Customs Agency under " +
            "the relevant customs laws and tariff schedules.",
    )
}

/**
 * Swift FigmaPackageDetailsViewController.isCustomsDutyCharge (:1621): a
 * charge row earns the info affordance when its name mentions both
 * "custom" and "duty" (case-insensitive).
 */
internal fun isCustomsDutyCharge(name: String): Boolean {
    val normalized = name.lowercase()
    return normalized.contains("custom") && normalized.contains("duty")
}

/** Test tag for the inline "CIF (Cost, Insurance, and Freight)" tap target. */
internal const val CIF_LINK_TAG = "customs-notice-cif-link"

/** Modal glass sheet matching the CIF sheet idiom (grabber, gray150, Radius.s). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CustomsNoticeSheet(
    onDismiss: () -> Unit,
    onCifValue: (() -> Unit)? = null,
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
                .testTag("customs-notice-sheet")
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.sm, bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = CustomsNoticeContent.TITLE,
                style = AirdropType.h5,
                color = colors.textDarkTitle,
            )
            // Figma renders one paragraph. When a CIF handler is supplied the
            // "CIF (Cost, Insurance, and Freight)" phrase inside it becomes an
            // inline tap target — same glyphs, same wrap, just tappable.
            Text(
                text = buildAnnotatedString {
                    append(CustomsNoticeContent.LEAD_PREFIX)
                    if (onCifValue != null) {
                        withLink(LinkAnnotation.Clickable(tag = CIF_LINK_TAG) { onCifValue() }) {
                            withStyle(SpanStyle(color = colors.orangeMain, fontWeight = FontWeight.Bold)) {
                                append(CustomsNoticeContent.LEAD_CIF_PHRASE)
                            }
                        }
                    } else {
                        append(CustomsNoticeContent.LEAD_CIF_PHRASE)
                    }
                    append(CustomsNoticeContent.LEAD_SUFFIX)
                },
                style = AirdropType.body2,
                color = colors.textDarkTitle,
                modifier = Modifier.testTag(CIF_LINK_TAG),
            )
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
            CustomsNoticeContent.closingParagraphs.forEach { paragraph ->
                Text(
                    text = paragraph,
                    style = AirdropType.body2,
                    color = colors.textDarkTitle,
                )
            }
        }
    }
}
