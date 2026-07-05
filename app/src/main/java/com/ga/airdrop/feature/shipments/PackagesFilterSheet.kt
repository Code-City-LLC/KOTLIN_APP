package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
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
        Box(Modifier.fillMaxSize().background(colors.gray150)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(shipmentsHeaderClearance()))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // Shipment method card
                    ShipmentsSectionCard(title = "Shipment method") {
                        MethodRow(
                            method = ShipmentMethodUi.Standard,
                            labelStyle = AirdropType.title2,
                            selected = selectedMethod == ShipmentTypeFilter.Standard,
                            onClick = { onSelectMethod(ShipmentTypeFilter.Standard) },
                            showDivider = true,
                        )
                        MethodRow(
                            method = ShipmentMethodUi.SeaDrop,
                            labelStyle = AirdropType.title2,
                            selected = selectedMethod == ShipmentTypeFilter.Seadrop,
                            onClick = { onSelectMethod(ShipmentTypeFilter.Seadrop) },
                            showDivider = true,
                        )
                        MethodRow(
                            method = ShipmentMethodUi.Express,
                            labelStyle = AirdropType.title2,
                            selected = selectedMethod == ShipmentTypeFilter.Express,
                            onClick = { onSelectMethod(ShipmentTypeFilter.Express) },
                            showDivider = false,
                        )
                    }

                    // Status of Shipment card
                    ShipmentsSectionCard(title = "Status of Shipment") {
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

            // "Sorting by" glass header with close X.
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(colors.glassOverlay70)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = Spacing.md, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sorting by",
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                        modifier = Modifier.weight(1f),
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_cross),
                        contentDescription = "Close",
                        colorFilter = ColorFilter.tint(colors.iconSelected),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onClose),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.divider)
                )
            }
        }
    }
}

@Composable
private fun MethodRow(
    method: ShipmentMethodUi,
    labelStyle: androidx.compose.ui.text.TextStyle,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val colors = AirdropTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) colors.gray300 else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Image(
                painter = painterResource(method.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(text = method.title, style = labelStyle, color = method.tint)
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider)
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
                .background(if (selected) colors.gray300 else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Image(
                painter = painterResource(ShipmentStatusCatalog.iconRes(status.id)),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
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
                    .background(colors.divider)
            )
        }
    }
}
