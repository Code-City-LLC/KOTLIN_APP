package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.core.designsystem.theme.AirdropType

/**
 * Swift TierChangeSheetViewController parity. This is presentation only: the
 * caller retains the server-offer gate and session-owned PATCH -> GET flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TierChangeSheet(
    target: TierPage,
    current: TierPage?,
    targetBenefits: List<String>?,
    currentBenefits: List<String>,
    isUpgrade: Boolean,
    phase: TierChangePhase,
    successName: String?,
    successMessage: String?,
    error: String?,
    onConfirm: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val working = phase == TierChangePhase.Working
    val latestWorking = rememberUpdatedState(working)
    var confirmationHeightPx by remember(target.id) { mutableIntStateOf(0) }
    val confirmationHeight = with(LocalDensity.current) { confirmationHeightPx.toDp() }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { next ->
            next != SheetValue.Hidden || !latestWorking.value
        },
    )

    ModalBottomSheet(
        onDismissRequest = { if (!working) onDismiss() },
        sheetState = sheetState,
        containerColor = target.gradientBottom,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !working,
        ),
        modifier = Modifier.testTag("tier-change-sheet"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .testTag("tier-change-sheet-content")
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(target.gradientTop, target.gradientBottom),
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
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 14.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = 0.45f))
                        .testTag("tier-change-grabber")
                )

                if (phase == TierChangePhase.Success) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (confirmationHeightPx > 0) {
                                    Modifier.height(confirmationHeight)
                                } else {
                                    Modifier.heightIn(min = 320.dp, max = 680.dp)
                                }
                            )
                            .testTag("tier-change-success-frame"),
                        contentAlignment = Alignment.Center,
                    ) {
                        TierChangeSuccess(
                            name = successName ?: target.name,
                            message = successMessage,
                            onDone = onDone,
                        )
                    }
                } else {
                    TierChangeConfirmation(
                        target = target,
                        current = current,
                        targetBenefits = targetBenefits,
                        currentBenefits = currentBenefits,
                        isUpgrade = isUpgrade,
                        working = working,
                        error = error.takeIf { phase == TierChangePhase.Error },
                        onConfirm = onConfirm,
                        onDismiss = onDismiss,
                        modifier = Modifier
                            .heightIn(max = 680.dp)
                            .onSizeChanged {
                                confirmationHeightPx = maxOf(confirmationHeightPx, it.height)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun TierChangeConfirmation(
    target: TierPage,
    current: TierPage?,
    targetBenefits: List<String>?,
    currentBenefits: List<String>,
    isUpgrade: Boolean,
    working: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownTargetBenefits = targetBenefits.orEmpty()
    val disclosureReady = shownTargetBenefits.isNotEmpty() &&
        (isUpgrade || currentBenefits.isNotEmpty())

    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("tier-change-scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(target.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.CenterHorizontally)
                    .testTag("tier-change-glyph"),
            )
            Text(
                text = if (isUpgrade) {
                    "Upgrade to ${target.name}"
                } else {
                    "Downgrade to ${target.name}?"
                },
                style = AirdropType.h5.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tier-change-title"),
            )
            Text(
                text = if (isUpgrade) {
                    "Changes apply immediately. Here's what you'll get:"
                } else {
                    "Are you sure? Here's what you'd be giving up:"
                },
                style = AirdropType.body2,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tier-change-subtitle"),
            )

            if (!disclosureReady) {
                Text(
                    text = "Tier benefits are unavailable.",
                    style = AirdropType.body2,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (isUpgrade) {
                TierBenefitPanel(
                    header = "What you'll get",
                    rows = shownTargetBenefits,
                    gained = true,
                    markColor = target.gradientBottom,
                    tag = "tier-change-benefits",
                )
            } else {
                val lost = currentBenefits.filterNot(shownTargetBenefits::contains)
                if (lost.isNotEmpty()) {
                    TierBenefitPanel(
                        header = "You'll lose",
                        rows = lost,
                        gained = false,
                        markColor = current?.gradientBottom ?: target.gradientBottom,
                        tag = "tier-change-lost-benefits",
                    )
                }
                TierBenefitPanel(
                    header = "What you'll have on ${target.name}",
                    rows = shownTargetBenefits,
                    gained = true,
                    markColor = target.gradientBottom,
                    tag = "tier-change-benefits",
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    style = AirdropType.body2,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(10.dp)
                        .testTag("tier-change-error"),
                )
            }

            Text(
                text = "Tier changes are applied by AirDrop and take effect immediately. " +
                    "Any open shipment quote must be refreshed.",
                style = AirdropType.body2.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tier-change-footnote"),
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("tier-change-actions"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isUpgrade) {
                TierSheetPrimaryButton(
                    label = "Upgrade Now",
                    loading = working,
                    enabled = disclosureReady && !working,
                    onClick = onConfirm,
                    tag = "tier-change-confirm",
                )
                TierSheetTextButton(
                    label = "Not Now",
                    enabled = !working,
                    onClick = onDismiss,
                    tag = "tier-change-cancel",
                )
            } else {
                TierSheetPrimaryButton(
                    label = "Keep My Benefits",
                    loading = false,
                    enabled = !working,
                    onClick = onDismiss,
                    tag = "tier-change-cancel",
                )
                TierSheetQuietButton(
                    label = "Downgrade Anyway",
                    loading = working,
                    enabled = disclosureReady && !working,
                    onClick = onConfirm,
                    tag = "tier-change-confirm",
                )
            }
        }
    }
}

@Composable
private fun TierChangeSuccess(
    name: String,
    message: String?,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("tier-change-success-content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TierSheetMark(
            gained = true,
            markColor = Color(0xFF1A1A1A),
            modifier = Modifier
                .size(56.dp)
                .testTag("tier-change-success-icon"),
        )
        Text(
            text = "Welcome to $name",
            style = AirdropType.h5.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message ?: "Your tier has been updated.",
            style = AirdropType.body2,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        TierSheetPrimaryButton(
            label = "Done",
            loading = false,
            enabled = true,
            onClick = onDone,
            tag = "tier-change-done",
            modifier = Modifier.width(200.dp),
        )
    }
}

@Composable
private fun TierBenefitPanel(
    header: String,
    rows: List<String>,
    gained: Boolean,
    markColor: Color,
    tag: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = header,
            style = AirdropType.button.copy(fontSize = 15.sp),
            color = Color.White,
        )
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TierSheetMark(
                    gained = gained,
                    markColor = markColor,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(17.dp),
                )
                Text(
                    text = row,
                    style = AirdropType.body2.copy(
                        textDecoration = if (gained) null else TextDecoration.LineThrough,
                    ),
                    color = if (gained) Color.White else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("$tag-row-$index"),
                )
            }
        }
    }
}

@Composable
private fun TierSheetMark(
    gained: Boolean,
    markColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clip(CircleShape)) {
        drawCircle(Color.White.copy(alpha = if (gained) 1f else 0.75f))
        val stroke = size.minDimension * 0.12f
        if (gained) {
            val check = Path().apply {
                moveTo(size.width * 0.27f, size.height * 0.52f)
                lineTo(size.width * 0.43f, size.height * 0.68f)
                lineTo(size.width * 0.73f, size.height * 0.35f)
            }
            drawPath(
                path = check,
                color = markColor,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            val inset = size.minDimension * 0.31f
            drawLine(
                color = markColor,
                start = Offset(inset, inset),
                end = Offset(size.width - inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = markColor,
                start = Offset(size.width - inset, inset),
                end = Offset(inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TierSheetPrimaryButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(22.dp)
                    .testTag("tier-change-spinner"),
                color = Color(0xFF1A1A1A),
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = label,
                style = AirdropType.button.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = Color(0xFF1A1A1A),
            )
        }
    }
}

@Composable
private fun TierSheetQuietButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(22.dp)
                    .testTag("tier-change-spinner"),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = label,
                style = AirdropType.button.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TierSheetTextButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AirdropType.button.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}
