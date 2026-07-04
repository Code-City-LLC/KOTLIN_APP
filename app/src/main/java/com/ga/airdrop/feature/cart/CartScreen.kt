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
            .background(colors.gray150)
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (isEmpty) {
                EmptyCartCard(onShopNow = onShopNow)
            } else {
                // ─── Packages — single combined list (Swift bug-fix parity) ───
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CartSectionHeader("Packages")
                    items.forEach { line ->
                        CartItemCard(line = line, onRemove = { viewModel.removeItem(line.id) })
                    }
                }

                // ─── Your Note ───
                NoteCard(note = state.note, onNoteChange = viewModel::updateNote)

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

                // ─── Payment Method row ───
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(59.dp)
                        .background(colors.gray100, RoundedCornerShape(Radius.s))
                        .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
                        .clickable { viewModel.setPaymentMethodDialogVisible(true) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_payments),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.iconSelected),
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "Payment Method",
                            style = AirdropType.subtitle1,
                            color = colors.textDarkTitle,
                        )
                    }
                    ShopChevronRight()
                }
            }
        }

        // ─── Bottom checkout bar (hidden on empty cart) ───
        if (!isEmpty) {
            Column(Modifier.fillMaxWidth().background(colors.glassOverlay70)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                Column(
                    Modifier
                        .padding(Spacing.md)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        BottomBarRow(
                            label = "Exchange Rate",
                            value = String.format(
                                Locale.US, "1 USD = %.2f JMD", state.exchangeUsdToJmd
                            ),
                        )
                        BottomBarRow(label = "Tax", value = "$ 0.00")
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Order Total",
                                style = AirdropType.title2,
                                color = colors.textDarkTitle,
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "USD %.2f / JMD %.2f",
                                    viewModel.totalUsd(),
                                    viewModel.totalJmd(),
                                ),
                                style = AirdropType.title2,
                                color = colors.textDarkTitle,
                            )
                        }
                    }
                    GradientButton(
                        text = "Make Payment",
                        loading = state.paying,
                        onClick = viewModel::pay,
                    )
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
    val colors = AirdropTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = AirdropType.title2, color = colors.textDarkTitle)
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier.size(24.dp),
        )
    }
}

/* ─── Item card — Figma "Card Page" (drop number / description / price) ── */

@Composable
private fun CartItemCard(line: CartStore.CartLine, onRemove: () -> Unit) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.xs))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column {
                Text(text = "Drop Number", style = AirdropType.body3, color = colors.textDescription)
                Text(
                    // Swift FigmaCartViewController.swift:459 — "AIR" + %010d (Swift wins over Figma sample).
                    text = String.format(Locale.US, "AIR%010d", line.id),
                    style = AirdropType.body2,
                    color = colors.textDarkTitle,
                )
            }
            Column {
                Text(text = "Description", style = AirdropType.body3, color = colors.textDescription)
                Text(
                    text = line.title,
                    style = AirdropType.body2,
                    color = colors.textDarkTitle,
                    maxLines = 2,
                )
            }
            Column {
                Text(text = "Price", style = AirdropType.body3, color = colors.textDescription)
                Text(
                    text = formatUsdPlain(line.priceUsd),
                    style = AirdropType.title2,
                    color = BrandPalette.OrangeMain,
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.ic_trash),
            contentDescription = "Remove ${line.title}",
            colorFilter = ColorFilter.tint(colors.iconSelected),
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onRemove),
        )
    }
}

/* ─── Your Note card ───────────────────────────────────────────────────── */

@Composable
private fun NoteCard(note: String, onNoteChange: (String) -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = "Your Note", style = AirdropType.title2, color = colors.textDarkTitle)
        Box(Modifier.fillMaxWidth()) {
            if (note.isEmpty()) {
                Text(
                    text = "Add any delivery notes or special requests",
                    style = AirdropType.body2,
                    color = colors.textDescription,
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
}

/* ─── Charges card — Figma "Fees Card" ─────────────────────────────────── */

@Composable
private fun ChargesCard(exchangeUsdToJmd: Double, totalPackages: Int, totalUsd: Double) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ChargeRow(label = "Payment Currency", value = "USD")
            ChargeRow(label = "Tax", value = "USD 0.00")
            ChargeRow(
                label = "Exchange Rate (USD)",
                value = String.format(Locale.US, "USD 1 = JMD %.2f", exchangeUsdToJmd),
            )
            ChargeRow(label = "Total Packages", value = totalPackages.toString())
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        // Total row — orange tertiary-6 footer, text #2C2825 (Figma).
        val footerText = if (colors.isDark) colors.textDarkTitle else Color(0xFF2C2825)
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    BrandPalette.OrangeTertiary6.copy(alpha = if (colors.isDark) 0.1f else 1f),
                    RoundedCornerShape(bottomStart = Radius.s, bottomEnd = Radius.s),
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Total Charges", style = AirdropType.body2, color = footerText)
            Text(
                text = formatUsdPlain(totalUsd),
                style = AirdropType.body2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = footerText,
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
        Text(text = label, style = AirdropType.body2, color = colors.textDarkTitle)
        Text(
            text = value,
            style = AirdropType.body2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = colors.textDarkTitle,
        )
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
                RoundedCornerShape(Radius.xs),
            )
            .border(
                1.dp,
                com.ga.airdrop.core.designsystem.theme.AlertPalette.Middle.OnHold,
                RoundedCornerShape(Radius.xs),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFF292929)),
            modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = "Our Promise", style = AirdropType.title2, color = Color(0xFF292929))
            Text(
                text = "•  We do not store any card details in our system.\n" +
                    "•  Your card details are safe and secure.",
                style = AirdropType.body2,
                color = Color(0xFF292929),
            )
        }
    }
}

/* ─── Billing form card ────────────────────────────────────────────────── */

@Composable
private fun BillingFormCard(viewModel: CartViewModel) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val form = state.form
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(Radius.s))
            .border(1.dp, colors.iconShape, RoundedCornerShape(Radius.s))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
