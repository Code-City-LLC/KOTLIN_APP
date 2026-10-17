package com.ga.airdrop.feature.more2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropColorScheme
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.alertOnHold
import com.ga.airdrop.core.designsystem.theme.infoBoxBackground
import com.ga.airdrop.core.designsystem.theme.infoBoxBorder
import com.ga.airdrop.core.designsystem.theme.lightAirdropColors
import com.ga.airdrop.feature.calculator.BlueInfoCard

/**
 * Restricted Items — Swift `FigmaRestrictedItemsViewController` entry list plus
 * per-category `FigmaRestrictedItemsInfoViewController` detail state. Swift is
 * the precedence source where the current Figma nodes show stale/alternate flows.
 *
 * The category page and the Information page are internal navigation state
 * (BackHandler pops them), so no extra route is needed.
 */
@Composable
fun RestrictedItemsScreen(onBack: () -> Unit) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showLegalInfo by rememberSaveable { mutableStateOf(false) }
    // The category page's own search. It survives a visit to Information and
    // a change of tab, and clears when the page closes.
    var categoryQuery by rememberSaveable { mutableStateOf("") }
    val category = selectedCategory?.let { name ->
        RestrictedCategory.entries.firstOrNull { it.displayName == name }
    }
    fun closeCategory() {
        selectedCategory = null
        categoryQuery = ""
    }

    BackHandler(enabled = showLegalInfo || category != null) {
        if (showLegalInfo) showLegalInfo = false else closeCategory()
    }

    when {
        // Legal "Information" page — Swift FigmaRestrictedItemsViewController:201
        // pushes FigmaRestrictedItemsLegalInfoViewController from the list; a
        // category page opens the same page from its own header (Kemar
        // 2026-10-17), and Back returns to that category.
        showLegalInfo -> RestrictedItemsLegalInfo(onBack = { showLegalInfo = false })
        category != null -> RestrictedItemsInfo(
            category = category,
            query = categoryQuery,
            onQueryChange = { categoryQuery = it },
            onSelectCategory = { selectedCategory = it.displayName },
            onOpenInformation = { showLegalInfo = true },
            onBack = { closeCategory() },
        )
        else -> RestrictedItemsList(
            onBack = onBack,
            onOpenCategory = { selectedCategory = it.displayName },
            onOpenInformation = { showLegalInfo = true },
        )
    }
}

/**
 * The header's info button — Swift figma.restricted.info
 * (FigmaRestrictedItemsViewController:142-184): a 36dp target 12dp from the
 * trailing edge with the 24dp info-circle glyph in the title colour. The list
 * and every category page open the Information page with it.
 */
@Composable
private fun RestrictedInfoButton(onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Box(
        modifier = Modifier
            .size(36.dp)
            .testTag("restricted-info-button")
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            // ic_calc_info_circle is Swift's 24-unit FigmaIcon.infoCircle path
            // without its 2-unit margin, so 2dp of padding makes the 24dp icon.
            painter = painterResource(R.drawable.ic_calc_info_circle),
            contentDescription = "Restricted items information",
            colorFilter = ColorFilter.tint(colors.textDarkTitle),
            modifier = Modifier
                .size(24.dp)
                .testTag("restricted-info-icon")
                .padding(2.dp),
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
            rightEndPadding = 12.dp,
            rightContent = { RestrictedInfoButton(onClick = onOpenInformation) },
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
                        NoResultsCard(query = query, testTag = "restricted-search-empty")
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

/**
 * The item search field, on the list and on every category page. [tag]
 * prefixes its test tags ("$tag-row", "$tag-input").
 */
@Composable
private fun SearchRow(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "restricted-search",
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("$tag-row")
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
                    .testTag("$tag-input"),
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

/** "No results found" for a search, on the list and on a category page. */
@Composable
private fun NoResultsCard(query: String, testTag: String) {
    val colors = AirdropTheme.colors
    More2OuterCard(modifier = Modifier.testTag(testTag)) {
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

/**
 * A category page. Kemar, 2026-10-17: "At the top of each restricted items, we
 * should have the bar ... where we can swipe from one item to the next instead
 * of having to go back. And we should also have the search bar." Figma
 * 40001432:14918 / 15045 / 18025 / 18303 / 18506; behaviour from RN
 * RestrictedItemsScreenView. Under the "Restricted Items" header and its info
 * button sit the list's search field, 20dp down, then the category tab bar,
 * 20dp under that, then the category's content as it was. A tab or a
 * horizontal swipe on the content changes the category in place.
 */
@Composable
private fun RestrictedItemsInfo(
    category: RestrictedCategory,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectCategory: (RestrictedCategory) -> Unit,
    onOpenInformation: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val scrollState = rememberScrollState()
    val selectCategory by rememberUpdatedState(onSelectCategory)
    // A new category reads from its top.
    LaunchedEffect(category) { scrollState.scrollTo(0) }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("restricted-detail-root-${category.name}")
            .background(colors.gray200)
    ) {
        More2InnerHeader(
            title = "Restricted Items",
            onBack = onBack,
            rightEndPadding = 12.dp,
            rightContent = { RestrictedInfoButton(onClick = onOpenInformation) },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .testTag("restricted-detail-scroll"),
        ) {
            Spacer(Modifier.height(Spacing.md))
            SearchRow(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.padding(horizontal = Spacing.md),
                tag = "restricted-detail-search",
            )
            Spacer(Modifier.height(Spacing.md))
            RestrictedCategoryTabs(selected = category, onSelect = onSelectCategory)
            Spacer(Modifier.height(Spacing.md))
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("restricted-detail-content")
                    // Swipe left for the next category, right for the previous
                    // one. Only a horizontal drag is taken; a vertical one
                    // still scrolls the page.
                    .pointerInput(category) {
                        val threshold = SWIPE_THRESHOLD.toPx()
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                val step = when {
                                    dragged <= -threshold -> 1
                                    dragged >= threshold -> -1
                                    else -> 0
                                }
                                if (step != 0) category.adjacent(step)?.let(selectCategory)
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            dragged += dragAmount
                        }
                    }
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm1),
            ) {
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

                // RN RestrictedItemsScreenView: the search filters the
                // category's items; the intro and notes stay.
                val shownItems = category.itemsMatching(query)
                if (shownItems.isEmpty()) {
                    NoResultsCard(
                        query = query.trim(),
                        testTag = "restricted-detail-search-empty",
                    )
                }
                shownItems.forEachIndexed { index, item ->
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
                        footer = "Please note: All packages will be handled as Airdrop " +
                            "unless identified otherwise in address line 2.",
                        testTag = "restricted-note-BATTERY",
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** How far a horizontal drag on a category's content must travel to change category. */
private val SWIPE_THRESHOLD = 56.dp

// ── Category tab bar (Figma Tabs 40001949:61968, RN RestrictedItemsScreenView) ──

/** A tab's colour (border, and fill when active), glyph, and content colour when active. */
private class CategoryTabStyle(val accent: Color, val iconRes: Int, val activeContent: Color)

/**
 * RN TAB_STYLES / Figma "Function Buttons Desktop": green, on-hold blue,
 * yellow, orange and purple. Active, a tab fills with its colour and draws
 * its icon and label in white, except yellow, which Figma draws in #292929.
 */
private fun categoryTabStyle(category: RestrictedCategory, colors: AirdropColorScheme): CategoryTabStyle =
    when (category) {
        RestrictedCategory.PERMITTED -> CategoryTabStyle(
            AlertPalette.Completed,
            R.drawable.ic_restricted_tab_permitted,
            BrandPalette.White,
        )
        RestrictedCategory.LICENSE_REQUIRED -> CategoryTabStyle(
            colors.alertOnHold,
            R.drawable.ic_restricted_tab_license_required,
            BrandPalette.White,
        )
        RestrictedCategory.RESTRICTED_COMMODITIES -> CategoryTabStyle(
            Color(0xFFFFC600),
            R.drawable.ic_restricted_tab_restricted_commodities,
            // #292929 on yellow in both themes.
            lightAirdropColors.textDarkTitle,
        )
        RestrictedCategory.PROHIBITED -> CategoryTabStyle(
            BrandPalette.OrangeMain,
            R.drawable.ic_restricted_tab_prohibited,
            BrandPalette.White,
        )
        RestrictedCategory.CONDITIONAL_COMMODITIES -> CategoryTabStyle(
            Color(0xFFA855F7),
            R.drawable.ic_restricted_tab_conditional_commodities,
            BrandPalette.White,
        )
    }

/**
 * The five categories in a horizontally scrolling row: 20dp in from both
 * ends, 10dp apart, no scroll indicator. The active tab scrolls until its
 * left edge sits at the 20dp inset, as far as the row can scroll.
 */
@Composable
private fun RestrictedCategoryTabs(
    selected: RestrictedCategory,
    onSelect: (RestrictedCategory) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected.ordinal)
    LaunchedEffect(selected) { listState.animateScrollToItem(selected.ordinal) }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("restricted-tabs"),
        contentPadding = PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(RestrictedCategory.entries, key = { it.name }) { category ->
            CategoryTab(
                category = category,
                active = category == selected,
                onClick = { onSelect(category) },
            )
        }
    }
}

/**
 * One tab: 40dp tall, radius 10, a 1dp border in its colour, 20dp sides, a
 * 24dp icon, 10dp, then the label in Cairo SemiBold 16. Inactive tabs are the
 * card colour with title-coloured icon and label.
 */
@Composable
private fun CategoryTab(category: RestrictedCategory, active: Boolean, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    val style = categoryTabStyle(category, colors)
    val content = if (active) style.activeContent else colors.textDarkTitle
    val shape = RoundedCornerShape(Radius.xs)
    Row(
        modifier = Modifier
            .height(40.dp)
            .testTag("restricted-tab-${category.name}")
            .clip(shape)
            .background(if (active) style.accent else colors.gray100)
            .border(1.dp, style.accent, shape)
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(style.iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(content),
            // Each glyph at its own size, centred: the 20x19 triangle stays
            // 20dp wide in the 24dp slot, as in Figma.
            contentScale = ContentScale.Inside,
            modifier = Modifier
                .size(24.dp)
                .testTag("restricted-tab-icon-${category.name}"),
        )
        Text(
            text = category.tabLabel,
            style = AirdropType.button,
            color = content,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ── Legal "Information" page (Swift FigmaRestrictedItemsLegalInfoViewController) ──

/**
 * The Information page, Figma 40001432:14025 (Kemar 2026-10-17: "The
 * information box ... when it's clicked, it should generate this page"), in
 * Swift's order: the title, a card with the introduction and the labeling
 * checks, the Legal Notice and U.S. authorities notices, then the Jamaican
 * authorities card.
 */
@Composable
private fun RestrictedItemsLegalInfo(onBack: () -> Unit) {
    val colors = AirdropTheme.colors
    val uriHandler = LocalUriHandler.current
    fun openLink(url: String) {
        // Swift shows an "Unable to open link" alert on failure; a silent
        // no-op keeps the page usable when no browser is available.
        runCatching { uriHandler.openUri(url) }
    }
    val linkStyle = AirdropType.body2.copy(
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline,
    )

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
                .padding(
                    start = Spacing.md,
                    top = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = RestrictedLegalInfo.TITLE,
                style = AirdropType.h6,
                color = colors.textDarkTitle,
                modifier = Modifier.testTag("restricted-legal-title"),
            )

            LegalCard(testTag = "restricted-legal-labeling-card") {
                Text(
                    text = RestrictedLegalInfo.INTRO,
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
                Text(
                    text = RestrictedLegalInfo.LABELING_TITLE,
                    style = AirdropType.title1,
                    color = colors.textDarkTitle,
                )
                Column {
                    Text(
                        text = RestrictedLegalInfo.LABELING_INTRO,
                        style = AirdropType.body2,
                        color = colors.textDescription,
                    )
                    RestrictedLegalInfo.labelingBullets.forEachIndexed { index, bullet ->
                        // Figma's disc list: the text 21dp in, its dot before it.
                        Row(Modifier.testTag("restricted-legal-bullet-$index")) {
                            Text(
                                text = "\u2022",
                                style = AirdropType.body2,
                                color = colors.textDescription,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(21.dp),
                            )
                            Text(
                                text = bullet,
                                style = AirdropType.body2,
                                color = colors.textDescription,
                            )
                        }
                    }
                }
            }

            BlueInfoCard(
                title = RestrictedLegalInfo.LEGAL_NOTICE_TITLE,
                text = {
                    Text(
                        text = RestrictedLegalInfo.LEGAL_NOTICE_BODY,
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                    )
                },
                modifier = Modifier.testTag("restricted-legal-notice"),
            )
            BlueInfoCard(
                title = RestrictedLegalInfo.US_AUTHORITIES_TITLE,
                text = {
                    Text(
                        text = RestrictedLegalInfo.US_AUTHORITIES_BODY,
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                    )
                },
                modifier = Modifier.testTag("restricted-legal-us-authorities"),
            )

            LegalCard(testTag = "restricted-legal-jm-authorities") {
                Text(
                    text = RestrictedLegalInfo.JM_AUTHORITIES_TITLE,
                    style = AirdropType.title1,
                    color = colors.textDarkTitle,
                )
                Column {
                    Text(
                        text = RestrictedLegalInfo.JM_AUTHORITIES_INTRO,
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                    )
                    RestrictedLegalInfo.jamaicanAuthorities.forEachIndexed { index, authority ->
                        Text(
                            text = authority.name,
                            style = AirdropType.body2.copy(fontWeight = FontWeight.Bold),
                            color = colors.textDarkTitle,
                        )
                        Text(
                            text = authority.label,
                            style = linkStyle,
                            color = BrandPalette.OrangeMain,
                            modifier = Modifier
                                .testTag("restricted-legal-authority-link-$index")
                                .clickable { openLink(authority.url) },
                        )
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        append(RestrictedLegalInfo.JM_AUTHORITIES_FOOTER)
                        append("\n\n")
                        append(RestrictedLegalInfo.DISCLAIMER)
                        append("\n")
                        append(RestrictedLegalInfo.BIS_LEAD_IN)
                        append(" ")
                        withLink(
                            LinkAnnotation.Clickable("bis") { openLink(RestrictedLegalInfo.bisLink.url) },
                        ) {
                            withStyle(
                                SpanStyle(
                                    color = BrandPalette.OrangeMain,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) { append(RestrictedLegalInfo.bisLink.label) }
                        }
                        append(".")
                    },
                    style = AirdropType.body2,
                    color = colors.textDarkTitle,
                    modifier = Modifier.testTag("restricted-legal-closing"),
                )
            }
        }
    }
}

/** The Information page's white cards: 20dp sides, 15dp top and bottom, 10dp between blocks. */
@Composable
private fun LegalCard(testTag: String, content: @Composable ColumnScope.() -> Unit) {
    More2OuterCard(modifier = Modifier.testTag(testTag)) {
        Column(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
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
            .background(colors.infoBoxBackground)
            .border(1.dp, colors.infoBoxBorder, RoundedCornerShape(Radius.s))
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
                color = if (colors.isDark) colors.textDarkTitle else colors.textDescription,
            )
        }
    }
}
