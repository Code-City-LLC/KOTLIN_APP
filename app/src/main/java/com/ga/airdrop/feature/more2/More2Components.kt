package com.ga.airdrop.feature.more2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/*
 * Shared building blocks for the MORE part-2 drill-down screens. All specs
 * ported 1:1 from the Swift Figma VCs (InnerHeader 56dp, RN MainButton
 * 52dp/radius-14 horizontal #FF783E→#F15114 gradient, RN MainInput 50dp
 * gray150 box).
 */

/** InnerHeader: back chevron (36dp tap target, 24dp glyph) + centered SubTitle1 title + 1dp divider. */
@Composable
internal fun More2InnerHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    rightContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.gray100)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .testTag("more2-inner-header-back")
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                // Swift's Figma VCs build this from chevronDown rotated left.
                // Figma's static Promotions node still shows an arrow asset, so
                // this vector stores the Swift-equivalent final left chevron.
                Image(
                    painter = painterResource(R.drawable.ic_more2_back_chevron),
                    contentDescription = "Back",
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("more2-inner-header-back-chevron"),
                )
            }
            Text(
                text = title,
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                modifier = Modifier.align(Alignment.Center),
            )
            if (rightContent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = Spacing.md),
                    content = rightContent,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.iconShape)
        )
    }
}

/** Pinned bottom CTA bar: gray100 surface, 1dp top border, 20dp padding. */
@Composable
internal fun More2BottomBar(
    modifier: Modifier = Modifier,
    verticalPadding: androidx.compose.ui.unit.Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.gray100)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.iconShape)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.md, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

/** RN MainButton main variant: 52dp, radius 14, horizontal FF783E→F15114 gradient. */
@Composable
internal fun More2PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            // alpha must precede background — applied after, it no-ops on the
            // already-drawn gradient and the disabled CTA looked enabled.
            .alpha(if (enabled) 1f else 0.5f)
            .background(
                Brush.horizontalGradient(listOf(Color(0xFFFF783E), Color(0xFFF15114)))
            )
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
}

/** RN MainButton ghost variant: transparent, 1dp orange border, orange label. */
@Composable
internal fun More2GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, BrandPalette.OrangeMain, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = BrandPalette.OrangeMain,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, style = AirdropType.button, color = BrandPalette.OrangeMain)
        }
    }
}

/** Solid destructive button (RN delete gradient #DC2626 → solid). */
@Composable
internal fun More2RedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color(0xFFDC2626),
    height: androidx.compose.ui.unit.Dp = 52.dp,
    radius: androidx.compose.ui.unit.Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(color)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AirdropType.button, color = BrandPalette.White)
    }
}

/** Outer content card: gray100, radius 15, 1dp iconShape border. */
@Composable
internal fun More2OuterCard(
    modifier: Modifier = Modifier,
    background: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(background ?: colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s)),
        content = content,
    )
}

/**
 * RN MainInput port: SubTitle2 label (+ asterisk), 5dp gap, 50dp gray150 box
 * with radius 10 + 1dp iconShape border, Body1 content, 14dp side insets.
 */
@Composable
internal fun More2Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldTag: String? = null,
    cardTag: String? = null,
    placeholder: String = "",
    required: Boolean = false,
    asteriskColor: Color = AlertPalette.Error,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = AirdropTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(text = label, style = AirdropType.subtitle2, color = colors.textDarkTitle)
            if (required) {
                Text(text = " *", style = AirdropType.subtitle2, color = asteriskColor)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(colors.gray150)
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                )
                .then(if (cardTag != null) Modifier.testTag(cardTag) else Modifier)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = AirdropType.body1,
                        color = colors.textPlaceholder,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = AirdropType.body1.copy(color = colors.textDarkTitle),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(BrandPalette.OrangeMain),
                    singleLine = true,
                    enabled = !readOnly && onClick == null,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = if (isPassword && !passwordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fieldTag != null) Modifier.testTag(fieldTag) else Modifier),
                )
            }
            if (isPassword && onTogglePasswordVisibility != null) {
                Image(
                    painter = painterResource(
                        if (passwordVisible) R.drawable.ic_eye else R.drawable.ic_hide
                    ),
                    contentDescription = "Toggle password visibility",
                    colorFilter = ColorFilter.tint(colors.gray500),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onTogglePasswordVisibility),
                )
            }
            if (trailing != null) trailing()
        }
    }
}

/** Full-screen loading overlay (orange spinner centered). */
@Composable
internal fun More2Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandPalette.OrangeMain)
    }
}

/** UIAlertController stand-in: title + message + OK. */
@Composable
internal fun More2Alert(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmText: String = "OK",
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    destructiveConfirm: Boolean = false,
) {
    val colors = AirdropTheme.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        title = { Text(title, style = AirdropType.title2, color = colors.textDarkTitle) },
        text = { Text(message, style = AirdropType.body2, color = colors.textDescription) },
        confirmButton = {
            Text(
                text = confirmText,
                style = AirdropType.subtitle2,
                color = if (destructiveConfirm) AlertPalette.Error else BrandPalette.OrangeMain,
                modifier = Modifier
                    .clickable {
                        onConfirm?.invoke()
                        onDismiss()
                    }
                    .padding(Spacing.sm),
            )
        },
        dismissButton = dismissText?.let {
            {
                Text(
                    text = it,
                    style = AirdropType.subtitle2,
                    color = colors.textDescription,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(Spacing.sm),
                )
            }
        },
    )
}

/** Swift FigmaDropdownPicker counterpart for More2 read-only selector fields. */
@Composable
internal fun More2PickerSheet(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    val cardShape = RoundedCornerShape(24.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .testTag("more2-picker-sheet"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(alpha = if (colors.isDark) 0.12f else 0.20f)
                    )
                    .clickable(onClick = onDismiss)
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .widthIn(max = 343.dp)
                    .clip(cardShape)
                    .background(
                        if (colors.isDark) {
                            colors.gray100.copy(alpha = 0.92f)
                        } else {
                            Color.White.copy(alpha = 0.92f)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (colors.isDark) {
                            Color.White.copy(alpha = 0.18f)
                        } else {
                            Color.White.copy(alpha = 0.70f)
                        },
                        shape = cardShape,
                    )
                    .padding(top = 20.dp, start = 10.dp, end = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("more2-picker-title"),
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    options.forEach { option ->
                        val selectedOption = option == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedOption) {
                                        BrandPalette.OrangeMain.copy(alpha = 0.09f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable {
                                    onSelect(option)
                                    onDismiss()
                                }
                                .padding(horizontal = 20.dp)
                                .testTag(
                                    "more2-picker-option-${
                                        option.filter { it.isLetterOrDigit() }.lowercase()
                                    }"
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option,
                                style = AirdropType.subtitle1,
                                color = if (selectedOption) {
                                    BrandPalette.OrangeMain
                                } else {
                                    colors.textDarkTitle
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (selectedOption) {
                                Image(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
