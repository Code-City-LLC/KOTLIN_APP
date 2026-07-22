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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
 * d7b3f8a; live Figma MCP page frame 40001452:23823),
 * rendering the SERVER-OWNED benefit copy from PR#46 (GET /service-tiers via
 * TierRepository — no hardcoded benefit strings survive here).
 *
 * Canon layers (Kemar 2026-07-11/12 — "we're going to Swift; the design
 * should be like Swift"):
 *  - Page gradient: linear 196.72° — start (0.64, 0) → end (0.36, 1).
 *  - FULL-BLEED contour lines: the exact Figma Shape export
 *    (drawable/tier_lines_shape.xml, 38 paths #FAEEED, geometry SHA-256
 *    92145bcc…) placed at (-122, -418) in 375-wide
 *    page space, scaled by screenWidth/375, at 5% alpha — the lines run
 *    behind the CTA and are CUT OFF by the screen bottom exactly like the
 *    design. Never a cell-clipped redraw.
 *  - Radial ellipse 284×284 at (-93, 151) behind the icon (Figma Ellipse
 *    3413, overlay-blend look approximated with the tier's own gradient).
 *  - Header has no scrim or fade (direct Kemar ruling 2026-07-13), so the
 *    tier page gradient remains uninterrupted behind it. The per-tier 1px
 *    hairline remains; title is PAGE-RELATIVE: "Your Tier" only on the
 *    customer's own page, "Tier" everywhere else.
 *  - Component-36 dash strip: 35dp strip, 5dp dashes r2.5, 20dp sides,
 *    #292929 @ 10% wash.
 *  - Bottom glass fade (Kemar 2026-07-12/13): one screen-level overlay starts
 *    64dp inside the benefits viewport and continues through the CTA/safe-area
 *    lane to the real page bottom. It remains visible on CTA-less pages, as in
 *    the accepted Swift implementation; no content mask or hard cutoff.
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
    /**
     * Display-only rows for the pages the API does NOT serve (Kemar ruling
     * 2026-07-12 via #22424: "the tier is missing info from inactive and
     * corporate" — these MUST show their benefit info). Never injected into
     * service_tiers; server copy still owns every apiCode page.
     */
    val staticBenefits: List<String>? = null,
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
        staticBenefits = listOf(
            "Access to account setup and onboarding assistance.",
            "Invitations to introductory offers or bonus AirCoins promos.",
            "Reactivation campaigns with one-time shipping credits.",
            "Auto-upgrade eligibility after their first paid shipment.",
        ),
    ),
    TierPage(
        id = "corporate", name = "Corporate",
        iconRes = R.drawable.tier_icon_corporate,
        hairline = Color(0xFF3A2663),
        gradientTop = TierPalette.PlatinumElite2, gradientBottom = Color(0xFF3A2663),
        inactiveDash = TierPalette.PlatinumElite2.copy(alpha = 0.6f), apiCode = null,
        staticBenefits = listOf(
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

/** Swift FigmaGoldPriorityViewController.TierRelation — CTA state machine. */
internal enum class TierRelation { UPGRADE, DOWNGRADE, CURRENT, PREVIEW, ACTIVATION }

/**
 * Verbatim port of Swift `relation(forNavigatingTo:)` (staging d7b3f8a):
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

/**
 * CTA label for the current Swift state machine:
 *  - unresolved/loading → a disabled progress CTA
 *  - unresolved/failed  → a functional retry CTA
 *  - CURRENT    → "Your Tier" (opens the breakdown)
 *  - UPGRADE    → "Upgrade to <page tier>"
 *  - ACTIVATION → activation copy (Inactive as the customer's own state)
 *  - DOWNGRADE / PREVIEW → hidden; downgrade remains available only from
 *    the current-tier breakdown sheet.
 */
internal fun tierCtaLabel(
    relation: TierRelation,
    pageName: String,
    resolutionStatus: TierResolutionStatus = TierResolutionStatus.Resolved,
): String? = when (resolutionStatus) {
    TierResolutionStatus.Loading -> "Loading your tier…"
    TierResolutionStatus.Failed -> "Retry tier details"
    TierResolutionStatus.Resolved -> when (relation) {
        TierRelation.CURRENT -> "Your Tier"
        TierRelation.UPGRADE -> "Upgrade to $pageName"
        TierRelation.ACTIVATION -> "Ship a package now to activate your account"
        TierRelation.DOWNGRADE, TierRelation.PREVIEW -> null
    }
}

/**
 * Benefit rows for a page: server copy for API-served tiers; the display-only
 * static rows for Inactive/Corporate (Kemar ruling #22424). Pure/testable.
 */
internal fun benefitRowsForPage(
    page: TierPage,
    benefitRowsByCode: Map<String, List<String>>,
): List<String>? = page.apiCode?.let(benefitRowsByCode::get) ?: page.staticBenefits

/**
 * Backend-offer gate (gates #22805/#22867-6): a change is legal ONLY when
 * can_change is true AND the exact code appears in available_changes as a
 * non-current offer whose direction is explicitly "upgrade" or "downgrade" —
 * "same", malformed, or missing directions never authorize a PATCH. Shared
 * by the ViewModel's PATCH gate and the sheets.
 */
internal fun isOfferedChange(
    offers: List<com.ga.airdrop.data.model.TierChangeOption>,
    canChange: Boolean,
    code: String?,
): Boolean = canChange && code != null && offers.any {
    it.code.equals(code, ignoreCase = true) && !it.isCurrent &&
        (
            it.direction.equals("upgrade", ignoreCase = true) ||
                it.direction.equals("downgrade", ignoreCase = true)
            )
}

/**
 * The offer's declared direction for a code — authoritative over page order
 * for the change sheet's copy (gate #22836-3: page math must never label a
 * backend downgrade as an upgrade). Null when the code isn't offered OR the
 * offer's direction isn't explicitly upgrade/downgrade (#22867-4: "same"/
 * malformed directions never open a sheet).
 */
internal fun offerDirectionIsUpgrade(
    offers: List<com.ga.airdrop.data.model.TierChangeOption>,
    code: String?,
): Boolean? = code?.let { c ->
    when (
        offers.firstOrNull { it.code.equals(c, ignoreCase = true) && !it.isCurrent }
            ?.direction?.lowercase()
    ) {
        "upgrade" -> true
        "downgrade" -> false
        else -> null
    }
}

/**
 * Breakdown-sheet target from the BACKEND OFFER LIST — direction and
 * lane_rank are authoritative (Swift: "index math is only the fallback";
 * after #22805 there is no index fallback at all: no offer ⇒ no button).
 * Nearest upgrade = lowest lane_rank among upgrade offers; nearest
 * downgrade = highest lane_rank among downgrade offers.
 */
internal fun offerTargetPage(
    offers: List<com.ga.airdrop.data.model.TierChangeOption>,
    canChange: Boolean,
    upward: Boolean,
): TierPage? {
    if (!canChange) return null
    val direction = if (upward) "upgrade" else "downgrade"
    val candidates = offers.filter {
        it.direction.equals(direction, ignoreCase = true) && !it.isCurrent && it.code.isNotBlank()
    }
    if (candidates.isEmpty()) return null
    val chosen = if (upward) {
        candidates.minByOrNull { it.laneRank ?: Int.MAX_VALUE }
    } else {
        candidates.maxByOrNull { it.laneRank ?: Int.MIN_VALUE }
    } ?: return null
    return tierPages.firstOrNull { it.apiCode?.equals(chosen.code, ignoreCase = true) == true }
}

@Composable
fun GoldPriorityScreen(
    onBack: () -> Unit,
    viewModel: GoldPriorityViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    GoldPriorityContent(
        onBack = onBack,
        sessionEpoch = state.sessionEpoch,
        resolvedTierIndex = state.resolvedTierIndex,
        benefitRowsByCode = state.benefitRowsByCode,
        catalogStatus = state.catalogStatus,
        resolutionStatus = state.resolutionStatus,
        onRetryBenefits = viewModel::retryBenefits,
        canChange = state.canChange,
        changeOffers = state.changeOffers,
        changePhase = state.changePhase,
        changeSuccessName = state.changeSuccessName,
        changeSuccessMessage = state.changeSuccessMessage,
        changeError = state.changeError,
        onChangeTier = viewModel::changeTier,
        onResetChangeFlow = viewModel::resetChangeFlow,
    )
}

/** Which sheet is presented over the pager. */
private sealed interface TierSheet {
    data object Breakdown : TierSheet
    data class Change(val target: TierPage, val isUpgrade: Boolean) : TierSheet
}

@Composable
internal fun GoldPriorityContent(
    onBack: () -> Unit,
    sessionEpoch: Long = 0L,
    resolvedTierIndex: Int? = null,
    initialPage: Int = defaultTierIndex,
    benefitRowsByCode: Map<String, List<String>> = emptyMap(),
    catalogStatus: TierCatalogStatus = TierCatalogStatus.Loading,
    resolutionStatus: TierResolutionStatus = TierResolutionStatus.Resolved,
    onRetryBenefits: () -> Unit = {},
    canChange: Boolean = false,
    changeOffers: List<com.ga.airdrop.data.model.TierChangeOption> = emptyList(),
    changePhase: TierChangePhase = TierChangePhase.Idle,
    changeSuccessName: String? = null,
    changeSuccessMessage: String? = null,
    changeError: String? = null,
    onChangeTier: (code: String, name: String) -> Unit = { _, _ -> },
    onResetChangeFlow: () -> Unit = {},
) {
    ForceLightStatusBarIcons()
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(tierPages.indices)) { tierPages.size }

    // Pre-scroll once the user's tier resolves (Swift scrollToItem).
    LaunchedEffect(resolvedTierIndex) {
        resolvedTierIndex?.let { pagerState.scrollToPage(it) }
    }

    // A sheet belongs to the authenticated owner that opened it. A replaced
    // session gets a fresh local modal state before any new offer can act.
    var activeSheet by remember(sessionEpoch) { mutableStateOf<TierSheet?>(null) }

    val activeIndex = pagerState.currentPage
    val activeTier = tierPages[activeIndex]
    val relation = tierRelation(activeIndex, resolvedTierIndex)
    val ctaLabel = tierCtaLabel(relation, activeTier.name, resolutionStatus)
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
                // Per-page CTA presence decides the reserved bottom space —
                // a hidden CTA must not leave a dead field (#22989).
                val pageRelation = tierRelation(page, resolvedTierIndex)
                val pageHasCta = tierCtaLabel(
                    pageRelation, tierPages[page].name, resolutionStatus,
                ) != null
                TierPageContent(
                    tier = tierPages[page],
                    // Server copy for API tiers; display-only static rows for
                    // Inactive/Corporate (Kemar ruling #22424).
                    benefitRows = benefitRowsForPage(tierPages[page], benefitRowsByCode),
                    catalogStatus = catalogStatus,
                    onRetry = onRetryBenefits,
                    reserveCtaSpace = pageHasCta,
                )
            }
        }

        // Un-offered upgrades render DISABLED (dimmed, inert, a11y-disabled)
        // instead of silently-dead (#22991-2).
        val ctaEnabled = when {
            resolutionStatus == TierResolutionStatus.Loading -> false
            resolutionStatus == TierResolutionStatus.Failed -> true
            relation == TierRelation.UPGRADE ->
                isOfferedChange(changeOffers, canChange, activeTier.apiCode)
            else -> true
        }
        TierBottomFadeOverlay(
            tierId = activeTier.id,
            bottomColor = gradientBottom,
            reservesCta = ctaLabel != null,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        TierCtaButton(
            label = ctaLabel,
            enabled = ctaEnabled,
            onTap = {
                if (resolutionStatus == TierResolutionStatus.Failed) {
                    onRetryBenefits()
                } else when (relation) {
                    // Kemar 2026-07-11: "Your Tier" opens the benefits
                    // breakdown — the ONE sanctioned downgrade entry. The
                    TierRelation.CURRENT -> activeSheet = TierSheet.Breakdown
                    // Upgrade opens the change sheet ONLY for an actual
                    // backend offer (#22867-5); the OFFER's direction labels
                    // the sheet (#22836-3).
                    TierRelation.UPGRADE -> {
                        val direction =
                            offerDirectionIsUpgrade(changeOffers, activeTier.apiCode)
                        if (direction != null &&
                            isOfferedChange(changeOffers, canChange, activeTier.apiCode)
                        ) {
                            activeSheet = TierSheet.Change(
                                target = activeTier,
                                isUpgrade = direction,
                            )
                        }
                    }
                    // Inactive page's activation copy pops back to Home.
                    TierRelation.ACTIVATION -> onBack()
                    TierRelation.DOWNGRADE, TierRelation.PREVIEW -> Unit
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val userTier = resolvedTierIndex?.let(tierPages::getOrNull)
    when (val sheet = activeSheet) {
        is TierSheet.Breakdown -> if (userTier != null) {
            YourTierBreakdownSheet(
                tier = userTier,
                benefitRows = benefitRowsForPage(userTier, benefitRowsByCode).orEmpty(),
                // Targets come ONLY from the backend offer list — no offer,
                // no button (#22805; can_change=false hides both).
                upgradeTarget = offerTargetPage(changeOffers, canChange, upward = true),
                downgradeTarget = offerTargetPage(changeOffers, canChange, upward = false),
                // Targets exist only as backend offers, so the direction
                // lookup cannot miss; a null (offer vanished between renders)
                // opens nothing (#22867-4: no default-direction guessing).
                onUpgrade = { target ->
                    offerDirectionIsUpgrade(changeOffers, target.apiCode)?.let { dir ->
                        activeSheet = TierSheet.Change(target = target, isUpgrade = dir)
                    }
                },
                onDowngrade = { target ->
                    offerDirectionIsUpgrade(changeOffers, target.apiCode)?.let { dir ->
                        activeSheet = TierSheet.Change(target = target, isUpgrade = dir)
                    }
                },
                onDismiss = { activeSheet = null },
            )
        } else {
            activeSheet = null
        }
        is TierSheet.Change -> TierChangeSheet(
            target = sheet.target,
            current = userTier,
            targetBenefits = benefitRowsForPage(sheet.target, benefitRowsByCode),
            currentBenefits = userTier?.let {
                benefitRowsForPage(it, benefitRowsByCode)
            }.orEmpty(),
            isUpgrade = sheet.isUpgrade,
            phase = changePhase,
            successName = changeSuccessName,
            successMessage = changeSuccessMessage,
            error = changeError,
            onConfirm = { onChangeTier(sheet.target.apiCode.orEmpty(), sheet.target.name) },
            onDone = {
                onResetChangeFlow()
                activeSheet = null
            },
            onDismiss = {
                // Swipe-away mid-flow: reset so the next open starts clean.
                onResetChangeFlow()
                activeSheet = null
            },
        )
        null -> Unit
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

// ─── Header (transparent chrome + per-tier hairline, page-relative title) ─

@Composable
private fun TierHeader(title: String, hairline: Color, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("gold-priority-header")
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

// Screen-level bottom fade (Swift TierBottomFadeView). The overlay begins
// 64dp inside the benefits viewport, then continues through the bottom-control
// and navigation-bar lane. It belongs to the active page and is always shown.
internal val TierFadeHeight = 64.dp
/** Swift constrains the benefits viewport 12dp above a 50dp CTA whose bottom
 * is 12dp above the safe area: 12 + 50 + 12 = 74dp. */
internal val TierCtaClearance = 74.dp
/** Scroll-content bottom padding: the last benefit row must come to rest
 *  fully above the screen-level overlay. Navigation insets are added by the
 *  scroll container. Pinned by TierFadeContractTest. */
internal val TierBottomPaddingWithCta = 142.dp
internal val TierBottomPaddingNoCta = 64.dp

@Composable
private fun TierBottomFadeOverlay(
    tierId: String,
    bottomColor: Color,
    reservesCta: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val navigationBarInset = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    }
    val controlLane = if (reservesCta) TierCtaClearance else 0.dp
    val overlayHeight = TierFadeHeight + controlLane + navigationBarInset
    val middleStop = with(density) {
        TierFadeHeight.toPx() / overlayHeight.toPx().coerceAtLeast(1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(overlayHeight)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to bottomColor.copy(alpha = 0f),
                        middleStop.coerceIn(0f, 1f) to bottomColor.copy(alpha = 0.62f),
                        1f to bottomColor,
                    )
                )
            )
            .testTag("gold-priority-fade-$tierId")
    )
}

@Composable
private fun TierPageContent(
    tier: TierPage,
    benefitRows: List<String>?,
    catalogStatus: TierCatalogStatus,
    onRetry: () -> Unit,
    reserveCtaSpace: Boolean = true,
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
                // Figma: 30dp sides (Spaceing-s), 20 top. Bottom padding lets
                // the final row scroll above the shared screen-level fade.
                .padding(horizontal = 30.dp)
                .padding(top = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    bottom = if (reserveCtaSpace) TierBottomPaddingWithCta
                    else TierBottomPaddingNoCta,
                ),
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
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    benefitRows.forEachIndexed { index, benefit ->
                        TierBenefitRow(
                            text = benefit,
                            visual = TierBenefitRowVisual.Page,
                            textStyle = AirdropType.body1,
                            textColor = Color.White,
                            modifier = Modifier.padding(vertical = 5.dp),
                            rowTag = "tier-page-${tier.id}-benefit-row-$index",
                            iconTag = "tier-page-${tier.id}-benefit-icon-$index",
                            textTag = "tier-page-${tier.id}-benefit-text-$index",
                        )
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

// ─── Glass CTA (Swift TierCTAButton — Kemar: "light outline") ──────────────

/**
 * Swift TierCTAButton verbatim: 316×50, radius 12, white 7% fill, 1px white
 * 18% border, Cairo SemiBold 17, white title. Label comes from
 * [tierCtaLabel]'s Swift state machine; [enabled]=false renders the honest
 * disabled state for loading and for upgrades the backend did not offer.
 */
@Composable
private fun TierCtaButton(
    label: String?,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (label == null) return
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 12.dp)
            .size(width = 316.dp, height = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.07f else 0.04f))
            .border(
                1.dp,
                Color.White.copy(alpha = if (enabled) 0.18f else 0.10f),
                RoundedCornerShape(12.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onTap,
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
            color = Color.White.copy(alpha = if (enabled) 1f else 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

// ─── Your-Tier breakdown sheet (Swift YourTierBreakdownSheetViewController) ─

/**
 * Kemar 2026-07-11 (#21052): tapping "Your Tier" opens a breakdown of what
 * the customer is getting, with explicit Upgrade AND Downgrade options — the
 * sheet is the ONE sanctioned downgrade path (the pager stays upsell-only).
 * Targets are the nearest REAL API tiers (Inactive/Corporate skipped).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun YourTierBreakdownSheet(
    tier: TierPage,
    benefitRows: List<String>,
    upgradeTarget: TierPage?,
    downgradeTarget: TierPage?,
    onUpgrade: (TierPage) -> Unit,
    onDowngrade: (TierPage) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp - 24).coerceAtLeast(320).dp
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    androidx.compose.material3.ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(tier.gradientTop, tier.gradientBottom),
                    )
                )
                .testTag("tier-breakdown-sheet")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painter = painterResource(tier.iconRes),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "YOUR TIER",
                style = AirdropType.subtitle2.copy(letterSpacing = 2.sp),
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = tier.name,
                style = AirdropType.h5,
                color = Color.White,
            )
            // Frosted "What you're getting" panel — server-owned rows.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "What you're getting",
                    style = AirdropType.title2,
                    color = Color.White,
                )
                benefitRows.forEachIndexed { index, benefit ->
                    TierBenefitRow(
                        text = benefit,
                        visual = TierBenefitRowVisual.Sheet(
                            gained = true,
                            markColor = tier.gradientBottom,
                        ),
                        textStyle = AirdropType.body2,
                        textColor = Color.White,
                        rowTag = "tier-breakdown-benefit-row-$index",
                        iconTag = "tier-breakdown-benefit-icon-$index",
                        textTag = "tier-breakdown-benefit-text-$index",
                    )
                }
                if (benefitRows.isEmpty()) {
                    Text(
                        text = "Tier benefits are unavailable.",
                        style = AirdropType.body2,
                        color = Color.White,
                    )
                }
            }
            Text(
                text = "Changes apply to how your future shipments are processed.",
                style = AirdropType.body3,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            if (upgradeTarget != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable { onUpgrade(upgradeTarget) }
                        .testTag("tier-breakdown-upgrade"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Upgrade to ${upgradeTarget.name}",
                        style = AirdropType.subtitle1.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = tier.gradientBottom,
                    )
                }
            }
            if (downgradeTarget != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .clickable { onDowngrade(downgradeTarget) }
                        .testTag("tier-breakdown-downgrade"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Downgrade to ${downgradeTarget.name}",
                        style = AirdropType.subtitle1.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
            Text(
                text = "Close",
                style = AirdropType.subtitle1,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(onClick = onDismiss)
                    .testTag("tier-breakdown-close"),
            )
        }
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
