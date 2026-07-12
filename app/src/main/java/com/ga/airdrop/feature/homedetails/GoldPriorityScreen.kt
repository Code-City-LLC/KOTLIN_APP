package com.ga.airdrop.feature.homedetails

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.flow.first

/**
 * Customer Tier swipe pager — canon Swift design (FigmaGoldPriorityViewController
 * @7d50d01): full-bleed contour lines from the canonical Figma Shape, 196.72°
 * page gradient, gradient header ("Your Tier" on the confirmed page, "Tier"
 * elsewhere), per-tier glyphs/hairlines, tinted outlined ticks, and the
 * light-outline change CTA (Kemar 2026-07-12). Data semantics per PR46 + the
 * current backend contract: benefits_summary rendered verbatim for
 * the five coded tiers; Inactive/Corporate carry Kemar-ruled DISPLAY-ONLY
 * static rows (#22424) and are never server tiers.
 */

data class TierPage(
    val id: String,
    /** Design name — server display_name overrides it when nonblank. */
    val name: String,
    val gradientTop: Color,
    val gradientBottom: Color,
    val inactiveDash: Color,
    /** Canonical Swift TierIcon_* glyph. */
    val glyphRes: Int,
    /** Per-tier 1px header hairline — each Figma page's "…3" token. */
    val hairline: Color,
    /** Laravel tier code (normalized uppercase); null for non-API pages. */
    val apiCode: String?,
    /**
     * Kemar-ruled display-only rows for the non-API pages (#22424) — verbatim
     * canonical Swift static catalogue. Never sourced from, or written to,
     * service_tiers. Null for coded tiers (server rows only).
     */
    val staticRows: List<String>? = null,
) {
    /**
     * The CURRENT-tier code this page represents. Every page has one —
     * INACTIVE and CORPORATE are valid server current-states the customer
     * cannot self-select (they stay out of change targets via apiCode=null).
     */
    val identityCode: String = apiCode ?: id.uppercase()
}

internal val tierPages = listOf(
    TierPage(
        id = "diamond", name = "Diamond Elite", apiCode = "DIAM",
        gradientTop = Color(0xFF6B6B6B), gradientBottom = Color(0xFF292929),
        inactiveDash = Color.Black.copy(alpha = 0.4f),
        glyphRes = R.drawable.tier_glyph_diamond,
        hairline = Color(0xFFACACAC),
    ),
    TierPage(
        id = "platinum", name = "Platinum Priority", apiCode = "PLAT",
        gradientTop = Color(0xFFCACACA), gradientBottom = Color(0xFF737373),
        inactiveDash = Color.White.copy(alpha = 0.3f),
        glyphRes = R.drawable.tier_glyph_platinum,
        hairline = Color(0xFF656565),
    ),
    TierPage(
        id = "gold", name = "Gold Standard", apiCode = "GOLD",
        gradientTop = Color(0xFFEFBF04), gradientBottom = Color(0xFF8C6F01),
        inactiveDash = Color(0xFFEFBF04).copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_gold,
        hairline = Color(0xFF766923),
    ),
    TierPage(
        id = "ruby", name = "Ruby Starter", apiCode = "RUBY",
        gradientTop = TierPalette.BronzeSaver2, gradientBottom = Color(0xFF5C262E),
        inactiveDash = TierPalette.BronzeSaver2.copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_ruby,
        hairline = Color(0xFF5C262E),
    ),
    TierPage(
        id = "sapphire", name = "Sapphire Saver", apiCode = "SAVR",
        gradientTop = TierPalette.CorporateBulk3, gradientBottom = TierPalette.CorporateBulk2,
        inactiveDash = TierPalette.CorporateBulk3.copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_sapphire,
        hairline = Color(0xFF0A96D4),
    ),
    TierPage(
        id = "inactive", name = "Inactive", apiCode = null,
        gradientTop = Color(0xFFF1A88C), gradientBottom = Color(0xFF8C2F0C),
        inactiveDash = Color(0xFF5C262E).copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_inactive,
        hairline = Color(0xFF8C2F0C),
        staticRows = listOf(
            "Access to account setup and onboarding assistance.",
            "Invitations to introductory offers or bonus AirCoins promos.",
            "Reactivation campaigns with one-time shipping credits.",
            "Auto-upgrade eligibility after their first paid shipment.",
        ),
    ),
    TierPage(
        id = "corporate", name = "Corporate", apiCode = null,
        gradientTop = TierPalette.PlatinumElite2, gradientBottom = Color(0xFF3A2663),
        inactiveDash = TierPalette.PlatinumElite2.copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_corporate,
        hairline = Color(0xFF3A2663),
        staticRows = listOf(
            "Expedited 24-hour processing for all cleared packages.",
            "Next-day shipping on all packages.",
            "Priority logging, warehouse handling, and customs clearance.",
            "Customized pricing agreements.",
            "Dedicated account manager.",
            "Monthly reporting and analytics.",
            "Warehouse coordination for large shipments.",
        ),
    ),
)

private val defaultTierIndex = tierPages.indexOfFirst { it.id == "gold" }

/** Page relationship relative to the customer's actual tier (Swift TierRelation). */
internal enum class TierRelation { Upgrade, Downgrade, Current, Preview, Activation }

internal fun tierRelation(pageIndex: Int, userTierIndex: Int?): TierRelation {
    val page = tierPages[pageIndex]
    if (page.id == "inactive") {
        return if (userTierIndex == pageIndex) TierRelation.Activation else TierRelation.Downgrade
    }
    if (userTierIndex == null) return TierRelation.Preview
    if (page.id == "corporate" || tierPages[userTierIndex].id == "corporate") {
        return TierRelation.Preview
    }
    return when {
        pageIndex < userTierIndex -> TierRelation.Upgrade
        pageIndex > userTierIndex -> TierRelation.Downgrade
        else -> TierRelation.Current
    }
}

/**
 * Benefit-tick tint — Kemar's rule (#21146): ticks sit close to the page
 * family, not stark white; the change-success check stays white.
 */
internal fun tierTickColor(page: TierPage): Color =
    lerp(page.gradientTop, Color.White, 0.35f)

/** What a page's Benefits section shows. */
internal sealed interface TierBenefitRows {
    /** Rows rendered verbatim and in order (server, or Kemar-ruled static). */
    data class Rows(val rows: List<String>) : TierBenefitRows
    /** Coded tier, catalogue still in flight. */
    data object Loading : TierBenefitRows
    /** Coded tier with no rows after a settled attempt — Retry, never client copy. */
    data object Failed : TierBenefitRows
}

internal fun benefitRowsFor(
    page: TierPage,
    benefitRowsByCode: Map<String, List<String>>,
    catalogStatus: TierCatalogStatus,
): TierBenefitRows {
    // Non-API pages: Kemar-ruled display-only static rows (#22424).
    val code = page.apiCode ?: return TierBenefitRows.Rows(page.staticRows.orEmpty())
    val rows = benefitRowsByCode[code]
    return when {
        !rows.isNullOrEmpty() -> TierBenefitRows.Rows(rows)
        catalogStatus == TierCatalogStatus.Loading -> TierBenefitRows.Loading
        else -> TierBenefitRows.Failed
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
        tierState = state,
        onRetryBenefits = viewModel::retryBenefits,
        onRetryCurrentTier = viewModel::retryCurrentTier,
        onRequestChange = viewModel::requestTierChange,
        onRetryConfirmation = viewModel::retryChangeConfirmation,
        onDismissError = viewModel::dismissChangeError,
        onConsumeSuccess = viewModel::consumeChangeSuccess,
    )
}

@Composable
internal fun GoldPriorityContent(
    onBack: () -> Unit,
    tierState: GoldPriorityUiState = GoldPriorityUiState(),
    initialPage: Int = defaultTierIndex,
    onRetryBenefits: () -> Unit = {},
    onRetryCurrentTier: () -> Unit = {},
    onRequestChange: (String) -> Unit = {},
    onRetryConfirmation: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onConsumeSuccess: () -> Unit = {},
) {
    ForceLightStatusBarIcons()
    val pagerState = rememberPagerState(
        initialPage = (tierState.resolvedTierIndex ?: initialPage).coerceIn(tierPages.indices),
    ) { tierPages.size }

    // Pre-scroll once the user's tier resolves (settled deterministic landing).
    LaunchedEffect(tierState.resolvedTierIndex) {
        val idx = tierState.resolvedTierIndex ?: return@LaunchedEffect
        snapshotFlow { pagerState.layoutInfo.viewportSize.width }.first { it > 0 }
        pagerState.scrollToPage(idx)
        if (pagerState.currentPage != idx) pagerState.scrollToPage(idx)
    }

    val activeTier = tierPages[pagerState.currentPage]
    val gradientTop by animateColorAsState(activeTier.gradientTop, label = "tierTop")
    val gradientBottom by animateColorAsState(activeTier.gradientBottom, label = "tierBottom")
    // "Your Tier" only for the CONFIRMED current page; anything else is "Tier".
    val userTierIndex = tierState.resolvedTierIndex.takeIf { tierState.tierConfirmed }
    val relation = tierRelation(pagerState.currentPage, userTierIndex)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("gold-priority-root")
            .drawBehind {
                // Figma page gradient: linear 196.72° — start (0.64, 0) → end (0.36, 1).
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(gradientTop, gradientBottom),
                        start = Offset(size.width * 0.64f, 0f),
                        end = Offset(size.width * 0.36f, size.height),
                    )
                )
            },
    ) {
        // Contour-lines backdrop — canonical Swift/Figma Shape at (-122,-418), 5%.
        TierLinesBackdrop(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            // Header on the Figma Header-Type gradient (rgba(41,41,41,0.8) → 0).
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF292929).copy(alpha = 0.8f), Color(0xFF292929).copy(alpha = 0f)),
                        )
                    )
            ) {
                HomeDetailsHeader(
                    title = if (relation == TierRelation.Current) "Your Tier" else "Tier",
                    onBack = onBack,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    showDivider = false,
                )
            }
            // Component-36 indicator with the per-tier 1px hairline overlaid at
            // the header/indicator boundary.
            SwipeIndicator(
                activeIndex = pagerState.currentPage,
                activeTier = activeTier,
                hairline = activeTier.hairline,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("gold-priority-pager"),
            ) { page ->
                val tierPage = tierPages[page]
                TierPageContent(
                    tier = tierPage,
                    title = tierTitleFor(tierPage, tierState),
                    rows = benefitRowsFor(tierPage, tierState.benefitRowsByCode, tierState.catalogStatus),
                    onRetryBenefits = onRetryBenefits,
                )
            }

            // Keep the single canonical CTA in normal layout flow so long
            // benefit lists are clipped and scrolled above it, never behind it.
            // The sheet surfaces remain owned by this same overlay.
            TierChangeOverlay(
                visibleTier = activeTier,
                relation = relation,
                userTierIndex = userTierIndex,
                state = tierState,
                onRequestChange = onRequestChange,
                onRetryConfirmation = onRetryConfirmation,
                onRetryCurrentTier = onRetryCurrentTier,
                onDismissError = onDismissError,
                onConsumeSuccess = onConsumeSuccess,
                onActivate = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

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

// ─── Swipe indicator strip ─────────────────────────────────────────────────

@Composable
private fun SwipeIndicator(activeIndex: Int, activeTier: TierPage, hairline: Color) {
    // Figma Component 36: 35dp strip on #292929 at 10%, 44×5 dashes (r2.5),
    // 20dp outer padding, 5dp gaps. The per-tier hairline is drawn ON the
    // strip's top edge (Swift overlays it at the header boundary).
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF292929).copy(alpha = 0.10f))
            .drawBehind {
                drawRect(color = hairline, size = size.copy(height = 1.dp.toPx()))
            }
            .height(35.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
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
    title: String,
    rows: TierBenefitRows,
    onRetryBenefits: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Figma Ellipse 3413 — 284×284 at (-93, 151) in 375-wide page space,
        // radial fade of the tier's own pair, mix-blend-overlay.
        val s = maxWidth / 375f
        val ellipseSize = s * 284f
        Box(
            Modifier
                .offset(x = s * -93f, y = s * 151f)
                .size(ellipseSize)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to tier.gradientTop.copy(alpha = 0.55f),
                            0.5f to tier.gradientTop.copy(alpha = 0.25f),
                            1.0f to tier.gradientBottom.copy(alpha = 0f),
                        ),
                        blendMode = BlendMode.Overlay,
                    )
                }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp)
                .padding(top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gold-priority-title-row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(tier.glyphRes),
                    contentDescription = null,
                    modifier = Modifier
                        .width(70.dp)
                        .height(64.dp)
                        .testTag("gold-priority-tier-badge"),
                )
                TierNameText(
                    name = title,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("gold-priority-tier-name"),
                )
            }
            Text(
                text = "Benefits",
                style = AirdropType.title2,
                color = Color.White,
            )
            when (rows) {
                is TierBenefitRows.Rows -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.rows.forEach { benefit ->
                        Row(
                            Modifier.padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            TierTick(
                                color = tierTickColor(tier),
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(24.dp),
                            )
                            Text(
                                text = benefit,
                                style = AirdropType.body1,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                TierBenefitRows.Loading -> Row(
                    Modifier
                        .padding(vertical = 8.dp)
                        .testTag("gold-priority-benefits-state"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Loading benefits...",
                        style = AirdropType.body1,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                TierBenefitRows.Failed -> Column(
                    Modifier.testTag("gold-priority-benefits-state"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Tier benefits are unavailable.",
                        style = AirdropType.body1,
                        color = Color.White,
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                            .clickable(onClick = onRetryBenefits)
                            .testTag("gold-priority-benefits-retry")
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    ) {
                        Text(text = "Retry", style = AirdropType.button, color = Color.White)
                    }
                }
            }
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
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        // Figma H4 32 with Swift's 0.7 minimum scale (≈ 22).
        val fontSize = remember(name, maxWidthPx, textMeasurer) {
            if (maxWidthPx <= 0) {
                32.sp
            } else {
                (32 downTo 22).firstOrNull { candidate ->
                    !textMeasurer.measure(
                        text = AnnotatedString(name),
                        style = AirdropType.h5.copy(
                            fontSize = candidate.sp,
                            lineHeight = (candidate * 1.4f).sp,
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
            style = AirdropType.h5.copy(
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.4f).sp,
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * Customer Tier benefit checkmark — the RN GoldCheckmarkIcon geometry (circle
 * outline + 3-segment check). Benefit rows tint it via [tierTickColor]; the
 * change-success check passes white.
 */
@Composable
internal fun TierTick(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val stroke = w / 15f
        drawCircle(
            color = color,
            radius = (w - stroke) / 2f,
            style = Stroke(width = stroke),
        )
        val check = Path().apply {
            moveTo(w * 0.28f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.68f)
            lineTo(w * 0.72f, w * 0.36f)
        }
        drawPath(
            path = check,
            color = color,
            style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
