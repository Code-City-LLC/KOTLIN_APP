package com.ga.airdrop.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.navigation.Routes
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.AuthenticatedOwnerChange
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.changeTo
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.DeliveryLocation
import com.ga.airdrop.data.model.DeliveryWarehouse
import com.ga.airdrop.data.model.isPickupCounter
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.data.repo.DeliveryGateway
import com.ga.airdrop.data.repo.DeliveryRepository
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.cart.CheckoutCurrency
import com.ga.airdrop.feature.cart.CheckoutFlow
import com.ga.airdrop.feature.cart.CheckoutFlowStore
import com.ga.airdrop.feature.cart.CheckoutNextRoute
import com.ga.airdrop.feature.cart.CheckoutPhase
import com.ga.airdrop.feature.cart.checkoutNextRoute
import com.ga.airdrop.feature.cart.parseCheckoutCurrency
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Delivery Method — behavior from FigmaDeliveryMethodViewController (Swift,
 * 2046 lines) per docs/PARITY_GAP_SPECS.md §3. Cart "Make Payment" now lands
 * here; the user picks Pickup (warehouse radio list) or Delivery (search /
 * map / validate), the preference is saved via POST /delivery/save-preference,
 * then the currency popup appears.
 *
 * Currency owns routing, never payment dispatch: JMD → Profile Information →
 * Order Summary; USD → Order Summary. Order Summary alone owns the terminal
 * rail decision.
 */
class DeliveryMethodViewModel(
    private val repo: DeliveryGateway = DeliveryRepository(ApiClient.service),
    private val sessionBoundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryUiState())
    val state: StateFlow<DeliveryUiState> = _state

    /** Debounced live-search Job — cancel+relaunch per keystroke (Swift searchDebounceWork). */
    private var searchJob: Job? = null

    /** Cancellable reverse-geocode chain (Swift reverseGeocodeTask). */
    private var reverseGeocodeJob: Job? = null

    /** In-flight validate-location call — last marker move wins. */
    private var validateJob: Job? = null
    private var settingsJob: Job? = null
    private var preferenceJob: Job? = null
    private var saveJob: Job? = null

    /** Swift latestSearchQuery — stale-response guard for search results. */
    private var latestQuery: String = ""
    private var sessionOwner: AuthenticatedSessionOwner? = sessionBoundary.capture()

    init {
        viewModelScope.launch {
            sessionBoundary.changes.collect { changed ->
                when (sessionOwner.changeTo(changed)) {
                    AuthenticatedOwnerChange.Unchanged -> return@collect
                    AuthenticatedOwnerChange.IdentityUpdated -> {
                        sessionOwner = changed
                        return@collect
                    }
                    AuthenticatedOwnerChange.SessionReplaced -> Unit
                }
                searchJob?.cancel()
                reverseGeocodeJob?.cancel()
                validateJob?.cancel()
                settingsJob?.cancel()
                preferenceJob?.cancel()
                saveJob?.cancel()
                latestQuery = ""
                sessionOwner = changed
                _state.value = DeliveryUiState(
                    errorTitle = if (changed == null) "Sign in required" else null,
                    errorMessage = if (changed == null) "Log in before continuing checkout." else null,
                )
                if (changed != null) {
                    loadSettings()
                    loadPreference()
                }
            }
        }
        loadSettings()
        loadPreference()
    }

    /** GET /delivery/settings; on failure fall back to the 4 Swift hard-coded warehouses. */
    fun loadSettings() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            // Pickup ruling (Kemar 2026-07-19): the PICKUP list shows only the
            // three counters. Delivery is untouched — validation/routing use
            // every active warehouse server-side.
            val warehouses = repo.deliverySettings()
                .map { it.settings?.warehouses.orEmpty() }
                .getOrElse { fallbackWarehouses() }
                .filter { it.supportsPickup != false && isPickupCounter(it.name) }
            _state.update {
                it.copy(
                    warehouses = warehouses,
                    selectedWarehouseId = resolveSelectedWarehouseId(
                        warehouses = warehouses,
                        currentSelectedId = it.selectedWarehouseId,
                        pickupLabel = it.pickupLabel,
                    ),
                )
            }
        }
    }

    /** GET /delivery/preference — pre-select mode / warehouse / re-hydrate delivery coord. */
    fun loadPreference() {
        val owner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId } ?: return
        val requestOwner = sessionBoundary.requestOwner(owner) ?: return
        preferenceJob?.cancel()
        preferenceJob = viewModelScope.launch {
            repo.preference(requestOwner.provenance).onSuccess { pref ->
                sessionBoundary.apply(owner) {
                    _state.update { s ->
                    val pickup = pref.pickupLocation?.takeIf { it.isNotBlank() } ?: s.pickupLabel
                    val loc = pref.deliveryLocation
                    val lat = loc?.latitude
                    val lng = loc?.longitude
                    val address = loc?.formattedAddress ?: loc?.address
                    val coord = if (lat != null && lng != null) lat to lng else null
                    s.copy(
                        mode = deliveryModeFromWire(pref.deliveryMode) ?: s.mode,
                        pickupLabel = pickup,
                        selectedWarehouseId = resolveSelectedWarehouseId(
                            warehouses = s.warehouses,
                            currentSelectedId = s.selectedWarehouseId,
                            pickupLabel = pickup,
                        ),
                        validatedAddress = if (coord != null) address else s.validatedAddress,
                        searchQuery = if (coord != null) address.orEmpty() else s.searchQuery,
                        markerCoord = coord ?: s.markerCoord,
                        mapCenter = coord ?: s.mapCenter,
                    )
                    }
                }
            }
            // Failure: screen starts in default state (Swift audit D-1 parity).
        }
    }

    fun onModeSelected(mode: DeliveryMode) {
        if (mode == DeliveryMode.Pickup) {
            searchJob?.cancel()
            reverseGeocodeJob?.cancel()
            validateJob?.cancel()
            latestQuery = ""
            _state.update {
                it.copy(
                    mode = mode,
                    searchQuery = "",
                    searchResults = emptyList(),
                    markerCoord = null,
                    validatedAddress = null,
                    validatedDistanceKm = null,
                    validatedFee = null,
                    validatedFeeCurrency = null,
                    ctaState = DeliveryCtaState.Idle,
                )
            }
        } else {
            _state.update { it.copy(mode = mode) }
        }
    }

    fun onWarehouseSelected(warehouse: DeliveryWarehouse) {
        _state.update {
            it.copy(
                selectedWarehouseId = warehouse.id,
                pickupLabel = warehouse.name,
            )
        }
    }

    /**
     * Swift onSearchEditingChanged → searchPlaces: 350ms debounce, min 2
     * chars; editing invalidates the validated coordinate so Continue forces
     * a fresh validate.
     */
    fun onSearchQueryChange(query: String) {
        validateJob?.cancel()
        reverseGeocodeJob?.cancel()
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchQuery = query,
                markerCoord = null,
                validatedAddress = null,
                validatedDistanceKm = null,
                validatedFee = null,
                validatedFeeCurrency = null,
                // Cancelling an in-flight validate skips BOTH result arms
                // (apiResult rethrows CancellationException), which would
                // wedge a Validating CTA forever — Swift never cancels
                // validate and unlocks Continue on every path.
                ctaState = if (it.ctaState == DeliveryCtaState.Validating ||
                    it.ctaState == DeliveryCtaState.LookingUp
                ) {
                    DeliveryCtaState.Idle
                } else {
                    it.ctaState
                },
            )
        }
        val trimmed = query.trim()
        // Swift sets latestSearchQuery EAGERLY per keystroke and resets it for
        // <2 chars — that's what lets onSubmitSearch's stale guard discard a
        // lookup response that lands after further edits.
        latestQuery = if (trimmed.length < SEARCH_MIN_CHARS) "" else trimmed
        if (trimmed.length < SEARCH_MIN_CHARS) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            repo.searchPlaces(trimmed).onSuccess { results ->
                // Stale guard (Swift does BOTH checks): only apply when this
                // is still the latest request AND the field text is unchanged.
                if (_state.value.mode == DeliveryMode.Delivery &&
                    shouldApplySearchResults(latestQuery, trimmed, _state.value.searchQuery)
                ) {
                    _state.update { it.copy(searchResults = results) }
                }
            }
            // Live-search failures are silent (Swift parity) — the user just
            // keeps typing or submits for the full lookup path.
        }
    }

    /** Dropdown row tapped — drop the marker and validate (Swift didSelectRowAt). */
    fun onSearchResultPicked(place: PlaceResult) {
        val lat = place.latitude
        val lng = place.longitude
        if (lat == null || lng == null) {
            _state.update {
                it.copy(
                    errorTitle = "Address not found",
                    errorMessage = "We couldn't find that address. Try a more specific town or parish.",
                )
            }
            return
        }
        searchJob?.cancel()
        reverseGeocodeJob?.cancel()
        _state.update {
            it.copy(
                searchResults = emptyList(),
                searchQuery = place.address ?: it.searchQuery,
                validatedAddress = place.address,
                markerCoord = lat to lng,
                mapCenter = lat to lng,
            )
        }
        validate(lat, lng, place.address)
    }

    /**
     * Return-key / Continue-with-typed-text path — Swift
     * lookupTypedAddressAndValidate: auto-pick `results.first`, else the
     * "Address not found" alert.
     */
    fun onSubmitSearch() {
        val trimmed = _state.value.searchQuery.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        latestQuery = trimmed
        _state.update { it.copy(searchResults = emptyList(), ctaState = DeliveryCtaState.LookingUp) }
        viewModelScope.launch {
            repo.searchPlaces(trimmed)
                .onSuccess { results ->
                    if (latestQuery != trimmed) {
                        _state.update { it.copy(ctaState = DeliveryCtaState.Idle) }
                        return@onSuccess
                    }
                    val pick = results.firstOrNull()
                    val lat = pick?.latitude
                    val lng = pick?.longitude
                    if (pick == null || lat == null || lng == null) {
                        _state.update {
                            it.copy(
                                ctaState = DeliveryCtaState.Idle,
                                errorTitle = "Address not found",
                                errorMessage = "We couldn't find that address. Try a more specific town or parish.",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                ctaState = DeliveryCtaState.Idle,
                                searchQuery = pick.address ?: it.searchQuery,
                                validatedAddress = pick.address,
                                markerCoord = lat to lng,
                                mapCenter = lat to lng,
                            )
                        }
                        validate(lat, lng, pick.address)
                    }
                }
                .onFailure { err ->
                    if (latestQuery != trimmed) {
                        // Stale failure must still release the CTA.
                        _state.update { it.copy(ctaState = DeliveryCtaState.Idle) }
                        return@onFailure
                    }
                    _state.update {
                        it.copy(
                            ctaState = DeliveryCtaState.Idle,
                            errorTitle = "Couldn't look up address",
                            errorMessage = err.toUserMessage(),
                        )
                    }
                }
        }
    }

    /**
     * Map tap / marker drag-end (Swift handleMarkerMoved): validate the point
     * immediately with a coordinate fallback label, and run a CANCELLABLE
     * reverse-geocode that commits the resolved address only if the marker
     * hasn't moved since this chain started (coord-equality guard).
     */
    fun onMapPointPicked(latitude: Double, longitude: Double) {
        val coord = latitude to longitude
        val fallback = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
        searchJob?.cancel()
        _state.update {
            it.copy(
                searchResults = emptyList(),
                searchQuery = fallback,
                markerCoord = coord,
                mapCenter = coord,
                validatedAddress = null,
                validatedDistanceKm = null,
                validatedFee = null,
                validatedFeeCurrency = null,
            )
        }
        validate(latitude, longitude, fallback)

        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = viewModelScope.launch {
            val address = resolveGeocodedAddress(repo, deviceGeocoder, latitude, longitude)
                ?: return@launch
            // PR47 lineage: cancellation is cooperative, so coordinate ownership
            // is checked again immediately before committing the place name.
            if (geocodeCommitAllowed(_state.value.markerCoord, coord)) {
                _state.update { it.copy(searchQuery = address, validatedAddress = address) }
            }
        }
    }

    /** Android service fallback supplied by the screen; Laravel remains first. */
    var deviceGeocoder: (suspend (Double, Double) -> String?)? = null

    /**
     * "Use Current Location". The SCREEN owns permission + the location
     * provider (VM stays Android-free); null means unavailable/denied/no-fix.
     */
    fun onUseCurrentLocation(latLng: Pair<Double, Double>?) {
        if (latLng == null) {
            _state.update {
                it.copy(
                    errorTitle = "Location unavailable",
                    errorMessage = "Couldn't get your current location. Enable Location Services " +
                        "for Airdrop in Settings, then try again.",
                )
            }
            return
        }
        onMapPointPicked(latLng.first, latLng.second)
    }

    /** POST /delivery/validate-location with the frozen checkout weight. */
    private fun validate(latitude: Double, longitude: Double, address: String?) {
        validateJob?.cancel()
        val owner = currentOwner() ?: return
        val requestOwner = sessionBoundary.requestOwner(owner) ?: run {
            showCheckoutContextError()
            return
        }
        val flow = CheckoutFlowStore.current(owner) ?: run {
            showCheckoutContextError()
            return
        }
        val flowId = flow.id
        val frozenWeightKg = flow.totalWeightKg
        _state.update { it.copy(ctaState = DeliveryCtaState.Validating) }
        validateJob = viewModelScope.launch {
            repo.validateLocation(
                latitude,
                longitude,
                address,
                totalWeightKg = frozenWeightKg,
                expectedSession = requestOwner.provenance,
            )
                .onSuccess { result ->
                    applyCurrentFlow(owner, flowId) {
                        if (result.valid == true) {
                            _state.update {
                                it.copy(
                                    ctaState = DeliveryCtaState.Idle,
                                    validatedAddress = address ?: it.validatedAddress,
                                    validatedDistanceKm = result.distanceKm,
                                    validatedFee = result.deliveryFee,
                                    validatedFeeCurrency = result.feeCurrency,
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    ctaState = DeliveryCtaState.Idle,
                                    markerCoord = null,
                                    validatedAddress = null,
                                    validatedDistanceKm = null,
                                    validatedFee = null,
                                    validatedFeeCurrency = null,
                                    errorTitle = "Delivery not available",
                                    errorMessage = result.reason
                                        ?: "This location can't be served by our warehouses. " +
                                            "Please choose a pickup point.",
                                )
                            }
                        }
                    }
                }
                .onFailure { err ->
                    applyCurrentFlow(owner, flowId) {
                        _state.update {
                            it.copy(
                                ctaState = DeliveryCtaState.Idle,
                                markerCoord = null,
                                validatedAddress = null,
                                validatedDistanceKm = null,
                                validatedFee = null,
                                validatedFeeCurrency = null,
                                errorTitle = "Couldn't validate location",
                                errorMessage = err.toUserMessage(),
                            )
                        }
                    }
                }
        }
    }

    /** "Choose Currency" CTA — Swift onContinue gating. */
    fun onContinue() {
        val s = _state.value
        if (s.ctaState != DeliveryCtaState.Idle) return
        when (
            val decision = decideContinue(
                mode = s.mode,
                selectedWarehouseId = s.selectedWarehouseId,
                warehouses = s.warehouses,
                pickupLabel = s.pickupLabel,
                markerCoord = s.markerCoord,
                validatedAddress = s.validatedAddress,
                searchQuery = s.searchQuery,
            )
        ) {
            is ContinueDecision.SavePickup ->
                savePreferenceAndProceed(DeliveryMode.Pickup, null, decision.label)
            is ContinueDecision.SaveDelivery ->
                savePreferenceAndProceed(
                    DeliveryMode.Delivery,
                    decision.latitude to decision.longitude,
                    decision.address,
                )
            ContinueDecision.RunSearch -> onSubmitSearch()
            is ContinueDecision.ShowError -> _state.update {
                it.copy(errorTitle = decision.title, errorMessage = decision.message)
            }
        }
    }

    /** POST /delivery/save-preference → currency popup (Swift savePreferenceAndProceed). */
    private fun savePreferenceAndProceed(
        mode: DeliveryMode,
        coord: Pair<Double, Double>?,
        address: String?,
    ) {
        val owner = currentOwner() ?: return
        val flow = CheckoutFlowStore.current(owner) ?: run {
            showCheckoutContextError()
            return
        }
        val flowId = flow.id
        val requestOwner = sessionBoundary.requestOwner(owner) ?: run {
            showCheckoutContextError()
            return
        }
        val frozenFee = if (mode == DeliveryMode.Delivery) _state.value.validatedFee else null
        val frozenFeeCurrency = if (mode == DeliveryMode.Delivery) {
            _state.value.validatedFeeCurrency
        } else {
            null
        }
        _state.update { it.copy(ctaState = DeliveryCtaState.Saving) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val result = when (mode) {
                DeliveryMode.Pickup -> repo.savePickupPreference(
                    address,
                    flow.totalWeightKg,
                    requestOwner.provenance,
                )
                DeliveryMode.Delivery -> repo.saveDeliveryPreference(
                    DeliveryLocation(
                        address = address,
                        latitude = coord?.first,
                        longitude = coord?.second,
                        formattedAddress = address,
                    ),
                    totalWeightKg = flow.totalWeightKg,
                    expectedSession = requestOwner.provenance,
                )
            }
            result
                .onSuccess {
                    applyCurrentFlow(owner, flowId) {
                        val updated = CheckoutFlowStore.update(owner, expectedFlowId = flowId) { current ->
                            current.copy(
                                deliveryMode = mode.wire,
                                deliveryAddress = if (mode == DeliveryMode.Delivery) address else null,
                                deliveryLatitude = if (mode == DeliveryMode.Delivery) coord?.first else null,
                                deliveryLongitude = if (mode == DeliveryMode.Delivery) coord?.second else null,
                                pickupLocation = if (mode == DeliveryMode.Pickup) address else null,
                                deliveryFee = frozenFee,
                                deliveryFeeCurrency = frozenFeeCurrency,
                            )
                        }
                        if (updated == null) {
                            showCheckoutContextError()
                        } else {
                            _state.update {
                                it.copy(ctaState = DeliveryCtaState.Idle, showCurrencyPopup = true)
                            }
                        }
                    }
                }
                .onFailure { err ->
                    applyCurrentFlow(owner, flowId) {
                        _state.update {
                            it.copy(
                                ctaState = DeliveryCtaState.Idle,
                                errorTitle = "Couldn't save preference",
                                errorMessage = err.toUserMessage(),
                            )
                        }
                    }
                }
        }
    }

    /**
     * Currency picked. Persist the exact rail choice into the owned checkout
     * and route to its next Swift screen; no payment request occurs here.
     */
    fun onCurrencyChosen(currency: String) {
        _state.update { it.copy(showCurrencyPopup = false) }
        if (_state.value.ctaState != DeliveryCtaState.Idle) return
        val selected = parseCheckoutCurrency(currency)
        val next = checkoutNextRoute(currency)
        if (selected == null || next == null) {
            _state.update {
                it.copy(
                    errorTitle = "Choose currency",
                    errorMessage = "Select USD or JMD before continuing.",
                )
            }
            return
        }
        val owner = currentOwner() ?: return
        val flow = CheckoutFlowStore.current(owner) ?: run {
            showCheckoutContextError()
            return
        }
        val phase = when (next) {
            CheckoutNextRoute.PROFILE_INFORMATION -> CheckoutPhase.PROFILE_INFORMATION
            CheckoutNextRoute.ORDER_SUMMARY -> CheckoutPhase.ORDER_SUMMARY
        }
        val committed = sessionBoundary.runWhileCurrent(owner) {
            val deliveryFlow = if (flow.phase == CheckoutPhase.DELIVERY) {
                CheckoutFlowStore.current(owner)?.takeIf { it.id == flow.id }
            } else {
                CheckoutFlowStore.rewindToDelivery(owner, flow.id)
            } ?: return@runWhileCurrent false
            val currentLines = CartStore.items.value
            val byKey = currentLines.associateBy(CartStore.CartLine::key)
            val capturedLines = deliveryFlow.cartKeys.mapNotNull(byKey::get)
            if (capturedLines.size != deliveryFlow.cartKeys.size ||
                capturedLines.any { !it.isCheckoutEligible() } ||
                CartStore.hasPendingPackageMutations(deliveryFlow.cartKeys) ||
                capturedLines.mapNotNull(CartStore.CartLine::packageId) != deliveryFlow.packageIds
            ) {
                _state.update {
                    it.copy(
                        errorTitle = "Checkout unavailable",
                        errorMessage = "One or more products are unavailable for sale checkout.",
                    )
                }
                return@runWhileCurrent false
            }
            val updated = CheckoutFlowStore.update(
                owner,
                expectedFlowId = deliveryFlow.id,
            ) { current ->
                current.copy(currency = selected.wireValue, phase = phase)
            } ?: return@runWhileCurrent false
            _state.update {
                it.copy(
                    ctaState = DeliveryCtaState.Idle,
                    navTarget = if (selected == CheckoutCurrency.JMD) {
                        Routes.PROFILE_INFORMATION
                    } else {
                        Routes.ORDER_SUMMARY
                    },
                )
            }
            updated.id == deliveryFlow.id
        }
        if (!committed) {
            if (_state.value.errorTitle == "Checkout unavailable") return
            showCheckoutContextError()
        }
    }

    fun dismissCurrencyPopup() {
        _state.update { it.copy(showCurrencyPopup = false) }
    }

    fun consumeNav() {
        _state.update { it.copy(navTarget = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorTitle = null, errorMessage = null) }
    }

    private fun currentOwner(): AuthenticatedSessionOwner? {
        val owner = sessionBoundary.capture()?.takeIf { it.sessionId == sessionOwner?.sessionId }
        if (owner == null) {
            _state.update {
                it.copy(
                    ctaState = DeliveryCtaState.Idle,
                    errorTitle = "Sign in required",
                    errorMessage = "Log in before continuing checkout.",
                )
            }
        }
        return owner
    }

    private fun showCheckoutContextError() {
        _state.update {
            it.copy(
                ctaState = DeliveryCtaState.Idle,
                errorTitle = "Checkout unavailable",
                errorMessage = "Your checkout session changed. Return to the cart and try again.",
            )
        }
    }

    /** Commit async results only while both auth owner and checkout id match. */
    private fun applyCurrentFlow(
        owner: AuthenticatedSessionOwner,
        flowId: String,
        action: () -> Unit,
    ): Boolean = sessionBoundary.runWhileCurrent(owner) {
        if (CheckoutFlowStore.current(owner)?.id != flowId) return@runWhileCurrent false
        action()
        true
    }
}

/* ─── State ─────────────────────────────────────────────────────────────── */

enum class DeliveryMode(val wire: String) {
    Pickup("pickup"),
    Delivery("delivery"),
}

internal fun deliveryModeFromWire(raw: String?): DeliveryMode? =
    DeliveryMode.values().firstOrNull { it.wire == raw?.lowercase(Locale.US) }

/** Swift lockContinue labels: "Looking up…" / "Validating…" / "Saving…". */
enum class DeliveryCtaState { Idle, LookingUp, Validating, Saving, CheckingOut }

data class DeliveryUiState(
    val mode: DeliveryMode = DeliveryMode.Pickup,
    val warehouses: List<DeliveryWarehouse> = emptyList(),
    val selectedWarehouseId: Int? = null,
    val pickupLabel: String? = null,
    val searchQuery: String = "",
    val searchResults: List<PlaceResult> = emptyList(),
    /** Swift default map region — Jamaica (18.1096, -77.2975). */
    val mapCenter: Pair<Double, Double>? = JAMAICA_CENTER,
    val markerCoord: Pair<Double, Double>? = null,
    val validatedAddress: String? = null,
    val validatedDistanceKm: Double? = null,
    val validatedFee: Double? = null,
    val validatedFeeCurrency: String? = null,
    val ctaState: DeliveryCtaState = DeliveryCtaState.Idle,
    val showCurrencyPopup: Boolean = false,
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    /**
     * One-shot route to push after currency choice. UNUSED in Phase-1: the
     * currency branch runs Stripe checkout directly (see class KDoc) —
     * restore JMD→Profile / USD→Order Summary when those screens land.
     */
    val navTarget: String? = null,
)

/* ─── Pure logic (plain-JVM testable — DeliveryMethodLogicTest) ─────────── */

internal const val SEARCH_DEBOUNCE_MS = 350L
internal const val SEARCH_MIN_CHARS = 2

/** Swift default MKCoordinateRegion center. */
internal val JAMAICA_CENTER: Pair<Double, Double> = 18.1096 to -77.2975

/**
 * Last-ditch fallback for when `/api/v1/delivery/settings` is unreachable —
 * EXACT port of Swift fallbackWarehouses() (the four warehouses in the Figma
 * frame).
 */
internal fun fallbackWarehouses(): List<DeliveryWarehouse> = listOf(
    DeliveryWarehouse(
        id = 1,
        name = "Kingston",
        address = "Unit 19 Pristine Plaza, 15 Eastwood Park Rd, Kingston, Jamaica",
        latitude = 18.012,
        longitude = -76.793,
        isPrimary = true,
    ),
    DeliveryWarehouse(
        id = 2,
        name = "Montego Bay",
        address = "Unit 14, The Annex Fairview Shopping Center, Montego Bay, Jamaica",
        latitude = 18.470,
        longitude = -77.918,
        isPrimary = false,
    ),
    DeliveryWarehouse(
        id = 3,
        name = "Savanna-La-Mar",
        address = "33 Beckford St, Savanna la Mar, Jamaica",
        latitude = 18.219,
        longitude = -78.135,
        isPrimary = false,
    ),
    DeliveryWarehouse(
        id = 4,
        name = "Yallas",
        address = "VCJ5+XMH, Poor Mans Corner, Jamaica",
        latitude = 17.881,
        longitude = -76.564,
        isPrimary = false,
    ),
)

/**
 * Swift renderPickupList auto-selection: default to the `isPrimary` (or
 * first) warehouse, then let a saved pickup label override by name
 * (case-insensitive).
 */
internal fun resolveSelectedWarehouseId(
    warehouses: List<DeliveryWarehouse>,
    currentSelectedId: Int?,
    pickupLabel: String?,
): Int? {
    var selected = currentSelectedId
        ?: (warehouses.firstOrNull { it.isPrimary == true } ?: warehouses.firstOrNull())?.id
    if (!pickupLabel.isNullOrBlank()) {
        warehouses.firstOrNull { (it.name ?: "").equals(pickupLabel, ignoreCase = true) }
            ?.let { selected = it.id }
    }
    return selected
}

/**
 * Swift stale-response guard (searchPlaces): apply results only when this is
 * still the latest request AND the current field text is unchanged.
 */
internal fun shouldApplySearchResults(
    latestQuery: String,
    requestQuery: String,
    currentFieldText: String,
): Boolean = latestQuery == requestQuery && currentFieldText.trim() == requestQuery

/** PR47 recovery: Laravel address first, then the platform geocoder. */
internal suspend fun resolveGeocodedAddress(
    gateway: DeliveryGateway,
    deviceGeocoder: (suspend (Double, Double) -> String?)?,
    latitude: Double,
    longitude: Double,
): String? = gateway.reverseGeocode(latitude, longitude).getOrNull()?.address
    ?.takeIf { it.isNotBlank() }
    ?: deviceGeocoder?.invoke(latitude, longitude)?.takeIf { it.isNotBlank() }

internal fun geocodeCommitAllowed(
    currentMarker: Pair<Double, Double>?,
    requestedMarker: Pair<Double, Double>,
): Boolean = currentMarker == requestedMarker

/** What tapping the CTA should do — Swift onContinue, decision extracted pure. */
internal sealed interface ContinueDecision {
    data class SavePickup(val label: String?) : ContinueDecision
    data class SaveDelivery(
        val latitude: Double,
        val longitude: Double,
        val address: String?,
    ) : ContinueDecision

    object RunSearch : ContinueDecision
    data class ShowError(val title: String, val message: String) : ContinueDecision
}

internal fun decideContinue(
    mode: DeliveryMode,
    selectedWarehouseId: Int?,
    warehouses: List<DeliveryWarehouse>,
    pickupLabel: String?,
    markerCoord: Pair<Double, Double>?,
    validatedAddress: String?,
    searchQuery: String,
): ContinueDecision = when (mode) {
    DeliveryMode.Pickup -> {
        if (selectedWarehouseId == null) {
            ContinueDecision.ShowError(
                title = "Pick a location",
                message = "Please select a pickup warehouse to continue.",
            )
        } else {
            val label = warehouses.firstOrNull { it.id == selectedWarehouseId }?.name ?: pickupLabel
            ContinueDecision.SavePickup(label)
        }
    }

    DeliveryMode.Delivery -> when {
        markerCoord != null -> ContinueDecision.SaveDelivery(
            latitude = markerCoord.first,
            longitude = markerCoord.second,
            address = validatedAddress ?: searchQuery.trim().takeIf { it.isNotEmpty() },
        )

        searchQuery.trim().isNotEmpty() -> ContinueDecision.RunSearch

        else -> ContinueDecision.ShowError(
            title = "Enter a delivery address",
            message = "Type a town or parish to look up your delivery address, " +
                "or tap Use Current Location.",
        )
    }
}
