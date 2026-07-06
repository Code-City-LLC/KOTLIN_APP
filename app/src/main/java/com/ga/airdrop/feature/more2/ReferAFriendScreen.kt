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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.ReferredFriend
import kotlinx.coroutines.delay

/**
 * Refer a Friend — Figma node 40001940:26885, behavior from
 * FigmaReferAFriendViewController: hero carousel, referral-link copy card,
 * Invite Friends CTA, referred-friends list (GET /refer-friend).
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
    val clipboard = LocalClipboardManager.current
    var showCopiedToast by remember { mutableStateOf(false) }
    var hasLoadedReferrals by remember { mutableStateOf(false) }

    // Swift reloads the referred list on viewWillAppear and after Invite completion.
    LaunchedEffect(refreshAfterInvite) {
        if (refreshAfterInvite || !hasLoadedReferrals) {
            viewModel.loadReferredFriends()
            hasLoadedReferrals = true
        }
        if (refreshAfterInvite) onRefreshAfterInviteConsumed()
    }

    if (showCopiedToast) {
        LaunchedEffect(showCopiedToast) {
            delay(1600)
            showCopiedToast = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
    ) {
        Column(Modifier.fillMaxSize()) {
            More2InnerHeader(title = "Refer a Friend", onBack = onBack)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md),
            ) {
                Spacer(Modifier.height(Spacing.md))

                HeroCarousel()
                Spacer(Modifier.height(Spacing.md))

                Text(
                    text = "Earn AirCoins for every friend you invite",
                    style = AirdropType.title1,
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Each friend who signs up and completes their first order adds " +
                        "AirCoins to your account. Apply your rewards toward your next " +
                        "shipment — there’s no limit to how much you can earn!",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.md))

                ReferralLinkCard(
                    link = state.referralLink,
                    onCopy = {
                        clipboard.setText(AnnotatedString(state.referralLink))
                        showCopiedToast = true
                    },
                )
                Spacer(Modifier.height(Spacing.md))

                // Swift invite CTA: solid orangeMain, radius 10, 52dp.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("refer-invite-button")
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(BrandPalette.OrangeMain)
                        .clickable(onClick = onInviteFriend),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Invite Friends", style = AirdropType.button, color = BrandPalette.White)
                }
                Spacer(Modifier.height(Spacing.md))

                Text("Your Referrals", style = AirdropType.title2, color = colors.textDarkTitle)
                Spacer(Modifier.height(Spacing.sm))

                if (state.loadingReferrals) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = BrandPalette.OrangeMain,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                } else if (state.referrals.isEmpty()) {
                    Text(
                        text = "You haven’t referred anyone yet. Tap Invite Friends " +
                            "above to share AirDrop.",
                        style = AirdropType.body2,
                        color = colors.textDescription,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        state.referrals.forEach { friend -> ReferralRow(friend) }
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }

        // "Link copied" toast pill above the bottom edge.
        if (showCopiedToast) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .width(160.dp)
                    .height(40.dp)
                    .testTag("refer-link-copied-toast")
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(colors.textDarkTitle.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Link copied",
                    style = AirdropType.subtitle2,
                    color = if (colors.isDark) colors.gray150 else BrandPalette.White,
                )
            }
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
    val colors = AirdropTheme.colors
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
    // Swift FigmaReferAFriendViewController.swift:207-300 — 238x220 cards
    // filled with tint@0.18 + 1dp tint border (radius 15), bare 90dp
    // illustration 18dp from the top, Title2 tinted title 10dp below,
    // Body3 textDarkTitle body 6dp below.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .testTag("refer-hero-carousel")
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        cards.forEach { card ->
            Column(
                modifier = Modifier
                    .width(238.dp)
                    .fillMaxSize()
                    .testTag("refer-hero-card-${card.tag}")
                    .clip(RoundedCornerShape(Radius.s))
                    .background(card.tint.copy(alpha = 0.18f))
                    .border(1.dp, card.tint, RoundedCornerShape(Radius.s))
                    .padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(18.dp))
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
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ReferralLinkCard(link: String, onCopy: () -> Unit) {
    val colors = AirdropTheme.colors
    More2OuterCard(Modifier.testTag("refer-referral-link-card")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "Your Referral Link",
                style = AirdropType.subtitle2,
                color = colors.textDescription,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = link.middleEllipsize(),
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("refer-referral-link-text"),
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("refer-copy-button")
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(BrandPalette.OrangeMain)
                        .clickable(onClick = onCopy)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Copy", style = AirdropType.button, color = BrandPalette.White)
                }
            }
        }
    }
}

private fun String.middleEllipsize(maxChars: Int = 44): String {
    if (length <= maxChars) return this
    val head = ((maxChars - 1) * 0.62f).toInt()
    val tail = maxChars - 1 - head
    return take(head) + "…" + takeLast(tail)
}

@Composable
private fun ReferralRow(friend: ReferredFriend) {
    val colors = AirdropTheme.colors
    val fullName = listOfNotNull(friend.friendFirstName, friend.friendLastName)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .ifEmpty { friend.friendName ?: "Referred friend" }

    val statusText = friend.statusText ?: when (friend.status ?: 0) {
        1 -> "Completed"
        2 -> "Cancelled"
        else -> "Pending"
    }
    val normalized = statusText.lowercase()
    val (pillBg, pillFg) = when {
        normalized.contains("complete") || normalized.contains("success") ->
            AlertPalette.Light.Completed to AlertPalette.Completed
        normalized.contains("cancel") || normalized.contains("fail") ->
            AlertPalette.Light.Error to AlertPalette.Error
        else -> AlertPalette.Light.Pending to AlertPalette.Pending
    }

    More2OuterCard {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = fullName,
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
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(pillBg)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(text = statusText, style = AirdropType.body3, color = pillFg)
            }
        }
    }
}
