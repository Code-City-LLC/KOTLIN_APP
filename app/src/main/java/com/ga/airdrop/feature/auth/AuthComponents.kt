package com.ga.airdrop.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.frostedGlassSurface

/*
 * Building blocks for the auth/onboarding screens — Figma "Header Type"
 * (L–Back variant), "Button Type" pinned CTA bar, Type-Input-Field-shaped
 * picker row, 16dp checkboxes and the "Bottom-sheet" success card shared by
 * Forget Password Pop Up (40006240:23961) and Registration Successful
 * (40006240:23983).
 */

/**
 * Detail-screen header — Figma Header Type / L–Back (40006240:23926):
 * white/70 glass, back arrow left, SubTitle-1 centered title, 1dp divider.
 */
@Composable
fun AuthDetailHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.frostedGlassSurface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = Spacing.md, vertical = 4.dp),
        ) {
            Image(
                // Unified back arrow — Swift left chevron (was the tailed ic_arrow).
                painter = painterResource(R.drawable.ic_more2_back_chevron),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.divider),
        )
    }
}

/**
 * Pinned CTA bar — Figma "Button Type" (40006240:23927): white/70 glass,
 * 1dp top divider, 20dp padding around the gradient button.
 */
@Composable
fun AuthBottomButtonBar(
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
            .background(colors.frostedGlassSurface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.divider),
        )
        GradientButton(
            text = text,
            onClick = onClick,
            loading = loading,
            enabled = enabled,
            modifier = Modifier.padding(Spacing.md),
        )
        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

/**
 * Type-Input-Field-shaped picker row (Figma 40006240:23917 etc.):
 * SemiBold-16 label + red asterisk, 50dp min box (radius 10, iconShape
 * border), value or "Select" placeholder, 24dp Small Arrow – Down glyph.
 */
@Composable
fun AuthSelectField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    required: Boolean = false,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = label, style = AirdropType.subtitle1, color = colors.textDarkTitle)
            if (required) {
                Text(text = "*", style = AirdropType.subtitle1, color = AlertPalette.Error)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .background(
                    if (enabled) colors.gray150 else colors.gray300,
                    RoundedCornerShape(Radius.xs),
                )
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = 12.dp)
                .then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm1),
        ) {
            Text(
                text = value.ifEmpty { placeholder },
                style = if (value.isEmpty()) AirdropType.body2 else AirdropType.body1,
                color = if (value.isEmpty()) colors.textPlaceholder else colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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
 * Option picker — Swift FigmaDropdownPicker parity: a centered frosted-glass
 * card (24dp corners) over a dim scrim, with a centered title, a search field
 * that appears once there are more than 10 options (e.g. Country), and 54dp rows
 * whose selected entry is bold with a checkmark. Replaces the old bottom sheet.
 */
@Composable
fun AuthOptionSheet(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    // Swift showsSearch: options.count > 10 (only long lists get the search box).
    val showSearch = options.size > 10
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        options
    } else {
        options.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    // Swift caps card height at ~68% of the screen so long lists scroll inside it.
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.68f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 343.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxCardHeight)
                    .shadow(24.dp, RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.frostedGlassSurface)
                    .padding(bottom = 12.dp)
                    .testTag("auth-option-sheet"),
            ) {
                Text(
                    text = title,
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, start = 20.dp, end = 20.dp),
                )
                if (showSearch) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.gray100)
                            .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.textDescription),
                            modifier = Modifier.size(18.dp),
                        )
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
                            cursorBrush = SolidColor(BrandPalette.OrangeMain),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search ${title.lowercase()}",
                                        style = AirdropType.body2,
                                        color = colors.textPlaceholder,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(if (showSearch) 12.dp else 8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        text = "No matches",
                        style = AirdropType.body2,
                        color = colors.textDescription,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
                        items(count = filtered.size) { index ->
                            val option = filtered[index]
                            val isSelected = option == selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clickable {
                                        onSelect(option)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option,
                                    // Swift: SemiBold 16; the selected row goes bold.
                                    style = if (isSelected) AirdropType.subtitle1 else AirdropType.body1,
                                    color = colors.textDarkTitle,
                                )
                                if (isSelected) {
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
        }
    }
}

/** Simple OK alert — counterpart of the RN/Swift auth Alert calls. */
@Composable
fun AuthAlertDialog(
    title: String,
    message: String,
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
                text = "OK",
                style = AirdropType.button,
                color = colors.textDarkTitle,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        },
    )
}

/**
 * Figma "Checkboxes with Text" (40006240:23924): 16dp box — gray100 fill,
 * 1dp iconShape border, radius 2 — filling brand orange with a white check
 * when selected; Body-2 text to the right.
 */
@Composable
fun AuthCheckboxRow(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    text: @Composable () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp)
                .background(
                    if (checked) BrandPalette.OrangeMain else colors.gray100,
                    RoundedCornerShape(2.dp),
                )
                .border(
                    1.dp,
                    if (checked) BrandPalette.OrangeMain else colors.iconShape,
                    RoundedCornerShape(2.dp),
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(BrandPalette.White),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Box(Modifier.weight(1f)) { text() }
    }
}

/**
 * Success bottom card — Figma "Bottom-sheet" + "Button Type"
 * (40006240:23973/26778 and 23995/26789): white/70 glass sheet with top
 * radius 31, 100x6 drag indicator, the img_auth_success illustration
 * (354x249 @1x), H5 title + Body-2 message, then a glass button bar with an
 * outline CTA. Anchor at the bottom of a full-size Box.
 */
@Composable
fun AuthSuccessSheet(
    title: String,
    message: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    colors.frostedGlassSurface,
                    RoundedCornerShape(topStart = 31.dp, topEnd = 31.dp),
                )
                .padding(horizontal = 31.dp, vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(21.dp),
        ) {
            Box(
                Modifier
                    .width(100.dp)
                    .height(6.dp)
                    .background(colors.gray300, RoundedCornerShape(Radius.full)),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_auth_success),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(354f / 249f),
                    contentScale = ContentScale.Fit,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = AirdropType.h5,
                        color = colors.textDarkTitle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = message,
                        style = AirdropType.body2,
                        color = colors.textDescription,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.frostedGlassSurface)
                .padding(horizontal = 31.dp)
                .padding(top = 21.dp, bottom = Spacing.sm),
        ) {
            OutlineButton(text = buttonText, onClick = onButtonClick)
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}
