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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
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
    val state by viewModel.state.collectAsState()

    NotificationsScreenContent(
        state = state,
        onBack = onBack,
        onOpenSettings = { onNavigate(Routes.NOTIFICATION_SETTINGS) },
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onSetUnreadOnly = viewModel::setUnreadOnly,
        onNotificationTap = { notification ->
            viewModel.onNotificationTapped(notification)?.let(onNavigate)
        },
    )
}

@Composable
internal fun NotificationsScreenContent(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSetUnreadOnly: (Boolean) -> Unit,
    onNotificationTap: (AirdropNotification) -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray200)
            .testTag(NotificationsTags.ROOT)
    ) {
        HomeDetailsHeader(title = "Notifications", onBack = onBack)

        when {
            state.loading && !state.loadedOnce -> LoadingState()
            // Load failed with nothing cached → All|Unread filter stays, the
            // shared failure card sits below it (Swift 89fbb11). A failure
            // with cached rows falls through to the list — cached rows win.
            state.error != null && state.items.isEmpty() ->
                LoadFailureBelowFilter(
                    unreadOnly = state.showUnreadOnly,
                    onSetUnreadOnly = onSetUnreadOnly,
                    onRetry = onRefresh,
                )
            // Genuinely no notifications (All mode) → Figma empty-state card.
            // Under the Unread filter, keep the segment + inline empty label.
            // Not while loading: a Retry/refetch shows the list spinner, not
            // a premature "no notifications" flash.
            state.items.isEmpty() && state.loadedOnce && !state.showUnreadOnly && !state.loading ->
                EmptyState(onOpenSettings = onOpenSettings)
            else -> NotificationList(
                items = state.visibleItems,
                unreadOnly = state.showUnreadOnly,
                emptyUnread = state.showUnreadOnly && state.visibleItems.isEmpty(),
                loadingMore = state.loadingMore,
                refreshing = state.loading && state.loadedOnce,
                onSetUnreadOnly = onSetUnreadOnly,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onTap = onNotificationTap,
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

/** Swift 89fbb11: refresh failure with an empty inbox — filter + shared card. */
@Composable
private fun LoadFailureBelowFilter(
    unreadOnly: Boolean,
    onSetUnreadOnly: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        NotificationsFilterSegment(unreadOnly = unreadOnly, onChange = onSetUnreadOnly)
        com.ga.airdrop.core.designsystem.components.LoadFailureCard(
            message = "Unable to load notifications. Check your connection and try again.",
            onRetry = onRetry,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
            testTagPrefix = "notifications-load-failure",
        )
    }
}

// ─── Live list ─────────────────────────────────────────────────────────────

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun NotificationList(
    items: List<AirdropNotification>,
    unreadOnly: Boolean,
    emptyUnread: Boolean,
    loadingMore: Boolean,
    refreshing: Boolean,
    onSetUnreadOnly: (Boolean) -> Unit,
    onRefresh: () -> Unit,
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

    Column(Modifier.fillMaxSize()) {
        // Swift All | Unread segment (server-side unread_only filter).
        NotificationsFilterSegment(unreadOnly = unreadOnly, onChange = onSetUnreadOnly)

        // Swift FigmaNotificationsListViewController refresh control.
        val ptrState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = ptrState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                    state = ptrState,
                    isRefreshing = refreshing,
                    color = com.ga.airdrop.core.designsystem.theme.BrandPalette.OrangeMain,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            if (emptyUnread) {
                // Swift filterEmptyLabel — scrollable so pull-to-refresh still fires.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No unread notifications.",
                        style = AirdropType.body2,
                        color = AirdropTheme.colors.textDescription,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 60.dp, start = 32.dp, end = 32.dp)
                            .testTag(NotificationsTags.FILTER_EMPTY),
                    )
                }
            } else {
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
        }
    }
}

/** Swift FigmaNotificationsFilterControl — compact All | Unread segment. */
@Composable
private fun NotificationsFilterSegment(
    unreadOnly: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .padding(start = Spacing.md, top = 14.dp, bottom = 4.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.gray300)
            .padding(3.dp)
            .testTag(NotificationsTags.FILTER),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        NotificationsFilterOption(
            label = "All",
            selected = !unreadOnly,
            testTag = NotificationsTags.FILTER_ALL,
            onClick = { onChange(false) },
        )
        NotificationsFilterOption(
            label = "Unread",
            selected = unreadOnly,
            testTag = NotificationsTags.FILTER_UNREAD,
            onClick = { onChange(true) },
        )
    }
}

@Composable
private fun NotificationsFilterOption(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Box(
        modifier = Modifier
            .width(82.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) BrandPalette.OrangeMain else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AirdropType.subtitle3,
            color = if (selected) androidx.compose.ui.graphics.Color.White else colors.textDescription,
        )
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
            .padding(Spacing.sm1)
            .testTag(NotificationsTags.row(notification.id)),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (unread) colors.peachLight else colors.gray200)
                .testTag(NotificationsTags.icon(notification.id)),
            contentAlignment = Alignment.Center,
        ) {
            // Per-type duotone glyph (ledger C5, Swift notificationIcon(for:)).
            // No tint — a solid tint flattens the duotone; unread emphasis
            // stays on the disc + dot + bold title.
            Image(
                painter = painterResource(
                    NotificationIconCatalog.iconRes(notification, colors.isDark)
                ),
                contentDescription = null,
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
            // Per-type CTA + right chevron — Swift actionLabel (underlined orange
            // SemiBold-16) + chevronDown rotated -90° (orange), at the card foot.
            Row(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = NotificationIconCatalog.actionTitle(notification),
                    style = AirdropType.subtitle1.copy(textDecoration = TextDecoration.Underline),
                    color = BrandPalette.OrangeMain,
                )
                Image(
                    painter = painterResource(R.drawable.ic_small_arrow_down),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(BrandPalette.OrangeMain),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(-90f),
                )
            }
        }
    }
}

internal object NotificationsTags {
    const val ROOT = "notifications-root"
    const val FILTER = "notifications-filter"
    const val FILTER_ALL = "notifications-filter-all"
    const val FILTER_UNREAD = "notifications-filter-unread"
    const val FILTER_EMPTY = "notifications-filter-empty"
    fun row(id: String) = "notification-row-$id"
    fun icon(id: String) = "notification-icon-$id"
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
    // Swift FigmaNotificationsListViewController.applyContentForCurrentState
    // (:268-282): when the master notifications toggle is ON the empty state
    // flips to the "You're all set!" variant and drops the settings link.
    // Re-read on every resume so returning from Settings updates the copy.
    val context = androidx.compose.ui.platform.LocalContext.current
    var notificationsOn by remember(context) {
        com.ga.airdrop.core.prefs.NotificationAccountPreferences.init(context)
        mutableStateOf(
            com.ga.airdrop.core.prefs.NotificationAccountPreferences.currentMasterEnabled(),
        )
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsOn =
                    com.ga.airdrop.core.prefs.NotificationAccountPreferences.currentMasterEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
                        // Swift letterboxes the hero in a 340:310 box.
                        .aspectRatio(340f / 310f),
                )
                Text(
                    text = if (notificationsOn) "You’re all set!" else "You’re all caught up.",
                    style = AirdropType.h5.copy(fontSize = 23.sp, lineHeight = 37.sp),
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(
                    text = if (notificationsOn) {
                        "We’ll notify you about important activity."
                    } else {
                        "Turn on notifications to get real-time updates on package " +
                            "tracking, status changes, pricing updates, and special offers."
                    },
                    style = AirdropType.body1.copy(fontSize = 18.sp, lineHeight = 28.sp),
                    color = colors.textDescription,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                if (!notificationsOn) {
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
