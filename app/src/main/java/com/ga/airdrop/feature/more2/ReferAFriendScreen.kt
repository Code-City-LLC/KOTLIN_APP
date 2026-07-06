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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.ReferredFriend
import kotlinx.coroutines.delay

/**
 * Refer a Friend landing.
 *
 * Swift precedence: the local Swift app on `staging/figma-redesign-testflight`
 * keeps the referral link and "Your Referrals" list inline. Figma nodes
 * 40001940:26885/26797 show the reset carousel-only page, but Swift wins for
 * runtime behavior and copy until that app changes.
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
    var showCopiedToast by remember { mutableStateOf(false) }

    LaunchedEffect(refreshAfterInvite) {
        if (refreshAfterInvite) {
            viewModel.loadFriends()
            onRefreshAfterInviteConsumed()
        }
    }

    if (showCopiedToast) {
        LaunchedEffect(showCopiedToast) {
            delay(1500)
            showCopiedToast = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .testTag("refer-figma-screen"),
    ) {
        Column(Modifier.fillMaxSize()) {
            More2InnerHeader(title = "Refer a Friend", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
                    .testTag("refer-scroll-content"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Spacer(Modifier.height(20.dp))
                ReferCarousel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("refer-hero-carousel"),
                )
                ReferInfoBlock()
                ReferralLinkCard(
                    referralLink = state.referralLink,
                    onCopy = {
                        clipboard.setText(AnnotatedString(state.referralLink))
                        showCopiedToast = true
                    },
                )
                ReferInviteFriendsButton(onClick = onInviteFriend)
                ReferralsSection(state)
                Spacer(Modifier.height(40.dp))
            }
        }

        if (showCopiedToast) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .width(160.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.textDarkTitle.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Link copied", style = AirdropType.subtitle2, color = BrandPalette.White)
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
            .testTag("refer-info-block"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Earn AirCoins for every friend you invite",
            style = AirdropType.title1.copy(lineHeight = 26.sp),
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("refer-earn-title"),
        )
        Text(
            text = "Each friend who signs up and completes their first order adds AirCoins to your " +
                "account. Apply your rewards toward your next shipment — there’s no limit to how " +
                "much you can earn!",
            style = AirdropType.body2,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("refer-earn-body"),
        )
    }
}

@Composable
private fun ReferCarousel(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(LocalSwiftCarouselHeight)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        referSlides().forEach { slide ->
            ReferSlideCard(slide)
        }
    }
}

@Composable
private fun ReferSlideCard(slide: ReferSlide) {
    val colors = AirdropTheme.colors
    Box(
        modifier = Modifier
            .width(LocalSwiftCarouselCardWidth)
            .height(LocalSwiftCarouselHeight)
            .testTag("refer-hero-card-${slide.tag}")
            .clip(RoundedCornerShape(15.dp))
            .background(slide.borderColor.copy(alpha = 0.18f))
            .border(1.dp, slide.borderColor, RoundedCornerShape(15.dp))
            .padding(horizontal = 14.dp),
    ) {
        Image(
            painter = painterResource(slide.imageRes),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .size(90.dp),
        )
        Text(
            text = slide.title,
            style = AirdropType.title2,
            color = slide.titleColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 118.dp)
                .fillMaxWidth()
                .testTag("refer-hero-card-title-${slide.tag}"),
        )
        Text(
            text = slide.body,
            style = AirdropType.body3,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 150.dp)
                .fillMaxWidth()
                .testTag("refer-hero-card-body-${slide.tag}"),
        )
    }
}

@Composable
private fun ReferralLinkCard(referralLink: String, onCopy: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(15.dp))
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)
            .testTag("refer-referral-link-card"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Your Referral Link", style = AirdropType.subtitle2, color = colors.textDescription)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = referralLink,
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("refer-referral-link"),
            )
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandPalette.OrangeMain)
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 14.dp)
                    .testTag("refer-copy-link-button"),
                contentAlignment = Alignment.Center,
            ) {
                Text("Copy", style = AirdropType.button, color = BrandPalette.White)
            }
        }
    }
}

@Composable
private fun ReferInviteFriendsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BrandPalette.OrangeMain)
            .clickable(onClick = onClick)
            .testTag("refer-invite-button"),
        contentAlignment = Alignment.Center,
    ) {
        Text("Invite Friends", style = AirdropType.button, color = BrandPalette.White)
    }
}

@Composable
private fun ReferralsSection(state: ReferAFriendUiState) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("refer-referrals-section"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Your Referrals", style = AirdropType.title2, color = colors.textDarkTitle)
        when {
            state.loadingReferrals -> {
                CircularProgressIndicator(
                    color = BrandPalette.OrangeMain,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                        .size(32.dp)
                        .testTag("refer-referrals-loading"),
                )
            }
            state.referrals.isEmpty() -> {
                Text(
                    text = "You haven’t referred anyone yet. Tap Invite Friends above to share AirDrop.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    modifier = Modifier.testTag("refer-referrals-empty"),
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.referrals.forEach { friend ->
                        InlineReferralRow(friend)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineReferralRow(friend: ReferredFriend) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("refer-referral-row-${friend.id}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = friend.displayName(),
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = friend.friendEmail.orEmpty(),
                style = AirdropType.body3,
                color = colors.textDescription,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ReferStatusPill(friend)
    }
}

@Composable
private fun ReferStatusPill(friend: ReferredFriend) {
    val label = friend.statusText?.takeIf { it.isNotBlank() } ?: friend.swiftStatusLabel()
    val normalized = label.lowercase()
    val (background, foreground) = when {
        normalized.contains("complete") || normalized.contains("success") ->
            AlertPalette.Light.Completed to AlertPalette.Completed
        normalized.contains("cancel") || normalized.contains("fail") ->
            AlertPalette.Light.Error to AlertPalette.Error
        else -> AlertPalette.Light.Pending to AlertPalette.Pending
    }
    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 8.dp)
            .testTag("refer-referral-status-${friend.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AirdropType.body3, color = foreground, maxLines = 1)
    }
}

private data class ReferSlide(
    val imageRes: Int,
    val title: String,
    val body: String,
    val titleColor: Color,
    val borderColor: Color,
    val tag: String,
)

private fun referSlides() = listOf(
    ReferSlide(
        imageRes = R.drawable.img_more2_refer_friends,
        title = "Invite your friends",
        body = "Tap \"Invite\", enter your friend's email - it's that simple.",
        titleColor = AlertPalette.OnHold,
        borderColor = AlertPalette.Middle.OnHold,
        tag = "invite",
    ),
    ReferSlide(
        imageRes = R.drawable.img_more2_refer_cash,
        title = "Refer. Reward. Repeat.",
        body = "The more you share, the more you save.",
        titleColor = AlertPalette.Completed,
        borderColor = AlertPalette.Middle.Completed,
        tag = "reward",
    ),
    ReferSlide(
        imageRes = R.drawable.img_more2_refer_cap,
        title = "Invite and Earn",
        body = "Share the gift of world-class service and get rewarded for it.",
        titleColor = AlertPalette.Pending,
        borderColor = AlertPalette.Middle.Pending,
        tag = "earn",
    ),
)

private fun ReferredFriend.displayName(): String {
    val combined = listOf(friendFirstName, friendLastName)
        .map { it.orEmpty().trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    return combined.ifBlank { friendName?.takeIf { it.isNotBlank() } ?: "Referred friend" }
}

private fun ReferredFriend.swiftStatusLabel(): String = when (status ?: 0) {
    1 -> "Completed"
    2 -> "Cancelled"
    else -> "Pending"
}

private val LocalSwiftCarouselCardWidth = 238.dp
private val LocalSwiftCarouselHeight = 220.dp
