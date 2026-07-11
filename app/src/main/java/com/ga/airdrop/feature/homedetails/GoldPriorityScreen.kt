package com.ga.airdrop.feature.homedetails

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.layout.ContentScale
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

/**
 * Customer Tier swipe pager — design + behavior from Swift
 * FigmaGoldPriorityViewController @4a3024d (Figma 40001432:23101):
 * full-bleed tier gradient at the Figma 196.72° angle, contour-lines Shape
 * backdrop at 5%, translucent-dark header gradient with a per-tier 1px
 * hairline, Component-36 dash indicator, one page per tier (70dp PNG glyph +
 * radial ellipse + name + Benefits checklist) and the page-relative Figma
 * CTA (Kemar 2026-07-08: "Upgrade to <page>" above the customer's tier,
 * "Your Tier" breakdown on their own page, activation copy on Inactive,
 * hidden below — never a downgrade sign).
 *
 * Kemar 2026-07-11 tick rule: the benefit checkmarks are tinted to the
 * page's own background family (gradientTop lightened 35% toward white),
 * not plain white — each page's ticks match its tier color.
 */

data class TierPage(
    val id: String,
    val name: String,
    val gradientTop: Color,
    val gradientBottom: Color,
    val inactiveDash: Color,
    /** Canonical RN PNG glyph (Swift TierIcon_* asset) — NOT the hex badge. */
    val glyphRes: Int,
    /** Per-tier 1px header border — each Figma page's "…3" token. */
    val hairline: Color,
    val benefits: List<String>,
    /**
     * Laravel tier code for the switchable tiers (service_tiers.code). null for
     * the presentational-only pages (Inactive, Corporate) that the customer
     * can't self-select.
     */
    val apiCode: String? = null,
    /** service_tiers.lane_rank — higher = more premium; drives upgrade vs downgrade. */
    val laneRank: Int = 0,
)

internal val tierPages = listOf(
    TierPage(
        id = "diamond", name = "Diamond Elite",
        apiCode = "DIAM", laneRank = 5,
        gradientTop = Color(0xFF6B6B6B), gradientBottom = Color(0xFF292929),
        inactiveDash = Color.Black.copy(alpha = 0.4f),
        glyphRes = R.drawable.tier_glyph_diamond,
        hairline = Color(0xFFACACAC),
        benefits = listOf(
            "Next-day shipping on all packages.",
            "Priority logging, warehouse handling, and customs clearance.",
            "Dedicated WhatsApp VIP line for real-time assistance.",
            "Exclusive discounts and AirCoins multipliers on every shipment.",
            "Unlimited free storage for all packages.",
            "Early access to clearance events, auctions, and flash sales.",
            "Personalized account concierge for dispute or issue resolution.",
            "Surprise appreciation gifts for milestone achievements (e.g. 100th shipment, 1-year VIP anniversary).",
        ),
    ),
    TierPage(
        id = "platinum", name = "Platinum Priority",
        apiCode = "PLAT", laneRank = 4,
        gradientTop = Color(0xFFCACACA), gradientBottom = Color(0xFF737373),
        inactiveDash = Color.White.copy(alpha = 0.3f),
        glyphRes = R.drawable.tier_glyph_platinum,
        hairline = Color(0xFF656565),
        benefits = listOf(
            "Expedited 24-hour processing for all cleared packages.",
            "Free storage for up to 60 days.",
            "Premium customer support queue with faster handling.",
            "Up to 10% shipping discounts and reduced handling fees.",
            "Double AirCoins events and random loyalty gifts.",
            "Priority in pre-auction and sales events.",
            "Access to affiliate and referral bonuses.",
            "Complimentary upgrade offers during seasonal promotions.",
        ),
    ),
    TierPage(
        id = "gold", name = "Gold Standard",
        apiCode = "GOLD", laneRank = 3,
        gradientTop = Color(0xFFEFBF04), gradientBottom = Color(0xFF8C6F01),
        inactiveDash = Color(0xFFEFBF04).copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_gold,
        hairline = Color(0xFF766923),
        benefits = listOf(
            "Free storage for 30 days on all incoming packages.",
            "Processing within 24-48 hours of package clearance.",
            "3-5% discounted shipping rates.",
            "Standard loyalty rewards plus double-points promotions during AirDrop events.",
            "Early notifications for sales, warehouse auctions, and holiday offers.",
            "General support line priority over standard-tier members.",
            "Eligibility for seasonal upgrade offers.",
        ),
    ),
    TierPage(
        id = "ruby", name = "Ruby Starter",
        apiCode = "RUBY", laneRank = 2,
        gradientTop = TierPalette.BronzeSaver2, gradientBottom = Color(0xFF5C262E),
        inactiveDash = TierPalette.BronzeSaver2.copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_ruby,
        hairline = Color(0xFF5C262E),
        // RUBY never mentions AirCoins (standing ruling — Swift static list).
        benefits = listOf(
            "Standard 2–3 business day processing.",
            "No free storage included.",
            "Competitive base shipping rates.",
            "Exclusive partner coupons and limited-time promos.",
            "Access to standard customer support channels during business hours.",
            "Auto-upgrade eligibility after 12 months of consistent activity.",
        ),
    ),
    TierPage(
        id = "sapphire", name = "Sapphire Saver",
        apiCode = "SAVR", laneRank = 1,
        gradientTop = TierPalette.CorporateBulk3, gradientBottom = TierPalette.CorporateBulk2,
        inactiveDash = TierPalette.CorporateBulk3.copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_sapphire,
        hairline = Color(0xFF0A96D4),
        // SAVR never mentions AirCoins (standing ruling — Swift static list).
        benefits = listOf(
            "Basic processing (3–5 business days).",
            "Standard shipping rates.",
            "No free storage included.",
            "Access to limited promotions and onboarding discounts.",
            "Eligibility for early upgrade upon meeting spend thresholds.",
            "Welcome emails and loyalty guidance to familiarize them with benefits.",
        ),
    ),
    TierPage(
        id = "inactive", name = "Inactive",
        gradientTop = Color(0xFFF1A88C), gradientBottom = Color(0xFF8C2F0C),
        inactiveDash = Color(0xFF5C262E).copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_inactive,
        hairline = Color(0xFF8C2F0C),
        benefits = listOf(
            "Access to account setup and onboarding assistance.",
            "Invitations to introductory offers or bonus AirCoins promos.",
            "Reactivation campaigns with one-time shipping credits.",
            "Auto-upgrade eligibility after their first paid shipment.",
        ),
    ),
    TierPage(
        id = "corporate", name = "Corporate",
        gradientTop = TierPalette.PlatinumElite2, gradientBottom = Color(0xFF3A2663),
        inactiveDash = TierPalette.PlatinumElite2.copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_corporate,
        hairline = Color(0xFF3A2663),
        benefits = listOf(
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
    // The Inactive page carries Figma's activation CTA when it is the
    // customer's own resolved state; for anyone else it's a lower page.
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

/** Nearest real API tier (apiCode != null) above/below an index — skips Inactive/Corporate. */
internal fun adjacentCodedTier(from: Int, step: Int): TierPage? {
    var i = from + step
    while (i in tierPages.indices) {
        if (tierPages[i].apiCode != null) return tierPages[i]
        i += step
    }
    return null
}

/**
 * Kemar 2026-07-11 tick rule: benefit checkmarks take the page's own
 * background family — the tier's gradientTop lightened 35% toward white so
 * it stays legible over the darker end of the gradient.
 */
internal fun tierTickColor(tier: TierPage): Color = lerp(tier.gradientTop, Color.White, 0.35f)

@Composable
fun GoldPriorityScreen(
    onBack: () -> Unit,
    viewModel: GoldPriorityViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    GoldPriorityContent(
        onBack = onBack,
        resolvedTierIndex = state.resolvedTierIndex,
        tierState = state,
        onRequestChange = viewModel::requestTierChange,
        onDismissError = viewModel::dismissChangeError,
        onConsumeJustChanged = viewModel::consumeJustChanged,
    )
}

@Composable
internal fun GoldPriorityContent(
    onBack: () -> Unit,
    resolvedTierIndex: Int? = null,
    initialPage: Int = defaultTierIndex,
    tierState: GoldPriorityUiState = GoldPriorityUiState(),
    onRequestChange: (String) -> Unit = {},
    onDismissError: () -> Unit = {},
    onConsumeJustChanged: () -> Unit = {},
) {
    ForceLightStatusBarIcons()
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(tierPages.indices)) { tierPages.size }

    // Pre-scroll once the user's tier resolves (Swift scrollToItem). Hardened
    // like Swift 4a3024d ("reload first, layout, then scroll"): a scroll issued
    // before the pager's first measure pass can be silently dropped, and a
    // second index resolution (legacy name → tier API) cancels the in-flight
    // effect and can strand the pager between pages — both were caught live
    // (landed on Diamond after a cold boot, Platinum after a fast reopen).
    LaunchedEffect(resolvedTierIndex) {
        val idx = resolvedTierIndex ?: return@LaunchedEffect
        snapshotFlow { pagerState.layoutInfo.viewportSize.width }.first { it > 0 }
        pagerState.scrollToPage(idx)
        // Settle: if a cancelled predecessor left the pager off-target, snap again.
        if (pagerState.currentPage != idx) pagerState.scrollToPage(idx)
    }

    val activeTier = tierPages[pagerState.currentPage]
    val gradientTop by animateColorAsState(activeTier.gradientTop, label = "tierTop")
    val gradientBottom by animateColorAsState(activeTier.gradientBottom, label = "tierBottom")
    val relation = tierRelation(pagerState.currentPage, resolvedTierIndex)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("gold-priority-root")
            .drawBehind {
                // Figma page gradient: linear 196.72° (mostly downward,
                // slightly left) — Swift TierGradientView start (0.64, 0) →
                // end (0.36, 1); was a 45° diagonal before the 07-08 audit.
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(gradientTop, gradientBottom),
                        start = Offset(size.width * 0.64f, 0f),
                        end = Offset(size.width * 0.36f, size.height),
                    )
                )
            },
    ) {
        // Contour-lines Shape backdrop — the exact Figma export (5% opacity,
        // placed at (-122, -418) in 375-wide page space), stroked at final
        // scale so the hairlines read like the Figma canvas.
        TierLinesBackdrop(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            // Header on the Figma Header-Type gradient (rgba(41,41,41,0.8) → 0).
            // Kemar 2026-07-08: "Your Tier" ONLY on the customer's own page;
            // any other page is just "Tier".
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
            // Per-tier 1px header border — each page's "…3" design token.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(activeTier.hairline)
            )
            SwipeIndicator(activeIndex = pagerState.currentPage, activeTier = activeTier)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val tierPage = tierPages[page]
                // Backend catalogue rows (processing_copy + benefits_summary +
                // restored marketing merge) when present, else the page's copy.
                val apiBenefits = tierPage.apiCode?.let { tierState.benefitsByCode[it] }
                TierPageContent(
                    tier = tierPage,
                    benefits = apiBenefits?.takeIf { it.isNotEmpty() } ?: tierPage.benefits,
                )
            }
        }

        // Page-relative Figma CTA + the sheets it opens (change / breakdown).
        TierChangeOverlay(
            visibleTier = activeTier,
            relation = relation,
            userTierIndex = resolvedTierIndex,
            state = tierState,
            onRequestChange = onRequestChange,
            onDismissError = onDismissError,
            onActivate = onBack,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Brief confirmation once a change applies (Swift success toast parity).
        tierState.justChangedToName?.let { name ->
            LaunchedEffect(name) {
                pagerState.animateScrollToPage(indexForTierCode(tierState.currentTierCode) ?: 0)
            }
            TierChangeSuccessBanner(
                name = name,
                onDismiss = onConsumeJustChanged,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/** Auto-dismissing "You're now {tier}" banner shown after a successful switch. */
@Composable
private fun TierChangeSuccessBanner(
    name: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(name) {
        kotlinx.coroutines.delay(2600)
        onDismiss()
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 96.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TierTick(color = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                text = "You're now on $name",
                style = AirdropType.button,
                color = Color.White,
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
private fun SwipeIndicator(activeIndex: Int, activeTier: TierPage) {
    // Figma Component 36: 35dp strip on #292929 at 10%, 44×5 dashes (r2.5),
    // 20dp outer padding, 5dp gaps; active dash white, inactive per-tier.
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF292929).copy(alpha = 0.10f))
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
private fun TierPageContent(tier: TierPage, benefits: List<String> = tier.benefits) {
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
                .padding(top = 20.dp, bottom = 92.dp),
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
                    painter = painterResource(tier.glyphRes),
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
                style = AirdropType.title2,
                color = Color.White,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val tick = tierTickColor(tier)
                benefits.forEach { benefit ->
                    Row(
                        Modifier.padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TierTick(
                            color = tick,
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
 * Customer Tier benefit checkmark — the RN GoldCheckmarkIcon (circle outline
 * + 3-segment check) drawn in the tier's own tint per Kemar's 2026-07-11
 * rule (tick color follows the page background family).
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
