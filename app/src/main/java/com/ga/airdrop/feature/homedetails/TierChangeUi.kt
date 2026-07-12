package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * A change the customer is about to confirm. The SERVER offer is the target —
 * [visual] only lends gradient/glyph when a page matches the offer's code.
 */
private data class PendingTierChange(
    val offer: TierOffer,
    val visual: TierPage,
    val current: TierPage?,
    val isUpgrade: Boolean,
)

/**
 * The tier-change layer over the pager: the single frosted CTA plus the sheets
 * it opens. The server's available_changes offer owns CTA visibility, label
 * and target (NavyCave #21359 blockers 1/2, CoralCove #21328); page relation
 * only decides the Current and Activation presentations. A failed current-tier
 * GET shows a Retry pill instead of controls.
 */
@Composable
internal fun TierChangeOverlay(
    visibleTier: TierPage,
    relation: TierRelation,
    userTierIndex: Int?,
    state: GoldPriorityUiState,
    onRequestChange: (String) -> Unit,
    onRetryConfirmation: () -> Unit,
    onRetryCurrentTier: () -> Unit,
    onDismissError: () -> Unit,
    onConsumeSuccess: () -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPage = userTierIndex?.let { tierPages.getOrNull(it) }
    var pending by remember { mutableStateOf<PendingTierChange?>(null) }
    var showBreakdown by remember { mutableStateOf(false) }

    // Current-tier GET failed: prior state retained, the only control is Retry.
    if (state.tierLoadFailed && !state.tierConfirmed) {
        TierCtaButton(
            label = "Couldn't confirm your tier — Retry",
            onClick = onRetryCurrentTier,
            modifier = modifier.testTag("tier-current-retry"),
        )
        return
    }

    fun startChange(offer: TierOffer) {
        pending = PendingTierChange(
            offer = offer,
            visual = pageForOffer(offer) ?: currentPage ?: visibleTier,
            current = currentPage,
            isUpgrade = offer.direction == "upgrade",
        )
    }

    // The server offer for the visible page — page order never decides.
    val pageOffer = upgradeOfferFor(state, visibleTier)
    val label = when {
        relation == TierRelation.Current -> "Your Tier"
        relation == TierRelation.Activation -> "Ship a package now to activate your account"
        pageOffer != null -> "Upgrade to ${offerTitle(pageOffer, state)}"
        else -> null
    }
    if (label != null) {
        TierCtaButton(
            label = label,
            onClick = {
                when {
                    relation == TierRelation.Activation -> onActivate()
                    relation == TierRelation.Current -> showBreakdown = true
                    pageOffer != null -> startChange(pageOffer)
                }
            },
            modifier = modifier,
        )
    }

    if (showBreakdown && currentPage != null) {
        // The breakdown carries the one sanctioned downgrade path. Targets are
        // the server's nearest offered step in each direction — non-adjacent
        // offers surface exactly as sent.
        val up = breakdownUpgradeOffer(state)
        val down = breakdownDowngradeOffer(state)
        YourTierSheet(
            tier = currentPage,
            title = tierTitleFor(currentPage, state),
            rows = benefitRowsFor(currentPage, state.benefitRowsByCode, state.catalogStatus),
            upgradeTitle = up?.let { offerTitle(it, state) },
            downgradeTitle = down?.let { offerTitle(it, state) },
            onUpgrade = {
                showBreakdown = false
                up?.let(::startChange)
            },
            onDowngrade = {
                showBreakdown = false
                down?.let(::startChange)
            },
            onDismiss = { showBreakdown = false },
        )
    }

    pending?.let { change ->
        TierChangeSheet(
            change = change,
            state = state,
            onConfirm = { onRequestChange(change.offer.code) },
            onRetryConfirmation = onRetryConfirmation,
            onDismiss = {
                if (state.changingToCode == null) {
                    pending = null
                    onDismissError()
                    if (state.changeSuccessName != null) onConsumeSuccess()
                }
            },
        )
    }
}

/**
 * The single tier CTA — canonical Swift frosted glass (white 7% fill, radius
 * 12, 1dp white-18% border, SemiBold 17 shrink-to-fit), 316×50 centered.
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
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
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

/** One-line CTA label — SemiBold 17 stepping down to the 0.75 floor (≈13). */
@Composable
private fun CtaShrinkText(label: String, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val fontSize = remember(label, maxWidthPx, textMeasurer) {
            if (maxWidthPx <= 0) {
                17.sp
            } else {
                (17 downTo 13).firstOrNull { candidate ->
                    !textMeasurer.measure(
                        text = AnnotatedString(label),
                        style = AirdropType.button.copy(fontSize = candidate.sp),
                        maxLines = 1,
                        constraints = Constraints(maxWidth = maxWidthPx),
                    ).hasVisualOverflow
                }?.sp ?: 13.sp
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

/**
 * Tier change confirmation — Swift TierChangeSheetViewController: bottom card
 * on the TARGET tier's gradient + 30% scrim, 64dp glyph, bold-24 title,
 * frosted benefit panels ("You'll lose" = current-minus-target server rows on
 * a downgrade), safe-primary action hierarchy, and the in-sheet Welcome/Done
 * success state (no toast, no banner).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierChangeSheet(
    change: PendingTierChange,
    state: GoldPriorityUiState,
    onConfirm: () -> Unit,
    onRetryConfirmation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visual = change.visual
    val changing = state.changingToCode != null
    val success = state.changeSuccessName

    ModalBottomSheet(
        onDismissRequest = { if (!changing) onDismiss() },
        sheetState = sheetState,
        containerColor = visual.gradientBottom,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
        modifier = Modifier.testTag("tier-change-sheet"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .testTag("tier-change-sheet-content")
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(visual.gradientTop, visual.gradientBottom),
                            start = Offset(size.width * 0.64f, 0f),
                            end = Offset(size.width * 0.36f, size.height),
                        )
                    )
                    // Same restrained dark wash the pager uses so white text
                    // reads on bright gradients without flattening dark ones.
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
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 14.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = 0.45f)),
                )

                if (success != null) {
                    ChangeSuccessContent(name = success, onDone = onDismiss)
                } else {
                    ChangeConfirmContent(
                        change = change,
                        state = state,
                        changing = changing,
                        onConfirm = onConfirm,
                        onRetryConfirmation = onRetryConfirmation,
                        onDismiss = onDismiss,
                        // Give the body a finite viewport so its verticalScroll
                        // owner can expose long downgrade actions on compact
                        // 812dp screens instead of measuring to full content.
                        modifier = Modifier.heightIn(max = 680.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangeConfirmContent(
    change: PendingTierChange,
    state: GoldPriorityUiState,
    changing: Boolean,
    onConfirm: () -> Unit,
    onRetryConfirmation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetTitle = offerTitle(change.offer, state)
    val targetRows = state.benefitRowsByCode[change.offer.code]?.takeIf { it.isNotEmpty() }
    val currentRows = change.current?.apiCode?.let { state.benefitRowsByCode[it] }
    val tick = tierTickColor(change.visual)

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(change.visual.glyphRes),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
        )
        Text(
            text = if (change.isUpgrade) "Upgrade to $targetTitle" else "Downgrade to $targetTitle?",
            style = AirdropType.h5.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (change.isUpgrade) {
                "Changes apply immediately. Here's what you'll get:"
            } else {
                "Are you sure? Here's what you'd be giving up:"
            },
            style = AirdropType.body2,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (targetRows == null) {
            // Server rows unavailable — same fail-closed surface as the page.
            Text(
                text = "Couldn't load this tier's benefits — retry from the tier page.",
                style = AirdropType.body2,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (change.isUpgrade) {
            GlassBenefitPanel(header = "What you'll get", rows = targetRows, gained = true, tickColor = tick)
        } else {
            // Benefits the customer LOSES — server rows present on the current
            // tier but absent from the target (never "everything").
            val lost = currentRows.orEmpty().filter { it !in targetRows }
            if (lost.isNotEmpty()) {
                GlassBenefitPanel(header = "You'll lose", rows = lost, gained = false, tickColor = tick)
            }
            GlassBenefitPanel(
                header = "What you'll have on $targetTitle",
                rows = targetRows,
                gained = true,
                tickColor = tick,
            )
        }

        if (state.changeError != null) {
            Text(
                text = state.changeError,
                style = AirdropType.body2,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(10.dp),
            )
        }
        if (state.awaitingConfirmation) {
            SheetQuietButton(
                text = "Retry confirmation",
                onClick = onRetryConfirmation,
                enabled = !changing,
                testTag = "tier-sheet-retry-confirmation",
            )
        }

        Text(
            text = "Tier changes are applied by AirDrop and take effect " +
                "immediately. Any open shipment quote must be refreshed.",
            style = AirdropType.body2.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tier-change-actions"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.awaitingConfirmation) {
                // The last PATCH is unconfirmed: no new PATCH action may exist
                // (NavyCave blocker 3) — only Retry (above) and a safe dismiss.
                SheetTextButton(text = "Close", onClick = onDismiss)
            } else if (change.isUpgrade) {
                // Primary = commit the upgrade.
                SheetPrimaryButton(
                    text = "Upgrade Now",
                    loading = changing,
                    onClick = onConfirm,
                    testTag = "tier-sheet-confirm",
                )
                SheetTextButton(text = "Not Now", onClick = onDismiss, enabled = !changing)
            } else {
                // Downgrade: the SAFE choice is primary; the destructive commit
                // is a quieter frosted pill underneath.
                SheetPrimaryButton(
                    text = "Keep My Benefits",
                    loading = false,
                    onClick = onDismiss,
                    enabled = !changing,
                    testTag = "tier-sheet-cancel",
                )
                SheetQuietButton(
                    text = "Downgrade Anyway",
                    onClick = onConfirm,
                    enabled = !changing,
                    loading = changing,
                    testTag = "tier-sheet-confirm",
                )
            }
        }
    }
}

/** In-sheet success — Swift showSuccess: check, "Welcome to X", Done. The
 * success check stays WHITE (Kemar #21146 tints benefit ticks only). */
@Composable
private fun ChangeSuccessContent(name: String, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TierTick(color = Color.White, modifier = Modifier.size(56.dp))
        Text(
            text = "Welcome to $name",
            style = AirdropType.h5.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Your tier has been updated.",
            style = AirdropType.body2,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(200.dp)
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .clickable(onClick = onDone)
                .testTag("tier-sheet-done"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Done",
                style = AirdropType.button.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1A1A1A),
            )
        }
    }
}

/** Frosted-glass section (white 10%, r16, 1dp white-18% border, 14dp pad). */
@Composable
private fun GlassBenefitPanel(
    header: String,
    rows: List<String>,
    gained: Boolean,
    tickColor: Color,
) {
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
            text = header,
            style = AirdropType.button.copy(fontSize = 15.sp),
            color = Color.White,
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (gained) {
                    TierTick(
                        color = tickColor,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(17.dp),
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_x),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(13.dp),
                    )
                }
                Text(
                    text = row,
                    style = AirdropType.body2,
                    color = if (gained) Color.White else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SheetPrimaryButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.6f))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color(0xFF1A1A1A), strokeWidth = 2.dp)
        } else {
            Text(
                text = text,
                style = AirdropType.button.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1A1A1A),
            )
        }
    }
}

@Composable
private fun SheetQuietButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    testTag: String = "tier-sheet-quiet",
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text = text, style = AirdropType.button, color = Color.White)
        }
    }
}

@Composable
private fun SheetTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("tier-sheet-cancel-text"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AirdropType.button.copy(fontSize = 15.sp),
            color = Color.White,
        )
    }
}
