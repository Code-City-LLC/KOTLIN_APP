package com.ga.airdrop.feature.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import androidx.compose.ui.unit.dp

/*
 * Building blocks shared by the More-tab screens — Figma "Header Type"
 * (back variant), "Card Page" 59dp selection row, "Button Type" pinned CTA
 * bar, and the Type-Input-Field-shaped picker row.
 */

/**
 * Detail-screen header — Swift inner-header pattern (FigmaSpecificPages.swift
 * :1332-1334/:1362-1366 Settings, FigmaProfileViewController.swift:144-198):
 * solid gray100, 56dp tall, back arrow left, SubTitle-1 centered title,
 * optional 24dp trailing action, 1dp iconShape divider underline.
 */
@Composable
fun MoreDetailHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    titleStyle: androidx.compose.ui.text.TextStyle = AirdropType.subtitle1,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.gray100)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Spacing.md, vertical = 4.dp),
        ) {
            Image(
                // Swift inner headers draw a left-pointing CHEVRON, not the
                // tailed arrow glyph.
                painter = painterResource(R.drawable.ic_small_arrow_down),
                contentDescription = "Back",
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(24.dp)
                    .rotate(90f)
                    .clickable(onClick = onBack),
            )
            Text(
                text = title,
                style = titleStyle,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center),
            )
            if (trailing != null) {
                Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                // Swift divider is iconShape (FigmaProfileViewController.swift:175).
                .background(colors.iconShape),
        )
    }
}

/**
 * Figma "Card Page" selection row: 59dp, radius 15, gray100 fill,
 * iconShape border, 24dp leading icon + SubTitle-1 title + right chevron.
 * Duotone icons pass `tint = null` so their orange accents survive.
 */
@Composable
fun MoreRowCard(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    testTagPrefix: String? = null,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(59.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .then(testTagPrefix?.let { Modifier.testTag("$it-row") } ?: Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = tint?.let { ColorFilter.tint(it) },
                modifier = Modifier
                    .size(24.dp)
                    .then(testTagPrefix?.let { Modifier.testTag("$it-icon") } ?: Modifier),
            )
            Text(
                text = title,
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            MoreChevronRight()
        }
    }
}

/** Small Arrow – Right 2 (Figma 307:22385): the shared 24dp chevron, rotated. */
@Composable
fun MoreChevronRight(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_small_arrow_down),
        contentDescription = null,
        colorFilter = ColorFilter.tint(AirdropTheme.colors.iconSelected),
        modifier = modifier
            .size(24.dp)
            .rotate(-90f),
    )
}

/**
 * Pinned CTA bar — Swift pinned-CTA pattern (FigmaSpecificPages.swift
 * :1630-1667 makeBottomLogoutBar, FigmaProfileViewController.swift:105-116):
 * solid gray100 bar, 1dp iconShape top border, 20dp padding around a solid
 * orangeMain button (radius 10, 52dp, Button typography, white label).
 */
@Composable
fun MoreBottomButtonBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.gray100),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.iconShape),
        )
        Box(
            modifier = Modifier
                .padding(Spacing.md)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(if (enabled) BrandPalette.OrangeMain else BrandPalette.ButtonDisable)
                .clickable(enabled = enabled && !loading, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = BrandPalette.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(text = text, style = AirdropType.button, color = BrandPalette.White)
            }
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

/**
 * Swift SelectableRow picker/read-only row: SubTitle-2 label with orange
 * asterisk, 52dp min box (radius 12, iconShape border), value or placeholder,
 * optional trailing glyph.
 */
@Composable
fun MoreSelectField(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    required: Boolean = false,
    enabled: Boolean = true,
    trailingIconRes: Int? = R.drawable.ic_small_arrow_down,
    testTagPrefix: String? = null,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (label.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                // Swift labels: SubTitle-2 with an ORANGE asterisk.
                Text(text = label, style = AirdropType.subtitle2, color = colors.textDarkTitle)
                if (required) {
                    Text(text = "*", style = AirdropType.subtitle2, color = BrandPalette.OrangeMain)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
                .then(testTagPrefix?.let { Modifier.testTag("$it-card") } ?: Modifier)
                .then(
                    if (onClick != null && enabled) Modifier.clickable(onClick = onClick)
                    else Modifier,
                )
                .padding(horizontal = Spacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm1),
        ) {
            Text(
                text = value.ifEmpty { placeholder },
                style = if (value.isEmpty()) AirdropType.body2 else AirdropType.body1,
                color = when {
                    value.isEmpty() -> colors.textPlaceholder
                    !enabled -> colors.textPlaceholder
                    else -> colors.textDarkTitle
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailingIconRes != null) {
                Image(
                    painter = painterResource(trailingIconRes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier
                        .size(12.dp)
                        .then(testTagPrefix?.let { Modifier.testTag("$it-chevron") } ?: Modifier),
                )
            }
        }
    }
}

/** Simple OK alert — Android counterpart of the Swift UIAlertController(.alert) calls. */
@Composable
fun MoreAlertDialog(
    title: String,
    message: String,
    confirmLabel: String = "OK",
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        title = { Text(title, style = AirdropType.h6, color = colors.textDarkTitle) },
        text = { Text(message, style = AirdropType.body2, color = colors.textDescription) },
        confirmButton = {
            Text(
                text = confirmLabel,
                style = AirdropType.button,
                color = colors.textDarkTitle,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        },
    )
}

/** Two-action confirm alert (Cancel + destructive/confirm action). */
@Composable
fun MoreConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    val colors = AirdropTheme.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        title = { Text(title, style = AirdropType.h6, color = colors.textDarkTitle) },
        text = { Text(message, style = AirdropType.body2, color = colors.textDescription) },
        confirmButton = {
            Text(
                text = confirmLabel,
                style = AirdropType.button,
                color = if (destructive) AlertPalette.Error else colors.textDarkTitle,
                modifier = Modifier
                    .clickable {
                        onDismiss()
                        onConfirm()
                    }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        },
        dismissButton = {
            Text(
                text = "Cancel",
                style = AirdropType.button,
                color = colors.textDescription,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        },
    )
}

/**
 * Bottom-sheet option picker — Android counterpart of the Swift/RN action
 * sheets used across Preferences and Profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionSheet(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.gray100,
    ) {
        Column(Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = title,
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(option)
                            onDismiss()
                        }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option,
                        style = if (option == selected) AirdropType.subtitle1 else AirdropType.body1,
                        color = colors.textDarkTitle,
                    )
                    if (option == selected) {
                        Image(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.iconSelected),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
