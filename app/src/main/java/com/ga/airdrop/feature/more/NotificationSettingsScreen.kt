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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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

    Box(Modifier.fillMaxSize().background(colors.gray100)) {
        Column(Modifier.fillMaxSize()) {
            MoreDetailHeader(title = "Notification Settings", onBack = onBack)
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ToggleRow(
                    title = "Notification (On/Off)",
                    titleStyle = AirdropType.subtitle1,
                    iconRes = R.drawable.ic_settings_notifications,
                    checked = state.master,
                    enabled = true,
                    onChange = { viewModel.setMaster(context, it) },
                )
                Spacer(Modifier.height(Spacing.sm))
                ToggleRow(
                    title = "Package Order Status",
                    titleStyle = AirdropType.subtitle2,
                    checked = state.packageMaster,
                    enabled = state.master,
                    onChange = { viewModel.setPackageMaster(context, it) },
                )
                ToggleRow(
                    title = "Email",
                    titleStyle = AirdropType.body1,
                    iconRes = R.drawable.ic_mail,
                    indented = true,
                    checked = state.packageEmail,
                    enabled = packageSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(packageEmail = v) }, on)
                    },
                )
                ToggleRow(
                    title = "SMS",
                    titleStyle = AirdropType.body1,
                    iconRes = R.drawable.ic_chat,
                    indented = true,
                    checked = state.packageSms,
                    enabled = packageSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(packageSms = v) }, on)
                    },
                )
                ToggleRow(
                    title = "Push",
                    titleStyle = AirdropType.body1,
                    iconRes = R.drawable.ic_notifications,
                    indented = true,
                    checked = state.packagePush,
                    enabled = packageSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(packagePush = v) }, on)
                    },
                )
                Spacer(Modifier.height(Spacing.sm))
                ToggleRow(
                    title = "Promotions",
                    titleStyle = AirdropType.subtitle2,
                    checked = state.promosMaster,
                    enabled = state.master,
                    onChange = { viewModel.setPromosMaster(context, it) },
                )
                ToggleRow(
                    title = "Email",
                    titleStyle = AirdropType.body1,
                    iconRes = R.drawable.ic_mail,
                    indented = true,
                    checked = state.promosEmail,
                    enabled = promosSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(promosEmail = v) }, on)
                    },
                )
                ToggleRow(
                    title = "SMS",
                    titleStyle = AirdropType.body1,
                    iconRes = R.drawable.ic_chat,
                    indented = true,
                    checked = state.promosSms,
                    enabled = promosSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(promosSms = v) }, on)
                    },
                )
                ToggleRow(
                    title = "Push",
                    titleStyle = AirdropType.body1,
                    iconRes = R.drawable.ic_notifications,
                    indented = true,
                    checked = state.promosPush,
                    enabled = promosSubEnabled,
                    onChange = { on ->
                        viewModel.setChannel(context, { s, v -> s.copy(promosPush = v) }, on)
                    },
                )
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    titleStyle: TextStyle,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    iconRes: Int? = null,
    indented: Boolean = false,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(59.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.weight(1f),
        ) {
            if (indented) Spacer(Modifier.width(Spacing.sm))
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
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
