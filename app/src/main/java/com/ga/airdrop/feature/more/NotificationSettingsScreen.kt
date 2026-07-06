package com.ga.airdrop.feature.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Notification Settings — behavior from FigmaNotificationSettingsViewController
 * (RN NotificationSettingsView port): master "Notification (On/Off)" row,
 * then Package Order Status and Promotions sections, each with Email / SMS /
 * Push channel toggles. Sub-trees dim + disable when their parent is off.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.start(context) }

    val packageSubEnabled = state.master && state.packageMaster
    val promosSubEnabled = state.master && state.promosMaster

    // Swift FigmaNotificationSettingsViewController.swift:60 — page bg gray200 (BG token).
    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(Modifier.fillMaxSize()) {
            MoreDetailHeader(title = "Notification Settings", onBack = onBack)
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.md, bottom = Spacing.xl),
            ) {
                ToggleRow(
                    title = "Notification (On/Off)",
                    titleStyle = AirdropType.subtitle1,
                    rowStyle = ToggleRowStyle.Master,
                    iconRes = R.drawable.ic_notifications,
                    iconTint = colors.iconSelected,
                    checked = state.master,
                    enabled = true,
                    onChange = { viewModel.setMaster(context, it) },
                    testTagPrefix = "notification-master",
                )
                Spacer(Modifier.height(SectionGap))
                ToggleRow(
                    title = "Package Order Status",
                    titleStyle = AirdropType.subtitle2,
                    rowStyle = ToggleRowStyle.Section,
                    checked = state.packageMaster,
                    enabled = state.master,
                    onChange = { viewModel.setPackageMaster(context, it) },
                    testTagPrefix = "notification-package-section",
                )
                Spacer(Modifier.height(RowGap))
                ToggleRow(
                    title = "Email",
                    titleStyle = AirdropType.body1,
                    rowStyle = ToggleRowStyle.Sub,
                    iconRes = R.drawable.ic_notification_mail,
                    darkIconRes = R.drawable.ic_notification_mail_dark,
                    checked = state.packageEmail,
                    enabled = packageSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(packageEmail = v) }, on)
                    },
                    testTagPrefix = "notification-package-email",
                )
                Spacer(Modifier.height(RowGap))
                ToggleRow(
                    title = "SMS",
                    titleStyle = AirdropType.body1,
                    rowStyle = ToggleRowStyle.Sub,
                    iconRes = R.drawable.ic_contacts_chat_light,
                    darkIconRes = R.drawable.ic_contacts_chat_dark,
                    checked = state.packageSms,
                    enabled = packageSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(packageSms = v) }, on)
                    },
                    testTagPrefix = "notification-package-sms",
                )
                Spacer(Modifier.height(RowGap))
                ToggleRow(
                    title = "Push",
                    titleStyle = AirdropType.body1,
                    rowStyle = ToggleRowStyle.Sub,
                    iconRes = R.drawable.ic_notifications,
                    iconTint = BrandPalette.OrangeMain,
                    checked = state.packagePush,
                    enabled = packageSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(packagePush = v) }, on)
                    },
                    testTagPrefix = "notification-package-push",
                )
                Spacer(Modifier.height(SectionGap))
                ToggleRow(
                    title = "Promotions",
                    titleStyle = AirdropType.subtitle2,
                    rowStyle = ToggleRowStyle.Section,
                    checked = state.promosMaster,
                    enabled = state.master,
                    onChange = { viewModel.setPromosMaster(context, it) },
                    testTagPrefix = "notification-promos-section",
                )
                Spacer(Modifier.height(RowGap))
                ToggleRow(
                    title = "Email",
                    titleStyle = AirdropType.body1,
                    rowStyle = ToggleRowStyle.Sub,
                    iconRes = R.drawable.ic_notification_mail,
                    darkIconRes = R.drawable.ic_notification_mail_dark,
                    checked = state.promosEmail,
                    enabled = promosSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(promosEmail = v) }, on)
                    },
                    testTagPrefix = "notification-promos-email",
                )
                Spacer(Modifier.height(RowGap))
                ToggleRow(
                    title = "SMS",
                    titleStyle = AirdropType.body1,
                    rowStyle = ToggleRowStyle.Sub,
                    iconRes = R.drawable.ic_contacts_chat_light,
                    darkIconRes = R.drawable.ic_contacts_chat_dark,
                    checked = state.promosSms,
                    enabled = promosSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(promosSms = v) }, on)
                    },
                    testTagPrefix = "notification-promos-sms",
                )
                Spacer(Modifier.height(RowGap))
                ToggleRow(
                    title = "Push",
                    titleStyle = AirdropType.body1,
                    rowStyle = ToggleRowStyle.Sub,
                    iconRes = R.drawable.ic_notifications,
                    iconTint = BrandPalette.OrangeMain,
                    checked = state.promosPush,
                    enabled = promosSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(promosPush = v) }, on)
                    },
                    testTagPrefix = "notification-promos-push",
                )
            }
        }
    }
}

private val RowGap = 12.dp
private val SectionGap = 20.dp

private enum class ToggleRowStyle(
    val rowHeight: Dp,
    val iconStart: Dp,
    val labelStartWithoutIcon: Dp,
) {
    Master(60.dp, 20.dp, 20.dp),
    Section(60.dp, 20.dp, 20.dp),
    Sub(56.dp, 28.dp, 32.dp),
}

@Composable
private fun ToggleRow(
    title: String,
    titleStyle: TextStyle,
    rowStyle: ToggleRowStyle,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    iconRes: Int? = null,
    darkIconRes: Int? = null,
    iconTint: Color? = null,
    testTagPrefix: String,
) {
    val colors = AirdropTheme.colors
    val resolvedIconRes = if (colors.isDark && darkIconRes != null) darkIconRes else iconRes
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowStyle.rowHeight)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .testTag("$testTagPrefix-row")
            .padding(
                start = if (resolvedIconRes != null) rowStyle.iconStart else rowStyle.labelStartWithoutIcon,
                end = Spacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (resolvedIconRes != null) {
                Image(
                    painter = painterResource(resolvedIconRes),
                    contentDescription = null,
                    colorFilter = iconTint?.let(ColorFilter::tint),
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("$testTagPrefix-icon"),
                )
            }
            Text(text = title, style = titleStyle, color = colors.textDarkTitle)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = BrandPalette.OrangeMain,
                checkedThumbColor = BrandPalette.White,
                uncheckedTrackColor = colors.gray300,
                uncheckedThumbColor = BrandPalette.White,
                uncheckedBorderColor = colors.gray300,
            ),
        )
    }
}
