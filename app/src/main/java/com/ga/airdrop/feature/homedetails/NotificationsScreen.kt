package com.ga.airdrop.feature.homedetails

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.feature.homedetails.components.HomeDetailsHeader
import com.ga.airdrop.feature.more.NotificationSettingsViewModel

/**
 * Notifications — Figma node 40007174:63447, behavior from
 * FigmaNotificationsListViewController: empty-state card + Settings footer.
 *
 * Swift/RN currently ship this empty-state surface unconditionally. The
 * notification payload route resolver remains in NotificationsViewModel for
 * push/deep-link handling, but this visible page follows Swift/Figma rather
 * than rendering a backend inbox variant that does not exist in Swift.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val colors = AirdropTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray200)
            .testTag("notifications-screen")
    ) {
        HomeDetailsHeader(title = "Notifications", onBack = onBack)
        EmptyState(
            modifier = Modifier.weight(1f),
            onOpenSettings = { onNavigate(Routes.NOTIFICATION_SETTINGS) },
        )
    }
}

// ─── Empty state (Figma 40007174:63447) ────────────────────────────────────

@Composable
private fun EmptyState(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(context.notificationsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Swift re-reads the same persisted master setting in viewWillAppear.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsOn = context.notificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier
            .fillMaxSize()
            .testTag("notifications-empty-root")
    ) {
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
                    .testTag("notifications-empty-card"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.md))
                Image(
                    painter = painterResource(R.drawable.img_homedet_notif_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        // Swift letterboxes the hero in a 340:310 box.
                        .aspectRatio(340f / 310f)
                        .testTag("notifications-empty-hero"),
                )
                Text(
                    text = if (notificationsOn) "You’re all set!" else "You’re all caught up.",
                    style = AirdropType.h5.copy(fontSize = 23.sp, lineHeight = 37.sp),
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, top = Spacing.sm, end = Spacing.md)
                        .testTag("notifications-empty-title"),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, top = Spacing.sm, end = Spacing.md)
                        .testTag("notifications-empty-body"),
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
                            .fillMaxWidth()
                            .padding(start = Spacing.md, top = Spacing.sm, end = Spacing.md)
                            .testTag("notifications-settings-link")
                            .clickable(onClick = onOpenSettings),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }
            Spacer(Modifier.height(Spacing.xl))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100)
                .testTag("notifications-footer")
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
            .testTag("notifications-settings-button")
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

private fun Context.notificationsEnabled(): Boolean =
    applicationContext
        .getSharedPreferences(NotificationSettingsViewModel.PREFS, Context.MODE_PRIVATE)
        .getBoolean("isNotifications", false)
