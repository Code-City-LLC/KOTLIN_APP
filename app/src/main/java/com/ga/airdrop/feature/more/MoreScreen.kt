package com.ga.airdrop.feature.more

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.AirdropHeader
import com.ga.airdrop.core.designsystem.components.AirdropHeaderStyle
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.session.SessionStore

/**
 * More tab root — Figma node 40001948:22354, behavior from
 * FigmaMoreViewController: profile card + the 12 menu rows in Figma order
 * (Preferences → Promotions → Settings → Documents → Users → Refer a friend
 * → Shipping Rates → Restricted Items → Payment Methods → FAQs → Terms &
 * Conditions → Privacy Policy). Card taps push routes; the avatar opens the
 * photo picker (upload/delete wired, unlike the Swift stub).
 */
private data class MoreMenuItem(val title: String, val iconRes: Int, val route: String)

// Order matches Figma node 40001948:22354 verbatim.
private val moreMenuItems = listOf(
    MoreMenuItem("Preferences", R.drawable.ic_preferences, Routes.PREFERENCES),
    MoreMenuItem("Promotions", R.drawable.ic_more_promotions, Routes.PROMOTIONS),
    MoreMenuItem("Settings", R.drawable.ic_more_settings, Routes.SETTINGS),
    MoreMenuItem("Documents", R.drawable.ic_more_documents, Routes.DOCUMENTS),
    MoreMenuItem("Users", R.drawable.ic_more_users, Routes.AUTHORIZED_USERS),
    MoreMenuItem("Refer a friend", R.drawable.ic_more_refer, Routes.REFER_A_FRIEND),
    MoreMenuItem("Shipping Rates", R.drawable.ic_more_shipping_rates, Routes.SHIPPING_RATES),
    MoreMenuItem("Restricted Items", R.drawable.ic_more_restricted, Routes.RESTRICTED_ITEMS),
    MoreMenuItem("Payment Methods", R.drawable.ic_more_payment_methods, MoreRoutes.PAYMENT_METHODS),
    MoreMenuItem("FAQs", R.drawable.ic_more_faqs, Routes.FAQ),
    MoreMenuItem("Terms & Conditions", R.drawable.ic_more_terms, Routes.TERMS),
    MoreMenuItem("Privacy Policy", R.drawable.ic_more_privacy, Routes.PRIVACY),
)

@Composable
fun MoreScreen(
    onNavigate: (String) -> Unit,
    viewModel: MoreViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val headerInfo by SessionStore.header.collectAsState()
    var showAvatarSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(colors.gray200)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(
                Modifier.height(
                    androidx.compose.foundation.layout.WindowInsets.statusBars
                        .asPaddingValues().calculateTopPadding() + 76.dp
                )
            ) // clearance under the solid header
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ProfileCard(
                    name = state.name,
                    account = state.account,
                    avatar = state.avatar,
                    avatarLoading = state.avatarLoading,
                    onOpenProfile = { onNavigate(Routes.PROFILE) },
                    onEditAvatar = { showAvatarSheet = true },
                )
                moreMenuItems.forEach { item ->
                    MoreRowCard(
                        iconRes = item.iconRes,
                        title = item.title,
                        onClick = { onNavigate(item.route) },
                    )
                }
                Spacer(Modifier.height(90.dp)) // glass bottom-bar clearance
            }
        }

        AirdropHeader(
            greeting = listOf(headerInfo.greeting, headerInfo.firstName)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            tierName = headerInfo.tierName,
            style = AirdropHeaderStyle.Solid,
            cartCount = headerInfo.cartCount,
            airCoins = headerInfo.airCoins,
            onTierClick = { onNavigate(Routes.GOLD_PRIORITY) },
            onBellClick = { onNavigate(Routes.NOTIFICATIONS) },
            onCartClick = { onNavigate(Routes.CART) },
            onAirCoinsClick = { onNavigate(Routes.AIRCOIN_HISTORY) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showAvatarSheet) {
        AvatarPickerSheet(
            hasExistingPhoto = state.avatar != null,
            onPicked = viewModel::uploadAvatar,
            onRemove = viewModel::deleteAvatar,
            onDismiss = { showAvatarSheet = false },
        )
    }
    state.avatarError?.let { message ->
        MoreAlertDialog(
            title = "Photo",
            message = message,
            onDismiss = viewModel::dismissAvatarError,
        )
    }
}

/**
 * Figma Card Page 40001948:22356 — 80dp profile card: 48dp avatar circle,
 * Cairo Bold 18 name + Bold 16 gray account number, 24dp edit-profile
 * glyph. Whole card opens Profile; the avatar itself opens the photo picker
 * (Swift require(toFail:) split).
 */
@Composable
private fun ProfileCard(
    name: String,
    account: String,
    avatar: Bitmap?,
    avatarLoading: Boolean,
    onOpenProfile: () -> Unit,
    onEditAvatar: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(colors.gray100)
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .clickable(onClick = onOpenProfile)
            .padding(start = Spacing.sm1, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.gray200)
                .border(1.dp, colors.iconShape, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEditAvatar,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar != null) {
                Image(
                    bitmap = avatar.asImageBitmap(),
                    contentDescription = "Edit profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_profile),
                    contentDescription = "Edit profile photo",
                    colorFilter = ColorFilter.tint(colors.gray500),
                    modifier = Modifier.size(28.dp),
                )
            }
            if (avatarLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colors.gray100,
                    strokeWidth = 2.dp,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = AirdropType.title1,
                color = colors.textDarkTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account,
                style = AirdropType.title2,
                color = colors.textDescription,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_edit_profile),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}
