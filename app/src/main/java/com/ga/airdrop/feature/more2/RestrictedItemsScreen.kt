package com.ga.airdrop.feature.more2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Restricted Items — Swift `FigmaRestrictedItemsViewController` entry list plus
 * per-category `FigmaRestrictedItemsInfoViewController` detail state. Swift is
 * the precedence source where the current Figma nodes show stale/alternate flows.
 *
 * The info variant is internal navigation state (BackHandler pops it) so no
 * extra route is needed.
 */
@Composable
fun RestrictedItemsScreen(onBack: () -> Unit) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showLegalInfo by rememberSaveable { mutableStateOf(false) }
    val category = selectedCategory?.let { name ->
        RestrictedCategory.entries.firstOrNull { it.displayName == name }
    }

    BackHandler(enabled = showLegalInfo || category != null) {
        if (showLegalInfo) showLegalInfo = false else selectedCategory = null
    }

    when {
        // Legal "Information" page — Swift FigmaRestrictedItemsViewController:201
        // pushes FigmaRestrictedItemsLegalInfoViewController from the list.
        showLegalInfo -> RestrictedItemsLegalInfo(onBack = { showLegalInfo = false })
        category != null ->
            RestrictedItemsInfo(category = category, onBack = { selectedCategory = null })
        else -> RestrictedItemsList(
            onBack = onBack,
            onOpenCategory = { selectedCategory = it.displayName },
            onOpenInformation = { showLegalInfo = true },
        )
    }
}

// ── Entry list ──

@Composable
private fun RestrictedItemsList(
    onBack: () -> Unit,
    onOpenCategory: (RestrictedCategory) -> Unit,
    onOpenInformation: () -> Unit,
) {
    val colors = AirdropTheme.colors
    var searchText by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("restricted-list-root")
            .background(colors.gray200)
    ) {
        More2InnerHeader(
            title = "Restricted Items",
            onBack = onBack,
            rightContent = {
                // Swift figma.restricted.info (FigmaRestrictedItemsViewController
                // :142-145) — top-right info button → legal Information page.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .size(36.dp)
                        .testTag("restricted-info-button")
                        .clickable(onClick = onOpenInformation),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_calc_info_circle),
                        contentDescription = "Restricted items information",
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("restricted-list-scroll")
                .padding(horizontal = Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Items that cannot be shipped through AirDrop",
                style = AirdropType.body2,
                color = colors.textDescription,
            )
            Spacer(Modifier.height(Spacing.sm1))
            Text(
                text = "* Item eligibility can change by carrier, customs, and government " +
                    "agency approval. Search an item before shipping.",
                style = AirdropType.body3,
                color = colors.textDescription,
            )
            Spacer(Modifier.height(Spacing.md))

            SearchRow(value = searchText, onValueChange = { searchText = it })
            Spacer(Modifier.height(Spacing.md))

            val query = searchText.trim()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm1)) {
                if (query.isEmpty()) {
                    RestrictedCategory.entries.forEach { category ->
                        CategoryCard(category = category, onClick = { onOpenCategory(category) })
                    }
                } else {
                    val matches = RestrictedCategory.entries.flatMap { category ->
                        category.items.map { item -> category to item }
                    }.filter { (category, item) ->
                        listOf(category.displayName, item.title, item.detail.orEmpty())
                            .joinToString(" ")
                            .contains(query, ignoreCase = true)
                    }
                    if (matches.isEmpty()) {
                        More2OuterCard {
                            Text(
                                text = "No results found for \"$query\".",
                                style = AirdropType.body2,
                                color = colors.textDescription,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                            )
                        }
                    } else {
                        matches.forEachIndexed { index, (category, item) ->
                            SearchResultCard(
                                category = category,
                                item = item,
                                onClick = { onOpenCategory(category) },
                                testTag = "restricted-search-result-$index",
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun SearchRow(value: String, onValueChange: (String) -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("restricted-search-row")
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, colors.gray300, RoundedCornerShape(Radius.xs))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Item search",
                    style = AirdropType.body2,
                    color = colors.gray500,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
                cursorBrush = SolidColor(BrandPalette.OrangeMain),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restricted-search-input"),
            )
        }
        Spacer(Modifier.width(8.dp))
        Image(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.gray500),
            modifier = Modifier.size(18.dp),
        )
    }
}

private data class CategoryIconSpec(
    val iconRes: Int,
    val tint: Color? = null,
    val preserveVectorColors: Boolean = false,
)

/** Category glyphs follow the Swift factory mapping, reusing existing vectors. */
private fun categoryIcon(category: RestrictedCategory): CategoryIconSpec = when (category) {
    RestrictedCategory.PERMITTED ->
        CategoryIconSpec(R.drawable.ic_check, tint = AlertPalette.Completed)
    RestrictedCategory.LICENSE_REQUIRED ->
        CategoryIconSpec(R.drawable.ic_calc_info_circle, tint = AlertPalette.OnHold)
    RestrictedCategory.RESTRICTED_COMMODITIES ->
        CategoryIconSpec(R.drawable.ic_shipments_status_dangerous_goods, preserveVectorColors = true)
    RestrictedCategory.PROHIBITED ->
        CategoryIconSpec(R.drawable.ic_info, tint = AlertPalette.Error)
    RestrictedCategory.CONDITIONAL_COMMODITIES ->
        CategoryIconSpec(R.drawable.ic_package)
}

@Composable
private fun CategoryIconCircle(category: RestrictedCategory) {
    val colors = AirdropTheme.colors
    val spec = categoryIcon(category)
    Box(
        modifier = Modifier
            .size(36.dp)
            .testTag("restricted-category-icon-circle-${category.name}")
            .clip(CircleShape)
            .background(BrandPalette.OrangeTertiary5),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(spec.iconRes),
            contentDescription = null,
            colorFilter = if (spec.preserveVectorColors) {
                null
            } else {
                ColorFilter.tint(spec.tint ?: colors.textDarkTitle)
            },
            modifier = Modifier
                .size(22.dp)
                .testTag("restricted-category-icon-glyph-${category.name}"),
        )
    }
}

@Composable
private fun CategoryCard(category: RestrictedCategory, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("restricted-category-${category.name}")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconCircle(category)
        Spacer(Modifier.width(Spacing.sm1))
        Text(
            text = category.displayName,
            style = AirdropType.subtitle1,
            color = colors.textDarkTitle,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.gray500),
            modifier = Modifier
                .size(13.dp)
                .rotate(-90f),
        )
    }
}

@Composable
private fun SearchResultCard(
    category: RestrictedCategory,
    item: RestrictedItem,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(Spacing.sm1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm1),
    ) {
        CategoryIconCircle(category)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = category.displayName,
                style = AirdropType.subtitle3,
                color = BrandPalette.OrangeMain,
                maxLines = 1,
            )
            Text(
                text = item.title,
                style = AirdropType.subtitle2,
                color = colors.textDarkTitle,
            )
            if (!item.detail.isNullOrEmpty()) {
                Text(
                    text = item.detail,
                    style = AirdropType.body3,
                    color = colors.textDescription,
                )
            }
        }
    }
}

// ── Info variant (per-category detail) ──

@Composable
private fun RestrictedItemsInfo(category: RestrictedCategory, onBack: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .testTag("restricted-detail-root-${category.name}")
            .background(colors.gray200)
    ) {
        More2InnerHeader(title = category.displayName, onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("restricted-detail-scroll")
                .padding(horizontal = Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm1)) {
                // Intro card.
                More2OuterCard(
                    modifier = Modifier.testTag("restricted-detail-intro-card"),
                    background = colors.gray150,
                ) {
                    Column(
                        Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = category.headline,
                            style = AirdropType.subtitle1,
                            color = colors.textDarkTitle,
                        )
                        Text(
                            text = category.intro,
                            style = AirdropType.body2,
                            color = colors.textDescription,
                        )
                    }
                }

                category.items.forEachIndexed { index, item ->
                    More2OuterCard(
                        modifier = Modifier.testTag("restricted-detail-item-card-$index"),
                        background = colors.gray150,
                    ) {
                        Row(Modifier.padding(Spacing.md)) {
                            Box(
                                Modifier
                                    .padding(top = 8.dp)
                                    .size(4.dp)
                                    .testTag("restricted-detail-item-bullet-$index")
                                    .background(BrandPalette.OrangeMain)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = item.title,
                                    style = AirdropType.subtitle2,
                                    color = colors.textDarkTitle,
                                )
                                if (!item.detail.isNullOrEmpty()) {
                                    Text(
                                        text = item.detail,
                                        style = AirdropType.body3,
                                        color = colors.textDescription,
                                    )
                                }
                            }
                        }
                    }
                }

                category.carrierNote?.let { note ->
                    InfoNoteCard(
                        title = "Carriage is subject to carrier approval",
                        body = "and may require documentation such as Safety Data Sheets " +
                            "(SDS), packing declarations and quantity limits. Contact " +
                            "AirDrop before shipping.",
                        footer = "Please note: $note",
                        testTag = "restricted-note-CARRIER",
                    )
                }
                category.batteryPolicy?.let { policy ->
                    InfoNoteCard(
                        title = "Battery policy (Jamaica + IATA):",
                        body = policy,
                        footer = "Please note: All packages will be handled as AirDrop " +
                            "Standard unless identified otherwise in address line 2.",
                        testTag = "restricted-note-BATTERY",
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

// ── Legal "Information" page (Swift FigmaRestrictedItemsLegalInfoViewController) ──

@Composable
private fun RestrictedItemsLegalInfo(onBack: () -> Unit) {
    val colors = AirdropTheme.colors
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    fun openLink(url: String) {
        // Swift shows an "Unable to open link" alert on failure; a silent
        // no-op keeps the page usable when no browser is available.
        runCatching { uriHandler.openUri(url) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("restricted-legal-root")
            .background(colors.gray200)
    ) {
        More2InnerHeader(title = "Information", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("restricted-legal-scroll")
                .padding(horizontal = Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm1)) {
                Text(
                    text = RestrictedLegalInfo.TITLE,
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                )

                InfoNoteCard(
                    title = RestrictedLegalInfo.LEGAL_NOTICE_TITLE,
                    body = RestrictedLegalInfo.LEGAL_NOTICE_BODY,
                    footer = null,
                    testTag = "restricted-legal-notice",
                )

                Text(
                    text = RestrictedLegalInfo.INTRO,
                    style = AirdropType.body2,
                    color = colors.textDarkTitle,
                )

                More2OuterCard(
                    modifier = Modifier.testTag("restricted-legal-labeling-card"),
                    background = colors.gray150,
                ) {
                    Column(
                        Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = RestrictedLegalInfo.LABELING_TITLE,
                            style = AirdropType.subtitle2,
                            color = colors.textDarkTitle,
                        )
                        Text(
                            text = RestrictedLegalInfo.LABELING_INTRO,
                            style = AirdropType.body2,
                            color = colors.textDarkTitle,
                        )
                        RestrictedLegalInfo.labelingBullets.forEachIndexed { index, bullet ->
                            Row {
                                Box(
                                    Modifier
                                        .padding(top = 8.dp)
                                        .size(4.dp)
                                        .testTag("restricted-legal-bullet-$index")
                                        .background(BrandPalette.OrangeMain)
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    text = bullet,
                                    style = AirdropType.body3,
                                    color = colors.textDarkTitle,
                                )
                            }
                        }
                    }
                }

                InfoNoteCard(
                    title = RestrictedLegalInfo.US_AUTHORITIES_TITLE,
                    body = RestrictedLegalInfo.US_AUTHORITIES_BODY,
                    footer = null,
                    testTag = "restricted-legal-us-authorities",
                )

                More2OuterCard(
                    modifier = Modifier.testTag("restricted-legal-jm-authorities"),
                    background = colors.gray150,
                ) {
                    Column(
                        Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = RestrictedLegalInfo.JM_AUTHORITIES_TITLE,
                            style = AirdropType.subtitle2,
                            color = colors.textDarkTitle,
                        )
                        Text(
                            text = RestrictedLegalInfo.JM_AUTHORITIES_INTRO,
                            style = AirdropType.body2,
                            color = colors.textDarkTitle,
                        )
                        RestrictedLegalInfo.jamaicanAuthorities.forEachIndexed { index, authority ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = authority.name,
                                    style = AirdropType.subtitle3,
                                    color = colors.textDarkTitle,
                                )
                                Text(
                                    text = authority.label,
                                    style = AirdropType.body2,
                                    color = BrandPalette.OrangeMain,
                                    modifier = Modifier
                                        .testTag("restricted-legal-authority-link-$index")
                                        .clickable { openLink(authority.url) },
                                )
                            }
                        }
                        Text(
                            text = RestrictedLegalInfo.JM_AUTHORITIES_FOOTER,
                            style = AirdropType.body2,
                            color = colors.textDarkTitle,
                        )
                        Text(
                            text = RestrictedLegalInfo.DISCLAIMER,
                            style = AirdropType.body2,
                            color = colors.textDarkTitle,
                        )
                        Text(
                            text = RestrictedLegalInfo.BIS_LEAD_IN,
                            style = AirdropType.body2,
                            color = colors.textDarkTitle,
                        )
                        Text(
                            text = RestrictedLegalInfo.bisLink.label,
                            style = AirdropType.body2,
                            color = BrandPalette.OrangeMain,
                            modifier = Modifier
                                .testTag("restricted-legal-bis-link")
                                .clickable { openLink(RestrictedLegalInfo.bisLink.url) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun InfoNoteCard(title: String, body: String, footer: String?, testTag: String) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(Radius.s))
            .background(AlertPalette.Light.OnHold)
            .border(1.dp, AlertPalette.Middle.OnHold, RoundedCornerShape(Radius.s))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Image(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(18.dp),
            )
            Text(text = title, style = AirdropType.subtitle2, color = colors.textDarkTitle)
        }
        Text(text = body, style = AirdropType.body2, color = colors.textDarkTitle)
        if (footer != null) {
            Text(
                text = footer,
                style = AirdropType.body2,
                color = colors.textDescription,
            )
        }
    }
}
