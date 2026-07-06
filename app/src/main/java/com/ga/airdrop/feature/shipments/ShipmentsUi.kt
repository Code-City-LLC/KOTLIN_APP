package com.ga.airdrop.feature.shipments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.SubcomposeAsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Shared visual vocabulary of the SHIPMENTS group — Figma components
 * "Header Type" (back variant), "Package Card", "Payment Card", "Order Card",
 * "Section Title", "List card", "Metro Step Card", "Total Airdrop Charges",
 * "Type Input Field" (search variant). Behavior mirrors the Figma* Swift VCs.
 */

@Composable
internal fun ShipmentsRefreshOnResume(key: Any, onRefresh: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, key) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            onRefresh()
        }
        onDispose { lifecycle.removeObserver(observer) }
    }
}

// ─── Shipping method branding (FigmaShipmentsViewController.methodBranding) ──

enum class ShipmentMethodUi(
    val title: String,
    val tint: Color,
    val iconRes: Int,
    val heroRes: Int,
) {
    // Titles per Swift PackagePresentation.methodLabel ("AirDrop Standard",
    // not the bare "AirDrop" an earlier pass used).
    Standard("AirDrop Standard", Color(0xFF10BBE9), R.drawable.ic_standard_shipping, R.drawable.img_shipments_hero_standard),
    Express("Express", Color(0xFFF15114), R.drawable.ic_express_shipping, R.drawable.img_shipments_hero_express),
    SeaDrop("SeaDrop", Color(0xFF0A96D4), R.drawable.ic_sea_drop_shipping, R.drawable.img_shipments_hero_seadrop);

    companion object {
        fun from(method: String?): ShipmentMethodUi {
            val normalized = method.orEmpty().lowercase(Locale.US).replace(" ", "")
            return when {
                normalized.contains("express") -> Express
                normalized.contains("seadrop") || normalized.contains("sea") -> SeaDrop
                else -> Standard
            }
        }
    }
}

// ─── Package status catalog (PackageStatusCatalog.swift defaults) ──────────

object ShipmentStatusCatalog {

    val defaults = listOf(
        PackageStatusInfo(1, "Drop Alerted", "#db3939", 1),
        PackageStatusInfo(2, "Shipment Received", "#f07f16", 2),
        PackageStatusInfo(3, "Port of Departure -MIA", "#34d1cc", 3),
        PackageStatusInfo(4, "Arrived at Port -JAM", "#3497d1", 4),
        PackageStatusInfo(9, "Processing at Customs", "#5b34d1", 5),
        PackageStatusInfo(10, "Detained at Customs", "#9534d1", 6),
        PackageStatusInfo(5, "Released From Customs", "#d134b4", 7),
        PackageStatusInfo(6, "Processing at our Warehouse", "#b4d134", 8),
        PackageStatusInfo(12, "In-Transit to counter", "#83d134", 9),
        PackageStatusInfo(7, "Ready for Pickup", "#0f03fc", 10),
        PackageStatusInfo(8, "Delivered", "#19d14b", 11),
        PackageStatusInfo(14, "Proof of Delivery", "#139135", 12),
        PackageStatusInfo(15, "Uncollected Packages", "#345c0b", 13),
        PackageStatusInfo(16, "Dangerous Goods", "#345c0b", 14),
        PackageStatusInfo(17, "Auction", "#345c0b", 15),
        PackageStatusInfo(18, "Paid and Ready for Pick Up", "#19d144", 16),
        PackageStatusInfo(19, "Returned to Merchant", "#ff6b6b", 17),
    )

    /** Per-status Figma glyph (FigmaPackagesFilterViewController.statusIcon). */
    fun iconRes(statusId: Int, dark: Boolean = false): Int = when (statusId) {
        1 -> if (dark) R.drawable.ic_shipments_status_drop_alerted_dark else R.drawable.ic_shipments_status_drop_alerted
        2 -> if (dark) R.drawable.ic_shipments_status_shipment_received_dark else R.drawable.ic_shipments_status_shipment_received
        3 -> if (dark) R.drawable.ic_shipments_status_port_departure_mia_dark else R.drawable.ic_shipments_status_port_departure_mia
        4 -> if (dark) R.drawable.ic_shipments_status_arrived_port_jam_dark else R.drawable.ic_shipments_status_arrived_port_jam
        5 -> if (dark) R.drawable.ic_shipments_status_released_customs_dark else R.drawable.ic_shipments_status_released_customs
        6 -> if (dark) R.drawable.ic_shipments_status_processing_warehouse_dark else R.drawable.ic_shipments_status_processing_warehouse
        7 -> if (dark) R.drawable.ic_shipments_status_ready_for_pickup_dark else R.drawable.ic_shipments_status_ready_for_pickup
        8 -> if (dark) R.drawable.ic_shipments_status_delivered_dark else R.drawable.ic_shipments_status_delivered
        9 -> if (dark) R.drawable.ic_shipments_status_processing_customs_dark else R.drawable.ic_shipments_status_processing_customs
        10 -> if (dark) R.drawable.ic_shipments_status_detained_customs_dark else R.drawable.ic_shipments_status_detained_customs
        12 -> if (dark) R.drawable.ic_shipments_status_in_transit_counter_dark else R.drawable.ic_shipments_status_in_transit_counter
        14 -> if (dark) R.drawable.ic_shipments_status_delivered_dark else R.drawable.ic_shipments_status_delivered
        15 -> if (dark) R.drawable.ic_shipments_status_detained_customs_dark else R.drawable.ic_shipments_status_detained_customs
        16 -> if (dark) R.drawable.ic_shipments_status_dangerous_goods_dark else R.drawable.ic_shipments_status_dangerous_goods
        17 -> if (dark) R.drawable.ic_shipments_status_auction_dark else R.drawable.ic_shipments_status_auction
        18 -> if (dark) R.drawable.ic_shipments_status_paid_ready_pickup_dark else R.drawable.ic_shipments_status_paid_ready_pickup
        19 -> if (dark) R.drawable.ic_shipments_status_returned_merchant_dark else R.drawable.ic_shipments_status_returned_merchant
        else -> R.drawable.ic_packages
    }

    fun iconResFor(statusName: String?): Int = iconRes(idFor(statusName) ?: 0)

    fun idFor(statusName: String?): Int? {
        val target = normalize(statusName ?: return null)
        return defaults.firstOrNull { normalize(it.name) == target }?.id
            ?: defaults.firstOrNull { normalize(it.name).contains(target) || target.contains(normalize(it.name)) }?.id
    }

    private fun normalize(s: String) = s.lowercase(Locale.US).filter { it.isLetterOrDigit() }
}

/** Status → text color, substring rules from FigmaPackagesViewController. */
fun packageStatusColor(statusName: String?): Color {
    val s = statusName.orEmpty().lowercase(Locale.US)
    return when {
        s.contains("delivered") || s.contains("complete") || s.contains("pick") || s.contains("arrived") ->
            AlertPalette.Completed
        s.contains("hold") -> AlertPalette.OnHold
        s.contains("cancel") -> AlertPalette.Cancel
        s.contains("error") || s.contains("fail") -> AlertPalette.Error
        s.contains("pending") || s.contains("processing") || s.contains("transit") -> AlertPalette.Pending
        else -> AlertPalette.Completed
    }
}

/** Timeline flavor — FigmaPackageDetailsViewController.statusColor. */
fun timelineStatusColor(statusName: String?): Color {
    val s = statusName.orEmpty().lowercase(Locale.US)
    return when {
        s.contains("delivered") || s.contains("complete") || s.contains("pick") ||
            s.contains("arrived") || s.contains("alerted") || s.contains("received") ->
            AlertPalette.Completed
        s.contains("hold") -> AlertPalette.OnHold
        s.contains("cancel") -> AlertPalette.Cancel
        s.contains("error") || s.contains("fail") -> AlertPalette.Error
        s.contains("pending") || s.contains("processing") || s.contains("transit") || s.contains("ready") ->
            AlertPalette.Pending
        else -> AlertPalette.Completed
    }
}

// ─── Formatters (ports of the Swift helpers) ───────────────────────────────

object ShipmentsFormat {

    private fun decimal(min: Int, max: Int): NumberFormat =
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = min
            maximumFractionDigits = max
            isGroupingUsed = true
        }

    /** "64,841.58" — decimal, grouped, always 2 fraction digits. */
    fun money(value: Double): String = decimal(2, 2).format(value)

    /** "64841.58" — Swift payment-detail String(format:) style, no grouping. */
    fun moneyPlain(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    /** "1.3" — 0–2 fraction digits (weights). */
    fun compact(value: Double): String = decimal(0, 2).format(value)

    /** "$1,550.00" */
    fun price(value: Double?): String = value?.let { "$" + money(it) } ?: "-"

    /** "USD 403.35" */
    fun usd(value: Double?): String = value?.let { "USD " + money(it) } ?: "-"

    /** "USD 403.35 / JMD 64,841.58" */
    fun usdJmd(usd: Double?, exchangeRate: Double): String =
        usd?.let { "USD ${money(it)} / JMD ${money(it * exchangeRate)}" } ?: "-"

    /** "USD 403.35 / JMD 64841.58" — no grouping, per payment-detail Swift. */
    fun usdJmdPlain(usd: Double?, exchangeRate: Double): String =
        usd?.let { "USD ${moneyPlain(it)} / JMD ${moneyPlain(it * exchangeRate)}" } ?: "-"

    /** Payment-detail Swift uses the same plain format, but guards zero values. */
    fun usdJmdPlainPositive(usd: Double?, exchangeRate: Double): String =
        usd?.takeIf { it > 0.0 }?.let { "USD ${moneyPlain(it)} / JMD ${moneyPlain(it * exchangeRate)}" } ?: "-"

    /** Weight rules: lbs → kg → raw → em dash. */
    fun weight(lbs: Double?, kg: String?, raw: String?): String = when {
        lbs != null -> "${compact(lbs)} lbs"
        !kg.isNullOrBlank() -> "${kg.trim()} kg"
        !raw.isNullOrBlank() -> raw.trim()
        else -> "—"
    }

    /** Drop-number formatting: prefix + last 11 digits grouped 3-4-4. */
    fun trackingCode(raw: String?): String {
        val stripped = raw.orEmpty().replace(" ", "")
        if (stripped.isEmpty()) return "-"
        val prefix = stripped.takeWhile { it.isLetter() }.uppercase(Locale.US)
        val digits = stripped.filter { it.isDigit() }.takeLast(11)
        val grouped = when {
            digits.length <= 3 -> digits
            digits.length <= 7 -> digits.dropLast(4) + " " + digits.takeLast(4)
            else -> {
                val tail8 = digits.takeLast(8)
                listOf(digits.dropLast(8), tail8.take(4), tail8.takeLast(4))
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
            }
        }
        return listOf(prefix, grouped).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "-" }
    }

    private val parsePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    private fun parseDate(iso: String?): Date? {
        val value = iso?.trim().orEmpty()
        if (value.isEmpty()) return null
        for (pattern in parsePatterns) {
            runCatching {
                return SimpleDateFormat(pattern, Locale.US).parse(value)
            }
        }
        return null
    }

    /** "Dec 15, 2024" — falls back to the raw string. */
    fun date(iso: String?): String {
        val parsed = parseDate(iso) ?: return iso?.takeIf { it.isNotBlank() } ?: "-"
        return SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed)
    }

    /** "12th Jan, 2024, 3:14pm" — Metro-step timeline date. */
    fun timelineDate(iso: String?): String {
        val parsed = parseDate(iso) ?: return iso?.takeIf { it.isNotBlank() } ?: "N/A"
        val day = SimpleDateFormat("d", Locale.US).format(parsed).toInt()
        val rest = SimpleDateFormat("MMM, yyyy, h:mma", Locale.US).format(parsed)
        return "$day${daySuffix(day)} ${rest.replace("AM", "am").replace("PM", "pm")}"
    }

    fun daySuffix(day: Int): String = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }

    /** FigmaPaymentsViewController.decodeHTMLEntities. */
    fun decodeHtmlEntities(s: String?): String = s.orEmpty()
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

    /** "some_product_name" → "Some Product Name". */
    fun titleCase(raw: String?): String {
        val cleaned = raw?.lowercase(Locale.US)?.replace("_", " ")?.trim().orEmpty()
        if (cleaned.isEmpty()) return "-"
        return cleaned.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase(Locale.US) }
        }
    }

    /** "PAYMENT" → "Payment". */
    fun capitalizeFirstWord(raw: String?): String {
        val cleaned = raw?.lowercase(Locale.US)?.trim().orEmpty()
        if (cleaned.isEmpty()) return "-"
        return cleaned.replaceFirstChar { it.uppercase(Locale.US) }
    }
}

// ─── Header Type (back variant) — Figma 40000643:22434 ─────────────────────

@Composable
fun ShipmentsDetailHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    rightIconRes: Int? = null,
    onRightClick: (() -> Unit)? = null,
    rightIconContentDescription: String? = null,
    rightIconTestTag: String? = null,
    // Swift uses Title2 (Bold 16) on the Payments/Orders/Order Details
    // headers and SubTitle1 elsewhere.
    titleStyle: TextStyle = AirdropType.subtitle1,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Swift makeInnerHeader: OPAQUE gray100 surface (never a
            // translucent wash) + 1pt bottom divider.
            .background(colors.gray100)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = Spacing.md, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_arrow),
                contentDescription = "Back",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack)
                    .padding(3.dp),
            )
            Text(
                text = title,
                style = titleStyle,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (rightIconRes != null) {
                val iconModifier = Modifier
                    .size(24.dp)
                    .then(if (rightIconTestTag != null) Modifier.testTag(rightIconTestTag) else Modifier)
                    .then(if (onRightClick != null) Modifier.clickable(onClick = onRightClick) else Modifier)
                Image(
                    painter = painterResource(rightIconRes),
                    contentDescription = rightIconContentDescription,
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = iconModifier,
                )
            } else {
                Spacer(Modifier.size(24.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.divider)
        )
    }
}

/**
 * Header clearance for content under the fixed detail header — the real
 * status-bar inset + the 62dp bar + 1dp divider (the old hardcoded 106dp
 * assumed an iOS 44pt status bar and left a dead gap on Android).
 */
@Composable
fun shipmentsHeaderClearance(): androidx.compose.ui.unit.Dp {
    val statusBar = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    return statusBar + 63.dp
}

// ─── Type Input Field (search variant) — Figma 40001666:42200 ─────────────

enum class ShipmentsSearchIconPlacement { Leading, Trailing }

@Composable
fun ShipmentsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Item search",
    onSubmit: () -> Unit = {},
    iconPlacement: ShipmentsSearchIconPlacement = ShipmentsSearchIconPlacement.Leading,
    iconSize: Dp = 22.dp,
    horizontalPadding: Dp = Spacing.md,
    iconTextGap: Dp = Spacing.sm,
    testTag: String? = null,
    iconTestTag: String? = null,
) {
    val colors = AirdropTheme.colors
    // Swift conflict: Packages uses FigmaPackagesViewController:278-311
    // leading 22pt glass; Payments/Orders use trailing 18pt glass
    // (FigmaPaymentsViewController:254-276, FigmaOrdersViewController:251-273).
    @Composable
    fun SearchIcon(modifier: Modifier = Modifier) {
        Image(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = "Search",
            colorFilter = ColorFilter.tint(colors.textDescription),
            modifier = modifier
                .then(iconTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                .size(iconSize),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(colors.gray150, RoundedCornerShape(12.dp))
            .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(iconTextGap),
    ) {
        if (iconPlacement == ShipmentsSearchIconPlacement.Leading) {
            SearchIcon()
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = placeholder, style = AirdropType.body2, color = colors.textDescription)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
                cursorBrush = SolidColor(BrandPalette.OrangeMain),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (iconPlacement == ShipmentsSearchIconPlacement.Trailing) {
            SearchIcon()
        }
    }
}

// ─── Section Title + List card rows — Figma "Section Title"/"List card" ────

@Composable
fun ShipmentsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = AirdropType.title2,
    showChevron: Boolean = true,
    headerDividerTestTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AirdropTheme.colors
    // Swift: summary cards COLLAPSE on header tap, chevron rotates.
    var expanded by remember { mutableStateOf(true) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.gray300, RoundedCornerShape(Radius.s)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.gray200)
                .then(
                    if (showChevron) Modifier.clickable { expanded = !expanded }
                    else Modifier
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = titleStyle, color = colors.textDarkTitle)
            if (showChevron) {
                Image(
                    painter = painterResource(R.drawable.ic_small_arrow_down),
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
            } else {
                Spacer(Modifier.size(24.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.gray300)
                .then(if (headerDividerTestTag != null) Modifier.testTag(headerDividerTestTag) else Modifier)
        )
        if (expanded) content()
    }
}

/** Key/value "List card" row (subtitle2 key, subtitle1 value). */
@Composable
fun ShipmentsListRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AirdropTheme.colors.textDarkTitle,
    valueStyle: TextStyle = AirdropType.subtitle1,
    showDivider: Boolean = true,
) {
    val colors = AirdropTheme.colors
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1)
        ) {
            Text(text = label, style = AirdropType.subtitle2, color = colors.textDescription)
            Text(text = value, style = valueStyle, color = valueColor)
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

/** Label-above-value block used inside Package/Payment/Order cards. */
@Composable
fun CardFieldColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AirdropTheme.colors.textDarkTitle,
    valueMaxLines: Int = Int.MAX_VALUE,
) {
    val colors = AirdropTheme.colors
    Column(modifier.fillMaxWidth()) {
        Text(text = label, style = AirdropType.subtitle2, color = colors.textDescription)
        Text(
            text = value,
            style = AirdropType.title2,
            color = valueColor,
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Package Card — Figma 40000643:22997 ───────────────────────────────────

@Composable
fun PackageCard(
    pkg: ShipmentPackage,
    exchangeRate: Double,
    onClick: () -> Unit,
    onToggleCart: () -> Unit,
    inCart: Boolean,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    cartToggleTestTag: String? = null,
) {
    val colors = AirdropTheme.colors
    val method = ShipmentMethodUi.from(pkg.shippingMethod)
    // Swift (FigmaPackagesViewController.swift:358) falls back to 0 when there
    // are no additional charges, so the row always shows a money string
    // ("USD 0.00 / JMD 0.00"), never "-". An empty map sums to 0.0.
    val chargesTotal = pkg.additionalChargesTotal ?: pkg.additionalCharges.values.sum()
    val rate = pkg.exchangeRate ?: exchangeRate

    Column(
        modifier = modifier
            // Swift pins 280x280 but its content (54 strip + 4 line-height
            // rows) measures ~298 and UIKit lets the stack overflow the
            // card. Wrap height here instead — every row is maxLines=1 so
            // all cards stay equal-height with the full Status row visible.
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .clickable(onClick = onClick),
    ) {
        // Method strip — Swift topBar (54pt): icon + label BOTH in the
        // method color (icon primary/secondary = methodColor), Title2.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(colors.gray150, RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
                .border(1.dp, colors.iconShape, RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
                .then(testTag?.let { Modifier.testTag("$it-method-strip") } ?: Modifier)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Image(
                painter = painterResource(method.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(method.tint),
                modifier = Modifier
                    .size(24.dp)
                    .then(testTag?.let { Modifier.testTag("$it-method-icon") } ?: Modifier),
            )
            Text(text = method.title, style = AirdropType.title2, color = method.tint)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Swift: body top 14, sides 20, bottom ≥16.
                .padding(start = Spacing.md, end = Spacing.md, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CardFieldColumn(
                label = "Description",
                value = pkg.description?.replaceFirstChar { it.uppercase(Locale.US) } ?: "—",
                valueMaxLines = 1,
            )
            CardFieldColumn(
                label = "Weight",
                value = ShipmentsFormat.weight(pkg.weightLbs, pkg.weightKg, pkg.weight),
                valueMaxLines = 1,
            )
            CardFieldColumn(
                label = "Total Charges",
                value = ShipmentsFormat.usdJmd(chargesTotal, rate),
                valueColor = BrandPalette.OrangeMain,
                valueMaxLines = 1,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(testTag?.let { Modifier.testTag("$it-status-row") } ?: Modifier),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Status", style = AirdropType.subtitle2, color = colors.textDescription)
                    Text(
                        text = pkg.statusName ?: pkg.status ?: "—",
                        style = AirdropType.title2,
                        color = AlertPalette.Completed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = testTag?.let { Modifier.testTag("$it-status-value") } ?: Modifier,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .then(cartToggleTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                        .clickable(onClick = onToggleCart),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(if (inCart) R.drawable.ic_check else R.drawable.ic_add),
                        contentDescription = if (inCart) "In cart" else "Add to cart",
                        colorFilter = ColorFilter.tint(
                            if (inCart) BrandPalette.OrangeMain else colors.iconSelected
                        ),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

// ─── Payment Card — Figma 40000643:23030 ───────────────────────────────────

@Composable
fun PaymentCard(
    payment: ShipmentPayment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDownloadInvoice: (() -> Unit)? = null,
    downloadingInvoice: Boolean = false,
    testTag: String? = null,
) {
    val colors = AirdropTheme.colors
    // Swift FigmaPaymentsViewController.swift:328-355 — download button pinned
    // top-right (22pt DownloadFile glyph tinted textDescription); rows inset
    // on the right to clear it.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Reserve the top-right corner for the download control.
            val rowEndInset = if (onDownloadInvoice != null) 32.dp else 0.dp
            CardFieldColumn(
                label = "Invoice Number",
                value = payment.invoiceId ?: "-",
                modifier = Modifier.padding(end = rowEndInset),
            )
            CardFieldColumn(label = "Drop Number", value = ShipmentsFormat.trackingCode(payment.trackingCode))
            CardFieldColumn(
                label = "Description",
                value = ShipmentsFormat.decodeHtmlEntities(
                    payment.packageDescription ?: payment.paymentType
                ).ifBlank { "-" },
            )
            CardFieldColumn(label = "Date", value = ShipmentsFormat.date(payment.paymentDate))
            Column {
                Text(text = "Amount", style = AirdropType.subtitle2, color = colors.textDescription)
                Text(
                    text = ShipmentsFormat.price(payment.totalAmount),
                    style = AirdropType.title2,
                    color = BrandPalette.OrangeMain,
                )
            }
        }
        if (onDownloadInvoice != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (downloadingInvoice) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = BrandPalette.OrangeMain,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_download_file),
                        contentDescription = "Download invoice",
                        colorFilter = ColorFilter.tint(colors.textDescription),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(onClick = onDownloadInvoice),
                    )
                }
            }
        }
    }
}

// ─── Order Card — Figma 40000643:23045 ─────────────────────────────────────

@Composable
fun OrderCard(
    order: ShipmentOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            // Swift makeOrderCard: fixed 280x380.
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(colors.gray150, RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
                .border(1.dp, colors.iconShape, RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
                // Swift: photo inset 20 on all sides of the 160 panel.
                .padding(Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(
                model = order.productImage,
                contentDescription = order.title,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Image(
                        painter = painterResource(R.drawable.ic_shop),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.gray400),
                        modifier = Modifier.size(80.dp),
                    )
                },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Swift: body top 14, sides 20, bottom ≥16.
                .padding(start = Spacing.md, end = Spacing.md, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CardFieldColumn(
                label = "Order Description",
                value = order.title ?: "-",
                valueMaxLines = 2,
            )
            CardFieldColumn(
                label = "Package Value",
                // Swift makeKeyValue (FigmaOrdersViewController.swift:349) renders
                // this in text.dark_title, NOT orange; formatUSD already gives "USD".
                value = ShipmentsFormat.usd(order.invoiceAmountUsd),
                valueMaxLines = 1,
            )
            CardFieldColumn(
                label = "Status",
                value = ShipmentsFormat.titleCase(order.orderStatus ?: order.status),
                valueColor = AlertPalette.Pending,
                valueMaxLines = 1,
            )
        }
    }
}

// ─── Metro step — Figma "Metro Step Card" 40001677:46332 ───────────────────

@Composable
fun MetroStep(
    iconRes: Int,
    title: String,
    titleColor: Color,
    date: String,
    showConnector: Boolean,
    modifier: Modifier = Modifier,
    connectorColor: Color = titleColor,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm1),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(vertical = 2.dp)) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(titleColor),
                    modifier = Modifier.size(24.dp),
                )
            }
            if (showConnector) {
                Box(
                    Modifier
                        .width(1.dp)
                        .weight(1f)
                        .background(connectorColor)
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = Spacing.sm)
        ) {
            Text(text = title, style = AirdropType.subtitle1, color = titleColor)
            Text(text = date, style = AirdropType.body3, color = colors.textPlaceholder)
        }
    }
}

// ─── Total Airdrop Charges box — Figma 40001464:31296 ──────────────────────

@Composable
fun TotalChargesBox(
    value: String,
    modifier: Modifier = Modifier,
    // Swift OrderDetails renders Total in orangeTertiary1 (#994D00);
    // PackageDetails keeps orangeMain.
    textColor: Color = BrandPalette.OrangeMain,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (colors.isDark) colors.peachLight else BrandPalette.OrangeTertiary6,
                RoundedCornerShape(Radius.xs),
            )
            .border(1.dp, colors.gray300, RoundedCornerShape(Radius.xs))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Total",
            style = AirdropType.title2,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = AirdropType.title2, color = textColor)
    }
}

// ─── Shared list scaffolding ───────────────────────────────────────────────

@Composable
fun ShipmentsLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = BrandPalette.OrangeMain,
            strokeWidth = 2.5.dp,
        )
    }
}

@Composable
fun ShipmentsEmptyLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = Spacing.xl), contentAlignment = Alignment.Center) {
        Text(text = text, style = AirdropType.body1, color = AirdropTheme.colors.textDescription)
    }
}

/**
 * Styled alert — Android counterpart of the Swift UIAlertControllers used
 * across the shipments VCs (CIF info, add-to-cart success, delete confirm).
 */
@Composable
fun ShipmentsAlertDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = null,
) {
    val colors = AirdropTheme.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.s))
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
        ) {
            Text(text = title, style = AirdropType.title1, color = colors.textDarkTitle)
            Text(text = message, style = AirdropType.body2, color = colors.textDescription)
            com.ga.airdrop.core.designsystem.components.GradientButton(
                text = confirmText,
                onClick = onConfirm,
            )
            if (dismissText != null) {
                com.ga.airdrop.core.designsystem.components.OutlineButton(
                    text = dismissText,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * "Header Section" — Swift makeSectionHeader: Title1 (Bold 18) section title
 * + underlined Body2 "View More" in orangeMain, baseline-aligned.
 */
@Composable
fun SectionHeaderRow(
    title: String,
    actionText: String,
    onAction: () -> Unit,
    actionTestTag: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = AirdropType.title1, color = AirdropTheme.colors.textDarkTitle)
        Text(
            text = actionText,
            style = AirdropType.body2.copy(
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            ),
            color = BrandPalette.OrangeMain,
            modifier = Modifier
                .then(actionTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                .clickable(onClick = onAction),
        )
    }
}
