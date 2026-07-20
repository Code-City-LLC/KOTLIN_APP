package com.ga.airdrop.feature.cart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.feature.shop.ShopInnerHeader
import com.ga.airdrop.feature.shop.formatUsdPlain
import java.util.Locale

/** Immutable render model supplied by the owned checkout ViewModel. */
data class OrderSummaryUiModel(
    val lines: List<CartStore.CartLine> = emptyList(),
    val note: String = "",
    val currency: String = "USD",
    val exchangeUsdToJmd: Double = com.ga.airdrop.feature.shipments.DEFAULT_USD_TO_JMD,
    val taxUsd: Double = 5.0,
    val totalCharges: Double = 0.0,
    val removingKeys: Set<CartStore.CartLineKey> = emptySet(),
    val removalLocked: Boolean = false,
    val paying: Boolean = false,
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    /** When a durable pending Stripe authority exists, offer a Shipments escape. */
    val errorShowShipments: Boolean = false,
)

/**
 * Distinct Order Summary review — no hero, Basket header, or editable inline
 * cart form. It renders only the checkout-captured rows, grouped as Packages
 * and Sales, then Special Instructions and Charges.
 */
@Composable
fun OrderSummaryScreen(
    model: OrderSummaryUiModel,
    onBack: () -> Unit,
    onNoteChange: (String) -> Unit,
    onRemoveItem: (CartStore.CartLine) -> Unit,
    onMakePayment: () -> Unit,
    onDismissError: () -> Unit = {},
    onGoToShipments: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val colors = AirdropTheme.colors
    val packageLines = model.lines.filter { it.resolvedKind == CartStore.CartLineKind.PACKAGE }
    val saleLines = model.lines.filter { it.resolvedKind == CartStore.CartLineKind.AUCTION }
    var showingNotePopup by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .testTag("order-summary-screen"),
    ) {
        ShopInnerHeader(title = "Order Summary", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (packageLines.isNotEmpty()) {
                OrderSummaryGroup(
                    title = "Packages",
                    lines = packageLines,
                    showInfo = true,
                    removingKeys = model.removingKeys,
                    removalLocked = model.removalLocked,
                    onRemoveItem = onRemoveItem,
                )
            }
            if (saleLines.isNotEmpty()) {
                OrderSummaryGroup(
                    title = "Sales",
                    lines = saleLines,
                    showInfo = false,
                    removingKeys = model.removingKeys,
                    removalLocked = model.removalLocked,
                    onRemoveItem = onRemoveItem,
                )
            }
            SpecialInstructionsCard(note = model.note, onClick = { showingNotePopup = true })
            OrderSummaryChargesCard(model)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray150)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        ) {
            CheckoutSolidButton(
                text = "Make Payment",
                onClick = onMakePayment,
                enabled = !model.paying && model.lines.isNotEmpty() && model.removingKeys.isEmpty(),
                loading = model.paying,
                modifier = Modifier.testTag("order-summary-make-payment"),
            )
        }
    }

    if (showingNotePopup) {
        CartNotePopup(
            initialNote = model.note,
            onSave = {
                onNoteChange(it)
                showingNotePopup = false
            },
            onDismiss = { showingNotePopup = false },
        )
    }

    if (model.errorTitle != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            containerColor = colors.gray100,
            title = {
                Text(model.errorTitle, style = AirdropType.title2, color = colors.textDarkTitle)
            },
            text = {
                Text(model.errorMessage.orEmpty(), style = AirdropType.body2, color = colors.textDescription)
            },
            confirmButton = {
                if (model.errorShowShipments) {
                    // Always-available escape while a durable Stripe authority
                    // blocks re-pay and Back — never strand the user on Order
                    // Summary. The pending record stays recorded under Shipments.
                    TextButton(onClick = onGoToShipments) {
                        Text("Go to Shipments", style = AirdropType.button, color = BrandPalette.OrangeMain)
                    }
                } else {
                    TextButton(onClick = onDismissError) {
                        Text("OK", style = AirdropType.button, color = BrandPalette.OrangeMain)
                    }
                }
            },
            dismissButton = if (model.errorShowShipments) {
                {
                    TextButton(onClick = onDismissError) {
                        Text("OK", style = AirdropType.button, color = colors.textDescription)
                    }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun OrderSummaryGroup(
    title: String,
    lines: List<CartStore.CartLine>,
    showInfo: Boolean,
    removingKeys: Set<CartStore.CartLineKey>,
    removalLocked: Boolean,
    onRemoveItem: (CartStore.CartLine) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("$title (${lines.size})", style = AirdropType.subtitle1, color = AirdropTheme.colors.textDarkTitle)
            if (showInfo) {
                Image(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = "Package information",
                    colorFilter = ColorFilter.tint(AirdropTheme.colors.iconSelected),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        lines.forEach { line ->
            if (line.resolvedKind == CartStore.CartLineKind.AUCTION) {
                OrderSummarySaleCard(
                    line = line,
                    removing = line.key in removingKeys,
                    removalLocked = removalLocked,
                    onRemove = { onRemoveItem(line) },
                )
            } else {
                OrderSummaryPackageCard(
                    line = line,
                    removing = line.key in removingKeys,
                    removalLocked = removalLocked,
                    onRemove = { onRemoveItem(line) },
                )
            }
        }
    }
}

@Composable
private fun OrderSummaryPackageCard(
    line: CartStore.CartLine,
    removing: Boolean,
    removalLocked: Boolean,
    onRemove: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(10.dp))
            .border(1.dp, colors.cardHairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 15.dp)
            .testTag("order-summary-package-${line.id}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            OrderSummaryValue("Drop Number", String.format(Locale.US, "ARD%010d", line.id))
            OrderSummaryValue("Description", line.title)
            OrderSummaryValue("Price", formatUsdPlain(line.priceUsd * line.qty), price = true)
        }
        OrderSummaryRemoveButton(
            line = line,
            disabled = removing || removalLocked,
            onRemove = onRemove,
        )
    }
}

@Composable
private fun OrderSummarySaleCard(
    line: CartStore.CartLine,
    removing: Boolean,
    removalLocked: Boolean,
    onRemove: () -> Unit,
) {
    val colors = AirdropTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 114.dp)
            .background(colors.gray100, RoundedCornerShape(10.dp))
            .border(1.dp, colors.cardHairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 15.dp)
            .testTag("order-summary-sale-${line.id}"),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val imageUrl = validatedProductImageUrl(line.imageUrl)
        var imageLoadSucceeded by remember(imageUrl) { mutableStateOf<Boolean?>(null) }
        Box(
            Modifier
                .size(84.dp)
                .background(colors.gray200, RoundedCornerShape(10.dp))
                .testTag("order-summary-sale-image-${line.id}"),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null && imageLoadSucceeded != false) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = line.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    onSuccess = { imageLoadSucceeded = true },
                    onError = { imageLoadSucceeded = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(7.dp)
                        .testTag(
                            if (imageLoadSucceeded == true) {
                                "order-summary-sale-image-loaded-${line.id}"
                            } else {
                                "order-summary-sale-image-loading-${line.id}"
                            },
                        ),
                )
            } else {
                // Neutral image well only: a Sale row must never gain a
                // shipment icon or other semantic identity when art is absent.
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("order-summary-sale-image-fallback-${line.id}"),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                line.title,
                style = AirdropType.body2,
                color = colors.textDarkTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("order-summary-sale-title-${line.id}"),
            )
            Text(
                String.format(Locale.US, "%.2f USD", line.priceUsd * line.qty),
                style = AirdropType.subtitle2,
                color = BrandPalette.OrangeMain,
                modifier = Modifier.testTag("order-summary-sale-price-${line.id}"),
            )
        }
        OrderSummaryRemoveButton(
            line = line,
            disabled = removing || removalLocked,
            onRemove = onRemove,
            modifier = Modifier.align(Alignment.Top),
        )
    }
}

@Composable
private fun OrderSummaryRemoveButton(
    line: CartStore.CartLine,
    disabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AirdropTheme.colors
    Box(
        modifier
            .size(24.dp)
            .clickable(enabled = !disabled, onClick = onRemove)
            .testTag(
                "order-summary-remove-${line.resolvedKind.name.lowercase(Locale.US)}-${line.id}",
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_trash),
            contentDescription = "Remove ${line.title}",
            colorFilter = ColorFilter.tint(
                if (disabled) colors.textDescription else colors.textDarkTitle,
            ),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun OrderSummaryValue(label: String, value: String, price: Boolean = false) {
    val colors = AirdropTheme.colors
    Column {
        Text(label, style = AirdropType.body3, color = colors.textDescription)
        Text(
            value,
            style = if (price) AirdropType.title2 else AirdropType.body2,
            color = if (price) BrandPalette.OrangeMain else colors.textDarkTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpecialInstructionsCard(note: String, onClick: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.gray100, RoundedCornerShape(15.dp))
            .border(1.dp, colors.cardHairline, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp)
            .testTag("order-summary-special-instructions"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Special Instructions", style = AirdropType.title2, color = colors.textDarkTitle)
        Text(
            note.trim().ifEmpty { "Add any delivery notes or special requests" },
            style = AirdropType.body2,
            color = colors.textDescription,
        )
    }
}

@Composable
private fun OrderSummaryChargesCard(model: OrderSummaryUiModel) {
    val colors = AirdropTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Charges", style = AirdropType.subtitle1, color = colors.textDarkTitle)
            Image(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = "Charges information",
                colorFilter = ColorFilter.tint(colors.iconSelected),
                modifier = Modifier.size(24.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100, RoundedCornerShape(15.dp))
                .border(1.dp, colors.cardHairline, RoundedCornerShape(15.dp))
                .testTag("order-summary-charges"),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SummaryChargeRow("Payment Currency", model.currency)
                SummaryChargeRow("Tax", String.format(Locale.US, "USD %.2f", model.taxUsd))
                SummaryChargeRow(
                    "Exchange Rate (USD)",
                    String.format(Locale.US, "USD 1 = JMD %.2f", model.exchangeUsdToJmd),
                )
                SummaryChargeRow("Total Packages and Sales", model.lines.size.toString())
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.cardHairline))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.peachLight)
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total Charges", style = AirdropType.subtitle1, color = colors.textDarkTitle)
                Text(
                    if (model.currency == "JMD") {
                        String.format(Locale.US, "JMD %.2f", model.totalCharges)
                    } else {
                        String.format(Locale.US, "USD %.2f", model.totalCharges)
                    },
                    style = AirdropType.subtitle1,
                    color = BrandPalette.OrangeMain,
                )
            }
        }
    }
}

@Composable
private fun SummaryChargeRow(label: String, value: String) {
    val colors = AirdropTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = AirdropType.body2, color = colors.textDescription)
        Text(value, style = AirdropType.subtitle2, color = colors.textDarkTitle)
    }
}
