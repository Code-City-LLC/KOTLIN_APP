package com.ga.airdrop.feature.cart

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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.TypeInputField
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.location.CountryCatalog
import com.ga.airdrop.feature.more.MoreRowCard
import com.ga.airdrop.feature.shop.ShopDropdownField
import com.ga.airdrop.feature.shop.ShopInnerHeader

internal val CHECKOUT_STATE_OPTIONS = listOf("Florida", "California", "New York", "Texas", "Other")
internal val CHECKOUT_CITY_OPTIONS = listOf("Miami", "Los Angeles", "New York City", "Houston", "Other")

/**
 * Checkout Profile Information — Figma 40008740:28560. This is deliberately
 * separate from the More-tab profile editor: it edits only the billing fields
 * required by the active JMD checkout and delegates persistence to its owner.
 */
@Composable
fun ProfileInformationScreen(
    form: CartBillingForm,
    profileOptions: List<String>,
    selectedProfile: String,
    countryOptions: List<String>,
    saving: Boolean,
    onBack: () -> Unit,
    onFormChange: (CartBillingForm) -> Unit,
    onProfileSelected: (String) -> Unit,
    onPaymentMethodClick: () -> Unit,
    onContinue: () -> Unit,
    errorTitle: String? = null,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
) {
    val colors = AirdropTheme.colors
    val showsPostal = CountryCatalog.requiresPostalCode(form.country)
    val requiresPostal = checkoutCountryRequiresPostalCode(form.country)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray150)
            .imePadding()
            .testTag("profile-information-screen"),
    ) {
        // Current Swift/Figma title intentionally uses a lowercase `i`.
        ShopInnerHeader(title = "Profile information", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 29.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShopDropdownField(
                label = "Select profile",
                value = selectedProfile,
                options = profileOptions,
                onSelect = onProfileSelected,
                testTagPrefix = "checkout-profile-select",
            )
            TypeInputField(
                label = "First Name",
                value = form.firstName,
                onValueChange = { onFormChange(form.copy(firstName = it)) },
                placeholder = "Enter first name",
                required = true,
                testTagPrefix = "checkout-profile-first-name",
            )
            TypeInputField(
                label = "Last Name",
                value = form.lastName,
                onValueChange = { onFormChange(form.copy(lastName = it)) },
                placeholder = "Enter last name",
                required = true,
                testTagPrefix = "checkout-profile-last-name",
            )
            CheckoutReadOnlyField(
                label = "Selected Payment Currency",
                value = form.currency,
                required = true,
                testTag = "checkout-profile-currency",
            )
            TypeInputField(
                label = "Address line 1",
                value = form.address1,
                onValueChange = { onFormChange(form.copy(address1 = it)) },
                placeholder = "Enter address line 1",
                required = true,
                testTagPrefix = "checkout-profile-address-1",
            )
            TypeInputField(
                label = "Address line 2",
                value = form.address2,
                onValueChange = { onFormChange(form.copy(address2 = it)) },
                placeholder = "Enter address line 2 (optional)",
                testTagPrefix = "checkout-profile-address-2",
            )
            ShopDropdownField(
                label = "State",
                value = form.state,
                options = CHECKOUT_STATE_OPTIONS,
                onSelect = { onFormChange(form.copy(state = it)) },
                required = true,
                testTagPrefix = "checkout-profile-state",
            )
            ShopDropdownField(
                label = "City",
                value = form.city,
                options = CHECKOUT_CITY_OPTIONS,
                onSelect = { onFormChange(form.copy(city = it)) },
                required = true,
                testTagPrefix = "checkout-profile-city",
            )
            ShopDropdownField(
                label = "Country",
                value = CountryCatalog.displayNameFor(form.country),
                options = countryOptions,
                onSelect = { selected ->
                    val canonical = CountryCatalog.canonicalName(selected)
                    onFormChange(
                        form.copy(
                            country = canonical,
                            postal = if (CountryCatalog.requiresPostalCode(canonical)) {
                                form.postal
                            } else {
                                ""
                            },
                        ),
                    )
                },
                required = true,
                testTagPrefix = "checkout-profile-country",
            )
            if (showsPostal) {
                TypeInputField(
                    label = "Postal Code",
                    value = form.postal,
                    onValueChange = { onFormChange(form.copy(postal = it)) },
                    placeholder = "Enter postal code",
                    required = requiresPostal,
                    testTagPrefix = "checkout-profile-postal",
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.cardHairline)
                    .testTag("checkout-profile-payment-divider"),
            )
            MoreRowCard(
                iconRes = R.drawable.ic_more_payment_methods,
                title = "Payment Method",
                onClick = onPaymentMethodClick,
                testTagPrefix = "checkout-profile-payment-method",
            )
            CheckoutSolidButton(
                text = "Continue to Order Summary",
                onClick = onContinue,
                enabled = !saving,
                loading = saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-information-continue"),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .testTag("profile-information-scroll-tail"),
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
                    Text("OK", style = AirdropType.button, color = BrandPalette.OrangeMain)
                }
            },
        )
    }
}

@Composable
private fun CheckoutReadOnlyField(
    label: String,
    value: String,
    required: Boolean,
    testTag: String,
) {
    val colors = AirdropTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = AirdropType.subtitle2, color = colors.textDarkTitle)
            if (required) Text(" *", style = AirdropType.subtitle2, color = BrandPalette.OrangeMain)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colors.gray100, RoundedCornerShape(12.dp))
                .border(1.dp, colors.cardHairline, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = AirdropType.body1, color = colors.textDarkTitle, modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ic_small_arrow_down),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.gray500),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal fun checkoutCountryRequiresPostalCode(country: String): Boolean {
    val normalized = country.trim().lowercase()
    return CountryCatalog.entry(country)?.isoCode == "US" ||
        normalized in setOf("usa", "u.s.", "u.s.a.")
}
