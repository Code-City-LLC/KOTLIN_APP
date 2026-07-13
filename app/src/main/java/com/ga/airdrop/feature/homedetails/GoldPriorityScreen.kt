package com.ga.airdrop.feature.homedetails

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
 * Gold Priority / Customer Tier — Figma 40001432:23506 (pager frames under
 * 40001432:23101), behavior from FigmaGoldPriorityViewController: full-screen
 * tier gradient, translucent-dark header, dash swipe indicator, one page per
 * tier (badge + name + Benefits checklist). Pre-scrolls to the user's tier.
 * Tier copy + gradient colors ported verbatim from the Swift/RN tierData
 * (TierPalette carries the accent seeds).
 */

data class TierPage(
    val id: String,
    val name: String,
    val gradientTop: Color,
    val gradientBottom: Color,
    val inactiveDash: Color,
    val glyphRes: Int,
    val apiCode: String?,
)

internal val tierPages = listOf(
    TierPage(
        id = "diamond", name = "Diamond Elite",
        gradientTop = Color(0xFF6B6B6B), gradientBottom = Color(0xFF292929),
        inactiveDash = Color.Black.copy(alpha = 0.4f),
        glyphRes = R.drawable.tier_glyph_diamond, apiCode = "DIAM",
    ),
    TierPage(
        id = "platinum", name = "Platinum Priority",
        gradientTop = Color(0xFFCACACA), gradientBottom = Color(0xFF737373),
        inactiveDash = Color.White.copy(alpha = 0.3f),
        glyphRes = R.drawable.tier_glyph_platinum, apiCode = "PLAT",
    ),
    TierPage(
        id = "gold", name = "Gold Standard",
        gradientTop = Color(0xFFEFBF04), gradientBottom = Color(0xFF8C6F01),
        inactiveDash = Color(0xFFEFBF04).copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_gold, apiCode = "GOLD",
    ),
    TierPage(
        id = "ruby", name = "Ruby Starter",
        gradientTop = TierPalette.BronzeSaver2, gradientBottom = Color(0xFF5C262E),
        inactiveDash = TierPalette.BronzeSaver2.copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_ruby, apiCode = "RUBY",
    ),
    TierPage(
        id = "sapphire", name = "Sapphire Saver",
        gradientTop = TierPalette.CorporateBulk3, gradientBottom = TierPalette.CorporateBulk2,
        inactiveDash = TierPalette.CorporateBulk3.copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_sapphire, apiCode = "SAVR",
    ),
    TierPage(
        id = "inactive", name = "Inactive",
        gradientTop = Color(0xFFF1A88C), gradientBottom = Color(0xFF8C2F0C),
        inactiveDash = Color(0xFF5C262E).copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_inactive, apiCode = null,
    ),
    TierPage(
        id = "corporate", name = "Corporate",
        gradientTop = TierPalette.PlatinumElite2, gradientBottom = Color(0xFF3A2663),
        inactiveDash = TierPalette.PlatinumElite2.copy(alpha = 0.6f),
        glyphRes = R.drawable.tier_glyph_corporate, apiCode = null,
    ),
)

private val defaultTierIndex = tierPages.indexOfFirst { it.id == "gold" }

internal val TierNormalBottomPadding = 24.dp
internal val TierFadeHeight = 64.dp
internal val TierCtaHeight = 50.dp
internal val TierCtaBottomGap = 12.dp
internal val TierCtaViewportGap = 12.dp
internal val TierCtaContentClearance = 110.dp
internal val TierCtaViewportInset =
    TierCtaHeight + TierCtaBottomGap + TierCtaViewportGap
internal val TierBottomPaddingWithCta =
    TierNormalBottomPadding + TierCtaContentClearance

/**
 * Issue #89 is visual-only. Until the authoritative tier-change behavior lands,
 * only the resolved current tier has the truthful, non-interactive "Your Tier"
 * status CTA. This single predicate owns CTA visibility, fade, and clearance.
 */
internal fun pageHasCta(pageIndex: Int, resolvedTierIndex: Int?): Boolean =
    resolvedTierIndex != null && pageIndex == resolvedTierIndex

internal data class TierPageVisualTreatment(val pageHasCta: Boolean) {
    val appliesBottomFade: Boolean get() = pageHasCta
    val reservesCtaSpace: Boolean get() = pageHasCta
    val contentBottomPadding get() =
        if (reservesCtaSpace) TierBottomPaddingWithCta else TierNormalBottomPadding
}

internal fun tierPageVisualTreatment(
    pageIndex: Int,
    resolvedTierIndex: Int?,
): TierPageVisualTreatment = TierPageVisualTreatment(
    pageHasCta = pageHasCta(pageIndex, resolvedTierIndex),
)

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

    val activeTier = tierPages[pagerState.currentPage]
    val gradientTop by animateColorAsState(activeTier.gradientTop, label = "tierTop")
    val gradientBottom by animateColorAsState(activeTier.gradientBottom, label = "tierBottom")

    Box(
        Modifier
            .fillMaxSize()
            .testTag("gold-priority-root")
            .background(
                // Swift CAGradientLayer startPoint (1,0) → endPoint (0,1).
                Brush.linearGradient(
                    colors = listOf(gradientTop, gradientBottom),
                    start = Offset(Float.POSITIVE_INFINITY, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY),
                )
            )
    ) {
        TierLinesBackdrop(
            Modifier
                .fillMaxSize()
                .testTag("gold-priority-lines")
        )
        Column(Modifier.fillMaxSize()) {
            // Header on a 35%-black overlay, white content, no divider.
            HomeDetailsHeader(
                title = "Customer Tier",
                onBack = onBack,
                containerColor = Color.Black.copy(alpha = 0.35f),
                contentColor = Color.White,
                showDivider = false,
            )
            SwipeIndicator(activeIndex = pagerState.currentPage, activeTier = activeTier)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                TierPageContent(
                    tier = tierPages[page],
                    benefitRows = tierPages[page].apiCode?.let(benefitRowsByCode::get),
                    catalogStatus = catalogStatus,
                    onRetry = onRetryBenefits,
                    treatment = tierPageVisualTreatment(page, resolvedTierIndex),
                )
            }
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
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.12f))
            .height(28.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tierPages.forEachIndexed { index, _ ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
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
    treatment: TierPageVisualTreatment,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    bottom = if (treatment.pageHasCta) TierCtaViewportInset else 0.dp,
                )
                .testTag("gold-priority-tier-scroll")
                .tierBottomFade(treatment.appliesBottomFade)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = treatment.contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    contentDescription = "${tier.name} tier glyph",
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
                style = AirdropType.title1,
                color = Color.White,
            )
            if (!benefitRows.isNullOrEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    benefitRows.forEach { benefit ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BenefitCheck(modifier = Modifier.padding(top = 2.dp))
                            Text(
                                text = benefit,
                                style = AirdropType.body2,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                val loading = tier.apiCode != null && catalogStatus == TierCatalogStatus.Loading
                Text(
                    text = if (loading) "Loading benefits..." else "Tier benefits are unavailable.",
                    style = AirdropType.body2,
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
        }

        if (treatment.pageHasCta) {
            TierCurrentStatusCta(
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun Modifier.tierBottomFade(enabled: Boolean): Modifier {
    if (!enabled) return this
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val fadeEnd = size.height
        if (fadeEnd <= 0f) return@drawWithContent
        val fadeStart = (fadeEnd - TierFadeHeight.toPx()).coerceAtLeast(0f)
        drawRect(
            brush = Brush.verticalGradient(
                fadeStart / size.height to Color.Black,
                (fadeEnd / size.height).coerceAtMost(1f) to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

@Composable
private fun TierCurrentStatusCta(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .width(316.dp)
            .padding(bottom = TierCtaBottomGap)
            .height(TierCtaHeight)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .testTag("gold-priority-tier-cta"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Your Tier",
            style = AirdropType.subtitle2,
            color = Color.White,
        )
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
        val fontSize = remember(name, maxWidthPx, textMeasurer) {
            if (maxWidthPx <= 0) {
                28.sp
            } else {
                (28 downTo 20).firstOrNull { candidate ->
                    !textMeasurer.measure(
                        text = AnnotatedString(name),
                        style = AirdropType.h5.copy(
                            fontSize = candidate.sp,
                            lineHeight = (candidate * 1.5f).sp,
                        ),
                        overflow = TextOverflow.Clip,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = maxWidthPx),
                    ).hasVisualOverflow
                }?.sp ?: 20.sp
            }
        }

        Text(
            text = name,
            style = AirdropType.h5.copy(
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.5f).sp,
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** White check in an 18%-white circle — Swift CheckmarkIconView. */
@Composable
private fun BenefitCheck(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(11.dp),
        )
    }
}
