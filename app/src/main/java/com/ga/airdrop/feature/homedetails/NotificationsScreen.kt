package com.ga.airdrop.feature.homedetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.data.model.AirdropNotification
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Notifications — Figma 40007174:63447 (empty-state card + Settings footer;
 * 40007176:63599 is the flat variant). The Swift VC is STATIC (known gap);
 * here the inbox is LIVE: GET /user/notifications paginated, unread styling,
 * tap → mark-read + deep-link via route/referenceID. The Figma empty state
 * shows when the backend returns no rows.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: NotificationsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray200)
    ) {
        HomeDetailsHeader(title = "Notifications", onBack = onBack)

        when {
            state.loading && !state.loadedOnce -> LoadingState()
            state.error != null && state.items.isEmpty() ->
                ErrorState(message = state.error!!, onRetry = viewModel::refresh)
            state.items.isEmpty() && state.loadedOnce ->
                EmptyState(onOpenSettings = { onNavigate(Routes.NOTIFICATION_SETTINGS) })
            else -> NotificationList(
                items = state.items,
                loadingMore = state.loadingMore,
                onLoadMore = viewModel::loadMore,
                onTap = { notification ->
                    viewModel.onNotificationTapped(notification)?.let(onNavigate)
                },
            )
        }
    }
}

// ─── Loading / error ───────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandPalette.OrangeMain, strokeWidth = 2.dp)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Unable to load notifications",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = AirdropType.body2,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
        )
        OutlineButton(text = "Try Again", onClick = onRetry)
    }
}

// ─── Live list ─────────────────────────────────────────────────────────────

@Composable
private fun NotificationList(
    items: List<AirdropNotification>,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onTap: (AirdropNotification) -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, items.size) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items.size, key = { items[it].id }) { index ->
            NotificationRow(notification = items[index], onClick = { onTap(items[index]) })
        }
        if (loadingMore) {
            item("loadingMore") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.sm1),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = BrandPalette.OrangeMain,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Row layout follows the app's list-card pattern (no Figma frame exists for
 * a populated inbox): 40dp bell disc, Title2/SubTitle2 headline, Body3 body,
 * timestamp; unread rows use the peach disc + orange dot + bold title.
 */
@Composable
private fun NotificationRow(notification: AirdropNotification, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    val unread = !notification.isRead
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onClick)
            .padding(Spacing.sm1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (unread) colors.peachLight else colors.gray200),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (unread) BrandPalette.OrangeMain else colors.iconSelected
                ),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    style = if (unread) AirdropType.title2 else AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Box(
                        Modifier
                            .padding(start = Spacing.xs)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(BrandPalette.OrangeMain)
                    )
                }
            }
            if (notification.body.isNotBlank()) {
                Text(
                    text = notification.body,
                    style = AirdropType.body3,
                    color = colors.textDescription,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            notification.createdAt?.let { raw ->
                Text(
                    text = formatNotificationDate(raw),
                    style = AirdropType.body3,
                    color = colors.gray500,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun formatNotificationDate(raw: String): String {
    val output = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)
    val inputs = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )
    for (pattern in inputs) {
        runCatching {
            val parser = SimpleDateFormat(pattern, Locale.US)
            return output.format(parser.parse(raw)!!)
        }
    }
    return raw
}

// ─── Empty state (Figma 40007174:63447) ────────────────────────────────────

@Composable
private fun EmptyState(onOpenSettings: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.xl))
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.gray100)
                    .padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.img_homedet_notif_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .aspectRatio(1062f / 747f),
                )
                Text(
                    text = "You’re all caught up.",
                    style = AirdropType.h5.copy(fontSize = 23.sp, lineHeight = 37.sp),
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(
                    text = "Turn on notifications to get real-time updates on package " +
                        "tracking, status changes, pricing updates, and special offers.",
                    style = AirdropType.body1.copy(fontSize = 18.sp, lineHeight = 28.sp),
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(
                    text = buildAnnotatedString {
                        append("Not sure if it’s enabled? Check your ")
                        withStyle(SpanStyle(color = BrandPalette.OrangeMain)) {
                            append("notification settings.")
                        }
                    },
                    style = AirdropType.body1.copy(fontSize = 18.sp, lineHeight = 28.sp),
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .clickable(onClick = onOpenSettings),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
        // Footer "Settings" ghost button bar.
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.md)
                    .navigationBarsPadding()
            ) {
                SettingsGhostButton(onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun SettingsGhostButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, BrandPalette.OrangeMain, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Settings",
            style = AirdropType.button,
            color = BrandPalette.OrangeMain,
        )
    }
}
