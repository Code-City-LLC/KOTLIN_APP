package com.ga.airdrop.feature.calculator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.util.Locale

/*
 * Shared building blocks for the Shipping Calculator screens — Figma "Type
 * Input Field" (40000643:20455/20464), "Erroring & Alerts" info card, and the
 * inner header used by every detail screen in the file.
 */

/** RN formatPrice — en-US currency, 2 decimals. */
internal fun formatPrice(value: Double): String = String.format(Locale.US, "$%,.2f", value)

/** Currency amount without the `$` symbol — the "USD 403.35" pill format. */
internal fun formatDecimal(value: Double): String = String.format(Locale.US, "%,.2f", value)

/**
 * Inner detail header — Figma "Header Type" (44 status + 62 bar): back arrow
 * at 20dp inset, centered Cairo SemiBold 16 title, 1dp divider.
 */
@Composable
internal fun InnerScreenHeader(title: String, onBack: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = Spacing.md),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_arrow),
                contentDescription = "Back",
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(24.dp)
                    .clickable(onClick = onBack),
            )
            Text(
                text = title,
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.iconShape)
        )
    }
}

/**
 * Blue info card — Figma "Erroring & Alerts": #E3ECFF fill, #97AFDD border,
 * radius 10, 20/15 padding, 24dp info-square icon, optional bold title.
 */
@Composable
internal fun BlueInfoCard(
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AlertPalette.Light.OnHold, RoundedCornerShape(Radius.xs))
            .border(1.dp, AlertPalette.Middle.OnHold, RoundedCornerShape(Radius.xs))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDarkTitle),
            modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (title != null) {
                Text(text = title, style = AirdropType.title2, color = colors.textDarkTitle)
            }
            text()
        }
    }
}

@Composable
internal fun BlueInfoCard(text: String, modifier: Modifier = Modifier, title: String? = null) {
    val colors = AirdropTheme.colors
    BlueInfoCard(
        text = { Text(text = text, style = AirdropType.body2, color = colors.textDarkTitle) },
        modifier = modifier,
        title = title,
    )
}

/** Field label row — Cairo SemiBold 16 + red asterisk (Figma error #D92A2A). */
@Composable
internal fun FieldLabel(label: String, required: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = label, style = AirdropType.subtitle1, color = AirdropTheme.colors.textDarkTitle)
        if (required) {
            Text(text = "*", style = AirdropType.subtitle1, color = AlertPalette.Error)
        }
    }
}

/**
 * Figma Type Input Field with trailing-slot support (search / `$` /
 * dropdown chevron) — same 50dp box, gray150 fill, iconShape border,
 * radius 10, 20/12 padding as core TypeInputField.
 */
@Composable
internal fun CalcInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    required: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: androidx.compose.ui.text.TextStyle = AirdropType.body2,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AirdropTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FieldLabel(label, required)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .background(
                    if (enabled) colors.gray150 else colors.gray300,
                    RoundedCornerShape(Radius.xs),
                )
                .border(
                    width = 1.dp,
                    color = if (enabled) colors.iconShape else colors.gray400,
                    shape = RoundedCornerShape(Radius.xs),
                )
                .padding(horizontal = Spacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm1),
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(text = placeholder, style = AirdropType.body2, color = colors.textPlaceholder)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = textStyle.copy(color = colors.textDarkTitle),
                    cursorBrush = SolidColor(BrandPalette.OrangeMain),
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
    }
}

/**
 * Picker-backed field — value in Cairo SemiBold 16 with a dropdown chevron;
 * tap opens a bottom-sheet picker (Android counterpart of the Swift
 * UIPickerView inputView / RN MainPicker).
 */
@Composable
internal fun CalcSelectField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    required: Boolean = false,
) {
    val colors = AirdropTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FieldLabel(label, required)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .background(colors.gray150, RoundedCornerShape(Radius.xs))
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm1),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = AirdropType.body2,
                    color = colors.textPlaceholder,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = value,
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    modifier = Modifier.weight(1f),
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_small_arrow_down),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Bottom-sheet option picker — gray150 sheet, 15dp top radius, 100×6 pill
 * indicator (Figma "Bottom-sheet" 40001817:20339 chrome).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionPickerSheet(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
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
                .padding(horizontal = Spacing.md)
                .padding(top = Spacing.sm, bottom = Spacing.lg)
                .navigationBarsPadding()
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Text(
                    text = option,
                    style = if (isSelected) AirdropType.title2 else AirdropType.subtitle1,
                    color = if (isSelected) BrandPalette.OrangeMain else colors.textDarkTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(option)
                            onDismiss()
                        }
                        .padding(vertical = Spacing.sm),
                )
            }
        }
    }
}

/** Swift presentSimpleAlert equivalent — title/message/OK. */
@Composable
internal fun SimpleAlertDialog(title: String, message: String, onDismiss: () -> Unit) {
    val colors = AirdropTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        title = { Text(text = title, style = AirdropType.title1, color = colors.textDarkTitle) },
        text = { Text(text = message, style = AirdropType.body2, color = colors.textDescription) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "OK", style = AirdropType.button, color = BrandPalette.OrangeMain)
            }
        },
    )
}

/** Trailing `$` glyph used by the invoice / package-value fields. */
@Composable
internal fun DollarTrailing() {
    Text(
        text = "$",
        style = AirdropType.subtitle1,
        color = AirdropTheme.colors.textDarkTitle,
        modifier = Modifier.width(24.dp),
        textAlign = TextAlign.Center,
    )
}
