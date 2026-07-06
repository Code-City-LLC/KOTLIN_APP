package com.ga.airdrop.feature.more2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.ReferredFriend

/**
 * Refer a Friend landing.
 *
 * Swift `FigmaReferAFriendViewController` keeps the carousel, referral-link card,
 * Invite CTA, and inline "Your Referrals" history on one landing surface.
 */
@Composable
fun ReferAFriendScreen(
    onBack: () -> Unit,
    onInviteFriend: () -> Unit,
    refreshAfterInvite: Boolean = false,
    onRefreshAfterInviteConsumed: () -> Unit = {},
    viewModel: ReferAFriendViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(refreshAfterInvite) {
        if (refreshAfterInvite) {
            viewModel.loadReferredFriends()
            onRefreshAfterInviteConsumed()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .testTag("refer-figma-screen"),
    ) {
        Column(Modifier.fillMaxSize()) {
            More2InnerHeader(title = "Refer a Friend", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag("refer-scroll-content"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))
                ReferCarousel()
                Spacer(Modifier.height(22.dp))
                ReferInfoBlock()
                Spacer(Modifier.height(22.dp))
                ReferralLinkCard(
                    referralLink = state.referralLink,
                    onCopy = {
                        clipboard.setText(AnnotatedString(state.referralLink))
                        viewModel.markLinkCopied()
                    },
                )
                Spacer(Modifier.height(22.dp))
                InlineReferralsSection(
                    referrals = state.referrals,
                    loading = state.loadingReferrals,
                    error = state.error,
                )
                Spacer(Modifier.height(110.dp))
            }

            More2BottomBar(verticalPadding = 20.dp) {
                More2PrimaryButton(
                    text = "Invite Friends",
                    onClick = onInviteFriend,
                    modifier = Modifier.testTag("refer-invite-button"),
                )
            }
        }

        state.copiedMessage?.let { message ->
            More2Alert(
                title = "Refer a Friend",
                message = message,
                onDismiss = viewModel::dismissCopiedMessage,
            )
        }
    }
}

@Composable
private fun ReferralLinkCard(
    referralLink: String,
    onCopy: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(15.dp))
            .padding(16.dp)
            .testTag("refer-referral-link-card"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Your Referral Link",
            style = AirdropType.subtitle2,
            color = colors.textDescription,
            modifier = Modifier.testTag("refer-referral-link-title"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = referralLink,
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("refer-referral-link-value"),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandPalette.OrangeMain)
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("refer-referral-link-copy"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Copy",
                    style = AirdropType.button,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun InlineReferralsSection(
    referrals: List<ReferredFriend>,
    loading: Boolean,
    error: String?,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("refer-inline-referrals"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Your Referrals",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            modifier = Modifier.testTag("refer-referrals-title"),
        )
        when {
            loading -> {
                CircularProgressIndicator(
                    color = BrandPalette.OrangeMain,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(34.dp)
                        .testTag("refer-referrals-loading"),
                )
            }
            referrals.isEmpty() -> {
                Text(
                    text = error ?: "You haven’t referred anyone yet. Tap Invite Friends above to share AirDrop.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    modifier = Modifier.testTag("refer-referrals-empty"),
                )
            }
            else -> referrals.forEach { friend ->
                ReferredFriendCard(friend)
            }
        }
    }
}

@Composable
private fun ReferInfoBlock() {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("refer-info-block"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "Earn $2 USD Per Invite",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("refer-earn-title"),
        )
        Text(
            text = "Each friend who signs up and completes their first order adds $2 USD to your " +
                "account. Apply your rewards toward your next shipment — there’s no limit to how " +
                "much you can earn!",
            style = AirdropType.body2.copy(lineHeight = 23.sp),
            color = colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("refer-earn-body"),
        )
    }
}

@Composable
private fun ReferCarousel() {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val centerSecondCardPx = remember(density) {
        with(density) { (CarouselCardWidth + CarouselSpacing).roundToPx() }
    }
    LaunchedEffect(centerSecondCardPx) {
        scroll.scrollTo(centerSecondCardPx)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CarouselCardHeight)
            .horizontalScroll(scroll)
            .testTag("refer-hero-carousel"),
        horizontalArrangement = Arrangement.spacedBy(CarouselSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(CarouselSideInset))
        referSlides().forEach { slide ->
            ReferSlideCard(slide)
        }
        Spacer(Modifier.width(CarouselSideInset))
    }
}

@Composable
private fun ReferSlideCard(slide: ReferSlide) {
    val colors = AirdropTheme.colors
    val iconBackground = if (slide.whiteIconPlate) colors.gray100 else colors.gray300

    Column(
        modifier = Modifier
            .width(CarouselCardWidth)
            .height(CarouselCardHeight)
            .testTag("refer-hero-card-${slide.tag}")
            .clip(RoundedCornerShape(15.dp))
            .background(colors.gray100)
            .border(1.dp, slide.borderColor, RoundedCornerShape(15.dp))
            .padding(horizontal = 20.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(122.dp)
                .shadow(elevation = 18.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(slide.imageRes),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = slide.title,
            style = AirdropType.title1.copy(lineHeight = 28.sp),
            color = slide.titleColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("refer-hero-card-title-${slide.tag}"),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = slide.body,
            style = AirdropType.body2.copy(lineHeight = 22.sp),
            color = colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("refer-hero-card-body-${slide.tag}"),
        )
    }
}

private data class ReferSlide(
    val imageRes: Int,
    val title: String,
    val body: String,
    val titleColor: Color,
    val borderColor: Color,
    val whiteIconPlate: Boolean,
    val tag: String,
)

private fun referSlides() = listOf(
    ReferSlide(
        imageRes = R.drawable.img_more2_refer_friends,
        title = "Invite your friends",
        body = "Tap “INVITE,” enter your friend’s email — it’s that simple",
        titleColor = AlertPalette.OnHold,
        borderColor = AlertPalette.Middle.OnHold,
        whiteIconPlate = false,
        tag = "invite",
    ),
    ReferSlide(
        imageRes = R.drawable.img_more2_refer_cash,
        title = "Refer. Reward. Repeat.",
        body = "The more you share, the more you save",
        titleColor = AlertPalette.Completed,
        borderColor = AlertPalette.Middle.Completed,
        whiteIconPlate = true,
        tag = "reward",
    ),
    ReferSlide(
        imageRes = R.drawable.img_more2_refer_cap,
        title = "Invite and Earn",
        body = "Share the gift of World-Class Service & get rewarded for it",
        titleColor = AlertPalette.Pending,
        borderColor = AlertPalette.Middle.Pending,
        whiteIconPlate = false,
        tag = "earn",
    ),
)

private val CarouselCardWidth = 238.dp
private val CarouselCardHeight = 339.dp
private val CarouselSpacing = 15.dp
private val CarouselSideInset = 68.5.dp
