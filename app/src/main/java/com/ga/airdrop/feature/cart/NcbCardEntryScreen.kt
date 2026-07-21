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
import androidx.lifecycle.viewmodel.compose.viewModel
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
 */
@Composable
fun NcbCardEntryScreen(
    onBack: () -> Unit,
    onNavigateTo3DS: () -> Unit,
    viewModel: CartViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()
    val form = state.form

    var cardName by remember { mutableStateOf("${form.firstName} ${form.lastName}".trim()) }
    var cardNumber by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") } // MM/YY
    var cvv by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.navToNcb3DS) {
        if (state.navToNcb3DS) {
            viewModel.consumeNcb3DSNav()
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
                    onValueChange = { cardName = it },
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
                    onValueChange = { v -> viewModel.updateForm { it.copy(address1 = v) } },
                    placeholder = "e.g. 22 Paradise Ave, Ironshore, Montego Bay",
                )
                TypeInputField(
                    label = "Billing Address 2",
                    value = form.address2,
                    onValueChange = { v -> viewModel.updateForm { it.copy(address2 = v) } },
                    placeholder = "e.g. 22 Paradise Ave, Ironshore, Montego Bay",
                )
                ShopDropdownField(
                    label = "Country",
                    value = CountryCatalog.displayNameFor(form.country),
                    options = viewModel.countryOptions,
                    onSelect = { selected ->
                        viewModel.updateForm { it.copy(country = CountryCatalog.canonicalName(selected)) }
                    },
                    required = true,
                )
                ShopDropdownField(
                    label = "State",
                    value = form.state,
                    options = CHECKOUT_STATE_OPTIONS,
                    onSelect = { v -> viewModel.updateForm { it.copy(state = v) } },
                    required = true,
                )
                TypeInputField(
                    label = "Zip Code",
                    required = true,
                    value = form.postal,
                    onValueChange = { v -> viewModel.updateForm { it.copy(postal = v) } },
                    placeholder = "e.g. 123456",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                (localError ?: state.errorMessage)?.let {
                    Text(it, style = AirdropType.body2, color = AlertPalette.Error)
                }
            }

            GradientButton(
                text = "Save",
                onClick = {
                    localError = validateNcbCard(cardName, cardNumber, expiry, cvv)
                    if (localError == null) {
                        val mm = expiry.substringBefore("/")
                        val yy = expiry.substringAfter("/")
                        viewModel.createNcbSession(
                            cardName = cardName.trim(),
                            cardNumber = cardNumber,
                            cardMonth = mm,
                            cardYear = "20$yy",
                            cardCvv = cvv,
                        )
                    }
                },
                loading = state.ncbBusy,
                enabled = !state.ncbBusy,
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
