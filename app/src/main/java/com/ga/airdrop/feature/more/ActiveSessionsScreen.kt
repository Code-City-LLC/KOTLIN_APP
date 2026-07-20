package com.ga.airdrop.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.ActiveSession
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Active Sessions (signed-in devices) — Settings sub-screen. Lists the user's
 * Sanctum tokens, signs one device out, or signs out all OTHER devices at once
 * (POST /user/sessions/revoke). The current device shows a "This device" tag
 * and can't be revoked here (Logout ends it).
 */
@Composable
fun ActiveSessionsScreen(
    onBack: () -> Unit,
    viewModel: ActiveSessionsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    var confirmAll by remember { mutableStateOf(false) }
    var confirmRevokeId by remember { mutableStateOf<String?>(null) }

    val hasOthers = state.sessions.any { !it.isCurrent }

    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(Modifier.fillMaxSize()) {
            MoreDetailHeader(title = "Active Sessions", onBack = onBack)

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.md, bottom = Spacing.xl)
                    .testTag("active-sessions-list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "These are the devices currently signed in to your account. " +
                        "Sign out any you don't recognise.",
                    style = AirdropType.body3,
                    color = colors.textDescription,
                    modifier = Modifier.padding(bottom = 2.dp),
                )

                if (state.loading && state.sessions.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = colors.iconSelected,
                            strokeWidth = 2.dp,
                        )
                    }
                } else if (state.sessions.isEmpty()) {
                    Text(
                        text = "No active sessions found.",
                        style = AirdropType.body2,
                        color = colors.gray500,
                        modifier = Modifier.padding(vertical = Spacing.lg),
                    )
                } else {
                    state.sessions.forEach { session ->
                        SessionCard(
                            session = session,
                            revoking = state.revokingId == session.id,
                            onRevoke = { confirmRevokeId = session.id },
                        )
                    }
                }
            }

            MoreBottomButtonBar(
                text = "Sign out all other devices",
                loading = state.revokingAll,
                enabled = hasOthers && state.revokingId == null,
                onClick = { confirmAll = true },
                modifier = Modifier.testTag("active-sessions-revoke-all"),
            )
        }
    }

    if (confirmAll) {
        MoreConfirmDialog(
            title = "Sign out other devices?",
            message = "This signs out every device except this one. Those devices " +
                "will need to log in again.",
            confirmLabel = "Sign out all",
            onConfirm = viewModel::revokeAllOthers,
            onDismiss = { confirmAll = false },
        )
    }

    confirmRevokeId?.let { id ->
        MoreConfirmDialog(
            title = "Sign out device?",
            message = "This device will be signed out and need to log in again.",
            confirmLabel = "Sign out",
            onConfirm = { viewModel.revoke(id) },
            onDismiss = { confirmRevokeId = null },
        )
    }

    state.alert?.let { (title, message) ->
        MoreAlertDialog(title = title, message = message, onDismiss = viewModel::dismissAlert)
    }
}

@Composable
private fun SessionCard(
    session: ActiveSession,
    revoking: Boolean,
    onRevoke: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.gray100)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = session.deviceName?.takeIf { it.isNotBlank() } ?: "Unknown device",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
            )
            val meta = listOfNotNull(
                session.platform?.takeIf { it.isNotBlank() }
                    ?.replaceFirstChar { it.titlecase(Locale.US) },
                formatLastSeen(session.lastSeenAt),
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = AirdropType.body3,
                    color = colors.textDescription,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            session.lastSeenIp?.takeIf { it.isNotBlank() }?.let { ip ->
                Text(
                    text = "IP $ip",
                    style = AirdropType.body3,
                    color = colors.gray500,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        when {
            session.isCurrent -> Text(
                text = "This device",
                style = AirdropType.subtitle3,
                color = colors.iconSelected,
            )
            revoking -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = AlertPalette.Error,
                strokeWidth = 2.dp,
            )
            else -> Text(
                text = "Sign out",
                style = AirdropType.button,
                color = AlertPalette.Error,
                modifier = Modifier
                    .clickable(onClick = onRevoke)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .testTag("active-sessions-revoke-${session.id}"),
            )
        }
    }
}

/** ISO-8601 → "MMM d, yyyy · h:mm a"; falls back to the raw string on parse error. */
private fun formatLastSeen(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        OffsetDateTime.parse(iso)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US))
    }.getOrDefault(iso)
}
