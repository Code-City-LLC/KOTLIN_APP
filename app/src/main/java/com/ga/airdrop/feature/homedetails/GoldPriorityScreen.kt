package com.ga.airdrop.feature.homedetails

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.TierPalette
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader

/**
 * Customer Tier pager — canon port of the Figma-audited Swift build
 * (FigmaGoldPriorityViewController @ staging/figma-redesign-testflight
 * b5e9a6f, SwiftForge 4db5d11/eece8be; Figma page frames 40001452:23823…),
 * rendering the SERVER-OWNED benefit copy from PR#46 (GET /service-tiers via
 * TierRepository — no hardcoded benefit strings survive here).
 *
 * Canon layers (Kemar 2026-07-11/12 — "we're going to Swift; the design
 * should be like Swift"):
 *  - Page gradient: linear 196.72° — start (0.64, 0) → end (0.36, 1).
 *  - FULL-BLEED contour lines: the exact Figma Shape export
 *    (drawable/tier_lines_shape.xml, 38 paths #FAEEED, byte-derived from the
 *    canon Swift asset, sha256 790a0ce3…) placed at (-122, -418) in 375-wide
 *    page space, scaled by screenWidth/375, at 5% alpha — the lines run
 *    behind the CTA and are CUT OFF by the screen bottom exactly like the
 *    design. Never a cell-clipped redraw.
 *  - Radial ellipse 284×284 at (-93, 151) behind the icon (Figma Ellipse
 *    3413, overlay-blend look approximated with the tier's own gradient).
 *  - Header on a rgba(41,41,41,.8)→0 gradient + per-tier 1px hairline;
 *    title is PAGE-RELATIVE: "Your Tier" only on the customer's own page,
 *    "Tier" everywhere else (recorded Kemar ruling — overrides the static
 *    "Customer Tier" frame label in raw Figma).
 *  - Component-36 dash strip: 35dp strip, 5dp dashes r2.5, 20dp sides,
 *    #292929 @ 10% wash.
 *  - Glass "light outline" CTA (Kemar): 316×50 r12, white 7% fill, 1px
 *    white 18% border, Cairo SemiBold 17 — TierRelation states verbatim
 *    from Swift (current / upgrade / activation / hidden).
 */

data class TierPage(
    val id: String,
    val name: String,
    val iconRes: Int,
    val hairline: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val inactiveDash: Color,
    /** Canonical API tier code (DIAM/PLAT/GOLD/RUBY/SAVR); null for the
     *  legacy-only pages (Inactive, Corporate) the API does not serve. */
    val apiCode: String?,
)

internal val tierPages = listOf(
    TierPage(
        id = "diamond", name = "Diamond Elite",
        iconRes = R.drawable.tier_icon_diamond,
        hairline = Color(0xFFACACAC),
        gradientTop = Color(0xFF6B6B6B), gradientBottom = Color(0xFF292929),
        inactiveDash = Color.Black.copy(alpha = 0.4f), apiCode = "DIAM",
    ),
    TierPage(
        id = "platinum", name = "Platinum Priority",
        iconRes = R.drawable.tier_icon_platinum,
        hairline = Color(0xFF656565),
        gradientTop = Color(0xFFCACACA), gradientBottom = Color(0xFF737373),
        inactiveDash = Color.White.copy(alpha = 0.3f), apiCode = "PLAT",
    ),
    TierPage(
        id = "gold", name = "Gold Standard",
        iconRes = R.drawable.tier_icon_gold,
        hairline = Color(0xFF766923),
        gradientTop = Color(0xFFEFBF04), gradientBottom = Color(0xFF8C6F01),
        inactiveDash = Color(0xFFEFBF04).copy(alpha = 0.6f), apiCode = "GOLD",
    ),
    TierPage(
        id = "ruby", name = "Ruby Starter",
        iconRes = R.drawable.tier_icon_ruby,
        hairline = Color(0xFF5C262E),
        gradientTop = TierPalette.BronzeSaver2, gradientBottom = Color(0xFF5C262E),
        inactiveDash = TierPalette.BronzeSaver2.copy(alpha = 0.6f), apiCode = "RUBY",
    ),
    TierPage(
        id = "sapphire", name = "Sapphire Saver",
        iconRes = R.drawable.tier_icon_sapphire,
        hairline = Color(0xFF0A96D4),
        gradientTop = TierPalette.CorporateBulk3, gradientBottom = TierPalette.CorporateBulk2,
        inactiveDash = TierPalette.CorporateBulk3.copy(alpha = 0.6f), apiCode = "SAVR",
    ),
    TierPage(
        id = "inactive", name = "Inactive",
        iconRes = R.drawable.tier_icon_inactive,
        hairline = Color(0xFF8C2F0C),
        gradientTop = Color(0xFFF1A88C), gradientBottom = Color(0xFF8C2F0C),
        inactiveDash = Color(0xFF5C262E).copy(alpha = 0.6f), apiCode = null,
    ),
    TierPage(
        id = "corporate", name = "Corporate",
        iconRes = R.drawable.tier_icon_corporate,
        hairline = Color(0xFF3A2663),
        gradientTop = TierPalette.PlatinumElite2, gradientBottom = Color(0xFF3A2663),
        inactiveDash = TierPalette.PlatinumElite2.copy(alpha = 0.6f), apiCode = null,
    ),
)

private val defaultTierIndex = tierPages.indexOfFirst { it.id == "gold" }

/** Swift FigmaGoldPriorityViewController.TierRelation — CTA state machine. */
internal enum class TierRelation { UPGRADE, DOWNGRADE, CURRENT, PREVIEW, ACTIVATION }

/**
 * Verbatim port of Swift `relation(forNavigatingTo:)` (staging b5e9a6f):
 * the Inactive page carries the activation CTA only when it IS the
 * customer's resolved state; Corporate (either side) is a preview; tiers are
 * ordered top→bottom so a lower index is a higher tier.
 */
internal fun tierRelation(pageIndex: Int, userTierIndex: Int?): TierRelation {
    val page = tierPages[pageIndex]
    if (page.id == "inactive") {
        return if (userTierIndex == pageIndex) TierRelation.ACTIVATION else TierRelation.DOWNGRADE
    }
    val user = userTierIndex ?: return TierRelation.PREVIEW
    if (user !in tierPages.indices) return TierRelation.PREVIEW
    if (page.id == "corporate" || tierPages[user].id == "corporate") return TierRelation.PREVIEW
    return when {
        pageIndex < user -> TierRelation.UPGRADE
        pageIndex > user -> TierRelation.DOWNGRADE
        else -> TierRelation.CURRENT
    }
}

@Composable
fun GoldPriorityScreen(
    onBack: () -> Unit,
    viewModel: GoldPriorityViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    GoldPriorityContent(
        onBack = onBack,
        resolvedTierIndex = state.resolvedTierIndex,
        benefitRowsByCode = state.benefitRowsByCode,
        catalogStatus = state.catalogStatus,
        onRetryBenefits = viewModel::retryBenefits,
    )
}

@Composable
internal fun GoldPriorityContent(
    onBack: () -> Unit,
    resolvedTierIndex: Int? = null,
    initialPage: Int = defaultTierIndex,
    benefitRowsByCode: Map<String, List<String>> = emptyMap(),
    catalogStatus: TierCatalogStatus = TierCatalogStatus.Loading,
    onRetryBenefits: () -> Unit = {},
) {
    ForceLightStatusBarIcons()
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(tierPages.indices)) { tierPages.size }

    // Pre-scroll once the user's tier resolves (Swift scrollToItem).
    LaunchedEffect(resolvedTierIndex) {
        resolvedTierIndex?.let { pagerState.scrollToPage(it) }
    }

    val activeIndex = pagerState.currentPage
    val activeTier = tierPages[activeIndex]
    val relation = tierRelation(activeIndex, resolvedTierIndex)
    val gradientTop by animateColorAsState(activeTier.gradientTop, label = "tierTop")
    val gradientBottom by animateColorAsState(activeTier.gradientBottom, label = "tierBottom")

    Box(
        Modifier
            .fillMaxSize()
            .testTag("gold-priority-root")
            .drawBehind {
                // Figma page gradient: linear 196.72° — start (0.64, 0) →
                // end (0.36, 1). (Swift TierGradientView; was a 45° diagonal
                // before the 2026-07-08 design audit.)
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(gradientTop, gradientBottom),
                        start = Offset(size.width * 0.64f, 0f),
                        end = Offset(size.width * 0.36f, size.height),
                    )
                )
            }
    ) {
        // Full-bleed Figma contour lines — behind ALL content, cut by the
        // screen bottom exactly like the design.
        TierLinesBackdrop()

        Column(Modifier.fillMaxSize()) {
            TierHeader(
                // Kemar ruling: "Your Tier" ONLY on the customer's actual
                // tier page; any other page is just "Tier".
                title = if (relation == TierRelation.CURRENT) "Your Tier" else "Tier",
                hairline = activeTier.hairline,
                onBack = onBack,
            )
            SwipeIndicator(activeIndex = activeIndex, activeTier = activeTier)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                TierPageContent(
                    tier = tierPages[page],
                    benefitRows = tierPages[page].apiCode?.let(benefitRowsByCode::get),
                    catalogStatus = catalogStatus,
                    onRetry = onRetryBenefits,
                )
            }
        }

        TierCtaButton(
            relation = relation,
            pageName = activeTier.name,
            onActivate = onBack,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ─── Full-bleed contour lines (Figma Shape export) ─────────────────────────

/**
 * Places the 1632.7×1847.93 Shape at (-122, -418) in 375-wide page space,
 * scaled by screenWidth/375 — Swift viewDidLayoutSubviews geometry. A custom
 * layout places the oversized art from the TOP-LEFT (Compose would otherwise
 * center an over-constrained child), and the parent clips to screen.
 */
@Composable
private fun TierLinesBackdrop() {
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
        val scale = maxWidth / 375f
        val artW = 1632.7f
        val artH = 1847.93f
        Image(
            painter = painterResource(R.drawable.tier_lines_shape),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            alpha = 0.05f,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val w = (artW * scale.toPx()).toInt()
                    val h = (artH * scale.toPx()).toInt()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            x = (-122f * scale.toPx()).toInt(),
                            y = (-418f * scale.toPx()).toInt(),
                        )
                    }
                }
                .testTag("gold-priority-lines"),
        )
    }
}

// ─── Header (gradient chrome + per-tier hairline, page-relative title) ─────

@Composable
private fun TierHeader(title: String, hairline: Color, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            // Figma Header Type: vertical gradient rgba(41,41,41,0.8) → 0
            // spanning the status bar + header row (Swift headerGradient).
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF292929).copy(alpha = 0.8f),
                        Color(0xFF292929).copy(alpha = 0f),
                    )
                )
            )
    ) {
        HomeDetailsHeader(
            title = title,
            onBack = onBack,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            titleStyle = AirdropType.subtitle1,
            showDivider = false,
        )
        // Per-tier 1px bottom border — the "…3" token of each page's set.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairline)
                .testTag("gold-priority-hairline")
        )
    }
}

// ─── Swipe indicator strip (Figma Component 36) ────────────────────────────

@Composable
private fun SwipeIndicator(activeIndex: Int, activeTier: TierPage) {
    // 35dp strip, 5dp-tall r2.5 dashes, 20dp sides, #292929 @ 10% wash.
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF292929).copy(alpha = 0.10f))
            .height(35.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tierPages.forEachIndexed { index, _ ->
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(if (index == activeIndex) Color.White else activeTier.inactiveDash)
            )
        }
    }
}

// ─── Tier page ─────────────────────────────────────────────────────────────

@Composable
private fun TierPageContent(
    tier: TierPage,
    benefitRows: List<String>?,
    catalogStatus: TierCatalogStatus,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Figma Ellipse 3413 — 284×284 at (-93, 151): soft radial glow from
        // the tier's own light color (RN EllipseBackground / Swift
        // TierEllipseView: top@55% center → top@25% → transparent).
        Box(
            Modifier
                .offset(x = (-93).dp, y = 151.dp)
                .size(284.dp)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            0.0f to tier.gradientTop.copy(alpha = 0.55f),
                            0.5f to tier.gradientTop.copy(alpha = 0.25f),
                            1.0f to tier.gradientBottom.copy(alpha = 0f),
                        )
                    )
                }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Figma: 30dp sides (Spaceing-s), 20 top; bottom reserves the
                // 50dp CTA + 12dp gap (Swift pins the scroll to the CTA top).
                .padding(horizontal = 30.dp)
                .padding(top = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 86.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gold-priority-title-row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                Image(
                    painter = painterResource(tier.iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(70.dp)
                        .testTag("gold-priority-tier-badge"),
                )
                TierNameText(
                    name = tier.name,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("gold-priority-tier-name"),
                )
            }
            Text(
                text = "Benefits",
                // Figma Title 2 — Cairo Bold 16 (Swift benefitsHeader).
                style = AirdropType.title2,
                color = Color.White,
            )
            if (!benefitRows.isNullOrEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    benefitRows.forEach { benefit ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BenefitCheck(
                                modifier = Modifier.padding(top = 1.dp)
                            )
                            Text(
                                text = benefit,
                                // Figma Body 1 — Cairo Regular 16.
                                style = AirdropType.body1,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                // Server-owned copy only (PR#46): while the catalog loads show
                // the loading line; a Failed catalog (or a code the server
                // doesn't return) gets the unavailable line + Retry.
                val loading = tier.apiCode != null && catalogStatus == TierCatalogStatus.Loading
                Text(
                    text = if (loading) "Loading benefits..." else "Tier benefits are unavailable.",
                    style = AirdropType.body1,
                    color = Color.White,
                    modifier = Modifier.testTag("gold-priority-benefits-state"),
                )
                if (!loading && tier.apiCode != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Retry",
                            style = AirdropType.subtitle2,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("gold-priority-benefits-retry")
                                .clickable(onClick = onRetry),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TierNameText(
    name: String,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth
        // Figma H4 — Cairo Bold 32; autoshrink to fit one line (Swift
        // adjustsFontSizeToFitWidth, minimumScaleFactor 0.7 → 32*0.7 ≈ 22).
        val fontSize = remember(name, maxWidthPx, textMeasurer) {
            if (maxWidthPx <= 0) {
                32.sp
            } else {
                (32 downTo 22).firstOrNull { candidate ->
                    !textMeasurer.measure(
                        text = AnnotatedString(name),
                        style = AirdropType.h4.copy(
                            fontSize = candidate.sp,
                            lineHeight = (candidate * 1.3f).sp,
                        ),
                        overflow = TextOverflow.Clip,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = maxWidthPx),
                    ).hasVisualOverflow
                }?.sp ?: 22.sp
            }
        }

        Text(
            text = name,
            style = AirdropType.h4.copy(
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.3f).sp,
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** White check in an 18%-white circle — Swift CheckmarkIconView, 24dp. */
@Composable
private fun BenefitCheck(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(12.dp),
        )
    }
}

// ─── Glass CTA (Swift TierCTAButton — Kemar: "light outline") ──────────────

/**
 * Swift TierCTAButton verbatim: 316×50, radius 12, white 7% fill, 1px white
 * 18% border, Cairo SemiBold 17, white title. Page-relative states from
 * [tierRelation]; DOWNGRADE/PREVIEW render nothing (never a downgrade sign).
 * CURRENT/UPGRADE taps are inert until the breakdown/change sheets land
 * (the narrow tier PR owns that wiring); ACTIVATION pops back to Home
 * (Swift behavior for the Inactive page's CTA).
 */
@Composable
private fun TierCtaButton(
    relation: TierRelation,
    pageName: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (relation) {
        TierRelation.CURRENT -> "Your Tier"
        TierRelation.UPGRADE -> "Upgrade to $pageName"
        TierRelation.ACTIVATION -> "Ship a package now to activate your account"
        TierRelation.DOWNGRADE, TierRelation.PREVIEW -> return
    }
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 12.dp)
            .size(width = 316.dp, height = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .then(
                if (relation == TierRelation.ACTIVATION) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onActivate,
                    )
                } else {
                    Modifier
                }
            )
            .testTag("gold-priority-cta"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AirdropType.subtitle1.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

// ─── Status bar ────────────────────────────────────────────────────────────

@Composable
private fun ForceLightStatusBarIcons() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view, context) {
        val window = context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousIsAppearanceLightStatusBars = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousIsAppearanceLightStatusBars
            }
        } else {
            onDispose {}
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
