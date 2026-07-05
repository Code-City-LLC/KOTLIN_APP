package com.ga.airdrop.feature.cart

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.feature.shop.ShopChevronRight
import com.ga.airdrop.feature.shop.ShopDropdownField
import com.ga.airdrop.feature.shop.ShopInnerHeader
import com.ga.airdrop.feature.shop.formatUsdPlain
import com.ga.airdrop.feature.shop.launchExternalUrl
import java.util.Locale

/**
 * My Cart — Figma "My Cart Page" 40008284:26547, behavior from
 * FigmaCartViewController (RN MyCartView). Items come from the local
 * [CartStore]; "Make Payment" opens Stripe hosted checkout in a Custom Tab
 * and clears the cart; the empty state shows the EmptyCartIllustration with
 * the ghost "Shop Now" CTA.
 */
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onShopNow: () -> Unit,
    viewModel: CartViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val items by viewModel.items.collectAsState()
    val context = LocalContext.current
    val isEmpty = items.isEmpty()

    LaunchedEffect(Unit) { CartStore.init(context) }

    // Stripe hosted checkout — Custom Tab, then clear the cart (RN parity).
    val checkoutUrl = state.checkoutUrl
    LaunchedEffect(checkoutUrl) {
        if (checkoutUrl != null) {
            launchExternalUrl(context, checkoutUrl)
            viewModel.onCheckoutOpened()
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
                .padding(Spacing.md),
            // Swift contentStack spacing 24 between sections.
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (isEmpty) {
                EmptyCartCard(onShopNow = onShopNow)
            } else {
                // ─── Packages — single combined list (Swift bug-fix parity) ───
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CartSectionHeader("Packages")
                    items.forEach { line ->
                        CartItemCard(line = line, onRemove = { viewModel.removeItem(line.id) })
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

/* ─── Item card — Figma "Card Page" (drop number / description / price) ── */

@Composable
private fun CartItemCard(line: CartStore.CartLine, onRemove: () -> Unit) {
    val colors = AirdropTheme.colors
    // Swift makeDropRow (:448-536): TRANSPARENT row, 1dp iconShape bottom
    // hairline — no fill, border, or radius. Labels SubTitle3; drop value
    // SubTitle1; price SubTitle1 orange; 20pt trash tinted textDarkTitle.
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column {
                    Text(text = "Drop Number", style = AirdropType.subtitle3, color = colors.textDescription)
                    Text(
                        // Swift :459 — "AIR" + %010d (Swift wins over Figma sample).
                        text = String.format(Locale.US, "AIR%010d", line.id),
                        style = AirdropType.subtitle1,
                        color = colors.textDarkTitle,
                        maxLines = 1,
                    )
                }
                Column {
                    Text(text = "Description", style = AirdropType.subtitle3, color = colors.textDescription)
                    Text(
                        text = line.title,
                        style = AirdropType.body2,
                        color = colors.textDarkTitle,
                        maxLines = 2,
                    )
                }
                Column {
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

/* ─── Your Note card ───────────────────────────────────────────────────── */

@Composable
private fun NoteCard(note: String, onNoteChange: (String) -> Unit) {
    val colors = AirdropTheme.colors
    // Swift buildNoteCard (:541-576): fixed 90pt gray100 radius-12 multiline
    // field; the "Your Note" header lives ABOVE the card.
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp)
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
            .background(
                com.ga.airdrop.core.designsystem.theme.AlertPalette.Light.OnHold,
                RoundedCornerShape(Radius.s),
            )
            .border(
                1.dp,
                com.ga.airdrop.core.designsystem.theme.AlertPalette.Middle.OnHold,
                RoundedCornerShape(Radius.s),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            // Swift buildPromiseCard (:642-692): 20pt blueMain icon.
            colorFilter = ColorFilter.tint(BrandPalette.BlueMain),
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = "Our Promise", style = AirdropType.subtitle1, color = BrandPalette.BlueMain)
            Text(
                text = "We do not store any card details in our system. " +
                    "Your card details are safe and secure.",
                style = AirdropType.body2,
                color = colors.textDescription,
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
