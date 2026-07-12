package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.core.designsystem.theme.AirdropType
import kotlinx.coroutines.launch

/**
 * "Your Tier" benefits breakdown — Swift YourTierBreakdownSheetViewController
 * (Kemar 2026-07-11: tapping the "Your Tier" CTA opens what the customer is
 * getting on their current tier, with explicit Upgrade AND Downgrade options
 * that hand off to the confirmation sheet — the sheet is the one sanctioned
 * downgrade path; the pager stays upsell-only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YourTierSheet(
    tier: TierPage,
    title: String,
    rows: TierBenefitRows,
    /** Server-offer target names — null hides the action (no dead controls). */
    upgradeTitle: String?,
    downgradeTitle: String?,
    onUpgrade: () -> Unit,
    onDowngrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun handOffAfterHide(onSelected: () -> Unit) {
        scope.launch {
            sheetState.hide()
            onSelected()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tier.gradientBottom,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
        modifier = Modifier.testTag("your-tier-sheet"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Current tier's gradient card + 30% scrim (Swift card).
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(tier.gradientTop, tier.gradientBottom),
                            start = Offset(size.width * 0.64f, 0f),
                            end = Offset(size.width * 0.36f, size.height),
                        )
                    )
                    drawRect(Color.Black.copy(alpha = 0.30f))
                },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                // Grabber (dragHandle is null so we draw Swift's own).
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 14.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = 0.45f)),
                )

                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(tier.glyphRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                    Text(
                        text = "YOUR TIER",
                        style = AirdropType.button.copy(fontSize = 13.sp, letterSpacing = 1.2.sp),
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = title,
                        style = AirdropType.h5.copy(fontSize = 24.sp, lineHeight = 30.sp),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))

                    // Frosted "What you're getting" panel (Swift glassPanel:
                    // white 10%, r16, 1dp white-18% border, 14dp padding).
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "What you're getting",
                            style = AirdropType.button.copy(fontSize = 15.sp),
                            color = Color.White,
                        )
                        when (rows) {
                            is TierBenefitRows.Rows -> rows.rows.forEach { benefit ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TierTick(
                                        color = tierTickColor(tier),
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(17.dp),
                                    )
                                    Text(
                                        text = benefit,
                                        style = AirdropType.body2,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            // Coded tiers never fall back to static copy (#41.2).
                            TierBenefitRows.Loading -> Text(
                                text = "Loading benefits…",
                                style = AirdropType.body2,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                            TierBenefitRows.Failed -> Text(
                                text = "Couldn't load your benefits — retry from the tier page.",
                                style = AirdropType.body2,
                                color = Color.White,
                            )
                        }
                    }

                    Text(
                        text = "Tier changes are applied by AirDrop and take effect " +
                            "immediately. Any open shipment quote must be refreshed.",
                        style = AirdropType.body2.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Actions: Upgrade (white primary), Downgrade (quiet frosted), Close.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    upgradeTitle?.let { up ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .clickable { handOffAfterHide(onUpgrade) }
                                .testTag("your-tier-upgrade"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Upgrade to $up",
                                style = AirdropType.button.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1A1A1A),
                            )
                        }
                    }
                    downgradeTitle?.let { down ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable { handOffAfterHide(onDowngrade) }
                                .testTag("your-tier-downgrade"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Downgrade to $down",
                                style = AirdropType.button.copy(fontSize = 15.sp),
                                color = Color.White,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .testTag("your-tier-close"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Close",
                            style = AirdropType.button.copy(fontSize = 15.sp),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
