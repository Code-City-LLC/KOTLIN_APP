package com.ga.airdrop.feature.cart

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.infoBoxBackground
import com.ga.airdrop.core.designsystem.theme.infoBoxBorder
import com.ga.airdrop.feature.shop.ShopChevronRight
import com.ga.airdrop.feature.shop.ShopDropdownField
import com.ga.airdrop.feature.shop.ShopInnerHeader
import com.ga.airdrop.feature.shop.formatUsdPlain
import com.ga.airdrop.feature.shop.launchExternalUrl
import java.util.Locale

/**
 * My Cart — Figma "My Cart Page" 40008284:26547, behavior from
 * FigmaCartViewController (RN MyCartView). Items come from the local
 * [CartStore]; "Make Payment" opens Stripe hosted checkout in a Custom Tab.
 * The verified-paid return flow owns cart clearing; the empty state shows the
 * EmptyCartIllustration with the ghost "Shop Now" CTA.
 */
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onShopNow: () -> Unit,
    viewModel: CartViewModel = viewModel(),
    openCheckoutUrl: ((String) -> Unit)? = null,
    /** Route push — "Make Payment" now goes Cart → Delivery Method. */
    onNavigate: (String) -> Unit = {},
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val items by viewModel.items.collectAsState()
    val savedItems by viewModel.savedItems.collectAsState()
    val context = LocalContext.current
    val isEmpty = items.isEmpty()
    var showingSavedForLater by rememberSaveable { mutableStateOf(false) }
    var actionLine by remember { mutableStateOf<CartStore.CartLine?>(null) }

    LaunchedEffect(Unit) {
        CartStore.init(context)
        SavedForLaterStore.init(context)
    }

    if (showingSavedForLater) {
        SavedForLaterScreen(
            savedItems = savedItems,
            onBack = { showingSavedForLater = false },
            onMoveToCart = viewModel::moveSavedToCart,
            onRemove = { line -> viewModel.removeSaved(line.id) },
        )
        return
    }

    // "Make Payment" hand-off — one-shot nav to the Delivery Method screen
    // (Swift cart → FigmaDeliveryMethodViewController parity).
    LaunchedEffect(state.navToDeliveryMethod) {
        if (state.navToDeliveryMethod) {
            onNavigate(Routes.DELIVERY_METHOD)
            viewModel.consumeDeliveryNav()
        }
    }

    // Stripe hosted checkout — Custom Tab; verified-paid return clears later.
    val checkoutUrl = state.checkoutUrl
    LaunchedEffect(checkoutUrl) {
        if (checkoutUrl != null) {
            val opened = if (openCheckoutUrl != null) {
                openCheckoutUrl(checkoutUrl)
                true
            } else {
                launchExternalUrl(context, checkoutUrl)
            }
            // Only clear the cart once the browser actually opened — a failed
            // launch must keep the cart intact (FuchsiaTower Pass-4 C5).
            if (opened) viewModel.onCheckoutOpened()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // Swift FigmaCartViewController.swift:82 — page is gray100.
            .background(colors.gray100)
            // Lift the billing form + payment bar above the keyboard.
            .imePadding()
    ) {
        // Swift keeps "My Cart" (Title1) so the empty cart never reads
        // "Order Summary".
        ShopInnerHeader(title = "My Cart", onBack = onBack, titleStyle = AirdropType.title1)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Swift contentStack insets (:274-277): top 16, sides 20, bottom 24.
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            // Swift contentStack spacing 24 between sections.
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (isEmpty) {
                EmptyCartCard(onShopNow = onShopNow)
            } else {
                // ─── Basket — single combined list + Swift Saved (N) pill ───
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CartBasketHeader(
                        itemCount = items.size,
                        savedCount = savedItems.size,
                        onSavedClick = { showingSavedForLater = true },
                    )
                    items.forEach { line ->
                        CartItemCard(
                            line = line,
                            onRemove = { viewModel.removeItem(line.id) },
                            onOpenActions = { actionLine = line },
                        )
                    }
                }

                // ─── Your Note — header ABOVE the 90dp note card (Swift) ───
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CartSectionHeader("Your Note")
                    NoteCard(note = state.note, onNoteChange = viewModel::updateNote)
                }

                // ─── Charges ───
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CartSectionHeader("Charges")
                    ChargesCard(
                        exchangeUsdToJmd = state.exchangeUsdToJmd,
                        totalPackages = items.size,
                        totalUsd = viewModel.totalUsd(),
                    )
                }

                // ─── Our Promise ───
                PromiseCard()

                // ─── {Name} Information ───
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val name = state.loadedFirstName.ifBlank { state.form.firstName }
                    CartSectionHeader("${name.ifBlank { "Your" }} Information")
                    BillingFormCard(viewModel)
                }

                // ─── Payment Method row — Swift buildPaymentMethodRow
                // (:753-790): 56pt, radius 15, label + 16pt gray500 chevron,
                // NO leading icon.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(colors.gray100, RoundedCornerShape(Radius.s))
                        .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                        .clickable { viewModel.setPaymentMethodDialogVisible(true) }
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Payment Method",
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_small_arrow_down),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.gray500),
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(-90f),
                    )
                }
            }
        }

        // ─── Bottom checkout bar — Swift :183-252: OPAQUE gray100 +
        // iconShape divider, Exchange Rate + Order Total rows (NO Tax row),
        // solid orangeMain radius-10 52pt Make Payment button.
        if (!isEmpty) {
            Column(Modifier.fillMaxWidth().background(colors.gray100)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
                Column(
                    Modifier
                        .padding(start = Spacing.md, end = Spacing.md, top = 12.dp, bottom = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Exchange Rate",
                            style = AirdropType.body3,
                            color = colors.textDescription,
                        )
                        Text(
                            text = String.format(
                                Locale.US, "1 USD = %.2f JMD", state.exchangeUsdToJmd
                            ),
                            style = AirdropType.body3,
                            color = colors.textDarkTitle,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Order Total",
                            style = AirdropType.subtitle1,
                            color = colors.textDarkTitle,
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "USD %.2f  /  JMD %.2f",
                                viewModel.totalUsd(),
                                viewModel.totalJmd(),
                            ),
                            style = AirdropType.title2,
                            color = BrandPalette.OrangeMain,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
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
                                text = "Make Payment",
                                style = AirdropType.button,
                                color = BrandPalette.White,
                            )
                        }
                    }
                }
            }
        }
    }

    // Payment Method sheet stand-in (Swift action sheet).
    if (state.showPaymentMethodDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setPaymentMethodDialogVisible(false) },
            containerColor = colors.gray100,
            title = {
                Text(text = "Payment Method", style = AirdropType.title2, color = colors.textDarkTitle)
            },
            text = {
                Text(
                    text = "Card on file is used through Stripe Hosted Checkout.",
                    style = AirdropType.body2,
                    color = colors.textDescription,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setPaymentMethodDialogVisible(false) }) {
                    Text(text = "Continue", style = AirdropType.button, color = BrandPalette.OrangeMain)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setPaymentMethodDialogVisible(false) }) {
                    Text(text = "Cancel", style = AirdropType.button, color = colors.textDarkTitle)
                }
            },
        )
    }

    actionLine?.let { line ->
        CartLineActionSheet(
            line = line,
            alreadySaved = SavedForLaterStore.contains(line),
            onSave = {
                viewModel.saveForLater(line)
                actionLine = null
            },
            onRemove = {
                viewModel.removeItem(line.id)
                actionLine = null
            },
            onDismiss = { actionLine = null },
        )
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

/* ─── Section header — Figma "Header Section" with info icon ───────────── */

@Composable
private fun CartSectionHeader(title: String) {
    // Swift sectionHeader (:809-815) — bare SubTitle1 label, no icon.
    Text(text = title, style = AirdropType.subtitle1, color = AirdropTheme.colors.textDarkTitle)
}

@Composable
private fun CartBasketHeader(itemCount: Int, savedCount: Int, onSavedClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Basket ($itemCount ${if (itemCount == 1) "Item" else "Items"})",
            style = AirdropType.title2,
            color = colors.textDarkTitle,
        )
        if (savedCount > 0) {
            Text(
                text = "Saved ($savedCount) >",
                style = AirdropType.body2,
                color = BrandPalette.OrangeMain,
                modifier = Modifier
                    .clickable(onClick = onSavedClick)
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    .testTag("cart-saved-pill"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartLineActionSheet(
    line: CartStore.CartLine,
    alreadySaved: Boolean,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AirdropTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.gray100,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.sm)
                    .width(62.dp)
                    .height(5.dp)
                    .background(colors.gray400, RoundedCornerShape(100.dp))
            )
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = line.title.ifBlank { "Cart item" },
                style = AirdropType.title2,
                color = colors.textDarkTitle,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
            CartLineActionRow(
                label = if (alreadySaved) "Already saved" else "Save for Later",
                enabled = !alreadySaved,
                onClick = onSave,
                testTag = "cart-action-save-for-later",
            )
            CartLineActionRow(
                label = "Remove",
                enabled = true,
                onClick = onRemove,
                testTag = "cart-action-remove",
            )
        }
    }
}

@Composable
private fun CartLineActionRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = AirdropType.subtitle1,
            color = if (enabled) colors.textDarkTitle else colors.textDescription,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
    }
}

/* ─── Item card — Figma "Card Page" (drop number / description / price) ── */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CartItemCard(
    line: CartStore.CartLine,
    onRemove: () -> Unit,
    onOpenActions: () -> Unit,
) {
    if (line.isAuction) {
        CartSaleItemCard(
            line = line,
            onRemove = onRemove,
            onOpenActions = onOpenActions,
        )
        return
    }

    val colors = AirdropTheme.colors
    // Swift makeDropRow (:448-536): TRANSPARENT row, 1dp iconShape bottom
    // hairline — no fill, border, or radius. Labels SubTitle3; drop value
    // SubTitle1; price SubTitle1 orange; 20pt trash tinted textDarkTitle.
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onOpenActions,
            )
            .testTag("cart-line-${line.id}")
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(
                Modifier.weight(1f),
                // Swift makeDropRow: 8 between groups, 2 within each pair (:487-492).
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Drop Number", style = AirdropType.subtitle3, color = colors.textDescription)
                    Text(
                        // Swift :459 — "AIR" + %010d (Swift wins over Figma sample).
                        text = String.format(Locale.US, "AIR%010d", line.id),
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                        maxLines = 1,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Description", style = AirdropType.subtitle3, color = colors.textDescription)
                    Text(
                        text = line.title,
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                        maxLines = 2,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Price", style = AirdropType.subtitle3, color = colors.textDescription)
                    Text(
                        text = formatUsdPlain(line.priceUsd),
                        style = AirdropType.subtitle1,
                        color = BrandPalette.OrangeMain,
                    )
                }
            }
            Box(
                Modifier
                    .size(24.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = "Remove ${line.title}",
                    colorFilter = ColorFilter.tint(colors.textDarkTitle),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CartSaleItemCard(
    line: CartStore.CartLine,
    onRemove: () -> Unit,
    onOpenActions: () -> Unit,
) {
    val colors = AirdropTheme.colors
    val cardShape = RoundedCornerShape(Radius.s)
    val imageUrl = line.imageUrl
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.replaceFirst("http://", "https://")

    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, cardShape)
            .border(1.dp, colors.cardHairline, cardShape)
            .combinedClickable(
                onClick = {},
                onLongClick = onOpenActions,
            )
            .testTag("cart-sale-line-${line.id}")
            .padding(horizontal = 20.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .background(colors.gray200, RoundedCornerShape(10.dp))
                .testTag("cart-sale-image-${line.id}"),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = line.title,
                    contentScale = ContentScale.Fit,
                    error = painterResource(R.drawable.ic_package),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(7.dp),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_package),
                    contentDescription = "Product image unavailable",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(7.dp),
                )
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = line.title,
                style = AirdropType.body2.copy(
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                ),
                color = colors.textDarkTitle,
                maxLines = 2,
                modifier = Modifier.testTag("cart-sale-title-${line.id}"),
            )
            Text(
                text = formatUsdPlain(line.priceUsd * line.qty),
                style = AirdropType.subtitle2.copy(fontWeight = FontWeight.Bold),
                color = BrandPalette.OrangeMain,
                modifier = Modifier.testTag("cart-sale-price-${line.id}"),
            )
        }
        Box(
            Modifier
                .size(24.dp)
                .align(Alignment.Top)
                .clickable(onClick = onRemove)
                .testTag("cart-sale-remove-${line.id}"),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_trash),
                contentDescription = "Remove ${line.title}",
                colorFilter = ColorFilter.tint(colors.textDarkTitle),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/* ─── Your Note card ───────────────────────────────────────────────────── */

@Composable
private fun NoteCard(note: String, onNoteChange: (String) -> Unit) {
    val colors = AirdropTheme.colors
    // Swift buildNoteCard (:541-576): fixed 90pt gray100 radius-12 multiline
    // field; the "Your Note" header lives ABOVE the card.
    Box(
        Modifier
            .fillMaxWidth()
            // Swift buildNoteCard (:565) — fixed 90pt height.
            .height(90.dp)
            .background(colors.gray100, RoundedCornerShape(12.dp))
            .border(1.dp, colors.iconShape, RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        if (note.isEmpty()) {
            Text(
                text = "Add any delivery notes or special requests",
                style = AirdropType.body2,
                color = colors.textPlaceholder,
            )
        }
        BasicTextField(
            value = note,
            onValueChange = onNoteChange,
            textStyle = AirdropType.body2.copy(color = colors.textDarkTitle),
            cursorBrush = SolidColor(BrandPalette.OrangeMain),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ─── Charges card — Figma "Fees Card" ─────────────────────────────────── */

@Composable
private fun ChargesCard(exchangeUsdToJmd: Double, totalPackages: Int, totalUsd: Double) {
    val colors = AirdropTheme.colors
    // Swift renderChargesRows (:601-638): ONE gray150 card, rows spaced 10,
    // then a 1dp divider and a "Total Charges" row with an ORANGE value —
    // no invented footer band, no hardcoded colors.
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray150, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ChargeRow(label = "Payment Currency", value = "USD")
        ChargeRow(label = "Tax", value = "0")
        ChargeRow(
            label = "Exchange Rate (USD)",
            value = String.format(Locale.US, "USD 1 = JMD %.2f", exchangeUsdToJmd),
        )
        ChargeRow(label = "Total Packages", value = totalPackages.toString())
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.iconShape))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Total Charges", style = AirdropType.subtitle1, color = colors.textDarkTitle)
            Text(
                text = formatUsdPlain(totalUsd),
                style = AirdropType.subtitle1,
                color = BrandPalette.OrangeMain,
            )
        }
    }
}

@Composable
private fun ChargeRow(label: String, value: String) {
    val colors = AirdropTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AirdropType.body2, color = colors.textDescription)
        Text(text = value, style = AirdropType.body2, color = colors.textDarkTitle)
    }
}

/* ─── Our Promise card — Figma "Erroring & Alerts" onHold variant ──────── */

@Composable
private fun PromiseCard() {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.infoBoxBackground, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.infoBoxBorder, RoundedCornerShape(Radius.s))
            // Swift buildPromiseCard row insets (:686-689) — 14 all sides.
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (colors.isDark) colors.textDarkTitle else BrandPalette.BlueMain,
            ),
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "Our Promise",
                style = AirdropType.subtitle1,
                color = if (colors.isDark) colors.textDarkTitle else BrandPalette.BlueMain,
            )
            Text(
                text = "We do not store any card details in our system. " +
                    "Your card details are safe and secure.",
                style = AirdropType.body2,
                color = if (colors.isDark) colors.textDarkTitle else colors.textDescription,
            )
        }
    }
}

/* ─── Billing form card ────────────────────────────────────────────────── */

@Composable
private fun BillingFormCard(viewModel: CartViewModel) {
    val state by viewModel.state.collectAsState()
    val form = state.form
    // Swift buildBillingForm (:696-745): fields sit directly on the page —
    // no wrapper card; name row + vertical spacing 12.
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TypeInputField(
                label = "First Name",
                value = form.firstName,
                onValueChange = { v -> viewModel.updateForm { it.copy(firstName = v) } },
                required = true,
                modifier = Modifier.weight(1f),
            )
            TypeInputField(
                label = "Last Name",
                value = form.lastName,
                onValueChange = { v -> viewModel.updateForm { it.copy(lastName = v) } },
                required = true,
                modifier = Modifier.weight(1f),
            )
        }
        ShopDropdownField(
            label = "Payment Currency",
            value = form.currency,
            options = viewModel.currencyOptions,
            onSelect = { v -> viewModel.updateForm { it.copy(currency = v) } },
            required = true,
        )
        TypeInputField(
            label = "Address line 1",
            value = form.address1,
            onValueChange = { v -> viewModel.updateForm { it.copy(address1 = v) } },
            required = true,
        )
        TypeInputField(
            label = "Address line 2",
            value = form.address2,
            onValueChange = { v -> viewModel.updateForm { it.copy(address2 = v) } },
        )
        TypeInputField(
            label = "State",
            value = form.state,
            onValueChange = { v -> viewModel.updateForm { it.copy(state = v) } },
            required = true,
        )
        TypeInputField(
            label = "City",
            value = form.city,
            onValueChange = { v -> viewModel.updateForm { it.copy(city = v) } },
            required = true,
        )
        ShopDropdownField(
            label = "Country",
            value = form.country,
            options = viewModel.countryOptions,
            onSelect = { v -> viewModel.updateForm { it.copy(country = v) } },
            required = true,
        )
        TypeInputField(
            label = "Postal Code",
            value = form.postal,
            onValueChange = { v -> viewModel.updateForm { it.copy(postal = v) } },
            required = true,
        )
    }
}

/* ─── Bottom bar row ───────────────────────────────────────────────────── */

@Composable
private fun BottomBarRow(label: String, value: String) {
    val colors = AirdropTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = AirdropType.subtitle2, color = colors.textDescription)
        Text(text = value, style = AirdropType.subtitle2, color = colors.textDescription)
    }
}

/* ─── Saved for Later — Swift FigmaSavedForLaterViewController ────────── */

@Composable
private fun SavedForLaterScreen(
    savedItems: List<CartStore.CartLine>,
    onBack: () -> Unit,
    onMoveToCart: (CartStore.CartLine) -> Unit,
    onRemove: (CartStore.CartLine) -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray200)
            .testTag("saved-for-later-screen")
    ) {
        ShopInnerHeader(title = "Saved for Later", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (savedItems.isEmpty()) {
                SavedForLaterEmptyState()
            } else {
                savedItems.forEach { line ->
                    SavedForLaterRow(
                        line = line,
                        onMoveToCart = { onMoveToCart(line) },
                        onRemove = { onRemove(line) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedForLaterEmptyState() {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(15.dp))
            .border(1.dp, colors.iconShape, RoundedCornerShape(15.dp))
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_bookmark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDescription),
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = "Nothing saved yet",
            style = AirdropType.subtitle1,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "Long-press a cart item and tap Save for Later to park it here without losing it.",
            style = AirdropType.body2,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SavedForLaterRow(
    line: CartStore.CartLine,
    onMoveToCart: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(15.dp))
            .border(1.dp, colors.iconShape, RoundedCornerShape(15.dp))
            .padding(16.dp),
    ) {
        Text(
            text = line.title,
            style = AirdropType.title2,
            color = colors.textDarkTitle,
            maxLines = 2,
        )
        Text(
            text = formatUsdPlain(line.priceUsd),
            style = AirdropType.body3,
            color = colors.textDescription,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SavedForLaterButton(
                label = "Move to Cart",
                fillColor = BrandPalette.OrangeMain,
                textColor = BrandPalette.White,
                modifier = Modifier.weight(1f).testTag("saved-move-${line.id}"),
                onClick = onMoveToCart,
            )
            SavedForLaterButton(
                label = "Remove",
                fillColor = colors.gray150,
                textColor = colors.textDescription,
                modifier = Modifier.weight(1f).testTag("saved-remove-${line.id}"),
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun SavedForLaterButton(
    label: String,
    fillColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(44.dp)
            .background(fillColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AirdropType.button, color = textColor)
    }
}

/* ─── Empty state — RN MyCartView / EmptyCartIllustration parity ───────── */

@Composable
private fun EmptyCartCard(onShopNow: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.md, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.img_cart_empty),
            contentDescription = null,
            modifier = Modifier
                .width(280.dp)
                .aspectRatio(354f / 250f),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = "Your cart is empty",
            style = AirdropType.title1,
            color = colors.textDarkTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = "Looks like you haven't added anything yet. Start shopping to fill it up!",
            style = AirdropType.body1,
            color = colors.textDescription,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        // RN ghost MainButton — orange border + orange label, transparent bg.
        Box(
            Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .height(52.dp)
                .border(1.dp, BrandPalette.OrangeMain, RoundedCornerShape(14.dp))
                .clickable(onClick = onShopNow),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Shop Now", style = AirdropType.button, color = BrandPalette.OrangeMain)
        }
    }
}
