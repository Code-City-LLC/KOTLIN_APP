package com.ga.airdrop.feature.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.DeliveryLocation
import com.ga.airdrop.data.model.DeliveryWarehouse
import com.ga.airdrop.data.model.PlaceResult
import com.ga.airdrop.data.repo.DeliveryGateway
import com.ga.airdrop.data.repo.DeliveryRepository
import com.ga.airdrop.feature.cart.CartStore
import com.ga.airdrop.feature.shop.ShopCheckoutRepository
import com.ga.airdrop.feature.shop.ShopRepoProvider
import com.ga.airdrop.feature.shop.isUnauthenticatedCheckoutFailure
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Delivery Method — behavior from FigmaDeliveryMethodViewController (Swift,
 * 2046 lines) per docs/PARITY_GAP_SPECS.md §3. Cart "Make Payment" now lands
 * here; the user picks Pickup (warehouse radio list) or Delivery (search /
 * map / validate), the preference is saved via POST /delivery/save-preference,
 * then the currency popup appears.
 *
 * CURRENCY-BRANCH DEVIATION (spec §4): Swift routes JMD → Profile Information
 * (FigmaBillingDetailsViewController) and USD → Order Summary
 * (FigmaCartViewController mode:.orderSummary). Kotlin has NO Profile-Info /
 * Order-Summary screens — the Kotlin cart pays via Stripe hosted checkout
 * directly. So [onCurrencyChosen] runs the cart's existing Stripe
 * createCheckout(packageIds, chosenCurrency, isAuction=true) with the SAME
 * guards + error copy as CartViewModel.pay(), and exposes the resulting
 * [DeliveryUiState.checkoutUrl] one-shot for the screen to open in a Custom
 * Tab. restore JMD→Profile / USD→Order Summary when those screens land.
 */
class DeliveryMethodViewModel(
    private val repo: DeliveryGateway = DeliveryRepository(ApiClient.service),
    private val checkout: ShopCheckoutRepository = ShopRepoProvider.checkout,
) : ViewModel() {

    private val _state = MutableStateFlow(DeliveryUiState())
    val state: StateFlow<DeliveryUiState> = _state

    /** Debounced live-search Job — cancel+relaunch per keystroke (Swift searchDebounceWork). */
    private var searchJob: Job? = null

    /** Cancellable reverse-geocode chain (Swift reverseGeocodeTask). */
    private var reverseGeocodeJob: Job? = null

    /** In-flight validate-location call — last marker move wins. */
    private var validateJob: Job? = null

    /** Swift latestSearchQuery — stale-response guard for search results. */
    private var latestQuery: String = ""

    init {
        loadSettings()
        loadPreference()
    }

    /** GET /delivery/settings; on failure fall back to the 4 Swift hard-coded warehouses. */
    fun loadSettings() {
        viewModelScope.launch {
            val warehouses = repo.deliverySettings()
                .map { it.settings?.warehouses.orEmpty() }
                .getOrElse { fallbackWarehouses() }
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
        viewModelScope.launch {
            repo.preference().onSuccess { pref ->
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
            // Failure: screen starts in default state (Swift audit D-1 parity).
        }
    }

    fun onModeSelected(mode: DeliveryMode) {
        if (mode == DeliveryMode.Pickup) {
            searchJob?.cancel()
            latestQuery = ""
            _state.update { it.copy(mode = mode, searchResults = emptyList()) }
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

    /** POST /delivery/validate-location — total_weight_kg=null (CartLine has no weight). */
    private fun validate(latitude: Double, longitude: Double, address: String?) {
        validateJob?.cancel()
        _state.update { it.copy(ctaState = DeliveryCtaState.Validating) }
        validateJob = viewModelScope.launch {
            repo.validateLocation(latitude, longitude, address, totalWeightKg = null)
                .onSuccess { result ->
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
                .onFailure { err ->
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
        _state.update { it.copy(ctaState = DeliveryCtaState.Saving) }
        viewModelScope.launch {
            val result = when (mode) {
                DeliveryMode.Pickup -> repo.savePickupPreference(address)
                DeliveryMode.Delivery -> repo.saveDeliveryPreference(
                    DeliveryLocation(
                        address = address,
                        latitude = coord?.first,
                        longitude = coord?.second,
                        formattedAddress = address,
                    ),
                )
            }
            result
                .onSuccess {
                    _state.update {
                        it.copy(ctaState = DeliveryCtaState.Idle, showCurrencyPopup = true)
                    }
                }
                .onFailure { err ->
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

    /**
     * Currency picked. See the class KDoc for the currency-branch deviation:
     * instead of pushing Profile Information / Order Summary (screens Kotlin
     * doesn't have yet) this front-runs the cart's terminal Stripe step.
     * Guards + error copy mirror CartViewModel.pay() EXACTLY.
     */
    fun onCurrencyChosen(currency: String) {
        _state.update { it.copy(showCurrencyPopup = false) }
        if (_state.value.ctaState != DeliveryCtaState.Idle) return
        val lines = CartStore.items.value
        if (lines.isEmpty()) {
            _state.update {
                it.copy(
                    errorTitle = "Cart empty",
                    errorMessage = "Add at least one item before checkout.",
                )
            }
            return
        }
        val packageIds = lines.mapNotNull { it.packageId }
        if (packageIds.size != lines.size) {
            _state.update {
                it.copy(
                    errorTitle = "Checkout unavailable",
                    errorMessage = "One or more products are missing the package ID required " +
                        "for auction checkout.",
                )
            }
            return
        }
        // Honest auction flag from the cart contents (Swift parity); the
        // server also derives it authoritatively.
        val cartIsAuction = lines.any { it.isAuction }
        viewModelScope.launch {
            _state.update { it.copy(ctaState = DeliveryCtaState.CheckingOut) }
            // RECONCILE: POST /payments/create-checkout
            // { package_ids, currency: chosen, is_auction } → data.checkout_url.
            checkout.createCheckout(packageIds, currency = currency.uppercase(Locale.US), isAuction = cartIsAuction)
                .onSuccess { url ->
                    _state.update { it.copy(ctaState = DeliveryCtaState.Idle, checkoutUrl = url) }
                }
                .onFailure { err ->
                    val unauthenticated = err.isUnauthenticatedCheckoutFailure()
                    _state.update {
                        it.copy(
                            ctaState = DeliveryCtaState.Idle,
                            errorTitle = if (unauthenticated) {
                                "Sign in required"
                            } else {
                                "Checkout failed"
                            },
                            errorMessage = if (unauthenticated) {
                                "Log in to your Airdropja account before checking out."
                            } else {
                                err.toUserMessage().ifBlank { "Stripe did not return a valid checkout URL." }
                            },
                        )
                    }
                }
        }
    }

    fun dismissCurrencyPopup() {
        _state.update { it.copy(showCurrencyPopup = false) }
    }

    /** The hosted checkout tab just opened — clear the one-shot URL (CartViewModel parity). */
    fun onCheckoutOpened() {
        _state.update { it.copy(checkoutUrl = null) }
    }

    fun consumeNav() {
        _state.update { it.copy(navTarget = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorTitle = null, errorMessage = null) }
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
    /** Stripe hosted checkout URL to open in a Custom Tab (one-shot). */
    val checkoutUrl: String? = null,
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
