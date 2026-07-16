package com.ga.airdrop.feature.more2

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.frostedGlassSurface
import com.ga.airdrop.feature.more.QuietHoursViewModel
import java.util.Calendar

private const val DisabledPickerAlpha = 0.45f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietHoursSheet(
    onDismiss: () -> Unit,
    viewModel: QuietHoursViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(viewModel, context) {
        viewModel.start(context)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.frostedGlassSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quiet-hours-sheet")
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Text(
                text = "Quiet hours",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                modifier = Modifier.testTag("quiet-hours-title"),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "AirDrop notifications stay silent inside the window — " +
                    "they still land in Notification Center, you just don't get the banner " +
                    "or sound. Back-in-stock alerts you subscribed to are exempt.",
                style = AirdropType.body3,
                color = colors.textDescription,
                modifier = Modifier.testTag("quiet-hours-explainer"),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quiet-hours-enable-row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Enable quiet hours",
                    style = AirdropType.body1,
                    color = colors.textDarkTitle,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { viewModel.setEnabled(context, it) },
                    modifier = Modifier
                        .testTag("quiet-hours-enable-switch")
                        .semantics {
                            contentDescription = "Enable quiet hours"
                            stateDescription = if (state.enabled) "On" else "Off"
                        },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrandPalette.White,
                        checkedTrackColor = colors.buttonStatic,
                        uncheckedThumbColor = BrandPalette.White,
                        uncheckedTrackColor = colors.gray300,
                        uncheckedBorderColor = colors.gray300,
                    ),
                )
            }
            Spacer(Modifier.height(20.dp))
            QuietHoursTimeRow(
                label = "From",
                minutes = state.startMinutes,
                enabled = state.enabled,
                onPicked = { viewModel.setStart(context, it) },
                testTagPrefix = "quiet-hours-from",
            )
            Spacer(Modifier.height(16.dp))
            QuietHoursTimeRow(
                label = "Until",
                minutes = state.endMinutes,
                enabled = state.enabled,
                onPicked = { viewModel.setEnd(context, it) },
                testTagPrefix = "quiet-hours-until",
            )
        }
    }
}

@Composable
private fun QuietHoursTimeRow(
    label: String,
    minutes: Int,
    enabled: Boolean,
    onPicked: (Int) -> Unit,
    testTagPrefix: String,
) {
    val colors = AirdropTheme.colors
    val context = LocalContext.current
    val formattedTime = formatTime(context, minutes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("$testTagPrefix-row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = AirdropType.body2,
            color = colors.textDescription,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.xxs))
                .background(colors.gray200)
                .testTag("$testTagPrefix-control")
                .alpha(if (enabled) 1f else DisabledPickerAlpha)
                .semantics {
                    contentDescription = "$label time, $formattedTime"
                    if (!enabled) disabled()
                }
                .then(
                    if (enabled) {
                        Modifier.clickable {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> onPicked(hour * 60 + minute) },
                                minutes / 60,
                                minutes % 60,
                                DateFormat.is24HourFormat(context),
                            ).show()
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedTime,
                    style = AirdropType.subtitle2,
                    color = colors.textDarkTitle,
                    modifier = Modifier.testTag("$testTagPrefix-value"),
                )
                Spacer(Modifier.width(6.dp))
                Image(
                    painter = painterResource(R.drawable.ic_small_arrow_down),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.gray500),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun formatTime(context: android.content.Context, minutes: Int): String {
    val normalized = minutes.coerceIn(0, 1_439)
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, normalized / 60)
        set(Calendar.MINUTE, normalized % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}
