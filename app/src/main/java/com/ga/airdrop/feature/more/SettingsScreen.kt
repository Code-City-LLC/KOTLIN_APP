package com.ga.airdrop.feature.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.OutlineButton
import com.ga.airdrop.core.designsystem.components.ThemeToggle
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.designsystem.theme.Spacing

internal object SettingsTags {
    const val CACHE = "settings-cache"
    const val NOTIFICATIONS = "settings-notifications"
    const val BACKGROUNDS = "settings-backgrounds"
    const val MODE = "settings-mode"
    const val MODE_TOGGLE = "settings-mode-toggle"
    const val ACCOUNT_DELETION = "settings-account-deletion"
    const val CACHE_SHEET = "settings-cache-sheet"
}

/**
 * Settings — Figma node 40007388:24260, behavior from
 * FigmaSettingsViewController: Notification Settings / Background Images
 * rows, Mode toggle (ThemeController via ThemeToggle), Account Deletion
 * pinned low, Logout in the glass bottom bar with full local hygiene, and
 * the header clear-cache action with the "Cache Cleared Successfully!"
 * sheet (Figma 40001383:11343).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    // Swift FigmaSpecificPages.swift:1316 — gray200 background.
    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(Modifier.fillMaxSize()) {
            MoreDetailHeader(
                title = "Settings",
                onBack = onBack,
                trailing = {
                    // RN SettingsView trailing CacheIcon → onClearCache.
                    Image(
                        painter = painterResource(R.drawable.ic_cache),
                        contentDescription = "Clear cache",
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier
                            .size(24.dp)
                            .testTag(SettingsTags.CACHE)
                            .clickable { viewModel.clearCache(context) },
                    )
                },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(Spacing.md),
            ) {
                MoreRowCard(
                    iconRes = R.drawable.ic_settings_notifications,
                    title = "Notification Settings",
                    tint = colors.iconSelected,
                    onClick = { onNavigate(Routes.NOTIFICATION_SETTINGS) },
                    testTagPrefix = SettingsTags.NOTIFICATIONS,
                )
                Spacer(Modifier.height(14.dp))
                MoreRowCard(
                    iconRes = R.drawable.ic_background,
                    title = "Background Images",
                    tint = colors.iconSelected,
                    onClick = { onNavigate(Routes.BACKGROUNDS) },
                    testTagPrefix = SettingsTags.BACKGROUNDS,
                )
                Spacer(Modifier.height(14.dp))
                MoreRowCard(
                    iconRes = R.drawable.ic_color_theme,
                    title = "Mode",
                    tint = colors.iconSelected,
                    // Swift flips the theme when the whole row is tapped.
                    onClick = {
                        com.ga.airdrop.core.designsystem.theme.ThemeController.set(
                            if (colors.isDark) {
                                com.ga.airdrop.core.designsystem.theme.ThemeController.Mode.LIGHT
                            } else {
                                com.ga.airdrop.core.designsystem.theme.ThemeController.Mode.DARK
                            }
                        )
                    },
                    trailing = { ThemeToggle(Modifier.testTag(SettingsTags.MODE_TOGGLE)) },
                    testTagPrefix = SettingsTags.MODE,
                )
                // Swift uses stack.spacing = 14 and setCustomSpacing(36)
                // after Mode; account deletion is not pinned to the bottom.
                Spacer(Modifier.height(36.dp))
                MoreRowCard(
                    iconRes = R.drawable.ic_trash,
                    title = "Account Deletion",
                    tint = AlertPalette.Error,
                    onClick = { onNavigate(Routes.ACCOUNT_DELETION) },
                    testTagPrefix = SettingsTags.ACCOUNT_DELETION,
                )
                Spacer(Modifier.weight(1f))
            }
            MoreBottomButtonBar(
                text = "Logout",
                loading = state.loggingOut,
                onClick = { showLogoutConfirm = true },
            )
        }
    }

    if (showLogoutConfirm) {
        MoreConfirmDialog(
            title = "Log Out",
            message = "Sign out of this AirDrop account?",
            confirmLabel = "Log Out",
            onConfirm = { viewModel.logout(context) },
            onDismiss = { showLogoutConfirm = false },
        )
    }
    state.logoutError?.let { message ->
        MoreAlertDialog(
            title = "Logout Failed",
            message = message,
            onDismiss = viewModel::dismissLogoutError,
        )
    }
    if (state.cacheCleared) {
        // Swift FigmaSpecificPages.swift:1430-1444 — clearing cache shows an
        // OK-only confirmation and stays on Settings (no navigation home).
        CacheClearedSheet(onDismiss = viewModel::dismissCacheCleared)
    }
}

/**
 * "Cache Cleared Successfully!" sheet — Figma 40001383:11343: orange badge
 * inside peach halo rings, H5 title, Body-2 description, divider, outline
 * "Back to Home" CTA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheClearedSheet(
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.gray100,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag(SettingsTags.CACHE_SHEET)
                .padding(bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xl))
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .background(BrandPalette.OrangeTertiary6, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(BrandPalette.OrangeTertiary5, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .background(BrandPalette.OrangeMain, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_cache),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(BrandPalette.White),
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = "Cache Cleared Successfully!",
                style = AirdropType.h5,
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            Text(
                text = "You’ve successfully cleared your cache. Enjoy smoother performance and more storage space.",
                style = AirdropType.body2,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
            Spacer(Modifier.height(Spacing.md))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
            OutlineButton(
                text = "OK",
                onClick = onDismiss,
                modifier = Modifier.padding(Spacing.md),
            )
        }
    }
}
