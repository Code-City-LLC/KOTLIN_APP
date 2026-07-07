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
        visibleTier = visibleTier,
        isCurrent = visibleCode != null && visibleCode.equals(state.currentTierCode, ignoreCase = true),
        canOfferChange = canOfferChange,
        isUpgrade = isUpgrade,
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
    visibleTier: TierPage,
    isCurrent: Boolean,
    canOfferChange: Boolean,
    isUpgrade: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing to offer on the current tier / presentational-only pages / when
    // the API disallows changes: the core page simply stands on its own.
    if (!isCurrent && !canOfferChange) return

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp)
            .navigationBarsPadding(),
    ) {
        if (isCurrent) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(10.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Your current tier",
                    style = AirdropType.button,
                    color = Color.White,
                )
            }
        } else {
            val label = if (isUpgrade) "Upgrade to ${visibleTier.name}" else "Switch to ${visibleTier.name}"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable(onClick = onClick)
                    .testTag("tier-change-cta")
                    .padding(vertical = 16.dp, horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = AirdropType.button,
                    color = visibleTier.gradientBottom,
                    textAlign = TextAlign.Center,
                )
            }
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
            // Gradient hero band in the target tier's colours.
            Box(
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
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_homedet_tier_badge),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (change.isUpgrade) "Upgrade to" else "Switch to",
                            style = AirdropType.body2,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            text = target.name,
                            style = AirdropType.h5.copy(fontSize = 24.sp, lineHeight = 30.sp),
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

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
                    text = "Switching to ${target.name} means giving up your " +
                        "${change.current?.name ?: "current"} benefits:",
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
