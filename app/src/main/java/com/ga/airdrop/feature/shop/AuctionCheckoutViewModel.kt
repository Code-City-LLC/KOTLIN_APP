package com.ga.airdrop.feature.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.data.model.CreateNcbSessionRequest
import com.ga.airdrop.feature.cart.CartBillingForm
import com.ga.airdrop.feature.cart.CheckoutCurrency
import com.ga.airdrop.feature.cart.CheckoutFlowStore
import com.ga.airdrop.feature.cart.CheckoutPhase
import com.ga.airdrop.feature.cart.NcbCheckoutHost
import com.ga.airdrop.feature.cart.NcbUiModel
import com.ga.airdrop.feature.cart.parseCheckoutCurrency
import com.ga.airdrop.feature.cart.validatedHostedCheckoutUrl
import com.ga.airdrop.feature.more.MoreProfileRepository
import com.ga.airdrop.feature.more.MoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuctionCheckoutUiState(
    val product: ShopProduct? = null,
    val currency: String = "USD",
    // Shared fallback rate (FuchsiaTower Pass-4 C6).
    val exchangeUsdToJmd: Double = com.ga.airdrop.feature.shipments.DEFAULT_USD_TO_JMD,
    val paying: Boolean = false,
    /** Deep-link product resolve in flight (Audit#7 C4). */
    val resolvingProduct: Boolean = false,
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    /** Stripe hosted checkout URL to open in a Custom Tab (one-shot). */
    val checkoutUrl: String? = null,
    /** Kept with the URL until dispatch; never persisted. */
    val checkoutSessionId: String? = null,
    val checkoutLaunchAttempt: Long = 0L,
    /** JMD → route into the NCB (PowerTranz) card-entry screen (one-shot). */
    val navToNcbCardEntry: Boolean = false,
)

/**
 * Auction pre-checkout review — behavior from
 * FigmaAuctionProductCheckoutViewController: hero card, Our Promise, USD/JMD
 * picker, sticky total + "Continue to pay" → POST /payments/create-checkout
 * (is_auction=true) → Stripe hosted checkout in a Custom Tab.
 *
 * The route carries no argument (Swift parity) — the product is handed over
 * via [ShopCheckoutStore].
 */
class AuctionCheckoutViewModel(
    private val checkout: ShopCheckoutRepository = ShopRepoProvider.checkout,
    private val products: ShopProductsRepository = ShopRepoProvider.products,
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
    private val profileRepository: MoreProfileRepository = MoreRepository(),
) : ViewModel(), NcbCheckoutHost {

    val currencyOptions = listOf("USD", "JMD")

    private val _state = MutableStateFlow(
        AuctionCheckoutUiState(
            product = ShopCheckoutStore.product,
            // Seed from the persisted rate like every sibling VM (CartViewModel
            // et al.) — otherwise the JMD total paints at the hardcoded default
            // until /exchange-rates answers, and stays wrong if it fails.
            exchangeUsdToJmd = com.ga.airdrop.core.prefs.ExchangeRateStore.current,
        )
    )
    val state: StateFlow<AuctionCheckoutUiState> = _state

    // NCB (JMD) — the auction "Buy Now" path's own copy of the NcbCheckoutHost
    // contract (the cart flow has its equivalent in CartViewModel). Billing is
    // prefilled from the profile below; the spi token is transient/private.
    private val _ncb = MutableStateFlow(NcbUiModel())
    override val ncbUi: StateFlow<NcbUiModel> = _ncb
    private var ncbSpiToken: String? = null
    private var ncbPrefillApplied = false

    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        if (_state.value.paying) {
                            _state.update {
                                it.copy(
                                    paying = false,
                                    checkoutUrl = null,
                                    checkoutSessionId = null,
                                    errorTitle = "Checkout interrupted",
                                    errorMessage = "Your account finished loading. Tap Pay again to continue safely.",
                                )
                            }
                        }
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                sessionOwner = changed
                _state.update {
                    it.copy(
                        paying = false,
                        checkoutUrl = null,
                        checkoutSessionId = null,
                        errorTitle = if (changed == null) "Sign in required" else null,
                        errorMessage = if (changed == null) {
                            "Log in to your Airdropja account before checking out."
                        } else {
                            null
                        },
                    )
                }
            }
        }
        // Deep-link fallback (B4): Swift FigmaRouteViewController:727 pushes
        // the checkout VC with product nil + an id/slug ref and the VC
        // resolves it. Mirror: no handed-over product but a pending ref →
        // fetch it before rendering "Product unavailable".
        if (_state.value.product == null) {
            ShopCheckoutStore.pendingRef?.let { pendingRef ->
                ShopCheckoutStore.pendingRef = null
                _state.update { it.copy(resolvingProduct = true) }
                viewModelScope.launch {
                    products.productBySlug(pendingRef, featured = false)
                        .onSuccess { resolved ->
                            _state.update { it.copy(product = resolved, resolvingProduct = false) }
                        }
                        .onFailure {
                            // Without this the screen kept a phantom "$0.00
                            // Auction Product" card forever (Audit#7 C4).
                            _state.update {
                                it.copy(
                                    resolvingProduct = false,
                                    errorTitle = "Product unavailable",
                                    errorMessage = "This product could not be loaded. Please try again from the shop.",
                                )
                            }
                        }
                }
            }
        }
        viewModelScope.launch {
            // RECONCILE: GET /exchange-rates → { usd_to_jmd } (no auth).
            checkout.exchangeRate().onSuccess { rate ->
                if (rate > 0) {
                    com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                    _state.update { it.copy(exchangeUsdToJmd = rate) }
                }
            }
        }
        prefillNcbBillingFromProfile()
    }

    /**
     * Prefill the NCB billing form from the saved profile ahead of time (the
     * auction "Buy Now" path has no profile step). Applied at most once, and only
     * fills BLANK fields so a late-completing fetch can't clobber billing the user
     * already edited on the card-entry screen. Country is coerced to JM/US so the
     * field honestly shows what create-ncb-session transmits (NCB is [US, JM] only).
     * Leaves [ncbPrefillApplied] false on failure so pay() can retry it.
     */
    private fun prefillNcbBillingFromProfile() {
        if (ncbPrefillApplied) return
        val owner = sessionBoundary.capture() ?: return
        val requestOwner = sessionBoundary.requestOwner(owner) ?: return
        viewModelScope.launch {
            profileRepository.currentUser(requestOwner.provenance).onSuccess { user ->
                applyCurrentOwner(owner) {
                    if (ncbPrefillApplied) return@applyCurrentOwner
                    ncbPrefillApplied = true
                    val jm = user.country?.trim()?.let {
                        it.equals("Jamaica", ignoreCase = true) || it.equals("JM", ignoreCase = true)
                    } == true
                    _ncb.update { s ->
                        val f = s.form
                        s.copy(
                            form = f.copy(
                                firstName = f.firstName.ifBlank { user.firstName.orEmpty() },
                                lastName = f.lastName.ifBlank { user.lastName.orEmpty() },
                                currency = CheckoutCurrency.JMD.wireValue,
                                address1 = f.address1.ifBlank { user.addressLine1.orEmpty() },
                                address2 = f.address2.ifBlank { user.addressLine2.orEmpty() },
                                state = f.state.ifBlank { user.state.orEmpty() },
                                city = f.city.ifBlank { user.city.orEmpty() },
                                // Only override the untouched default; preserve a user's pick.
                                country = if (f.country.isBlank() || f.country.equals("United States", true)) {
                                    if (jm) "Jamaica" else "United States"
                                } else {
                                    f.country
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    fun setCurrency(value: String) {
        _state.update { it.copy(currency = value) }
    }

    fun totalLabel(): String {
        val s = _state.value
        val usd = s.product?.priceUsd ?: 0.0
        // Swift renderTotal: USD "$X.XX", JMD " JA$X.XX".
        return if (s.currency == "USD") formatUsd(usd) else " " + formatJmd(usd * s.exchangeUsdToJmd)
    }

    fun pay() {
        val s = _state.value
        if (s.paying) return
        if (s.checkoutUrl != null || s.checkoutSessionId != null) {
            val owner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId }
            val url = validatedHostedCheckoutUrl(s.checkoutUrl)
            val sessionId = s.checkoutSessionId?.trim()?.takeIf(String::isNotEmpty)
            if (owner != null && url != null && sessionId != null &&
                CheckoutFlowStore.pending(sessionId, owner) != null
            ) {
                _state.update { it.copy(checkoutLaunchAttempt = it.checkoutLaunchAttempt + 1) }
            } else {
                _state.update {
                    it.copy(
                        errorTitle = "Checkout unavailable",
                        errorMessage = "The saved checkout session is invalid. Check Shipments before retrying.",
                    )
                }
            }
            return
        }
        val currentOwner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId }
        if (currentOwner != null && CheckoutFlowStore.pending(currentOwner) != null) {
            _state.update {
                it.copy(
                    errorTitle = "Payment still pending",
                    errorMessage = "A checkout is already pending. Check Shipments before paying again.",
                )
            }
            return
        }
        if (currentOwner != null && CheckoutFlowStore.creating(currentOwner) != null) {
            _state.update {
                it.copy(
                    errorTitle = "Payment status unknown",
                    errorMessage = "A checkout request may already have reached Stripe. It cannot be retried safely; check Shipments.",
                )
            }
            return
        }
        val product = s.product
        if (product == null) {
            _state.update {
                it.copy(
                    errorTitle = "Product unavailable",
                    errorMessage = "No product was loaded for this checkout.",
                )
            }
            return
        }
        val packageId = product.packageId?.takeIf { it > 0 }
        if (packageId == null || product.id <= 0) {
            _state.update {
                it.copy(
                    errorTitle = "Checkout unavailable",
                    errorMessage = "This product is missing the package ID required for sale checkout.",
                )
            }
            return
        }
        when (parseCheckoutCurrency(s.currency)) {
            CheckoutCurrency.JMD -> {
                // JMD → the NCB (PowerTranz) card-entry screen (is_auction=true,
                // this single package). No Stripe hosted checkout / pending record.
                // Retry the billing prefill in case the init fetch failed, so the
                // card entry has name/address/city (self-guards if already applied).
                prefillNcbBillingFromProfile()
                _state.update {
                    it.copy(navToNcbCardEntry = true, errorTitle = null, errorMessage = null)
                }
                return
            }
            null -> {
                _state.update {
                    it.copy(
                        errorTitle = "Checkout unavailable",
                        errorMessage = "Choose USD to continue to secure payment.",
                    )
                }
                return
            }
            CheckoutCurrency.USD -> Unit
        }
        val owner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId }
        if (owner == null) {
            _state.update {
                it.copy(
                    errorTitle = "Sign in required",
                    errorMessage = "Log in to your Airdropja account before checking out.",
                )
            }
            return
        }
        _state.update { it.copy(paying = true) }
        var requestOwner: com.ga.airdrop.core.session.AuthenticatedRequestOwner? = null
        var summaryFlow: com.ga.airdrop.feature.cart.CheckoutFlow? = null
        var creation: com.ga.airdrop.feature.cart.PendingCheckoutCreation? = null
        var dispatchNote: String? = null
        val bound = sessionBoundary.runWhileCurrent(owner) {
            requestOwner = sessionBoundary.requestOwner(owner) ?: return@runWhileCurrent false
            val flow = CheckoutFlowStore.start(owner, listOf(product.toCartLine()))
                ?: return@runWhileCurrent false
            summaryFlow = CheckoutFlowStore.update(owner, expectedFlowId = flow.id) { current ->
                current.copy(
                    currency = CheckoutCurrency.USD.wireValue,
                    phase = CheckoutPhase.ORDER_SUMMARY,
                )
            }
            dispatchNote = com.ga.airdrop.feature.cart.CartNoteStore.note(owner)
                .trim()
                .takeIf(String::isNotEmpty)
            if (summaryFlow?.id != flow.id) return@runWhileCurrent false
            creation = CheckoutFlowStore.beginHostedCheckoutCreation(owner)
            creation != null
        }
        val request = requestOwner
        val creationId = creation?.id
        if (!bound || summaryFlow == null || request == null || creationId == null) {
            val blockedByUnknownCreation = CheckoutFlowStore.creating(owner) != null
            _state.update {
                it.copy(
                    paying = false,
                    errorTitle = if (blockedByUnknownCreation) "Payment status unknown" else "Checkout unavailable",
                    errorMessage = if (blockedByUnknownCreation) {
                        "A checkout request may already have reached Stripe. It cannot be retried safely; check Shipments."
                    } else {
                        "The checkout could not be saved safely, so no payment request was sent."
                    },
                )
            }
            return
        }
        viewModelScope.launch {
            // RECONCILE: POST /payments/create-checkout
            // { package_ids: [packageId], currency, is_auction: true } →
            // data.checkout_url (Stripe hosted checkout).
            checkout.createCheckout(
                packageIds = listOf(packageId),
                currency = CheckoutCurrency.USD.wireValue,
                isAuction = true,
                userNote = dispatchNote,
                expectedSession = request.provenance,
            )
                .onSuccess { response ->
                    applyCurrentOwner(owner) {
                        val url = validatedHostedCheckoutUrl(response.checkoutUrl)
                        val sessionId = response.sessionId?.trim()?.takeIf(String::isNotEmpty)
                        val pending = if (sessionId != null) {
                            CheckoutFlowStore.recordHostedCheckout(owner, creationId, sessionId)
                        } else {
                            null
                        }
                        if (sessionId == null || pending == null) {
                            _state.update {
                                it.copy(
                                    paying = false,
                                    errorTitle = "Payment status unknown",
                                    errorMessage = "Stripe did not return a checkout session ID. It cannot be retried safely; check Shipments.",
                                )
                            }
                        } else if (url == null) {
                            _state.update {
                                it.copy(
                                    paying = false,
                                    checkoutUrl = null,
                                    checkoutSessionId = null,
                                    errorTitle = "Payment still pending",
                                    errorMessage = "Stripe started checkout but did not return a secure URL. Check Shipments; do not pay again.",
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    paying = false,
                                    checkoutUrl = url,
                                    checkoutSessionId = sessionId,
                                    checkoutLaunchAttempt = it.checkoutLaunchAttempt + 1,
                                )
                            }
                        }
                    }
                }
                .onFailure { err ->
                    applyCurrentOwner(owner) {
                        val unauthenticated = err.isUnauthenticatedCheckoutFailure()
                        _state.update {
                            it.copy(
                                paying = false,
                                errorTitle = if (unauthenticated) {
                                    "Sign in required"
                                } else {
                                    "Payment status unknown"
                                },
                                errorMessage = if (unauthenticated) {
                                    "Your session changed after checkout started. Check Shipments before paying again."
                                } else {
                                    "The checkout request may have reached Stripe. It cannot be retried safely; check Shipments."
                                },
                            )
                        }
                    }
                }
        }
    }

    fun consumeCheckoutUrl() {
        _state.update { it.copy(checkoutUrl = null, checkoutSessionId = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorTitle = null, errorMessage = null) }
    }

    // --- NcbCheckoutHost (auction "Buy Now" JMD) -------------------------------

    override fun updateNcbForm(transform: (CartBillingForm) -> CartBillingForm) {
        _ncb.update { it.copy(form = transform(it.form)) }
    }

    override fun consumeNcb3DSNav() {
        _ncb.update { it.copy(navTo3DS = false) }
    }

    override fun consumeNcbSuccessNav() {
        _ncb.update { it.copy(navToSuccess = false) }
    }

    fun consumeNcbCardEntryNav() {
        _state.update { it.copy(navToNcbCardEntry = false) }
    }

    override fun createNcbSession(
        cardName: String,
        cardNumber: String,
        cardMonth: String,
        cardYear: String,
        cardCvv: String,
    ) {
        if (_ncb.value.busy) return
        val product = _state.value.product
        val packageId = product?.packageId?.takeIf { it > 0 }
        if (product == null || packageId == null || product.id <= 0) {
            _ncb.update { it.copy(errorMessage = "This product can't be purchased right now.") }
            return
        }
        val owner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId }
        val requestOwner = owner?.let { sessionBoundary.requestOwner(it) }
        if (owner == null || requestOwner == null) {
            _ncb.update { it.copy(errorMessage = "Log in to your Airdropja account before paying.") }
            return
        }
        val form = _ncb.value.form
        // The Buy-Now path has no profile step and the card-entry screen has no
        // first/last/city fields, so those come only from the profile prefill. Fall
        // back to the (required, validated) cardholder name for first/last, and hard-
        // guard first/last/city so we never POST an unrecoverable-422 empty required
        // field — mirrors the cart path's validateCheckoutProfile gate.
        val holder = cardName.trim()
        val firstName = form.firstName.trim().ifBlank { holder.substringBefore(' ').trim() }
        val lastName = form.lastName.trim().ifBlank { holder.substringAfter(' ', "").trim() }
        val city = form.city.trim()
        if (firstName.isBlank() || lastName.isBlank() || city.isBlank()) {
            _ncb.update {
                it.copy(
                    errorMessage = "Add your full billing name and city in More → Profile " +
                        "before paying with JMD, or choose USD.",
                )
            }
            return
        }
        val request = CreateNcbSessionRequest(
            packageIds = listOf(packageId),
            currency = CheckoutCurrency.JMD.wireValue,
            isAuction = true,
            firstName = firstName,
            lastName = lastName,
            address = listOf(form.address1, form.address2).filter { it.isNotBlank() }
                .joinToString(", ").trim(),
            city = city,
            country = ncbCountryCode(form.country),
            cardName = cardName.trim(),
            cardNumber = cardNumber.filter(Char::isDigit),
            cardMonth = cardMonth.trim(),
            cardYear = cardYear.trim(),
            cardCvv = cardCvv.trim(),
            deliveryMode = "pickup",
        )
        _ncb.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            checkout.createNcbSession(request, requestOwner.provenance)
                .onSuccess { resp ->
                    applyCurrentOwner(owner) {
                        ncbSpiToken = resp.spiToken
                        _ncb.update {
                            it.copy(busy = false, redirectData = resp.redirectData, navTo3DS = true)
                        }
                    }
                }
                .onFailure { e ->
                    applyCurrentOwner(owner) {
                        _ncb.update {
                            it.copy(
                                busy = false,
                                errorMessage = e.message ?: "We couldn't start the payment. Please try again.",
                            )
                        }
                    }
                }
        }
    }

    override fun completeNcbPayment() {
        if (_ncb.value.busy) return
        val spiToken = ncbSpiToken?.trim()?.takeIf(String::isNotEmpty) ?: return
        val owner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId } ?: return
        val requestOwner = sessionBoundary.requestOwner(owner) ?: return
        _ncb.update { it.copy(busy = true, errorMessage = null) }
        viewModelScope.launch {
            checkout.ncbCompletePayment(spiToken, requestOwner.provenance)
                .onSuccess { resp ->
                    applyCurrentOwner(owner) {
                        // Auction Buy-Now buys a single package directly — there is no
                        // cart row to clear. Consume the token so a late 3DS callback
                        // can't re-POST ncb-complete-payment.
                        ncbSpiToken = null
                        _ncb.update {
                            it.copy(
                                busy = false,
                                invoiceId = resp.invoiceId,
                                redirectData = null,
                                navToSuccess = true,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    applyCurrentOwner(owner) {
                        _ncb.update {
                            it.copy(
                                busy = false,
                                errorMessage = e.message
                                    ?: "We couldn't confirm your payment. Check Shipments before paying again.",
                            )
                        }
                    }
                }
        }
    }

    private fun ncbCountryCode(country: String): String {
        val c = country.trim()
        return if (c.equals("Jamaica", ignoreCase = true) || c.equals("JM", ignoreCase = true)) "JM" else "US"
    }

    private fun applyCurrentOwner(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean =
        sessionBoundary.runWhileCurrent(owner) {
            action()
            true
        }
}
