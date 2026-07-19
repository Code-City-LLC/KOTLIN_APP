package com.ga.airdrop.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.feature.shop.ShopDropdownField
import com.ga.airdrop.feature.shop.ShopInnerHeader

/**
 * NCB card-entry — Swift FigmaCartViewController payment-method VC (JMD
 * rail, ~L4560-5140): Saved profile, Card Name, Card Number, Expiry+CVV
 * row, Billing Address 1/2, Country, State, Zip, then a single Save CTA
 * that starts the NCB session and pushes Card Authentication (3DS).
 *
 * Card PAN/CVV live ONLY in this screen's local state and the outgoing
 * request; the ViewModel/UI state never store them. Billing rows edit the
 * shared [CartBillingForm] so Profile Information and this screen stay one
 * source of truth.
 */
@Composable
fun NcbCardEntryScreen(
    form: CartBillingForm,
    countryOptions: List<String>,
    savedProfile: String,
    paying: Boolean,
    onBack: () -> Unit,
    onFormChange: (CartBillingForm) -> Unit,
    onSubmit: (NcbCardFields) -> Unit,
    errorTitle: String? = null,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
) {
    val colors = AirdropTheme.colors
    // Sensitive fields: plain remember (never rememberSaveable — no disk).
    var cardName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150),
    ) {
        ShopInnerHeader(title = "Payment Method", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 29.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TypeInputField(
                label = "Saved profile",
                value = savedProfile,
                onValueChange = {},
                enabled = false,
            )
            TypeInputField(
                label = "Card Name",
                value = cardName,
                onValueChange = { cardName = it },
                placeholder = "e.g. Joshua Ricketts",
                required = true,
            )
            TypeInputField(
                label = "Card Number",
                value = cardNumber,
                onValueChange = { next -> cardNumber = next.filter { it.isDigit() || it == ' ' } },
                placeholder = "e.g. 1234 5678 9012 3456",
                required = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.testTag("ncb-card-number"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeInputField(
                    label = "Expiry Date",
                    value = expiry,
                    onValueChange = { expiry = it },
                    placeholder = "MM/YY",
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("ncb-card-expiry"),
                )
                TypeInputField(
                    label = "CVV",
                    value = cvv,
                    onValueChange = { next -> cvv = next.filter(Char::isDigit).take(4) },
                    placeholder = "e.g. 123",
                    required = true,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f).testTag("ncb-card-cvv"),
                )
            }
            TypeInputField(
                label = "Billing Address 1",
                value = form.address1,
                onValueChange = { onFormChange(form.copy(address1 = it)) },
                placeholder = "e.g. 22 Paradise Ave, Ironshore, Montego...",
                required = true,
            )
            TypeInputField(
                label = "Billing Address 2",
                value = form.address2,
                onValueChange = { onFormChange(form.copy(address2 = it)) },
                placeholder = "e.g. 22 Paradise Ave, Ironshore, Montego...",
            )
            ShopDropdownField(
                label = "Country",
                value = form.country,
                options = countryOptions,
                onSelect = { onFormChange(form.copy(country = it)) },
                required = true,
                testTagPrefix = "ncb-country",
            )
            ShopDropdownField(
                label = "State",
                value = form.state,
                options = CHECKOUT_STATE_OPTIONS,
                onSelect = { onFormChange(form.copy(state = it)) },
                required = true,
                testTagPrefix = "ncb-state",
            )
            TypeInputField(
                label = "Zip Code",
                value = form.postal,
                onValueChange = { onFormChange(form.copy(postal = it)) },
                placeholder = "e.g. 123456",
                required = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            CheckoutSolidButton(
                text = if (paying) "Processing..." else "Save",
                onClick = {
                    onSubmit(
                        NcbCardFields(
                            cardName = cardName,
                            cardNumber = cardNumber,
                            expiry = expiry,
                            cvv = cvv,
                        ),
                    )
                },
                enabled = !paying,
                loading = paying,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ncb-card-save"),
            )
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp),
            )
        }
    }

    if (errorTitle != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            containerColor = colors.gray100,
            title = { Text(errorTitle, style = AirdropType.title2, color = colors.textDarkTitle) },
            text = {
                Text(errorMessage.orEmpty(), style = AirdropType.body2, color = colors.textDescription)
            },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text("OK", style = AirdropType.subtitle2, color = BrandPalette.OrangeMain)
                }
            },
        )
    }
}
