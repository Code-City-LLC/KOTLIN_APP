package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.ReferredFriend
import com.ga.airdrop.feature.shipments.ShipmentsFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferredFriendsScreen(
    onBack: () -> Unit,
    viewModel: ReferredFriendsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            state.referrals.isNotEmpty() && layout.totalItemsCount > 0 &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.referrals.size, state.hasMore) {
        if (nearEnd) viewModel.loadMore()
    }

    Column(Modifier.fillMaxSize().background(colors.gray150).testTag("referred-friends-screen")) {
        More2InnerHeader(title = "Referred Friends", onBack = onBack)
        PullToRefreshBox(
            isRefreshing = state.loading && state.referrals.isNotEmpty(),
            onRefresh = viewModel::loadFriends,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("referred-friends-refresh"),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("referred-friends-list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.referrals.isEmpty()) {
                    item(key = "initial-state") {
                        when {
                            state.loading -> Box(Modifier.fillMaxWidth().padding(top = 44.dp), contentAlignment = Alignment.TopCenter) {
                                CircularProgressIndicator(color = BrandPalette.OrangeMain,
                                    modifier = Modifier.size(42.dp).testTag("referred-friends-loading"))
                            }
                            state.loadFailed -> ReferralRetry(
                                "Couldn't load your referrals. Tap Retry to try again.",
                                "referred-friends-load-failed", "referred-friends-retry", viewModel::loadFriends,
                                Modifier.padding(top = 64.dp, start = 12.dp, end = 12.dp),
                            )
                            else -> Text(
                                text = "You haven't referred any friends yet.",
                                style = AirdropType.body1, color = colors.textDescription,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 12.dp, end = 12.dp)
                                    .testTag("referred-friends-empty"),
                            )
                        }
                    }
                } else {
                    if (state.loadFailed && !state.loading) {
                        item(key = "refresh-failure") {
                            ReferralRetry("Couldn't refresh your referrals. Your previous results are shown.",
                                "referred-friends-refresh-failed", "referred-friends-refresh-retry", viewModel::loadFriends)
                        }
                    }
                    items(state.referrals, key = { it.id }) { friend -> ReferredFriendCard(friend) }
                    if (state.loadingMore) {
                        item(key = "loading-more") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandPalette.OrangeMain,
                                    modifier = Modifier.size(32.dp).testTag("referred-friends-loading-more"))
                            }
                        }
                    } else if (state.loadMoreError) {
                        item(key = "more-failure") {
                            ReferralRetry("Couldn't load more referrals.", "referred-friends-more-failed",
                                "referred-friends-more-retry", viewModel::retryLoadMore)
                        }
                    }
                    item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ReferralRetry(message: String, tag: String, retryTag: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = AirdropType.body1, color = AirdropTheme.colors.textDescription, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.md))
        Text("Retry", style = AirdropType.subtitle2, color = BrandPalette.OrangeMain,
            modifier = Modifier.clickable(onClick = onRetry).padding(Spacing.sm).testTag(retryTag))
    }
}

@Composable
private fun ReferredFriendCard(friend: ReferredFriend) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("referred-friends-row-${friend.id}")
            .clip(RoundedCornerShape(12.dp))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FriendInfoRow("Name", friend.displayName())
        FriendInfoRow("Email", friend.friendEmail?.takeIf { it.isNotBlank() } ?: "—")
        FriendInfoRow("Refer Date", ShipmentsFormat.date(friend.referDate))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Status",
                style = AirdropType.subtitle3,
                color = colors.textDescription,
                modifier = Modifier.weight(1f),
            )
            StatusChip(friend)
        }
    }
}

@Composable
private fun FriendInfoRow(label: String, value: String) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AirdropType.subtitle3,
            color = colors.textDescription,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = AirdropType.body2,
            color = colors.textDarkTitle,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
private fun StatusChip(friend: ReferredFriend) {
    val colors = AirdropTheme.colors
    val label = friend.statusText?.takeIf { it.isNotBlank() } ?: friend.statusFallbackText()
    val (background, foreground) = when (friend.status) {
        1 -> AlertPalette.Light.Completed to AlertPalette.Completed
        2 -> AlertPalette.Light.Pending to AlertPalette.Pending
        3 -> AlertPalette.Light.Error to AlertPalette.Error
        4 -> colors.gray300 to colors.textDescription
        else -> AlertPalette.Light.OnHold to AlertPalette.OnHold
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("referred-friends-status-${friend.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AirdropType.subtitle3,
            color = foreground,
            maxLines = 1,
        )
    }
}

private fun ReferredFriend.displayName(): String {
    if (!friendName.isNullOrBlank()) return friendName
    val combined = listOf(friendFirstName, friendLastName)
        .map { it.orEmpty().trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    return combined.ifBlank { "—" }
}

private fun ReferredFriend.statusFallbackText(): String = when (status) {
    0, 99 -> "Not Signed Up"
    1 -> "Signed up but has not placed first order"
    2 -> "Placed first order but not delivered"
    3 -> "AirCoin Credited"
    else -> "Unknown Status"
}
