package com.ga.airdrop.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.frostedGlassSurface

/**
 * The single option picker used everywhere the user taps a field and chooses
 * from a list of values — Sign Up, Edit Profile, Preferences, and any future
 * one. A faithful port of Swift's FigmaDropdownPicker: a centered frosted-glass
 * card (24dp corners) over a dim scrim, a centered title, 54dp rows whose
 * selected entry is bold with a checkmark, and a search box that appears once
 * there are more than 10 options. Feature-level wrappers (AuthOptionSheet,
 * MoreOptionSheet) delegate here so every picker looks and behaves identically.
 */
@Composable
fun AirdropOptionPicker(
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
                    .testTag("airdrop-option-picker"),
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, start = 20.dp, end = 20.dp),
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                }
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
                    LazyColumn(Modifier.fillMaxWidth()) {
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
