package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.Warehouse
import com.ga.airdrop.feature.homedetails.components.CopiedToastPill
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader
import kotlinx.coroutines.delay

/**
 * Warehouses — Figma 40000944:3571 (page) + 40000944:3698 (copied-toast
 * variant), behavior from FigmaWarehousesViewController.
 *
 * APPROVED DEVIATION (Kemar, 2026-05-22, carried over from Swift): ONE screen
 * with a Standard / SeaDrop / Express tab strip on top instead of three
 * separate nav destinations. Figma-vs-app audits must NOT remove the tabs.
 */

enum class WarehouseType(
    val key: String,
    val prettyName: String,
    val bigTitle: String,
    val subtitle: String,
    val addressLine2Prefix: String,
    val tint: Color,
    val heroRes: Int,
    val circleIconRes: Int,
) {
    Standard(
        key = "standard",
        prettyName = "Standard",
        bigTitle = "AirDrop (Air Freight)",
        subtitle = "2 to 3 business days after items are delivered to our warehouse.",
        addressLine2Prefix = "AIR – ",
        tint = Color(0xFF6C46C5),
        heroRes = R.drawable.img_homedet_hero_standard,
        circleIconRes = R.drawable.ic_standard_shipping,
    ),
    SeaDrop(
        key = "seadrop",
        prettyName = "SeaDrop",
        bigTitle = "SeaDrop (Sea Freight)",
        subtitle = "2 to 4 weeks after items are delivered to our warehouse.",
        addressLine2Prefix = "SEADROP – ",
        tint = Color(0xFF0A96D4),
        heroRes = R.drawable.img_homedet_hero_seadrop,
        circleIconRes = R.drawable.ic_sea_drop_shipping,
    ),
    Express(
        key = "express",
        prettyName = "Express",
        bigTitle = "Express (Air Express)",
        subtitle = "1 to 2 business days after items are delivered to our warehouse.",
        addressLine2Prefix = "EXPRESS – ",
        tint = Color(0xFFF15114),
        heroRes = R.drawable.img_homedet_hero_express,
        circleIconRes = R.drawable.ic_express_shipping,
    );

    companion object {
        fun from(raw: String?): WarehouseType =
            entries.firstOrNull { it.key == raw?.lowercase() } ?: Standard
    }
}

private const val FALLBACK_ADDRESS_LINE1 = "6175 NW 167th Street, Unit G36"
private const val FALLBACK_CITY = "Hialeah"
private const val FALLBACK_STATE = "Florida"
private const val FALLBACK_PHONE = "1(954)508-1797"

@Composable
fun WarehousesScreen(
    onBack: () -> Unit,
    initialType: String? = null,
    viewModel: WarehousesViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    var current by rememberSaveable { mutableStateOf(WarehouseType.from(initialType).key) }
    val type = WarehouseType.from(current)
    var toast by remember { mutableStateOf<String?>(null) }

    if (toast != null) {
        LaunchedEffect(toast) {
            delay(2000)
            toast = null
        }
    }

    fun copy(value: String, message: String = "Copied") {
        clipboard.setText(AnnotatedString(value))
        toast = message
    }

    val warehouse = viewModel.warehouseFor(type.key)
    val fields = warehouseFields(type, state, warehouse)

    Box(Modifier.fillMaxSize().background(colors.gray150)) {
        Column(Modifier.fillMaxSize()) {
            HomeDetailsHeader(
                title = type.prettyName,
                onBack = onBack,
                trailingIconRes = R.drawable.ic_copy,
                trailingContentDescription = "Copy all warehouse info",
                onTrailingClick = {
                    copy(
                        value = fields.joinToString("\n") { "${it.label}: ${it.value}" },
                        message = "All the information is copied",
                    )
                },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                TypeTabs(current = type, onSelect = { current = it.key })
                WarehouseHero(type)
                // Title block — H6 tinted, Body2 subtitle, bottom divider.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = type.bigTitle,
                        style = AirdropType.h6,
                        color = type.tint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = type.subtitle,
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.iconShape)
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md)
                        .padding(top = Spacing.md, bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "Please ship your items with the following information:",
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                    )
                    InfoCard(type = type, fields = fields, onCopy = { copy(it) })
                    PleaseNoteCard()
                }
            }
        }

        toast?.let {
            CopiedToastPill(
                text = it,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp),
            )
        }
    }
}

// ─── Field assembly (Swift renderCurrentWarehouse) ─────────────────────────

private data class WarehouseField(
    val label: String,
    val value: String,
    val isAccountLine: Boolean = false,
)

private fun warehouseFields(
    type: WarehouseType,
    state: WarehousesUiState,
    warehouse: Warehouse?,
): List<WarehouseField> {
    val fullName = listOfNotNull(state.user?.firstName, state.user?.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val account = state.user?.accountNumber.orEmpty()
    return listOf(
        WarehouseField("Full Name", fullName.ifEmpty { "—" }),
        WarehouseField(
            "Address Line 1",
            (warehouse?.address ?: FALLBACK_ADDRESS_LINE1).ifEmpty { "—" },
        ),
        WarehouseField(
            "Address Line 2",
            if (account.isEmpty()) "—" else "${type.addressLine2Prefix}$account",
            isAccountLine = true,
        ),
        WarehouseField("City", capitalizeFirst(warehouse?.city ?: FALLBACK_CITY)),
        WarehouseField("State", capitalizeFirst(warehouse?.state ?: FALLBACK_STATE)),
        WarehouseField("Zip Code", warehouse?.zipCode?.takeIf { it.isNotBlank() } ?: "—"),
        WarehouseField(
            "Phone Number",
            formatPhoneNumber(warehouse?.phoneNumber?.takeIf { it.isNotBlank() } ?: FALLBACK_PHONE),
        ),
    )
}

private fun capitalizeFirst(s: String): String =
    if (s.isEmpty()) s else s.first().uppercase() + s.drop(1).lowercase()

private fun formatPhoneNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    if (digits.length < 10) return phone
    val lastTen = digits.takeLast(10)
    return "+1(${lastTen.substring(0, 3)}) ${lastTen.substring(3, 6)}-${lastTen.substring(6)}"
}

// ─── Type tabs (approved Swift-only deviation) ─────────────────────────────

@Composable
private fun TypeTabs(current: WarehouseType, onSelect: (WarehouseType) -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.gray150)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WarehouseType.entries.forEach { type ->
            val active = type == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(if (active) type.tint.copy(alpha = 0.10f) else colors.gray100)
                    .border(
                        1.dp,
                        if (active) type.tint else colors.iconShape,
                        RoundedCornerShape(Radius.xs),
                    )
                    .clickable { onSelect(type) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = type.prettyName,
                    style = AirdropType.subtitle2,
                    color = if (active) type.tint else colors.textDarkTitle,
                )
            }
        }
    }
}

// ─── Hero image + overlapping circle badge (Figma 40000944:3572 / 3591) ───

@Composable
private fun WarehouseHero(type: WarehouseType) {
    val colors = AirdropTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(219.dp) // 174dp photo + 45dp circle overhang
    ) {
        Image(
            painter = painterResource(type.heroRes),
            contentDescription = type.prettyName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(174.dp),
        )
        // 90dp white circle centered on the photo's bottom edge; 40dp
        // tinted shipping glyph inside — Figma 40000944:3591.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(90.dp)
                .clip(CircleShape)
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(type.circleIconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(type.tint),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

// ─── Info card (Figma "List card" rows) ────────────────────────────────────

@Composable
private fun InfoCard(
    type: WarehouseType,
    fields: List<WarehouseField>,
    onCopy: (String) -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs)),
    ) {
        fields.forEachIndexed { index, field ->
            val accountRow = field.isAccountLine
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (accountRow) colors.gray150 else Color.Transparent)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = field.label,
                        style = AirdropType.subtitle2,
                        color = colors.textDescription,
                    )
                    Text(
                        text = field.value,
                        style = if (accountRow) AirdropType.title1 else AirdropType.subtitle1,
                        color = if (accountRow) type.tint else colors.textDarkTitle,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = "Copy ${field.label}",
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onCopy(field.value) },
                )
            }
            if (index != fields.lastIndex) {
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

// ─── "Please note" blue info card (Figma 40001174:4853) ───────────────────

@Composable
private fun PleaseNoteCard() {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xs))
            .background(AlertPalette.Light.OnHold)
            .border(1.dp, AlertPalette.Middle.OnHold, RoundedCornerShape(Radius.xs))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFF292929)),
            modifier = Modifier.size(24.dp),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = "Please note",
                style = AirdropType.title2,
                color = Color(0xFF292929),
            )
            Text(
                text = "Please note: All packages will be handled as Airdrop Standard " +
                    "unless identified otherwise in address line 2.",
                style = AirdropType.body2,
                color = Color(0xFF292929),
            )
        }
    }
}
