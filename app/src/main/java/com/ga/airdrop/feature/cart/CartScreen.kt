package com.ga.airdrop.feature.cart

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.designsystem.theme.frostedGlassSurface
import com.ga.airdrop.feature.shop.ShopChevronRight
import com.ga.airdrop.feature.shop.ShopInnerHeader
import com.ga.airdrop.feature.shop.formatUsdPlain
import java.net.URI
import java.util.Locale

/**
 * My Cart — Figma "My Cart Page" 40008284:26547, behavior from
 * FigmaCartViewController (RN MyCartView). The server hydrates package rows
 * into the shared [CartStore], local auction rows retain a distinct identity,
 * and Continue starts the Delivery Method flow. Payment controls live only on
 * Order Summary.
 */
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onShopNow: () -> Unit,
    viewModel: CartViewModel = viewModel(),
    /** Route push — Continue goes Cart → Delivery Method. */
    onNavigate: (String) -> Unit = {},
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val items by viewModel.items.collectAsState()
    val savedItems by viewModel.savedItems.collectAsState()
    val context = LocalContext.current
    val isEmpty = items.isEmpty()
    var showingSavedForLater by rememberSaveable { mutableStateOf(false) }
    var showingNotePopup by rememberSaveable { mutableStateOf(false) }
    var actionLine by remember { mutableStateOf<CartStore.CartLine?>(null) }
    val scrollTailPadding = if (isEmpty) 24.dp else 12.dp

    LaunchedEffect(Unit) {
        CartStore.init(context)
        SavedForLaterStore.init(context)
    }

    if (showingSavedForLater) {
        SavedForLaterScreen(
            savedItems = savedItems,
            onBack = { showingSavedForLater = false },
            onMoveToCart = viewModel::moveSavedToCart,
            onRemove = viewModel::removeSaved,
        )
        return
    }

    // Continue hand-off — one-shot nav to the Delivery Method screen
    // (Swift cart → FigmaDeliveryMethodViewController parity).
    LaunchedEffect(state.navToDeliveryMethod) {
        if (state.navToDeliveryMethod) {
            onNavigate(Routes.DELIVERY_METHOD)
            viewModel.consumeDeliveryNav()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // Swift/Figma 40008798:29430 — gray150 canvas behind white cards.
            .background(colors.gray150)
            // Lift the saved-note editor above the keyboard.
            .imePadding()
    ) {
        // Live Figma My Cart Header Type 40008798:29447 uses Subtitle1:
        // Cairo 16sp SemiBold / 26sp. Keep "My Cart" in every state so the
        // empty cart never reads "Order Summary".
        ShopInnerHeader(title = "My Cart", onBack = onBack, titleStyle = CartHeaderTitleStyle)

        // Swift pins scrollView.bottom directly to bottomBar.top. Keep the
        // footer as a fixed sibling so larger text can never reduce the
        // scroll viewport without Compose knowing its exact boundary.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = scrollTailPadding,
                ),
            // Both My Cart and Order Summary use a 20pt section rhythm.
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.loadingCart && isEmpty) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = BrandPalette.OrangeMain)
                }
            } else if (isEmpty) {
                EmptyCartCard(onShopNow = onShopNow)
            } else {
                CartMacBookHero()

                // Exact order: hero → Basket → cards → compact Your Note row.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CartBasketHeader(
                        itemCount = items.size,
                        savedCount = savedItems.size,
                        onSavedClick = { showingSavedForLater = true },
                    )
                    items.forEach { line ->
                        CartItemCard(
                            line = line,
                            onRemove = { viewModel.removeItem(line) },
                            onOpenActions = { actionLine = line },
                        )
                    }
                }

                CartNoteRow(note = state.note, onClick = { showingNotePopup = true })
            }
        }

        if (!isEmpty) {
            CartTotalsFooter(
                currency = state.form.currency,
                exchangeUsdToJmd = state.exchangeUsdToJmd,
                totalUsd = viewModel.totalUsd(),
                totalJmd = viewModel.totalJmd(),
                paying = state.paying,
                onChooseDelivery = viewModel::pay,
            )
        }
    }

    if (showingNotePopup) {
        CartNotePopup(
            initialNote = state.note,
            onSave = {
                viewModel.updateNote(it)
                showingNotePopup = false
            },
            onDismiss = { showingNotePopup = false },
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
                viewModel.removeItem(line)
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

internal val CartHeaderTitleStyle = AirdropType.subtitle1

@Composable
private fun CartMacBookHero() {
    Image(
        painter = painterResource(R.drawable.img_cart_macbook_hero),
        contentDescription = "The New MacBook Pro",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            // 335×172 is the exact exported Figma frame inside 20dp insets.
            .height(172.dp)
            .clip(RoundedCornerShape(10.dp))
            .testTag("cart-macbook-hero"),
    )
}

@Composable
internal fun CartNoteRow(
    note: String,
    title: String = "Your Note",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    val display = note.trim().ifEmpty { title }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(59.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(colors.gray100)
            .border(1.dp, colors.cardHairline, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .testTag("cart-your-note-row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_chat),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = display,
            style = AirdropType.subtitle1,
            color = colors.textDarkTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ShopChevronRight()
    }
}

@Composable
private fun CartTotalsFooter(
    currency: String,
    exchangeUsdToJmd: Double,
    totalUsd: Double,
    totalJmd: Double,
    paying: Boolean,
    onChooseDelivery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.frostedGlassSurface)
            .testTag("cart-frosted-totals-footer"),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.cardHairline))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BottomBarRow(
                label = "Exchange Rate",
                value = String.format(Locale.US, "USD 1 = JMD %.2f", exchangeUsdToJmd),
            )
            BottomBarRow(label = "Fax", value = "$ 5.00")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Order Total", style = AirdropType.title2, color = colors.textDarkTitle)
                Text(
                    text = if (currency.equals("JMD", ignoreCase = true)) {
                        String.format(Locale.US, "JMD %.2f", totalJmd)
                    } else {
                        String.format(Locale.US, "USD %.2f", totalUsd)
                    },
                    style = AirdropType.title2,
                    color = colors.textDarkTitle,
                )
            }
            Spacer(Modifier.height(6.dp))
            CheckoutSolidButton(
                text = "Choose Delivery",
                loading = paying,
                enabled = !paying,
                onClick = onChooseDelivery,
                modifier = Modifier.testTag("cart-choose-delivery"),
            )
        }
    }
}

@Composable
internal fun CheckoutSolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = AirdropTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) colors.buttonStatic else BrandPalette.ButtonDisable)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = BrandPalette.White,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CheckoutArrow()
                Text(text = text, style = AirdropType.button, color = BrandPalette.White)
                CheckoutArrow()
            }
        }
    }
}

@Composable
private fun CheckoutArrow() {
    Image(
        painter = painterResource(R.drawable.ic_small_arrow_down),
        contentDescription = null,
        colorFilter = ColorFilter.tint(BrandPalette.White),
        modifier = Modifier.size(20.dp).rotate(-90f),
    )
}

@Composable
internal fun CartNotePopup(
    initialNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(initialNote) { mutableStateOf(initialNote) }
    val focusRequester = remember { FocusRequester() }
    var focusTargetAttached by remember { mutableStateOf(false) }
    val colors = AirdropTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        LaunchedEffect(focusTargetAttached) {
            if (focusTargetAttached) focusRequester.requestFocus()
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color(0xB3292929))
                .imePadding()
                .padding(horizontal = 19.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val popupHeight = maxHeight.coerceAtMost(566.dp)
            val inputHeight = (popupHeight - 147.dp).coerceIn(120.dp, 332.dp)
            Box(
                Modifier
                    .widthIn(max = 337.dp)
                    .fillMaxWidth()
                    .height(popupHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.gray100)
                    // Consume blank-card taps so they never fall through to
                    // the dismissing backdrop.
                    .clickable(onClick = {})
                    .testTag("cart-note-popup"),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_cart_note_popup_pattern),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(x = (-122).dp, y = (-418).dp)
                        .width(1631.53.dp)
                        .height(1846.758.dp)
                        .alpha(0.07f)
                        .testTag("cart-note-popup-pattern"),
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 13.dp, top = 13.dp, bottom = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(25.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_chat),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(text = "Your Note", style = AirdropType.subtitle1, color = colors.textDarkTitle)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(inputHeight)
                            .background(colors.gray100, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.cardHairline, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                text = "Enter Your text",
                                style = AirdropType.body3,
                                color = colors.textDescription,
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = AirdropType.body3.copy(color = colors.textDarkTitle),
                            cursorBrush = SolidColor(BrandPalette.OrangeMain),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(focusRequester)
                                .onGloballyPositioned { focusTargetAttached = it.isAttached }
                                .testTag("cart-note-input"),
                        )
                    }
                    CheckoutSolidButton(
                        text = "Save",
                        onClick = { onSave(draft.trim()) },
                        modifier = Modifier.testTag("cart-note-save"),
                    )
                }
            }
        }
    }
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
    if (line.resolvedKind == CartStore.CartLineKind.AUCTION) {
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
    val imageUrl = validatedProductImageUrl(line.imageUrl)
    var imageLoadSucceeded by remember(imageUrl) { mutableStateOf<Boolean?>(null) }

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
            if (imageUrl != null && imageLoadSucceeded != false) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = line.title,
                    contentScale = ContentScale.Fit,
                    onSuccess = { imageLoadSucceeded = true },
                    onError = { imageLoadSucceeded = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(7.dp)
                        .testTag(
                            if (imageLoadSucceeded == true) {
                                "cart-sale-image-loaded-${line.id}"
                            } else {
                                "cart-sale-image-loading-${line.id}"
                            }
                        ),
                )
            } else {
                // Frozen Sale rows never impersonate a shipment. A missing,
                // non-HTTPS, or failed product URL leaves only this neutral
                // image well: no package/auction icon and no fabricated ID.
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("cart-sale-image-fallback-${line.id}"),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 84.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = line.title,
                style = AirdropType.body2.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                ),
                color = colors.textDarkTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("cart-sale-title-${line.id}"),
            )
            Text(
                text = formatUsdPlain(line.priceUsd * line.qty),
                style = AirdropType.subtitle2.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                ),
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

/** Sale images are rendered only from an absolute, credential-free HTTPS URL. */
internal fun validatedProductImageUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
    if (uri.userInfo != null) return null
    return trimmed
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
