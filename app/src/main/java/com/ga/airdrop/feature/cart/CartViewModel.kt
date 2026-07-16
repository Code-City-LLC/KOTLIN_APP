package com.ga.airdrop.feature.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.location.CountryCatalog
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionJobs
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.feature.more.MoreProfileRepository
import com.ga.airdrop.feature.more.MoreRepository
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import com.ga.airdrop.feature.shop.ShopRepoProvider
import java.net.URI
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartBillingForm(
    val firstName: String = "",
    val lastName: String = "",
    val currency: String = "JMD",
    val address1: String = "",
    val address2: String = "",
    val state: String = "",
    val city: String = "",
    val country: String = "United States",
    val postal: String = "",
)

data class CartUiState(
    // Swift moved off the hardcoded 161.00 to the shared last-known rate;
    // seed with the same shipments default (FuchsiaTower Pass-4 C6).
    val exchangeUsdToJmd: Double = com.ga.airdrop.feature.shipments.DEFAULT_USD_TO_JMD,
    val form: CartBillingForm = CartBillingForm(),
    val note: String = "",
    val loadingCart: Boolean = false,
    val mutatingKeys: Set<CartStore.CartLineKey> = emptySet(),
    val paying: Boolean = false,
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    /**
     * One-shot: "Make Payment" now routes to the Delivery Method screen
     * (Swift cart → FigmaDeliveryMethodViewController parity,
     * docs/PARITY_GAP_SPECS.md §4) instead of creating the Stripe checkout
     * directly. Delivery and Order Summary own all later decisions.
     */
    val navToDeliveryMethod: Boolean = false,
    val profileLoading: Boolean = false,
    val profileSaving: Boolean = false,
    val profileSummaryNav: Boolean = false,
    val profileOptions: List<String> = listOf(ADD_NEW_CHECKOUT_PROFILE),
    val selectedProfile: String = ADD_NEW_CHECKOUT_PROFILE,
    val orderPaying: Boolean = false,
    /** Hosted URL is transient and appears only after durable pending identity. */
    val checkoutUrl: String? = null,
    val checkoutSessionId: String? = null,
    val checkoutLaunchAttempt: Long = 0L,
)

internal const val ADD_NEW_CHECKOUT_PROFILE = "Add new profile"

/**
 * My Cart — behavior from FigmaCartViewController (RN MyCartView): items
 * from [CartStore], billing form autofilled from the user profile, exchange
 * rate. "Continue" starts one session-owned checkout context and routes to
 * Delivery Method. Server `/cart` is authoritative for package rows; auction
 * rows remain in the same domain-qualified local store.
 */
class CartViewModel(
    private val checkout: ShopCheckoutRepository = ShopRepoProvider.checkout,
    private val cartServer: CartServerGateway = DataCartServerGateway(),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
    private val profileRepository: MoreProfileRepository = MoreRepository(),
) : ViewModel() {

    val countryOptions: List<String> get() = CountryCatalog.displayOptions

    private val _state = MutableStateFlow(
        CartUiState(exchangeUsdToJmd = com.ga.airdrop.core.prefs.ExchangeRateStore.current),
    )
    val state: StateFlow<CartUiState> = _state

    /** Live cart lines (Swift re-reads FigmaCartStore on every appearance). */
    val items: StateFlow<List<CartStore.CartLine>> = CartStore.items

    /** Live saved-for-later lines, separate from the active checkout cart. */
    val savedItems: StateFlow<List<CartStore.CartLine>> = SavedForLaterStore.items

    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()
    private var hydrateGeneration = 0L
    private val packageCartMutations = PackageCartMutationCoordinator(cartServer, sessionBoundary)
    private val sessionJobs = AuthenticatedSessionJobs(viewModelScope)
    private var profileLoadJob: Job? = null
    private var profileSaveJob: Job? = null
    private var orderPayJob: Job? = null
    private var loadedProfileForm: CartBillingForm? = null
    private var loadedProfileUserId: Int? = null
    private var loadedProfileEmail: String? = null

    init {
        sessionOwner?.let { owner ->
            _state.update { it.copy(note = CartNoteStore.note(owner)) }
        }
        loadRate()
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                val replaced = sessionOwner?.sessionId != changed?.sessionId
                sessionOwner = changed
                if (replaced) {
                    sessionJobs.replaceSession()
                    profileLoadJob = null
                    profileSaveJob = null
                    orderPayJob = null
                    loadedProfileForm = null
                    loadedProfileUserId = null
                    loadedProfileEmail = null
                    hydrateGeneration++
                    CartStore.clear()
                    CheckoutFlowStore.clear()
                    _state.value = CartUiState(
                        exchangeUsdToJmd = com.ga.airdrop.core.prefs.ExchangeRateStore.current,
                        loadingCart = changed != null,
                        note = changed?.let(CartNoteStore::note).orEmpty(),
                    )
                } else if (changed != null) {
                    // Account id may be strengthened after /user/profile.
                    _state.update { it.copy(note = CartNoteStore.note(changed)) }
                }
                if (changed != null) {
                    hydrateServerCart(changed)
                }
            }
        }
    }

    private fun loadRate() {
        viewModelScope.launch {
            // RECONCILE: GET /exchange-rates → { usd_to_jmd }.
            checkout.exchangeRate().onSuccess { rate ->
                if (rate > 0) {
                    com.ga.airdrop.core.prefs.ExchangeRateStore.update(rate)
                    _state.update { it.copy(exchangeUsdToJmd = rate) }
                }
            }
        }
    }

    private suspend fun hydrateServerCart(owner: AuthenticatedSessionOwner) {
        val requestOwner = sessionBoundary.requestOwner(owner) ?: return
        val generation = ++hydrateGeneration
        val snapshot = CartStore.beginServerCartSnapshot()
        if (!sessionBoundary.apply(owner) { _state.update { it.copy(loadingCart = true) } }) return
        cartServer.cart(requestOwner.provenance)
            .onSuccess { lines ->
                applyLatestHydrate(generation, requestOwner.session) {
                    CartStore.reconcileServerPackages(lines, snapshot)
                    _state.update { it.copy(loadingCart = false) }
                }
            }
            .onFailure { error ->
                applyLatestHydrate(generation, requestOwner.session) {
                    _state.update {
                        it.copy(
                            loadingCart = false,
                            errorTitle = "Couldn't refresh cart",
                            errorMessage = error.message ?: "Please try again.",
                        )
                    }
                }
            }
    }

    private fun applyLatestHydrate(
        generation: Long,
        owner: AuthenticatedSessionOwner,
        action: () -> Unit,
    ): Boolean = sessionBoundary.runWhileCurrent(owner) {
        if (generation != hydrateGeneration) return@runWhileCurrent false
        action()
        true
    }

    fun updateForm(transform: (CartBillingForm) -> CartBillingForm) {
        _state.update { it.copy(form = transform(it.form)) }
    }

    fun updateNote(value: String) {
        val owner = currentOwner()
        if (owner == null || owner.accountId == null) {
            _state.update {
                it.copy(
                    errorTitle = "Couldn't save note",
                    errorMessage = "Your signed-in account could not be confirmed.",
                )
            }
            return
        }
        sessionBoundary.runWhileCurrent(owner) {
            if (!CartNoteStore.save(owner, value)) return@runWhileCurrent false
            _state.update { it.copy(note = CartNoteStore.note(owner)) }
            true
        }
    }

    /** Load the one saved billing profile for the active JMD checkout. */
    fun loadCheckoutProfile() {
        if (profileLoadJob?.isActive == true) return
        val owner = currentOwner() ?: return profileError(
            "Sign in required",
            "Log in to your Airdropja account before continuing.",
        )
        val flow = CheckoutFlowStore.current(owner)?.takeIf {
            it.phase == CheckoutPhase.PROFILE_INFORMATION &&
                parseCheckoutCurrency(it.currency) == CheckoutCurrency.JMD
        } ?: return profileError(
            "Checkout unavailable",
            "Return to your cart and restart checkout.",
        )
        val requestOwner = sessionBoundary.requestOwner(owner) ?: return profileError(
            "Sign in required",
            "Log in to your Airdropja account before continuing.",
        )
        val expectedFlowId = flow.id
        _state.update { it.copy(profileLoading = true, errorTitle = null, errorMessage = null) }
        profileLoadJob = sessionJobs.launch {
            profileRepository.currentUser(requestOwner.provenance)
                .onSuccess { user ->
                    val userId = user.id
                    if (userId != null && !sessionBoundary.bindAccountId(owner, userId)) {
                        return@onSuccess
                    }
                    val liveOwner = sessionBoundary.capture()
                        ?.takeIf { it.sessionId == owner.sessionId }
                        ?: return@onSuccess
                    CheckoutFlowStore.onAuthenticatedSessionChanged(liveOwner)
                    val stillExact = CheckoutFlowStore.current(liveOwner)?.let {
                        it.id == expectedFlowId &&
                            it.phase == CheckoutPhase.PROFILE_INFORMATION &&
                            parseCheckoutCurrency(it.currency) == CheckoutCurrency.JMD
                    } == true
                    if (!stillExact) return@onSuccess
                    val loaded = CartBillingForm(
                        firstName = user.firstName.orEmpty(),
                        lastName = user.lastName.orEmpty(),
                        currency = CheckoutCurrency.JMD.wireValue,
                        address1 = user.addressLine1.orEmpty(),
                        address2 = user.addressLine2.orEmpty(),
                        state = user.state.orEmpty(),
                        city = user.city.orEmpty(),
                        country = user.country.orEmpty(),
                    )
                    val label = listOf(user.firstName, user.lastName)
                        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
                        .joinToString(" ")
                        .ifEmpty { "Saved profile" }
                    sessionBoundary.apply(liveOwner) {
                        sessionOwner = liveOwner
                        loadedProfileForm = loaded
                        loadedProfileUserId = user.id
                        loadedProfileEmail = user.email
                        _state.update {
                            it.copy(
                                form = loaded,
                                profileLoading = false,
                                profileOptions = listOf(label, ADD_NEW_CHECKOUT_PROFILE).distinct(),
                                selectedProfile = label,
                                note = CartNoteStore.note(liveOwner),
                            )
                        }
                    }
                }
                .onFailure { error ->
                    sessionBoundary.apply(owner) {
                        _state.update {
                            it.copy(
                                profileLoading = false,
                                errorTitle = "Couldn't load profile",
                                errorMessage = error.message ?: "Please try again.",
                            )
                        }
                    }
                }
        }
    }

    /** Profile selector state belongs to the nav-scoped owner, never Compose remember. */
    fun selectCheckoutProfile(value: String) {
        val state = _state.value
        when {
            value == ADD_NEW_CHECKOUT_PROFILE -> _state.update {
                it.copy(
                    selectedProfile = ADD_NEW_CHECKOUT_PROFILE,
                    form = CartBillingForm(
                        currency = CheckoutCurrency.JMD.wireValue,
                        country = "",
                    ),
                )
            }
            value in state.profileOptions -> loadedProfileForm?.let { loaded ->
                _state.update { it.copy(selectedProfile = value, form = loaded) }
            }
        }
    }

    fun saveProfileInformation() {
        if (_state.value.profileSaving) return
        val owner = currentOwner() ?: return profileError(
            "Sign in required",
            "Log in to your Airdropja account before continuing.",
        )
        val flow = CheckoutFlowStore.current(owner)?.takeIf {
            it.phase == CheckoutPhase.PROFILE_INFORMATION &&
                parseCheckoutCurrency(it.currency) == CheckoutCurrency.JMD
        } ?: return profileError(
            "Checkout unavailable",
            "Return to your cart and restart checkout.",
        )
        val form = _state.value.form.copy(currency = CheckoutCurrency.JMD.wireValue)
        validateCheckoutProfile(form)?.let { return profileError("Missing information", it) }
        val requestOwner = sessionBoundary.requestOwner(owner) ?: return profileError(
            "Sign in required",
            "Log in to your Airdropja account before continuing.",
        )
        val expectedFlowId = flow.id
        _state.update {
            it.copy(profileSaving = true, form = form, errorTitle = null, errorMessage = null)
        }
        profileSaveJob = sessionJobs.launch {
            val fields = mapOf(
                "user_id" to loadedProfileUserId?.toString(),
                "email" to loadedProfileEmail,
                "first_name" to form.firstName.trim().ifEmpty { null },
                "last_name" to form.lastName.trim().ifEmpty { null },
                "user_address_line_1" to form.address1.trim().ifEmpty { null },
                "user_address_line_2" to form.address2.trim().ifEmpty { null },
                "user_address_city" to form.city.trim().ifEmpty { null },
                "user_address_state" to form.state.trim().ifEmpty { null },
                "user_address_country" to CountryCatalog.canonicalName(form.country).ifEmpty { null },
                "payment_currency" to CheckoutCurrency.JMD.wireValue,
            )
            profileRepository.updateProfile(fields, requestOwner.provenance)
                .onSuccess {
                    sessionBoundary.runWhileCurrent(owner) {
                        val advanced = CheckoutFlowStore.update(owner, expectedFlowId) {
                            it.copy(phase = CheckoutPhase.ORDER_SUMMARY)
                        } ?: return@runWhileCurrent false
                        if (advanced.id != expectedFlowId) return@runWhileCurrent false
                        loadedProfileForm = form
                        val label = listOf(form.firstName.trim(), form.lastName.trim())
                            .filter(String::isNotEmpty)
                            .joinToString(" ")
                            .ifEmpty { "Saved profile" }
                        _state.update {
                            it.copy(
                                profileSaving = false,
                                profileSummaryNav = true,
                                profileOptions = listOf(label, ADD_NEW_CHECKOUT_PROFILE).distinct(),
                                selectedProfile = label,
                            )
                        }
                        true
                    }
                }
                .onFailure { error ->
                    sessionBoundary.runWhileCurrent(owner) {
                        if (CheckoutFlowStore.current(owner)?.id != expectedFlowId) {
                            return@runWhileCurrent false
                        }
                        _state.update {
                            it.copy(
                                profileSaving = false,
                                errorTitle = "Save failed",
                                errorMessage = error.message ?: "Please try again.",
                            )
                        }
                        true
                    }
                }
        }
    }

    fun consumeProfileSummaryNav() {
        _state.update { it.copy(profileSummaryNav = false) }
    }

    /** Exact flow snapshot; later cart additions are deliberately excluded. */
    fun capturedCheckoutLines(): List<CartStore.CartLine> {
        val owner = currentOwner() ?: return emptyList()
        val flow = CheckoutFlowStore.current(owner) ?: return emptyList()
        return capturedLines(flow).orEmpty()
    }

    fun currentCheckoutFlow(): CheckoutFlow? {
        val owner = currentOwner() ?: return null
        return CheckoutFlowStore.current(owner)
    }

    /** Persist the exact phase rewind before header/system Back may pop. */
    fun rewindOrderSummary(): Boolean {
        if (_state.value.orderPaying) {
            _state.update {
                it.copy(
                    errorTitle = "Checkout in progress",
                    errorMessage = "Wait for the secure checkout request to finish before going back.",
                )
            }
            return false
        }
        val owner = currentOwner() ?: return false
        val flow = CheckoutFlowStore.current(owner) ?: return false
        val rewound = CheckoutFlowStore.rewindOrderSummary(owner, flow.id)
        if (rewound == null) {
            orderError(
                "Checkout still pending",
                "This checkout cannot go back while payment verification is pending.",
            )
            return false
        }
        _state.update {
            it.copy(
                profileSummaryNav = false,
                orderPaying = false,
                checkoutUrl = null,
                checkoutSessionId = null,
            )
        }
        return true
    }

    /** Final Order Summary dispatch. Delivery/Profile never create Stripe. */
    fun payOrderSummary() {
        if (_state.value.orderPaying) return
        val owner = currentOwner() ?: return orderError(
            "Sign in required",
            "Log in to your Airdropja account before checking out.",
        )
        val existing = _state.value
        if (existing.checkoutUrl != null || existing.checkoutSessionId != null) {
            val existingUrl = validatedHostedCheckoutUrl(existing.checkoutUrl)
            val existingSession = existing.checkoutSessionId?.trim()?.takeIf(String::isNotEmpty)
            if (existingUrl != null && existingSession != null &&
                CheckoutFlowStore.pending(existingSession, owner) != null
            ) {
                _state.update { it.copy(checkoutLaunchAttempt = it.checkoutLaunchAttempt + 1) }
            } else {
                orderError("Checkout unavailable", "The saved checkout session is invalid. Check Shipments before retrying.")
            }
            return
        }
        if (CheckoutFlowStore.pending(owner) != null) {
            return orderError(
                "Payment still pending",
                "A checkout is already pending. Check Shipments before paying again.",
            )
        }
        if (CheckoutFlowStore.creating(owner) != null) {
            return orderError(
                "Payment status unknown",
                "A checkout request may already have reached Stripe. It cannot be retried safely; check Shipments before paying again.",
            )
        }
        val flow = CheckoutFlowStore.current(owner)?.takeIf { it.phase == CheckoutPhase.ORDER_SUMMARY }
            ?: return orderError("Checkout unavailable", "Return to your cart and restart checkout.")
        val lines = capturedLines(flow)
            ?: return orderError("Checkout unavailable", "The cart changed. Return to your cart and restart checkout.")
        if (CartStore.hasPendingPackageMutations()) {
            return orderError("Cart update in progress", "Wait for your cart changes to finish before paying.")
        }
        when (checkoutPaymentRail(flow.currency)) {
            CheckoutPaymentRail.NCB_POWERTRANZ -> return orderError(
                "JMD checkout unavailable",
                "JMD payment is not available yet. No payment was started.",
            )
            CheckoutPaymentRail.STRIPE -> Unit
            null -> return orderError("Checkout unavailable", "The selected payment currency is invalid.")
        }
        val requestOwner = sessionBoundary.requestOwner(owner)
            ?: return orderError("Sign in required", "Log in to your Airdropja account before checking out.")
        val expectedFlowId = flow.id
        val expectedKeys = flow.cartKeys
        var userNote: String? = null
        var creation: PendingCheckoutCreation? = null
        val dispatchPrepared = sessionBoundary.runWhileCurrent(owner) {
            val exact = CheckoutFlowStore.current(owner)?.takeIf {
                it.id == expectedFlowId && it.cartKeys == expectedKeys &&
                    it.phase == CheckoutPhase.ORDER_SUMMARY
            } ?: return@runWhileCurrent false
            if (capturedLines(exact) == null) return@runWhileCurrent false
            userNote = CartNoteStore.note(owner).trim().takeIf(String::isNotEmpty)
            creation = CheckoutFlowStore.beginHostedCheckoutCreation(owner)
            creation != null
        }
        if (!dispatchPrepared) {
            val blockedByUnknownCreation = CheckoutFlowStore.creating(owner) != null
            return orderError(
                if (blockedByUnknownCreation) "Payment status unknown" else "Checkout unavailable",
                if (blockedByUnknownCreation) {
                    "A checkout request may already have reached Stripe. It cannot be retried safely; check Shipments before paying again."
                } else {
                    "The checkout could not be saved safely, so no payment request was sent."
                },
            )
        }
        val creationId = requireNotNull(creation).id
        _state.update { it.copy(orderPaying = true, errorTitle = null, errorMessage = null) }
        orderPayJob = sessionJobs.launch {
            checkout.createCheckout(
                packageIds = lines.mapNotNull(CartStore.CartLine::packageId),
                currency = CheckoutCurrency.USD.wireValue,
                isAuction = flow.isAuction,
                userNote = userNote,
                expectedSession = requestOwner.provenance,
            ).onSuccess { response ->
                val url = validatedHostedCheckoutUrl(response.checkoutUrl)
                val checkoutSessionId = response.sessionId?.trim()?.takeIf(String::isNotEmpty)
                if (checkoutSessionId == null) {
                    sessionBoundary.apply(owner) {
                        orderError(
                            "Payment status unknown",
                            "Stripe did not return a checkout session ID. It cannot be retried safely; check Shipments.",
                        )
                    }
                    return@onSuccess
                }
                sessionBoundary.runWhileCurrent(owner) {
                    if (CheckoutFlowStore.recordHostedCheckout(owner, creationId, checkoutSessionId) == null) {
                        _state.update {
                            it.copy(
                                orderPaying = false,
                                errorTitle = "Payment status unknown",
                                errorMessage = "The checkout response could not be saved safely. It cannot be retried; check Shipments.",
                            )
                        }
                        return@runWhileCurrent true
                    }
                    if (url == null) {
                        _state.update {
                            it.copy(
                                orderPaying = false,
                                checkoutUrl = null,
                                checkoutSessionId = null,
                                errorTitle = "Payment still pending",
                                errorMessage = "Stripe started checkout but did not return a secure URL. Check Shipments; do not pay again.",
                            )
                        }
                        return@runWhileCurrent true
                    }
                    _state.update {
                        it.copy(
                            orderPaying = false,
                            checkoutUrl = url,
                            checkoutSessionId = checkoutSessionId,
                            checkoutLaunchAttempt = it.checkoutLaunchAttempt + 1,
                        )
                    }
                    true
                }
            }.onFailure { error ->
                sessionBoundary.runWhileCurrent(owner) {
                    if (CheckoutFlowStore.current(owner)?.id != expectedFlowId) {
                        return@runWhileCurrent false
                    }
                    orderError(
                        if (error.message?.contains("Unauthenticated", ignoreCase = true) == true) {
                            "Sign in required"
                        } else {
                            "Payment status unknown"
                        },
                        if (error.message?.contains("Unauthenticated", ignoreCase = true) == true) {
                            "Your session changed after checkout started. Check Shipments before paying again."
                        } else {
                            "The checkout request may have reached Stripe. It cannot be retried safely; check Shipments."
                        },
                    )
                    true
                }
            }
        }
    }

    fun consumeCheckoutUrl() {
        _state.update { it.copy(checkoutUrl = null, checkoutSessionId = null) }
    }

    private fun currentOwner(): AuthenticatedSessionOwner? {
        val observed = sessionOwner ?: return null
        val current = sessionBoundary.capture() ?: return null
        if (current.sessionId != observed.sessionId || !sessionBoundary.isCurrent(current)) return null
        if (current != observed) sessionOwner = current
        return current
    }

    private fun capturedLines(flow: CheckoutFlow): List<CartStore.CartLine>? {
        if (flow.cartKeys.isEmpty() || flow.cartKeys.distinct().size != flow.cartKeys.size) return null
        val byKey = CartStore.items.value.associateBy(CartStore.CartLine::key)
        val lines = flow.cartKeys.map { byKey[it] ?: return null }
        if (lines.any { !it.isCheckoutEligible() }) return null
        if (lines.mapNotNull(CartStore.CartLine::packageId) != flow.packageIds) return null
        if (CartStore.hasPendingPackageMutations(flow.cartKeys)) return null
        return lines
    }

    private fun profileError(title: String, message: String) {
        _state.update {
            it.copy(
                profileLoading = false,
                profileSaving = false,
                errorTitle = title,
                errorMessage = message,
            )
        }
    }

    private fun orderError(title: String, message: String) {
        _state.update {
            it.copy(
                orderPaying = false,
                checkoutUrl = null,
                checkoutSessionId = null,
                errorTitle = title,
                errorMessage = message,
            )
        }
    }

    fun removeItem(line: CartStore.CartLine) {
        if (line.resolvedKind == CartStore.CartLineKind.AUCTION) {
            CartStore.remove(line.key)
        } else {
            mutatePackageLine(line, add = false) { }
        }
    }

    fun saveForLater(line: CartStore.CartLine) {
        if (line.resolvedKind == CartStore.CartLineKind.AUCTION) {
            if (SavedForLaterStore.save(line)) CartStore.remove(line.key)
            return
        }
        mutatePackageLine(line, add = false) {
            SavedForLaterStore.save(line)
        }
    }

    fun moveSavedToCart(line: CartStore.CartLine) {
        if (line.resolvedKind == CartStore.CartLineKind.AUCTION) {
            CartStore.add(line)
            SavedForLaterStore.remove(line.key)
            return
        }
        mutatePackageLine(line, add = true) {
            SavedForLaterStore.remove(line.key)
        }
    }

    fun removeSaved(line: CartStore.CartLine) = SavedForLaterStore.remove(line.key)

    private fun mutatePackageLine(
        line: CartStore.CartLine,
        add: Boolean,
        onSuccess: () -> Unit,
    ) {
        val started = {
            _state.update { it.copy(mutatingKeys = it.mutatingKeys + line.key) }
        }
        val succeeded = {
            _state.update { it.copy(mutatingKeys = it.mutatingKeys - line.key) }
            onSuccess()
        }
        val failed: (String) -> Unit = { message ->
            _state.update {
                it.copy(
                    mutatingKeys = it.mutatingKeys - line.key,
                    errorTitle = "Cart update failed",
                    errorMessage = message,
                )
            }
        }
        if (add) {
            packageCartMutations.add(
                line = line,
                scope = viewModelScope,
                onStarted = started,
                onSuccess = succeeded,
                onFailure = failed,
            )
        } else {
            packageCartMutations.remove(
                line = line,
                scope = viewModelScope,
                onStarted = started,
                onSuccess = succeeded,
                onFailure = failed,
            )
        }
    }

    fun totalUsd(): Double = CartStore.totalUsd()

    fun totalJmd(): Double = totalUsd() * _state.value.exchangeUsdToJmd

    /**
     * Shared Swift-onPay guards: non-empty cart and a package id on every
     * line. Returns null (after surfacing the error) when checkout can't
     * proceed. Error copy is load-bearing — mirrored by
     * DeliveryMethodViewModel.onCurrencyChosen.
     */
    private fun guardedLines(): List<CartStore.CartLine>? {
        val lines = items.value
        if (lines.isEmpty()) {
            _state.update {
                it.copy(
                    errorTitle = "Cart empty",
                    errorMessage = "Add at least one item before checkout.",
                )
            }
            return null
        }
        val packageIds = lines.mapNotNull { it.packageId }
        if (packageIds.size != lines.size || packageIds.any { it <= 0 } ||
            lines.any { !it.isCheckoutEligible() }
        ) {
            _state.update {
                it.copy(
                    errorTitle = "Checkout unavailable",
                    errorMessage = "One or more products are unavailable for sale checkout.",
                )
            }
            return null
        }
        return lines
    }

    /**
     * Swift onPay: needs a package id on every line. The cart no longer goes
     * straight to Stripe — after the guards pass it hands off to the Delivery
     * Method screen (one-shot [CartUiState.navToDeliveryMethod]), which owns
     * the pickup/delivery choice + currency popup and then runs the original
     * checkout path (docs/PARITY_GAP_SPECS.md §4).
     */
    fun pay() {
        if (_state.value.paying) return
        if (_state.value.loadingCart || _state.value.mutatingKeys.isNotEmpty() ||
            CartStore.hasPendingPackageMutations()
        ) {
            _state.update {
                it.copy(
                    errorTitle = "Cart update in progress",
                    errorMessage = "Wait for your cart changes to finish before choosing delivery.",
                )
            }
            return
        }
        // Set synchronously before any snapshot/store work so a rapid second
        // tap cannot replace the first checkout flow.
        _state.update { it.copy(paying = true) }
        if (guardedLines() == null) {
            _state.update { it.copy(paying = false) }
            return
        }
        val owner = sessionOwner?.takeIf(sessionBoundary::isCurrent)
        if (owner == null) {
            _state.update {
                it.copy(
                    errorTitle = "Sign in required",
                    errorMessage = "Log in to your Airdropja account before checking out.",
                )
            }
            _state.update { it.copy(paying = false) }
            return
        }
        val started = sessionBoundary.runWhileCurrent(owner) {
            val currentLines = guardedLines() ?: return@runWhileCurrent false
            val flow = CheckoutFlowStore.start(owner, currentLines)
                ?: return@runWhileCurrent false
            _state.update { it.copy(navToDeliveryMethod = true) }
            flow.ownerSessionId == owner.sessionId
        }
        if (!started) {
            _state.update {
                it.copy(
                    errorTitle = "Checkout unavailable",
                    errorMessage = "The cart changed before checkout could start. Refresh and try again.",
                )
            }
            _state.update { it.copy(paying = false) }
            return
        }
    }

    /** The screen navigated — clear the one-shot flag. */
    fun consumeDeliveryNav() {
        _state.update { it.copy(navToDeliveryMethod = false, paying = false) }
    }

    fun dismissError() {
        _state.update { it.copy(errorTitle = null, errorMessage = null) }
    }
}

internal fun validateCheckoutProfile(form: CartBillingForm): String? = when {
    form.firstName.isBlank() -> "Please enter First Name"
    form.lastName.isBlank() -> "Please enter Last Name"
    parseCheckoutCurrency(form.currency) != CheckoutCurrency.JMD -> "Please select Payment Currency"
    form.address1.isBlank() -> "Please enter Address line 1"
    form.state.isBlank() -> "Please select State"
    form.city.isBlank() -> "Please select City"
    form.country.isBlank() -> "Please select Country"
    CountryCatalog.entry(form.country)?.isoCode == "US" && form.postal.isBlank() -> "Please enter ZIP Code"
    else -> null
}

/** Reject non-HTTPS, hostless, credential-bearing, or malformed launch URLs. */
internal fun validatedHostedCheckoutUrl(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
    if (uri.userInfo != null || uri.fragment != null) return null
    return uri.toASCIIString()
}
