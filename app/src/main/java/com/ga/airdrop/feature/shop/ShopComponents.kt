package com.ga.airdrop.feature.shop

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Cairo
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import java.util.Locale

/* ─── Formatting (Swift displayPrice / renderTotal parity) ─────────────── */

fun formatUsd(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)

fun formatUsdPlain(value: Double): String = String.format(Locale.US, "USD %.2f", value)

fun formatJmd(value: Double): String = "JA$" + String.format(Locale.US, "%,.2f", value)

/* ─── Stripe hosted checkout / external links (androidx.browser) ───────── */

/** Opens [url] in a Chrome Custom Tab (Swift SFSafariViewController parity). */
fun launchExternalUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching {
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(android.graphics.Color.parseColor("#F15114"))
                    .build()
            )
            .build()
            .launchUrl(context, uri)
    }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

/* ─── InnerHeader — Figma "Header Type" (back + title + trailing) ──────── */

@Composable
fun ShopInnerHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = AirdropType.subtitle1,
    trailing: @Composable () -> Unit = {},
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.gray100)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = Spacing.md)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_arrow),
                contentDescription = "Back",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(24.dp)
                    .clickable(onClick = onBack),
            )
            Text(
                text = title,
                style = titleStyle,
                color = colors.textDarkTitle,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.iconShape)
        )
    }
}

/** Cart icon + orange count pill, used as InnerHeader trailing content. */
@Composable
fun ShopHeaderCartIcon(count: Int, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Box {
        Image(
            painter = painterResource(R.drawable.ic_header_cart),
            contentDescription = "Cart",
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onClick),
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-3).dp)
                    .background(BrandPalette.OrangeMain, RoundedCornerShape(40.dp))
                    .padding(horizontal = Spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    style = TextStyle(
                        fontFamily = Cairo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                    ),
                    color = BrandPalette.White,
                )
            }
        }
    }
}

/* ─── Search field — Figma "Type Input Field" search variant ───────────── */

@Composable
fun ShopSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .background(colors.gray100, RoundedCornerShape(Radius.xs))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
            .padding(horizontal = Spacing.md, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm1),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textDescription),
                modifier = Modifier.size(24.dp),
            )
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = AirdropType.body2,
                        color = colors.textDescription,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
                    cursorBrush = SolidColor(BrandPalette.OrangeMain),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.ic_filter),
            contentDescription = "Filter",
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onFilterClick),
        )
    }
}

/* ─── Product card — Figma "Item Static" (ProductHighlightCard pattern) ── */

@Composable
fun ShopProductCard(
    product: ShopProduct,
    inCart: Boolean,
    onClick: () -> Unit,
    onToggleCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(135.dp)
                .clip(RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
                .background(colors.gray150)
                .border(1.dp, colors.iconShape, RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
                .padding(horizontal = Spacing.md, vertical = Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            var failed by remember(product.imageUrl) {
                mutableStateOf(product.imageUrl.isNullOrBlank())
            }
            if (failed) {
                // "empty status" variant — Airdrop logo 64x67 placeholder.
                Image(
                    painter = painterResource(R.drawable.img_airdrop_logo),
                    contentDescription = product.title,
                    modifier = Modifier
                        .width(64.dp)
                        .height(67.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onError = { failed = true },
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm1, vertical = Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = product.title,
                style = AirdropType.body2,
                color = colors.textDarkTitle,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatUsd(product.priceUsd),
                    style = AirdropType.title2,
                    color = BrandPalette.OrangeMain,
                )
                if (inCart) {
                    Image(
                        painter = painterResource(R.drawable.ic_check_box),
                        contentDescription = "Remove from cart",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onToggleCart),
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = "Add to cart",
                        colorFilter = ColorFilter.tint(colors.iconSelected),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onToggleCart),
                    )
                }
            }
        }
    }
}

/** Loading placeholder matching the Item Static card shape (Swift skeletons). */
@Composable
fun ShopSkeletonCard(modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(135.dp)
                .clip(RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
                .background(colors.gray150)
                .border(1.dp, colors.iconShape, RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm1, vertical = Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(end = Spacing.sm)
                    .height(14.dp)
                    .background(colors.gray300, RoundedCornerShape(6.dp))
            )
            Box(
                Modifier
                    .width(80.dp)
                    .height(18.dp)
                    .background(colors.gray300, RoundedCornerShape(6.dp))
            )
        }
    }
}

/* ─── Section header — Figma "Header Section" ──────────────────────────── */

@Composable
fun ShopSectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = AirdropType.title2, color = colors.textDarkTitle)
        Text(
            text = actionLabel,
            style = AirdropType.underlineLink.copy(
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            ),
            color = BrandPalette.OrangeMain,
            modifier = Modifier.clickable(onClick = onAction),
        )
    }
}

/* ─── Sort bottom sheet — Figma ActionBottomSheet (node 40001846:55036) ── */

enum class ShopSort(val label: String) {
    ALL("All Products"),
    PRICE_ASC("Low to High Price"),
    PRICE_DESC("High to Low Price"),
    NEWEST("Newest Products"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSortSheet(
    selected: ShopSort,
    onSelect: (ShopSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.sm)
                    .width(62.dp)
                    .height(5.dp)
                    .background(colors.gray400, RoundedCornerShape(100.dp))
            )
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Sorting by", style = AirdropType.title2, color = colors.textDarkTitle)
                Image(
                    painter = painterResource(R.drawable.ic_cross),
                    contentDescription = "Close",
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onDismiss),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
            ShopSort.entries.forEach { sort ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (sort == selected) colors.gray200 else colors.gray100)
                        .clickable { onSelect(sort) }
                ) {
                    Text(
                        text = sort.label,
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                }
            }
        }
    }
}

/* ─── Dropdown field — Figma "Type Input Field" with chevron ───────────── */

@Composable
fun ShopDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
) {
    val colors = AirdropTheme.colors
    var expanded by remember { mutableStateOf(false) }
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
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 50.dp)
                    .background(colors.gray150, RoundedCornerShape(Radius.xs))
                    .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
                    .clickable { expanded = true }
                    .padding(horizontal = Spacing.md, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = value, style = AirdropType.body2, color = colors.textDarkTitle)
                Image(
                    painter = painterResource(R.drawable.ic_small_arrow_down),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.iconSelected),
                    modifier = Modifier.size(24.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(text = option, style = AirdropType.body2, color = colors.textDarkTitle)
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/* ─── Back-chevron helper reused by list screens (rotated small arrow) ─── */

@Composable
fun ShopChevronRight(modifier: Modifier = Modifier) {
    val colors = AirdropTheme.colors
    Image(
        painter = painterResource(R.drawable.ic_small_arrow_down),
        contentDescription = null,
        colorFilter = ColorFilter.tint(colors.iconSelected),
        modifier = modifier
            .size(24.dp)
            .rotate(-90f),
    )
}
