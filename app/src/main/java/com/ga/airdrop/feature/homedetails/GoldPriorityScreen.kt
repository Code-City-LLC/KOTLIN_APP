package com.ga.airdrop.feature.homedetails

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val benefits: List<String>,
)

internal val tierPages = listOf(
    TierPage(
        id = "diamond", name = "Diamond Elite",
        gradientTop = Color(0xFF6B6B6B), gradientBottom = Color(0xFF292929),
        inactiveDash = Color.Black.copy(alpha = 0.4f),
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
        gradientTop = Color(0xFFCACACA), gradientBottom = Color(0xFF737373),
        inactiveDash = Color.White.copy(alpha = 0.3f),
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
        gradientTop = Color(0xFFEFBF04), gradientBottom = Color(0xFF8C6F01),
        inactiveDash = Color(0xFFEFBF04).copy(alpha = 0.6f),
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
        gradientTop = TierPalette.BronzeSaver2, gradientBottom = Color(0xFF5C262E),
        inactiveDash = TierPalette.BronzeSaver2.copy(alpha = 0.6f),
        benefits = listOf(
            "Standard 2–3 business day processing.",
            "No free storage included.",
            "Competitive base shipping rates.",
            "Access to AirCoins rewards with occasional bonus-point days.",
            "Exclusive partner coupons and limited-time promos.",
            "Access to standard customer support channels during business hours.",
            "Auto-upgrade eligibility after 12 months of consistent activity.",
        ),
    ),
    TierPage(
        id = "sapphire", name = "Sapphire Saver",
        gradientTop = TierPalette.CorporateBulk3, gradientBottom = TierPalette.CorporateBulk2,
        inactiveDash = TierPalette.CorporateBulk3.copy(alpha = 0.6f),
        benefits = listOf(
            "Basic processing (3–5 business days).",
            "Standard shipping rates.",
            "No free storage included.",
            "Introductory AirCoins on their first three shipments.",
            "Access to limited promotions and onboarding discounts.",
            "Eligibility for early upgrade upon meeting spend thresholds.",
            "Welcome emails and loyalty guidance to familiarize them with benefits.",
        ),
    ),
    TierPage(
        id = "inactive", name = "Inactive",
        gradientTop = Color(0xFFF1A88C), gradientBottom = Color(0xFF8C2F0C),
        inactiveDash = Color(0xFF5C262E).copy(alpha = 0.6f),
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

@Composable
fun GoldPriorityScreen(
    onBack: () -> Unit,
    viewModel: GoldPriorityViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(initialPage = defaultTierIndex) { tierPages.size }

    // Pre-scroll once the user's tier resolves (Swift scrollToItem).
    LaunchedEffect(state.resolvedTierIndex) {
        state.resolvedTierIndex?.let { pagerState.scrollToPage(it) }
    }

    val activeTier = tierPages[pagerState.currentPage]
    val gradientTop by animateColorAsState(activeTier.gradientTop, label = "tierTop")
    val gradientBottom by animateColorAsState(activeTier.gradientBottom, label = "tierBottom")

    Box(
        Modifier
            .fillMaxSize()
            .background(
                // Swift CAGradientLayer startPoint (1,0) → endPoint (0,1).
                Brush.linearGradient(
                    colors = listOf(gradientTop, gradientBottom),
                    start = Offset(Float.POSITIVE_INFINITY, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY),
                )
            )
    ) {
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
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                TierPageContent(tierPages[page])
            }
        }
    }
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
private fun TierPageContent(tier: TierPage) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 30.dp)
            .padding(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_homedet_tier_badge),
                contentDescription = null,
                modifier = Modifier.size(70.dp),
            )
            Text(
                text = tier.name,
                style = AirdropType.h5.copy(fontSize = 28.sp, lineHeight = 42.sp),
                color = Color.White,
                maxLines = 1,
            )
        }
        Text(
            text = "Benefits",
            style = AirdropType.title1,
            color = Color.White,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tier.benefits.forEach { benefit ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BenefitCheck(
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = benefit,
                        style = AirdropType.body2,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
