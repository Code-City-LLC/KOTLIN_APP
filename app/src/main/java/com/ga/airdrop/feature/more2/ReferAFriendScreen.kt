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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Refer a Friend — scoped Figma override. Figma nodes 40001940:26885/26797 win
 * over FigmaReferAFriendViewController.swift for this page only.
 *
 * The page is only: inner header, three hero cards, "Earn $2 USD Per Invite"
 * copy, and a bottom-pinned Invite button. Do not add the Swift referral-link
 * card, inline "Invite Friends" button, or referred-friends list here.
 */
@Composable
fun ReferAFriendScreen(
    onBack: () -> Unit,
    onInviteFriend: () -> Unit,
    refreshAfterInvite: Boolean = false,
    onRefreshAfterInviteConsumed: () -> Unit = {},
) {
    val colors = AirdropTheme.colors

    LaunchedEffect(refreshAfterInvite) {
        if (refreshAfterInvite) onRefreshAfterInviteConsumed()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150),
    ) {
        More2InnerHeader(title = "Refer a Friend", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.md))

            HeroCarousel()
            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "Earn $2 USD Per Invite",
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Each friend who signs up and completes their first order adds " +
                    "$2 USD to your account. Apply your rewards toward your next " +
                    "shipment — there’s no limit to how much you can earn!",
                style = AirdropType.body2,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray150),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("refer-invite-button")
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF783E), Color(0xFFF15114)),
                            ),
                        )
                        .clickable(onClick = onInviteFriend),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Invite", style = AirdropType.button, color = BrandPalette.White)
                }
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
    val circleWhite: Boolean,
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
            circleWhite = false,
        ),
        HeroCard(
            imageRes = R.drawable.img_more2_refer_cash,
            title = "Refer. Reward. Repeat.",
            body = "The more you share, the more you save.",
            tint = AlertPalette.Middle.Completed,
            textColor = AlertPalette.Completed,
            tag = "reward",
            circleWhite = true,
        ),
        HeroCard(
            imageRes = R.drawable.img_more2_refer_cap,
            title = "Invite and Earn",
            body = "Share the gift of world-class service and get rewarded for it.",
            tint = AlertPalette.Middle.Pending,
            textColor = AlertPalette.Pending,
            tag = "earn",
            circleWhite = false,
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .testTag("refer-hero-carousel")
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        cards.forEach { card ->
            Column(
                modifier = Modifier
                    .width(238.dp)
                    .fillMaxHeight()
                    .testTag("refer-hero-card-${card.tag}")
                    .clip(RoundedCornerShape(Radius.s))
                    .background(colors.gray100)
                    .border(1.dp, card.tint, RoundedCornerShape(Radius.s))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(122.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(if (card.circleWhite) colors.gray100 else colors.gray300),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(card.imageRes),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = card.title,
                    style = AirdropType.title1,
                    color = card.textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = card.body,
                    style = AirdropType.body2,
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
