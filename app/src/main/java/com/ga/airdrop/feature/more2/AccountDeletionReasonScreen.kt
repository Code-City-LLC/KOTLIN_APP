package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Account Deletion Reason — Figma node 40007388:27504 (+ confirmation modal
 * 40007462:64371), behavior from FigmaAccountDeletionReasonViewController:
 * 6 radio reasons, bottom "Delete Account" CTA (disabled until a reason is
 * picked), red-warning confirmation sheet, then POST /user/deactivate-account
 * and a full local logout.
 */
@Composable
fun AccountDeletionReasonScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onVerificationMissing: () -> Unit = onBack,
    viewModel: AccountDeletionReasonViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        if (!viewModel.hasVerifiedCredentials()) onVerificationMissing()
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .imePadding()
    ) {
        More2InnerHeader(title = "Account Deletion", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Why do you want to delete your account?",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
            )
            Spacer(Modifier.height(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DELETION_REASONS.forEach { reason ->
                    ReasonRow(
                        reason = reason,
                        selected = state.selectedReason == reason,
                        onSelect = { viewModel.selectReason(reason) },
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        More2BottomBar {
            More2PrimaryButton(
                text = "Delete Account",
                onClick = viewModel::requestDelete,
                enabled = state.selectedReason != null && !state.deleting,
                loading = state.deleting,
            )
        }
    }

    if (state.showConfirmModal) {
        DeletionConfirmationModal(
            onCancel = viewModel::dismissModal,
            onConfirm = { viewModel.confirmDelete(context) },
        )
    }
    state.error?.let { message ->
        More2Alert(title = "Error", message = message, onDismiss = viewModel::dismissError)
    }
}

/** RN optionItem: radius 15, 1dp border, ≥56dp, 18dp radio ring + 8dp dot. */
@Composable
private fun ReasonRow(reason: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = AirdropTheme.colors
    val borderColor = if (selected) BrandPalette.OrangeMain else colors.iconShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, borderColor, RoundedCornerShape(Radius.s))
            .clickable(onClick = onSelect)
            .padding(horizontal = Spacing.md, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BrandPalette.OrangeMain)
                )
            }
        }
        Text(
            text = reason,
            style = AirdropType.body2,
            color = colors.textDarkTitle,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Bottom-sheet confirmation — Swift-precedence runtime sheet: no drag handle,
 * red warning halo, H5 question, Body2 description, full-bleed divider, Cancel
 * (outlined #D71111) + Delete Account (solid #D71111) side by side.
 */
@Composable
private fun DeletionConfirmationModal(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val colors = AirdropTheme.colors
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account-deletion-confirm-sheet")
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(colors.gray100)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                DeletionWarningGraphic()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Are you sure you want to delete your account?",
                    style = AirdropType.h5,
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "This action is permanent and cannot be undone. Your account " +
                        "access will end and your account-level data will be deleted or " +
                        "anonymized.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.iconShape)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    // Cancel: gray100 bg, 1dp #D71111 border, #DC2626 text.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(colors.gray100)
                            .border(1.dp, Color(0xFFD71111), RoundedCornerShape(Radius.xs))
                            .clickable(onClick = onCancel),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", style = AirdropType.button, color = Color(0xFFDC2626))
                    }
                    // Delete: solid #D71111, white text.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(Color(0xFFD71111))
                            .clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Delete Account",
                            style = AirdropType.button,
                            color = BrandPalette.White,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Port of the RN WarningIcon SVG (viewBox 294x220): 3 concentric red halos
 * (5%/10% alpha, gradient core), white inner ring, exclamation triangle.
 */
@Composable
private fun DeletionWarningGraphic() {
    val red = Color(0xFFFF383C)
    val redDark = Color(0xFFE62327)
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account-deletion-confirm-warning")
            .height(225.dp)
    ) {
        val scale = minOf(size.width / 294f, size.height / 220f)
        val dx = (size.width - 294f * scale) / 2f
        val dy = (size.height - 220f * scale) / 2f
        fun pt(x: Float, y: Float) = Offset(dx + x * scale, dy + y * scale)
        fun r(v: Float) = v * scale

        val center = pt(147f, 110f)
        // Halo rings.
        drawCircle(color = red.copy(alpha = 0.05f), radius = r(100f), center = center)
        drawCircle(color = red.copy(alpha = 0.10f), radius = r(75f), center = center)
        // Gradient core disc.
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(red, redDark),
                startY = center.y - r(50f),
                endY = center.y + r(50f),
            ),
            radius = r(50f),
            center = center,
        )
        // White inner stroke ring (opacity 0.2, width 3).
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = r(51.5f),
            center = center,
            style = Stroke(width = r(3f)),
        )
        // Exclamation triangle outline.
        val triangle = androidx.compose.ui.graphics.Path().apply {
            moveTo(pt(147.75f, 92f).x, pt(147.75f, 92f).y)
            lineTo(pt(164.025f, 116.843f).x, pt(164.025f, 116.843f).y)
            lineTo(pt(131.475f, 116.825f).x, pt(131.475f, 116.825f).y)
            close()
        }
        drawPath(
            path = triangle,
            color = Color.White,
            style = Stroke(
                width = r(2f),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.cornerPathEffect(r(2f)),
            ),
        )
        // Exclamation stem + dot.
        drawLine(
            color = Color.White,
            start = pt(147.75f, 104.75f),
            end = pt(147.75f, 113.5f),
            strokeWidth = r(2f),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = pt(147.741f, 118.75f),
            end = pt(147.756f, 118.75f),
            strokeWidth = r(2.5f),
            cap = StrokeCap.Round,
        )
    }
}
