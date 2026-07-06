package com.ga.airdrop.feature.more2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.ReferredFriend
import com.ga.airdrop.feature.homedetails.components.CopiedToastPill
import kotlinx.coroutines.delay

/**
 * Refer a Friend — Swift-precedence implementation.
 *
 * Figma nodes 40001940:26885/26797 still provide the three-card hero assets, but
 * Swift FigmaReferAFriendViewController is the runtime guide for this page:
 * referral-link card, Copy affordance, Invite Friends CTA, and referred-friends
 * history all render here instead of being suppressed by the stale Figma-only
 * landing-page override.
 */
@Composable
fun ReferAFriendScreen(
    onBack: () -> Unit,
    onInviteFriend: () -> Unit,
    refreshAfterInvite: Boolean = false,
    onRefreshAfterInviteConsumed: () -> Unit = {},
    viewModel: ReferAFriendViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadReferredFriends()
    }
    LaunchedEffect(refreshAfterInvite) {
        if (refreshAfterInvite) {
            viewModel.loadReferredFriends()
            onRefreshAfterInviteConsumed()
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_600)
            copied = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
            .testTag("refer-swift-screen"),
    ) {
        Column(Modifier.fillMaxSize()) {
            More2InnerHeader(title = "Refer a Friend", onBack = onBack)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag("refer-scroll-content")
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroCarousel()
                HeroCopy()
                ReferralLinkCard(
                    referralLink = state.referralLink,
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Referral Link", state.referralLink),
                        )
                        copied = true
                    },
                )
                SolidOrangeButton(
                    text = "Invite Friends",
                    onClick = onInviteFriend,
                    modifier = Modifier.testTag("refer-invite-friends-button"),
                )
                Text(
                    text = "Your Referrals",
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("refer-referrals-header"),
                )
                ReferralsContent(state)
                Spacer(Modifier.height(40.dp))
            }
        }

        if (copied) {
            CopiedToastPill(
                text = "Link copied",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .testTag("refer-copy-toast"),
            )
        }
    }
}

@Composable
private fun HeroCopy() {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Earn AirCoins for every friend you invite",
            style = AirdropType.title1.copy(lineHeight = 26.sp),
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Each friend who signs up and completes their first order adds " +
                "AirCoins to your account. Apply your rewards toward your next " +
                "shipment — there’s no limit to how much you can earn!",
            style = AirdropType.body2,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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
            .testTag("refer-referral-link-card")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Your Referral Link",
            style = AirdropType.subtitle2,
            color = colors.textDescription,
        )
        Spacer(Modifier.height(6.dp))
        Row(
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
                    .testTag("refer-referral-link-label"),
            )
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(BrandPalette.OrangeMain)
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 14.dp)
                    .testTag("refer-copy-button"),
                contentAlignment = Alignment.Center,
            ) {
                Text("Copy", style = AirdropType.button, color = BrandPalette.White)
            }
        }
    }
}

@Composable
private fun SolidOrangeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(Radius.xs))
            .background(BrandPalette.OrangeMain)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AirdropType.button, color = BrandPalette.White)
    }
}

@Composable
private fun ReferralsContent(state: ReferAFriendUiState) {
    val colors = AirdropTheme.colors
    when {
        state.loadingReferrals -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm)
                    .testTag("refer-referrals-loading"),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = BrandPalette.OrangeMain,
                    strokeWidth = 2.dp,
                )
            }
        }
        state.referrals.isEmpty() -> {
            Text(
                text = "You haven’t referred anyone yet. Tap Invite Friends above to share AirDrop.",
                style = AirdropType.body2,
                color = colors.textDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("refer-referrals-empty"),
            )
        }
        else -> {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.referrals.forEach { friend ->
                    ReferralRow(friend)
                }
            }
        }
    }
}

@Composable
private fun ReferralRow(friend: ReferredFriend) {
    val colors = AirdropTheme.colors
    val status = friend.statusText?.takeIf { it.isNotBlank() }
        ?: statusLabel(friend.status)
    val (statusBg, statusFg) = statusColors(friend.status, status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("refer-referral-row-${friend.id}")
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.xs))
                .background(statusBg)
                .padding(horizontal = Spacing.sm, vertical = 1.dp)
                .testTag("refer-referral-status-${friend.id}"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = status,
                style = AirdropType.body3,
                color = statusFg,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private data class HeroCard(
    val imageRes: Int,
    val title: String,
    val body: String,
    val tint: Color,
    val textColor: Color,
    val tag: String,
)

@Composable
private fun HeroCarousel() {
    val cards = listOf(
        HeroCard(
            imageRes = R.drawable.img_more2_refer_friends,
            title = "Invite your friends",
            body = "Tap “Invite”, enter your friend’s email — it’s that simple.",
            tint = AlertPalette.Middle.OnHold,
            textColor = AlertPalette.OnHold,
            tag = "invite",
        ),
        HeroCard(
            imageRes = R.drawable.img_more2_refer_cash,
            title = "Refer. Reward. Repeat.",
            body = "The more you share, the more you save.",
            tint = AlertPalette.Middle.Completed,
            textColor = AlertPalette.Completed,
            tag = "reward",
        ),
        HeroCard(
            imageRes = R.drawable.img_more2_refer_cap,
            title = "Invite and Earn",
            body = "Share the gift of world-class service and get rewarded for it.",
            tint = AlertPalette.Middle.Pending,
            textColor = AlertPalette.Pending,
            tag = "earn",
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .horizontalScroll(rememberScrollState())
            .testTag("refer-hero-carousel"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        cards.forEach { card ->
            Column(
                modifier = Modifier
                    .width(CARD_WIDTH)
                    .fillMaxHeight()
                    .testTag("refer-hero-card-${card.tag}")
                    .clip(RoundedCornerShape(Radius.s))
                    .background(card.tint.copy(alpha = 0.18f))
                    .border(1.dp, card.tint, RoundedCornerShape(Radius.s))
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(card.imageRes),
                    contentDescription = null,
                    modifier = Modifier.size(90.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = card.title,
                    style = AirdropType.title2,
                    color = card.textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = card.body,
                    style = AirdropType.body3,
                    color = AirdropTheme.colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun ReferredFriend.displayName(): String {
    val fullName = listOfNotNull(friendFirstName, friendLastName)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    return fullName.ifBlank { friendName?.takeIf { it.isNotBlank() } ?: "Referred friend" }
}

private fun statusLabel(status: Int?): String =
    when (status ?: 0) {
        1 -> "Completed"
        2 -> "Cancelled"
        else -> "Pending"
    }

private fun statusColors(status: Int?, statusText: String): Pair<Color, Color> {
    val normalized = statusText.lowercase()
    return when {
        normalized.contains("complete") || normalized.contains("success") ->
            AlertPalette.Light.Completed to AlertPalette.Completed
        normalized.contains("cancel") || normalized.contains("fail") || status == 2 ->
            AlertPalette.Light.Error to AlertPalette.Error
        else ->
            AlertPalette.Light.Pending to AlertPalette.Pending
    }
}

private val CARD_WIDTH = 238.dp
