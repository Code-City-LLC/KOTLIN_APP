package com.ga.airdrop.feature.cart

import kotlinx.coroutines.flow.StateFlow

/**
 * The slice of state + actions the NCB (JMD) card-entry and 3-D Secure screens
 * need, independent of which checkout owns them. Implemented by [CartViewModel]
 * (cart → order-summary flow) and by AuctionCheckoutViewModel (auction "Buy Now"
 * direct purchase) so both reuse the same [NcbCardEntryScreen]/[NcbThreeDSScreen].
 */
interface NcbCheckoutHost {
    val ncbUi: StateFlow<NcbUiModel>

    /** Edit the billing form the card-entry screen renders (prefilled from profile). */
    fun updateNcbForm(transform: (CartBillingForm) -> CartBillingForm)

    /** Card fields are transient — never stored/logged; sent over TLS to create-ncb-session. */
    fun createNcbSession(
        cardName: String,
        cardNumber: String,
        cardMonth: String,
        cardYear: String,
        cardCvv: String,
    )

    /** Finalize after the 3-D Secure callback (ncb-complete-payment). */
    fun completeNcbPayment()

    fun consumeNcb3DSNav()
    fun consumeNcbSuccessNav()
}

/** Host-agnostic view model for the two NCB screens. */
data class NcbUiModel(
    val form: CartBillingForm = CartBillingForm(),
    val busy: Boolean = false,
    val redirectData: String? = null,
    val invoiceId: String? = null,
    val navTo3DS: Boolean = false,
    val navToSuccess: Boolean = false,
    val errorMessage: String? = null,
)
