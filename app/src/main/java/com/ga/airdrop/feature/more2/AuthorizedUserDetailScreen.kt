package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Authorized User Detail — Figma node 40001185:5345, behavior from
 * FigmaAuthorizedUserDetailViewController: read-only detail card,
 * Activate/Deactivate + Delete CTAs, plus the EDIT header affordance the
 * Swift app never wired (opens the Add form in edit mode).
 */
@Composable
fun AuthorizedUserDetailScreen(
    userId: Int,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    viewModel: AuthorizedUserDetailViewModel = viewModel(
        factory = detailFactory(userId),
        key = "authorizedUserDetail_$userId",
    ),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    // Refetch when coming back from the edit form.
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
    ) {
        More2InnerHeader(
            title = "User Details",
            onBack = onBack,
            rightContent = if (state.user != null) {
                {
                    Text(
                        text = "Edit",
                        style = AirdropType.subtitle2,
                        color = BrandPalette.OrangeMain,
                        modifier = Modifier.clickable { onEdit(userId) },
                    )
                }
            } else null,
        )

        Box(Modifier.weight(1f)) {
            val user = state.user
            when {
                state.loading -> More2Loading()
                user == null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(Spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.emptyMessage ?: "User not found",
                            style = AirdropType.subtitle1,
                            color = AlertPalette.Error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md)
                    ) {
                        More2OuterCard {
                            val rows = buildList {
                                add(Triple("Name", user.fullName(), false))
                                add(Triple("ID Type", user.identificationType ?: "-", false))
                                add(Triple("ID Number", user.identificationIdNumber ?: "-", false))
                                add(Triple("Email Address", user.email ?: "-", false))
                                add(Triple("Mobile Number", user.mobileDisplay(), false))
                                add(Triple("Tax Registration Number", formatTrn(user.trnNumber), false))
                                add(Triple("Status", user.status ?: "-", true))
                                val activeTimes = user.activeTimes
                                if (activeTimes != null && activeTimes > 0) {
                                    add(
                                        Triple(
                                            "Active Times",
                                            "$activeTimes ${if (activeTimes == 1) "Time" else "Times"}",
                                            false,
                                        )
                                    )
                                }
                            }
                            rows.forEachIndexed { index, (label, value, isStatus) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    Text(
                                        text = label,
                                        style = AirdropType.body2,
                                        color = colors.textDescription,
                                    )
                                    Text(
                                        text = value,
                                        style = AirdropType.body2,
                                        color = when {
                                            isStatus && user.isActive -> AlertPalette.Completed
                                            isStatus -> colors.textDescription
                                            else -> colors.textDarkTitle
                                        },
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (index != rows.lastIndex) {
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
                }
            }
        }

        if (state.user != null) {
            More2BottomBar {
                if (state.user?.isActive == true) {
                    More2GhostButton(
                        text = "Deactivate User",
                        onClick = viewModel::togglePrimary,
                        loading = state.processing,
                    )
                } else {
                    More2PrimaryButton(
                        text = "Activate User",
                        onClick = viewModel::togglePrimary,
                        loading = state.processing,
                    )
                }
                More2RedButton(
                    text = "Delete User",
                    onClick = { showDeleteConfirm = true },
                    enabled = !state.processing,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        More2Alert(
            title = "Delete User",
            message = "Are you sure you want to delete this user?",
            confirmText = "Delete",
            destructiveConfirm = true,
            dismissText = "Cancel",
            onConfirm = viewModel::delete,
            onDismiss = { showDeleteConfirm = false },
        )
    }
    state.error?.let { message ->
        More2Alert(title = "Error", message = message, onDismiss = viewModel::dismissError)
    }
}

private fun detailFactory(userId: Int): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthorizedUserDetailViewModel(userId) as T
    }
