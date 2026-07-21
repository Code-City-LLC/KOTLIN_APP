package com.ga.airdrop.feature.cart

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.AlertPalette
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.ga.airdrop.core.location.CountryCatalog
import com.ga.airdrop.feature.shop.ShopDropdownField

/**
 * NCB (JMD) card-entry — Swift FigmaCheckoutPaymentMethodViewController parity:
 * title "Payment Method", card fields, then the editable billing (prefilled from
 * the checkout profile) with Country/State dropdowns and Zip. Card fields live
 * only in this screen's transient state and are never persisted or logged.
 *
 * Driven by an [NcbCheckoutHost] so both the cart and the auction "Buy Now"
 * checkouts reuse this screen.
 */
@Composable
fun NcbCardEntryScreen(
    onBack: () -> Unit,
    onNavigateTo3DS: () -> Unit,
    host: NcbCheckoutHost,
) {
    val colors = AirdropTheme.colors
    val ui by host.ncbUi.collectAsState()
    val form = ui.form

    var cardName by remember { mutableStateOf("${form.firstName} ${form.lastName}".trim().take(70)) }
    var cardNumber by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") } // MM/YY
    var cvv by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    // The NCB (JMD) rail only accepts country in [US, JM]; offering the full
    // catalogue would silently coerce e.g. "United Kingdom" → "US" (ncbCountryCode).
    val ncbCountryOptions = remember {
        listOf(
            CountryCatalog.displayNameFor("Jamaica"),
            CountryCatalog.displayNameFor("United States"),
        )
    }

    // NCB accepts country in [US, JM] only. Per the Laravel ruling we do NOT
    // coerce a non-JM/US prefill to US (that would send a wrong billing country
    // to the processor) — the field shows the profile country honestly, the
    // picker offers only Jamaica/US, and the host REJECTS a non-JM/US value
    // before POST (see ncbCountryCode → error). The user must pick explicitly.

    LaunchedEffect(ui.navTo3DS) {
        if (ui.navTo3DS) {
            host.consumeNcb3DSNav()
            onNavigateTo3DS()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_more2_back_chevron),
                        contentDescription = "Back",
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "Payment Method",
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(40.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                TypeInputField(
                    label = "Card Name",
                    required = true,
                    value = cardName,
                    // Laravel caps card_name at 70; enforce here so an over-long
                    // name can't round-trip into a 422.
                    onValueChange = { cardName = it.take(70) },
                    placeholder = "e.g. Joshua Ricketts",
                )
                TypeInputField(
                    label = "Card Number",
                    required = true,
                    value = formatCardNumber(cardNumber),
                    onValueChange = { cardNumber = it.filter(Char::isDigit).take(19) },
                    placeholder = "e.g. 1234 5678 9012 3456",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TypeInputField(
                        label = "Expiry Date",
                        required = true,
                        value = expiry,
                        onValueChange = { expiry = formatExpiry(it) },
                        placeholder = "MM/YY",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    TypeInputField(
                        label = "CVV",
                        required = true,
                        value = cvv,
                        onValueChange = { cvv = it.filter(Char::isDigit).take(4) },
                        placeholder = "e.g. 123",
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Billing — prefilled from the checkout profile, editable (Swift parity).
                TypeInputField(
                    label = "Billing Address 1",
                    required = true,
                    value = form.address1,
                    onValueChange = { v -> host.updateNcbForm { it.copy(address1 = v) } },
                    placeholder = "e.g. 22 Paradise Ave, Ironshore, Montego Bay",
                )
                TypeInputField(
                    label = "Billing Address 2",
                    value = form.address2,
                    onValueChange = { v -> host.updateNcbForm { it.copy(address2 = v) } },
                    placeholder = "e.g. 22 Paradise Ave, Ironshore, Montego Bay",
                )
                ShopDropdownField(
                    label = "Country",
                    value = CountryCatalog.displayNameFor(form.country),
                    options = ncbCountryOptions,
                    onSelect = { selected ->
                        host.updateNcbForm { it.copy(country = CountryCatalog.canonicalName(selected)) }
                    },
                    required = true,
                )
                // State + Zip are shown for Swift-form parity but are NOT part of the
                // create-ncb-session contract, so they carry no required marker (they
                // are never validated or transmitted).
                ShopDropdownField(
                    label = "State",
                    value = form.state,
                    options = CHECKOUT_STATE_OPTIONS,
                    onSelect = { v -> host.updateNcbForm { it.copy(state = v) } },
                )
                TypeInputField(
                    label = "Zip Code",
                    value = form.postal,
                    onValueChange = { v -> host.updateNcbForm { it.copy(postal = v) } },
                    placeholder = "e.g. 123456",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                (localError ?: ui.errorMessage)?.let {
                    Text(it, style = AirdropType.body2, color = AlertPalette.Error)
                }
            }

            GradientButton(
                text = "Save",
                onClick = {
                    localError = validateNcbCard(cardName, cardNumber, expiry, cvv)
                        ?: validateNcbBilling(form)
                    if (localError == null) {
                        val mm = expiry.substringBefore("/")
                        val yy = expiry.substringAfter("/")
                        host.createNcbSession(
                            cardName = cardName.trim(),
                            cardNumber = cardNumber,
                            cardMonth = mm,
                            cardYear = "20$yy",
                            cardCvv = cvv,
                        )
                    }
                },
                loading = ui.busy,
                enabled = !ui.busy,
                modifier = Modifier
                    .padding(Spacing.md)
                    .fillMaxWidth(),
            )
        }
    }
}

private fun formatCardNumber(digits: String): String =
    digits.chunked(4).joinToString(" ")

private fun formatExpiry(raw: String): String {
    val d = raw.filter(Char::isDigit).take(4)
    return if (d.length <= 2) d else d.substring(0, 2) + "/" + d.substring(2)
}

internal fun validateNcbCard(name: String, number: String, expiry: String, cvv: String): String? {
    if (name.isBlank()) return "Enter the cardholder name."
    val digits = number.filter(Char::isDigit)
    if (digits.length < 13 || digits.length > 19) return "Enter a valid card number."
    val parts = expiry.split("/")
    val mm = parts.getOrNull(0)?.toIntOrNull()
    if (parts.size != 2 || parts[1].length != 2 || mm == null || mm !in 1..12) return "Enter the expiry as MM/YY."
    if (cvv.length < 3 || cvv.length > 4) return "Enter a valid CVV."
    return null
}

/**
 * Billing is prefilled from the checkout profile but editable here; the
 * create-ncb-session contract requires a non-empty `address`, so guard the one
 * editable required billing field before we POST (avoids a server-side 422 for
 * a locally-detectable-empty address).
 */
internal fun validateNcbBilling(form: CartBillingForm): String? {
    if (form.address1.isBlank()) return "Enter your billing address."
    return null
}
