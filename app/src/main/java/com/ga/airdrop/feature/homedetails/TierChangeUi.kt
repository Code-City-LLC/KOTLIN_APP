package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
 * The tier-change layer over the untouched Customer Tier pager: the single
 * page-relative Figma CTA (Kemar 2026-07-08 — "Upgrade to <page>" above the
 * customer's tier, "Your Tier" breakdown on their own page, activation copy
 * on Inactive, hidden below/preview — never a downgrade sign) plus the
 * sheets it opens. The backend validates and applies every change.
 */
@Composable
internal fun TierChangeOverlay(
    visibleTier: TierPage,
    relation: TierRelation,
    userTierIndex: Int?,
    state: GoldPriorityUiState,
    onRequestChange: (String) -> Unit,
    onDismissError: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPage = userTierIndex?.let { tierPages.getOrNull(it) }
    var pending by remember { mutableStateOf<PendingTierChange?>(null) }
    var showBreakdown by remember { mutableStateOf(false) }

    // Backend catalogue rows when present, else the page's own copy.
    fun benefitsFor(tier: TierPage): List<String> =
        tier.apiCode?.let { state.benefitsByCode[it] }?.takeIf { it.isNotEmpty() } ?: tier.benefits

    val label = when (relation) {
        TierRelation.Current -> "Your Tier"
        TierRelation.Upgrade -> "Upgrade to ${visibleTier.name}"
        TierRelation.Activation -> "Ship a package now to activate your account"
        TierRelation.Downgrade, TierRelation.Preview -> null
    }
    if (label != null) {
        TierCtaButton(
            label = label,
            onClick = {
                when (relation) {
                    TierRelation.Activation -> onActivate()
                    TierRelation.Current -> showBreakdown = true
                    TierRelation.Upgrade -> if (visibleTier.apiCode != null) {
                        pending = PendingTierChange(visibleTier, currentPage, isUpgrade = true)
                    }
                    else -> Unit
                }
            },
            modifier = modifier,
        )
    }

    if (showBreakdown && currentPage != null) {
        // Kemar 2026-07-11: the breakdown carries the one sanctioned
        // downgrade path. Adjacent CODED tiers only (skips Inactive/Corporate).
        val up = if (state.canChange && userTierIndex != null) adjacentCodedTier(userTierIndex, -1) else null
        val down = if (state.canChange && userTierIndex != null) adjacentCodedTier(userTierIndex, +1) else null
        YourTierSheet(
            tier = currentPage,
            benefits = benefitsFor(currentPage),
            upgradeTarget = up,
            downgradeTarget = down,
            onUpgrade = {
                showBreakdown = false
                up?.let { pending = PendingTierChange(it, currentPage, isUpgrade = true) }
            },
            onDowngrade = {
                showBreakdown = false
                down?.let { pending = PendingTierChange(it, currentPage, isUpgrade = false) }
            },
            onDismiss = { showBreakdown = false },
        )
    }

    pending?.let { change ->
        TierChangeSheet(
            change = change,
            benefits = benefitsFor(if (change.isUpgrade) change.target else (change.current ?: change.target)),
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

/**
 * Figma "Function Buttons Desktop" — 316×50, #FF783E→#F15114 gradient,
 * radius 10, Cairo SemiBold 16, horizontally centered (never full-width).
 */
@Composable
private fun TierCtaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(bottom = 12.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 316.dp, height = 50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFF783E), Color(0xFFF15114))))
                .clickable(onClick = onClick)
                .testTag("tier-change-cta"),
            contentAlignment = Alignment.Center,
        ) {
            CtaShrinkText(
                label = label,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/** One-line CTA label that steps its font down until it fits (Swift 316×50 component). */
@Composable
private fun CtaShrinkText(label: String, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val fontSize = remember(label, maxWidthPx, textMeasurer) {
            if (maxWidthPx <= 0) {
                16.sp
            } else {
                (16 downTo 11).firstOrNull { candidate ->
                    !textMeasurer.measure(
                        text = AnnotatedString(label),
                        style = AirdropType.button.copy(fontSize = candidate.sp),
                        maxLines = 1,
                        constraints = Constraints(maxWidth = maxWidthPx),
                    ).hasVisualOverflow
                }?.sp ?: 11.sp
            }
        }
        Text(
            text = label,
            style = AirdropType.button.copy(fontSize = fontSize),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierChangeSheet(
    change: PendingTierChange,
    benefits: List<String>,
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
                TierBenefitList(benefits, tint = Color.White, tickColor = tierTickColor(target))
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
                    benefits,
                    tint = Color.White.copy(alpha = 0.85f),
                    tickColor = tierTickColor(change.current ?: target),
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
                GlassPrimaryButton(
                    text = "Upgrade to ${target.name}",
                    loading = changing,
                    onClick = onConfirm,
                    fill = Color.White,
                    content = target.gradientBottom,
                )
                Spacer(Modifier.height(10.dp))
                GlassSecondaryButton(text = "Maybe later", onClick = onDismiss, enabled = !changing)
            } else {
                GlassPrimaryButton(
                    text = "Keep ${change.current?.name ?: "my tier"}",
                    loading = false,
                    onClick = onDismiss,
                    fill = Color.White,
                    content = Color(0xFF1B1B1B),
                    enabled = !changing,
                )
                Spacer(Modifier.height(10.dp))
                GlassSecondaryButton(
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
private fun TierBenefitList(
    benefits: List<String>,
    tint: Color,
    tickColor: Color,
    lost: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        benefits.forEach { benefit ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (lost) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_x),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color(0xFFF7A072)),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                } else {
                    // Kemar 2026-07-11 tick rule: checks carry the tier's tint.
                    TierTick(
                        color = tickColor,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(22.dp),
                    )
                }
                Text(text = benefit, style = AirdropType.body2, color = tint, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Filled action button of the dark glass sheets (tier change, package
 * insurance). Shared so both flows keep the identical Swift-aligned recipe:
 * 14dp radius, 52dp min height, spinner-in-place while loading.
 */
@Composable
internal fun GlassPrimaryButton(
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
            .testTag("glass-primary-button"),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = content, strokeWidth = 2.dp)
        } else {
            Text(text = text, style = AirdropType.button, color = content)
        }
    }
}

/** Outlined companion of [GlassPrimaryButton]; destructive = coral accent. */
@Composable
internal fun GlassSecondaryButton(
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
            .testTag("glass-secondary-button"),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = accent, strokeWidth = 2.dp)
        } else {
            Text(text = text, style = AirdropType.button.copy(fontWeight = FontWeight.SemiBold), color = accent)
        }
    }
}
