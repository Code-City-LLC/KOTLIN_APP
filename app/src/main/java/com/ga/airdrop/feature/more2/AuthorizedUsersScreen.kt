package com.ga.airdrop.feature.more2

import androidx.compose.foundation.Image
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.data.model.AuthorizedUser

/**
 * Authorized Users list — Figma node 40000975:7859, behavior from
 * FigmaAuthorizedUsersViewController: Active/Inactive sections of user cards,
 * empty placeholders, bottom "Add User" CTA, refetch on every focus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizedUsersScreen(
    onBack: () -> Unit,
    onAddUser: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    viewModel: AuthorizedUsersViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val ptrState = rememberPullToRefreshState()

    // RN uses useIsFocused() to refetch on every focus — re-entering
    // composition after a pop triggers this again.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
    ) {
        More2InnerHeader(title = "Authorized Users", onBack = onBack)

        Box(Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                state = ptrState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("authorized-users-pull-refresh"),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = ptrState,
                        isRefreshing = state.refreshing,
                        color = AirdropTheme.colors.orangeMain,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.md)
                ) {
                    Spacer(Modifier.height(Spacing.md))

                    SectionHeader("Active")
                    Spacer(Modifier.height(Spacing.md))
                    if (state.activeUsers.isEmpty()) {
                        EmptyUsersCard()
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            state.activeUsers.forEach { user ->
                                UserCard(user = user, onClick = { onOpenDetail(user.id) })
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.lg))

                    SectionHeader("Inactive")
                    Spacer(Modifier.height(Spacing.md))
                    if (state.inactiveUsers.isEmpty()) {
                        EmptyUsersCard()
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            state.inactiveUsers.forEach { user ->
                                UserCard(user = user, onClick = { onOpenDetail(user.id) })
                            }
                        }
                    }

                    // Clearance so the last inactive card isn't hidden by the CTA.
                    Spacer(Modifier.height(30.dp))
                }
            }

            if (state.loading) More2Loading()
        }

        More2BottomBar(verticalPadding = 14.dp) {
            More2PrimaryButton(
                text = "Add User",
                onClick = onAddUser,
                modifier = Modifier.testTag("authorized-users-add-user"),
            )
        }
    }

    state.error?.let { message ->
        More2Alert(
            title = "Could not load users",
            message = message,
            onDismiss = viewModel::dismissError,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text = text, style = AirdropType.title2, color = AirdropTheme.colors.textDarkTitle)
}

@Composable
private fun EmptyUsersCard() {
    val colors = AirdropTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No Users Added",
            style = AirdropType.body2,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
        )
    }
}

/** RN userCard: radius 10, 1dp border, gray150 56dp header + field rows. */
@Composable
private fun UserCard(user: AuthorizedUser, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("authorized-users-card-${user.id}")
            .clip(RoundedCornerShape(Radius.xs))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("authorized-users-card-header-${user.id}")
                .height(56.dp)
                .background(colors.gray150)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = user.fullName(),
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier.weight(1f),
            )
            Image(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier
                    .size(16.dp)
                    .rotate(-90f),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.iconShape)
        )

        // Swift list cards do NOT render the "Active User / N Times Active"
        // row — fields start at ID Type for every card.
        val fields = listOf(
            Triple("ID Type", user.identificationType ?: "-", null),
            Triple("ID Number", user.identificationIdNumber ?: "-", null),
            Triple("Email Address", user.email ?: "-", null),
            Triple("Mobile Number", user.mobileDisplay(), null),
            Triple("Tax Registration Number", formatTrn(user.trnNumber), null),
            Triple("Status", user.status ?: "-", user.statusColor()),
        )
        fields.forEachIndexed { index, (label, value, valueColor) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = label, style = AirdropType.body2, color = colors.textDescription)
                Text(
                    text = value,
                    style = AirdropType.subtitle1,
                    color = valueColor ?: colors.textDarkTitle,
                )
            }
            if (index != fields.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.iconShape)
                )
            }
        }
    }
}

// ── Formatting helpers shared with the detail screen ──

internal fun AuthorizedUser.fullName(): String {
    val joined = listOfNotNull(firstName, middleName, lastName)
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    return joined.ifEmpty { "Authorized User" }
}

internal fun AuthorizedUser.mobileDisplay(): String {
    val cc = countryCode.orEmpty()
    val mn = mobileNumber.orEmpty()
    return when {
        cc.isEmpty() && mn.isEmpty() -> "-"
        cc.isEmpty() -> mn
        else -> "$cc $mn"
    }
}

internal fun formatTrn(raw: String?): String {
    if (raw.isNullOrEmpty()) return "-"
    val digits = raw.replace(Regex("[-\\s]"), "")
    if (digits.length == 9 && digits.all { it.isDigit() }) {
        return "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6, 9)}"
    }
    return raw
}

@Composable
internal fun AuthorizedUser.statusColor(): Color {
    return if (status?.lowercase() == "active") AlertPalette.Completed
    else AirdropTheme.colors.textPlaceholder
}
