package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Packages filter sheet — Figma node 40006358:75618 ("Sorting by"), behavior
 * from FigmaPackagesFilterViewController: "Shipment method" + "Status of
 * Shipment" cards; each row applies immediately, tapping the active row
 * clears it; X commits and closes.
 */
@Composable
fun PackagesFilterSheet(
    statuses: List<PackageStatusInfo>,
    selectedStatus: Int,
    selectedMethod: ShipmentTypeFilter,
    onSelectStatus: (Int) -> Unit,
    onSelectMethod: (ShipmentTypeFilter) -> Unit,
    onClose: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.gray150)
                .testTag("packages-filter-root")
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(packagesFilterHeaderClearance()))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, end = Spacing.md, top = 16.dp, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PackagesFilterSectionCard(
                        title = "Shipment Method",
                        modifier = Modifier.testTag("packages-filter-method-card"),
                    ) {
                        MethodRow(
                            method = ShipmentMethodUi.Standard,
                            selected = selectedMethod == ShipmentTypeFilter.Standard,
                            onClick = { onSelectMethod(ShipmentTypeFilter.Standard) },
                            showDivider = true,
                        )
                        MethodRow(
                            method = ShipmentMethodUi.SeaDrop,
                            selected = selectedMethod == ShipmentTypeFilter.Seadrop,
                            onClick = { onSelectMethod(ShipmentTypeFilter.Seadrop) },
                            showDivider = true,
                        )
                        MethodRow(
                            method = ShipmentMethodUi.Express,
                            selected = selectedMethod == ShipmentTypeFilter.Express,
                            onClick = { onSelectMethod(ShipmentTypeFilter.Express) },
                            showDivider = false,
                        )
                    }

                    PackagesFilterSectionCard(
                        title = "Status of Shipment",
                        modifier = Modifier.testTag("packages-filter-status-card"),
                    ) {
                        statuses.forEachIndexed { index, status ->
                            StatusRow(
                                status = status,
                                selected = selectedStatus == status.id,
                                onClick = { onSelectStatus(status.id) },
                                showDivider = index != statuses.lastIndex,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            // Swift FigmaPackagesFilterViewController uses an opaque adaptive
            // gray100 inner header; Figma's older glass layer is secondary.
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(colors.gray100)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .testTag("packages-filter-header")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = Spacing.md)
                        .testTag("packages-filter-header-row"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sorting by",
                        style = AirdropType.title2,
                        color = colors.textDarkTitle,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onClose)
                            .testTag("packages-filter-close"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_cross),
                            contentDescription = "Close",
                            colorFilter = ColorFilter.tint(colors.iconSelected),
                            modifier = Modifier.size(18.dp),
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
    }
}

@Composable
private fun packagesFilterHeaderClearance(): androidx.compose.ui.unit.Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar + 57.dp
}

@Composable
private fun PackagesFilterSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    var expanded by remember { mutableStateOf(true) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(colors.gray200)
                .clickable { expanded = !expanded }
                .padding(horizontal = Spacing.md)
                .testTag("packages-filter-section-$title"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = AirdropType.title2, color = colors.textDarkTitle)
            Image(
                painter = painterResource(R.drawable.ic_small_arrow_down),
                contentDescription = if (expanded) "Collapse" else "Expand",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.iconShape)
        )
        if (expanded) content()
    }
}

@Composable
private fun MethodRow(
    method: ShipmentMethodUi,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val colors = AirdropTheme.colors
    val label = when (method) {
        ShipmentMethodUi.Standard -> "AirDrop"
        ShipmentMethodUi.Express -> method.title
        ShipmentMethodUi.SeaDrop -> method.title
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(if (selected) colors.gray300 else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md)
                .testTag("packages-filter-method-row-${method.name.lowercase()}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Image(
                painter = painterResource(method.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .testTag("packages-filter-method-icon-${method.name.lowercase()}"),
            )
            Text(
                text = label,
                style = AirdropType.title2,
                color = method.tint,
                modifier = Modifier.weight(1f),
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
                    .testTag("packages-filter-method-divider-${method.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun StatusRow(
    status: PackageStatusInfo,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val colors = AirdropTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(if (selected) colors.gray300 else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md)
                .testTag("packages-filter-status-row-${status.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Image(
                painter = painterResource(ShipmentStatusCatalog.iconRes(status.id, dark = colors.isDark)),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .testTag("packages-filter-status-icon-${status.id}"),
            )
            Text(
                text = status.name,
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
                    .testTag("packages-filter-status-divider-${status.id}")
            )
        }
    }
}
