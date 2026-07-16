package com.ga.airdrop.feature.shop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.infoBoxBackground
import com.ga.airdrop.core.designsystem.theme.infoBoxBorder
import java.util.Locale

/**
 * Auction Checkout — behavior + layout from
 * FigmaAuctionProductCheckoutViewController (RN AuctionProductCheckoutView).
 * NOTE: the BUILD_PLAN maps this to Figma node 40001846:54756, but that node
 * renders the Feature Products list + sort sheet — the Swift/RN layout is
 * the design truth here.
 */
@Composable
fun AuctionCheckoutScreen(
    onBack: () -> Unit,
    onCheckoutOpened: () -> Unit,
    viewModel: AuctionCheckoutViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val product = state.product
    val heroImageUrl = product?.imageUrl

    // Open the Stripe hosted checkout in a Custom Tab, then pop back
    // (Swift pops checkout + details after presenting Safari). Only consume the
    // one-shot URL when the browser actually opened — a failed launch must keep
    // it retryable so a retry doesn't mint a second Stripe session (same gate
    // as CartScreen, FuchsiaTower Pass-4 C5).
    val checkoutUrl = state.checkoutUrl
    LaunchedEffect(checkoutUrl) {
        if (checkoutUrl != null) {
            if (launchExternalUrl(context, checkoutUrl)) {
                viewModel.consumeCheckoutUrl()
                onCheckoutOpened()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.gray150)) {
        ShopInnerHeader(title = "Auction Checkout", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ─── Hero product card ───
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.s))
                    .background(colors.gray100)
                    .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s))
                        .background(colors.gray150)
                        .border(
                            1.dp,
                            colors.iconShape,
                            RoundedCornerShape(topStart = Radius.s, topEnd = Radius.s),
                        )
                        .padding(horizontal = Spacing.md, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    var heroImageFailed by remember(heroImageUrl) { mutableStateOf(false) }
                    if (heroImageUrl.isNullOrBlank() || heroImageFailed) {
                        Text(
                            text = AuctionCheckoutGiftPlaceholder,
                            fontSize = 80.sp,
                            lineHeight = 88.sp,
                            modifier = Modifier.testTag("auction-checkout-hero-placeholder"),
                        )
                    } else {
                        AsyncImage(
                            model = heroImageUrl,
                            contentDescription = product?.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onError = { heroImageFailed = true },
                            onSuccess = { heroImageFailed = false },
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = product?.title
                            ?: if (state.resolvingProduct) "Loading product…" else "Product unavailable",
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = product?.priceUsd
                            ?.let { usd -> String.format(Locale.US, "$%.2f USD", usd) }
                            ?: "—",
                        style = AirdropType.title2,
                        color = BrandPalette.OrangeMain,
                    )
                }
            }

            // ─── Our Promise card ───
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.infoBoxBackground, RoundedCornerShape(Radius.s))
                    .border(1.dp, colors.infoBoxBorder, RoundedCornerShape(Radius.s))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Our Promise:",
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                    )
                    Text(
                        text = "✅ We do not store any card details in our system.\n" +
                            "✅ Your card details are safe and secure.",
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                    )
                }
            }

            // ─── Payment currency ───
            ShopDropdownField(
                label = "Payment Currency",
                value = state.currency,
                options = viewModel.currencyOptions,
                onSelect = viewModel::setCurrency,
                required = true,
                testTagPrefix = "auction-checkout-currency",
            )
        }

        // ─── Sticky bottom bar: Total + Continue to pay ───
        Column(Modifier.fillMaxWidth().background(colors.gray150)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
            Column(
                Modifier
                    .padding(horizontal = Spacing.md, vertical = 14.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Total", style = AirdropType.title1, color = colors.textDarkTitle)
                    Text(
                        text = viewModel.totalLabel(),
                        style = AirdropType.title1,
                        color = colors.textDarkTitle,
                    )
                }
                // Swift: flat orangeMain button, radius 10 (not the gradient).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("auction-checkout-continue")
                        .background(BrandPalette.OrangeMain, RoundedCornerShape(10.dp))
                        .clickable(enabled = !state.paying, onClick = viewModel::pay),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.paying) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = BrandPalette.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Continue to pay",
                            style = AirdropType.button,
                            color = BrandPalette.White,
                        )
                    }
                }
            }
        }
    }

    val errorTitle = state.errorTitle
    if (errorTitle != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            containerColor = colors.gray100,
            title = { Text(text = errorTitle, style = AirdropType.title2, color = colors.textDarkTitle) },
            text = {
                Text(
                    text = state.errorMessage.orEmpty(),
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(text = "OK", style = AirdropType.button, color = BrandPalette.OrangeMain)
                }
            },
        )
    }
}

private const val AuctionCheckoutGiftPlaceholder = "\uD83C\uDF81"
