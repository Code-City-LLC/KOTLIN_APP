package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropType

/** A tier the customer could switch to, relative to the one they hold now. */
private data class PendingTierChange(
    val target: TierPage,
    val current: TierPage?,
    val isUpgrade: Boolean,
)

/**
 * The upgrade/downgrade layer that sits ON TOP of the untouched Customer Tier
 * pager: a bottom-pinned call-to-action for the visible tier, plus a beautiful
 * confirmation sheet. The backend validates and applies the change; this only
 * requests it. No tier is shown as switchable unless the API says can_change.
 */
@Composable
internal fun TierChangeOverlay(
    visibleTier: TierPage,
    state: GoldPriorityUiState,
    onRequestChange: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPage = remember(state.currentTierCode) {
        tierPages.firstOrNull { it.apiCode != null && it.apiCode == state.currentTierCode }
    }
    var pending by remember { mutableStateOf<PendingTierChange?>(null) }

    // Open the sheet only for a switchable, non-current tier the API allows.
    val visibleCode = visibleTier.apiCode
    val canOfferChange = state.canChange &&
        visibleCode != null &&
        state.currentTierCode != null &&
        !visibleCode.equals(state.currentTierCode, ignoreCase = true)

    val isUpgrade = when (state.directionByCode[visibleCode]?.lowercase()) {
        "upgrade" -> true
        "downgrade" -> false
        // Fallback when the API omitted a direction: compare lane rank.
        else -> visibleTier.laneRank > (currentPage?.laneRank ?: Int.MIN_VALUE)
    }

    TierChangeCtaBar(
        canOfferChange = canOfferChange,
        onClick = { pending = PendingTierChange(visibleTier, currentPage, isUpgrade) },
        modifier = modifier,
    )

    pending?.let { change ->
        TierChangeSheet(
            change = change,
            changing = state.changingToCode == change.target.apiCode,
            error = state.changeError,
            onConfirm = { change.target.apiCode?.let(onRequestChange) },
            onDismiss = {
                if (state.changingToCode == null) {
                    pending = null
                    onDismissError()
                }
            },
        )
    }

    // A completed change dismisses the sheet.
    if (state.justChangedToName != null && pending != null && state.changingToCode == null) {
        pending = null
    }
}

@Composable
private fun TierChangeCtaBar(
    canOfferChange: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The current tier and the presentational-only pages carry NO indicator
    // (Kemar 2026-07-07: the "your current tier" reminder duplicated the header
    // on almost every page). Only a switchable tier shows an action, and it's a
    // restrained translucent pill — Swift "Set as My Tier" parity — not a heavy
    // solid button, so the tier art stays the hero.
    if (!canOfferChange) return

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 22.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .testTag("tier-change-cta")
                .padding(vertical = 13.dp, horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Set as My Tier",
                style = AirdropType.button.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierChangeSheet(
    change: PendingTierChange,
    changing: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val target = change.target

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1B1B1B),
        dragHandle = null,
        modifier = Modifier.testTag("tier-change-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 28.dp)
                .navigationBarsPadding(),
        ) {
            // Grab handle (dragHandle is null so we draw our own, subtly).
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 16.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            )

            // Gradient hero in the target tier's colours. No badge — the generic
            // crystal repeats on every tier and adds nothing (Kemar 2026-07-07);
            // the tier's own gradient + name carry the identity.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(target.gradientTop, target.gradientBottom),
                            start = Offset(Float.POSITIVE_INFINITY, 0f),
                            end = Offset(0f, Float.POSITIVE_INFINITY),
                        ),
                    )
                    .padding(horizontal = 22.dp, vertical = 24.dp),
            ) {
                Text(
                    text = (if (change.isUpgrade) "UPGRADE TO" else "SWITCH TO"),
                    style = AirdropType.button.copy(fontSize = 12.sp, letterSpacing = 1.5.sp),
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = target.name,
                    style = AirdropType.h5.copy(fontSize = 28.sp, lineHeight = 34.sp),
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(22.dp))

            if (change.isUpgrade) {
                Text(
                    text = "What you'll unlock",
                    style = AirdropType.title1,
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                TierBenefitList(target.benefits, tint = Color.White)
            } else {
                // Downgrade: make the loss explicit before they commit.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF15114).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("!", style = AirdropType.title1, color = Color(0xFFF7A072))
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "Are you sure?",
                        style = AirdropType.title1,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    // Swift TierChangeSheet copy parity.
                    text = "Here's what you'd be giving up from your " +
                        "${change.current?.name ?: "current tier"}:",
                    style = AirdropType.body2,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                TierBenefitList(
                    (change.current ?: target).benefits,
                    tint = Color.White.copy(alpha = 0.85f),
                    lost = true,
                )
            }

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error,
                    style = AirdropType.body2,
                    color = Color(0xFFF17A72),
                )
            }

            Spacer(Modifier.height(24.dp))

            // Primary action. On an upgrade it is the confirm; on a downgrade
            // the safe "keep" choice is primary and the confirm is secondary.
            if (change.isUpgrade) {
                TierPrimaryButton(
                    text = "Upgrade to ${target.name}",
                    loading = changing,
                    onClick = onConfirm,
                    fill = Color.White,
                    content = target.gradientBottom,
                )
                Spacer(Modifier.height(10.dp))
                TierSecondaryButton(text = "Maybe later", onClick = onDismiss, enabled = !changing)
            } else {
                TierPrimaryButton(
                    text = "Keep ${change.current?.name ?: "my tier"}",
                    loading = false,
                    onClick = onDismiss,
                    fill = Color.White,
                    content = Color(0xFF1B1B1B),
                    enabled = !changing,
                )
                Spacer(Modifier.height(10.dp))
                TierSecondaryButton(
                    text = "Downgrade to ${target.name}",
                    onClick = onConfirm,
                    loading = changing,
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun TierBenefitList(benefits: List<String>, tint: Color, lost: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        benefits.forEach { benefit ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(if (lost) R.drawable.ic_x else R.drawable.ic_check),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(if (lost) Color(0xFFF7A072) else Color.White),
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(text = benefit, style = AirdropType.body2, color = tint, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TierPrimaryButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    fill: Color,
    content: Color,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) fill else fill.copy(alpha = 0.5f))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .testTag("tier-primary-button"),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = content, strokeWidth = 2.dp)
        } else {
            Text(text = text, style = AirdropType.button, color = content)
        }
    }
}

@Composable
private fun TierSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
) {
    val accent = if (destructive) Color(0xFFF17A72) else Color.White.copy(alpha = 0.85f)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .testTag("tier-secondary-button"),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = accent, strokeWidth = 2.dp)
        } else {
            Text(text = text, style = AirdropType.button.copy(fontWeight = FontWeight.SemiBold), color = accent)
        }
    }
}
