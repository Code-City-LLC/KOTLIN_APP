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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReferredFriendsScreen(
    onBack: () -> Unit,
    viewModel: ReferredFriendsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    // Swift load-more: fetch the next page as the scroll nears the bottom.
    val scrollState = rememberScrollState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val max = scrollState.maxValue
            max > 0 && scrollState.value >= max - 400
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    val refreshing = state.loading && state.loadedOnce
    val ptrState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .testTag("referred-friends-screen"),
    ) {
        More2InnerHeader(title = "Referred Friends", onBack = onBack)

        // Swift FigmaReferredFriendsViewController refresh control.
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            state = ptrState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            indicator = {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                    state = ptrState,
                    isRefreshing = refreshing,
                    color = BrandPalette.OrangeMain,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            when {
                state.loading && !state.loadedOnce -> {
                    Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            color = BrandPalette.OrangeMain,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 60.dp)
                                .size(42.dp)
                                .testTag("referred-friends-loading"),
                        )
                    }
                }
                state.error != null && state.referrals.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Spacer(Modifier.height(80.dp))
                        Text(
                            text = state.error ?: "Unable to load your referrals.",
                            style = AirdropType.body1,
                            color = colors.textDescription,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("referred-friends-error"),
                        )
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .border(
                                    1.dp,
                                    BrandPalette.OrangeMain,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(onClick = viewModel::refresh)
                                .padding(horizontal = 24.dp)
                                .testTag("referred-friends-retry"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Retry",
                                style = AirdropType.subtitle2,
                                color = BrandPalette.OrangeMain,
                            )
                        }
                    }
                }
                state.referrals.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "You haven't referred any friends yet.",
                            style = AirdropType.body1,
                            color = colors.textDescription,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(top = 80.dp, start = 32.dp, end = 32.dp)
                                .testTag("referred-friends-empty"),
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .testTag("referred-friends-list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.referrals.forEach { friend ->
                            ReferredFriendCard(friend)
                        }
                        if (state.loadingMore) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = BrandPalette.OrangeMain,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("referred-friends-loadmore"),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
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
    1 -> "Completed"
    2 -> "Pending"
    3 -> "Expired"
    4 -> "Cancelled"
    else -> "Unknown"
}
