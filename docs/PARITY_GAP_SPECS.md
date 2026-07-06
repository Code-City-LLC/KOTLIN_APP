# KOTLIN_APP — Swift→Kotlin HIGH Parity-Gap Implementation Specs

**Author:** BlueDeer (verification lane) · 2026-07-06 · from parity audit ORC #15525 (lead-dev-confirmed #15554).
**Scope:** the two HIGH mainline-missing gaps from ledger §1c. Both live in the cart→pay flow, under Kemar's **checkout HOLD** — do NOT start building until Kemar clears the HOLD. These specs are the blueprint so implementation is unblocked the moment it lands. Swift = source of truth; every reference is `git -C SWIFT_APP show origin/main:<path>`.

> Verifier note: I authored these from a read-only Swift analysis + Kotlin integration mapping. I did not implement them. When built, I device+code-verify vs Swift.

---

## Delivery Method  (effort: XL)

# Delivery Method — Kotlin Implementation Spec

Swift source of truth: `Airdrop/FigmaDeliveryMethodViewController.swift` (2046 lines) + `Airdrop/DeliveryDefaultsStore.swift` + currency popup in `Airdrop/FigmaCartViewController.swift:2773`.

Effort: **XL** (new screen + ViewModel + interactive map dependency + cart-flow re-wire).

## 0. What already exists (reuse, do not recreate)
The entire delivery **data layer is ported and orphaned** (verified: `git grep DeliveryRepository` finds only its own definition — zero consumers).
- `data/model/Delivery.kt` — all request/response models (Flexible* serializers).
- `data/repo/DeliveryRepository.kt` — `deliverySettings()`, `validateLocation()`, `savePickupPreference()`, `saveDeliveryPreference()`, `preference()`, `reverseGeocode()`, `searchPlaces()`. Construct via `DeliveryRepository(ApiClient.service)`.
- `data/api/AirdropApiService.kt:~388-410` — all 6 endpoints.

Conventions to mirror: `feature/shop/ShopNav.kt` (nav graph), `feature/cart/CartViewModel.kt` (MVVM StateFlow + one-shot nav fields), `core/designsystem/components/Buttons.kt#GradientButton`, `feature/shop/ShopComponents.kt#ShopInnerHeader`, `AirdropTheme.colors` / `AirdropType`.

## 1. Flow (verified against Swift)
```
My Cart ─"Make Payment"→ Delivery Method ─"Choose Currency"→ [Currency popup]
  ├ USD → Order Summary (final Make Payment)
  └ JMD → Profile Information → Order Summary
```
Precondition: cart has ≥1 Ready-for-Pickup package (packageIds present). No QA/deep-link entry.

## 2. Screen anatomy (top → bottom)
- Header `ShopInnerHeader("Delivery Method", onBack)`.
- Question: title2 "How would you like to receive your packages?".
- **Two tiles (104dp)**: Pickup "Collect from warehouse" / Delivery "Deliver to your location". Selected = peach `#F1855C` 1.5dp border + orange tint bg. Default = Pickup.
- **Pickup section** (mode==Pickup): header "Select Pickup Location *" (red asterisk). Radio list of warehouses (88dp rows, 29dp radio, name subtitle1 + address subtitle2). Auto-select `isPrimary` or first.
- **Delivery section** (mode==Delivery): header "Select Delivery Location *"; caption "Service areas: Kingston, Montego Bay, Ocho Rios, Portmore + 13 more"; row header "Search for your location" + right "Use Current Location" pill; search field (placeholder "Search by Town or Parish"); results dropdown; **map 201dp radius 5**; Selected-Location card (hidden until validated: pin + "Selected Location:" + place name); hint "You can also click or drag the marker on the map to select your exact location.".
- Bottom CTA `GradientButton("Choose Currency")` — loading/disabled while busy.

## 3. Behaviour (ViewModel `DeliveryMethodViewModel`)
State: `DeliveryUiState(mode, warehouses, selectedWarehouseId, pickupLabel, searchQuery, searchResults, mapCenter, markerCoord, validatedAddress, validatedDistanceKm, validatedFee, validatedFeeCurrency, ctaState, showCurrencyPopup, errorTitle, errorMessage, navTarget)`. Ctor default arg `repo = DeliveryRepository(ApiClient.service)`.

- `loadSettings()` → `repo.deliverySettings()`; on failure fall back to the 4 hard-coded warehouses (Kingston / Montego Bay / Savanna-La-Mar / Yallas — exact addresses+coords from Swift `fallbackWarehouses()`).
- `loadPreference()` → `repo.preference()` pre-selects mode / warehouse / re-hydrates validated delivery coord+address.
- `onSearchQueryChange(q)` → cancel+relaunch a debounce Job (`delay(350)`), min 2 chars, `repo.searchPlaces(q)`; apply only if `latestQuery==q` **and** field text unchanged (stale guard). Editing sets `markerCoord`/validated = null (forces re-validate on Continue).
- `onSearchResultPicked(place)` / `onSubmitSearch()` (auto-pick `results.first`, else error "Address not found") → set marker → `validate()`.
- `onMapTap/onMarkerDragEnd(latLng)` → `validate()` immediately, plus a **cancellable** reverse-geocode Job (`repo.reverseGeocode`) that commits the address to the card only if `markerCoord` is unchanged.
- `onUseCurrentLocation(latLng?)` → null → error "Location unavailable… enable Location Services". (Screen owns permission + FusedLocationProvider; VM stays Android-free.)
- `validate(latLng, address)` → ctaState=Validating → `repo.validateLocation(lat,lng,address,totalWeightKg=null)`; `valid` → commit validated*, else error with `reason`.
- `onContinue()` → Pickup: require `selectedWarehouseId` (else "Pick a location") → `savePreferenceAndProceed`. Delivery: `markerCoord` present → proceed; else query nonblank → `onSubmitSearch`; else error "Enter a delivery address".
- `savePreferenceAndProceed(mode, coord, address)` → ctaState=Saving → `repo.savePickupPreference(label)` / `repo.saveDeliveryPreference(DeliveryLocation(address,lat,lng,formattedAddress=address))` → `showCurrencyPopup=true`.
- `onCurrencyChosen(currency)` → set `navTarget` (see §4). `consumeNav()`, `dismissError()`.

Debounce/race parity is load-bearing — copy the cancel-and-guard logic exactly (Swift `searchDebounceWork`, `reverseGeocodeTask`, coord-equality guard).

## 4. Nav wiring
- Add `Routes.DELIVERY_METHOD = "deliveryMethod"` (no args).
- Register in `ShopNav.kt#shopGraph`: `composable(Routes.DELIVERY_METHOD){ DeliveryMethodScreen(onBack={navController.popBackStack()}, onNavigate={navController.navigate(it)}) }`.
- **Re-wire cart** (`feature/cart/CartViewModel.kt#pay()`): keep the empty-cart + packageId guards, then instead of `createCheckout(...)` set a one-shot `navToDeliveryMethod=true`. `CartScreen.kt`: `LaunchedEffect(navToDeliveryMethod){ onNavigate(Routes.DELIVERY_METHOD); consumeDeliveryNav() }` (add `onNavigate` param; pass it from the CART composable).
- **Currency branch gap**: Kotlin has NO Profile Information / Order Summary screens (the Kotlin cart pays via Stripe hosted checkout directly). For this work order, route `onCurrencyChosen` to the existing Stripe `createCheckout(packageIds, chosenCurrency, isAuction=true)` → Custom Tab. Document the deviation in the screen header and flag "restore JMD→Profile / USD→Order Summary when those screens land" as a **separate follow-up**. Do NOT create placeholder destinations.

## 5. API (all endpoints exist — consumers only)
| Call | Repo method | Notes |
|---|---|---|
| GET delivery/settings | `deliverySettings()` | warehouses for radio list |
| POST search-places `{q}` | `searchPlaces(q)` | debounce 350ms, ≥2 chars |
| POST reverse-geocode `{lat,lng}` | `reverseGeocode()` | name map point |
| POST validate-location `{lat,lng,address?,total_weight_kg?}` | `validateLocation()` | FLAT response; `valid`/`reason`/`deliveryFee`/`feeCurrency` |
| POST save-preference | `savePickupPreference()` / `saveDeliveryPreference()` | `delivery_mode` pickup/delivery |
| GET delivery/preference | `preference()` | pre-select on load |
| POST payments/create-checkout | `ShopCheckoutRepository.createCheckout` (existing) | terminal Stripe step, moves after currency |

`total_weight_kg` = null (CartLine has no weight); server re-validates. Add `weightKg` to `CartStore.CartLine` only if exact fee parity is required.

## 6. Dependency decision — THE MAP (blocker to flag)
App has no map/location deps today. **Recommended: Google Maps Compose** behind a `DeliveryMapView.kt` abstraction so a swap touches one file:
```toml
# libs.versions.toml
mapsCompose = "6.4.1"; playServicesMaps = "19.0.0"; playServicesLocation = "21.3.0"
maps-compose = { group="com.google.maps.android", name="maps-compose", version.ref="mapsCompose" }
play-services-maps = { group="com.google.android.gms", name="play-services-maps", version.ref="playServicesMaps" }
play-services-location = { group="com.google.android.gms", name="play-services-location", version.ref="playServicesLocation" }
```
```kotlin
// app/build.gradle.kts
implementation(libs.maps.compose)
implementation(libs.play.services.maps)
implementation(libs.play.services.location)
```
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}"/>
```
Inject `MAPS_API_KEY` via `manifestPlaceholders` per flavor. **DECISION REQUIRED (human)**: provision an Android Maps SDK key (Google Cloud console, restrict by package + SHA-1). This build-time key is DISTINCT from `DeliverySettingsPayload.googleMapsApiKey` (server-side, used by Laravel for geocoding). `GoogleMap{ Marker(draggable, state) + onMapClick + rememberCameraPositionState }` maps 1:1 to Swift's MKMapView tap/drag/zoom.

Fallbacks if the key is unavailable: **Phase-1 static** `DeliveryMapView` (search + Use-Current-Location only, no interactive marker) — functionally viable since all geocoding is server-side — then swap to GoogleMap later. OSM/MapLibre only if a key can never be provisioned (no Compose wrapper, off-brand tiles). Add `play-services-location` regardless (small, keyless) for Use-Current-Location.

## 7. Edge cases
Location permission owned by Screen (launcher → FusedLocation.lastLocation; null/denied → `onUseCurrentLocation(null)`). Stale search + reverse-geocode races guarded (cancel + coord-equality). `loadSettings` failure → hard-coded warehouses. Pickup auto-selects primary. `validate !valid` → alert + clear. Dark mode via theme tokens (no manual re-apply). **`DeliveryDefaultsStore` (About-screen toggle) is a separate forwards-compat feature the cart flow does NOT read — SKIP for this work order.** Currency branch: route to existing Stripe checkout until Profile/Order Summary screens exist. Keep the packageId guard before navigating.

## 8. Files
Create: `feature/delivery/DeliveryMethodViewModel.kt`, `DeliveryMethodScreen.kt`, `DeliveryMapView.kt`, `CurrencyChoicePopup.kt` (+ optional `DeliveryModels.kt`, `DeliveryCheckoutContext.kt`).
Edit: `core/navigation/Routes.kt`, `feature/shop/ShopNav.kt`, `feature/cart/CartViewModel.kt`, `feature/cart/CartScreen.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml` (+ `AirdropApp.kt` only if adding a DeliveryRepoProvider binding).

---

<details><summary>Full Swift flow + Kotlin-existing (audit detail)</summary>

**Swift flow:**

FigmaDeliveryMethodViewController.swift — verified end-to-end:

INIT (:279) init(packageIDs, currency="USD", isAuction=false). viewDidLoad (:315): bg gray150, hides nav bar, builds header/bottomBar/content, calls modeChanged(), loadSettings(), loadPreference(). State: selectedMode:.pickup (default, matches Laravel getPreference), settings, warehouses, selectedWarehouseID, pickupLocationLabel, deliveryAddressText, validatedCoordinate (didSet → refreshMap + renderSelectedLocationCard), validatedAddress/DistanceKm/DeliveryFee/FeeCurrency.

TILES (:1052 makeTilesRow): two 104pt cards side-by-side. Pickup ("Collect from warehouse", FigmaPickupPhoto) + Delivery ("Deliver to your location", FigmaDeliveryPhoto). Tap → onPickPickupMode/onPickDeliveryMode → selectedMode set → modeChanged (:1421): styleModeCard selected=peach #F1855C 1.5pt border + orange 6-10% tint bg; unselected=cardHairline 1pt. Shows pickupSectionCard XOR deliverySectionCard. Title above: "How would you like to receive your packages?".

PICKUP SECTION (:627): header "Select Pickup Location *" (red asterisk). renderPickupList (:1548): auto-selects primary (isPrimary==true) or first warehouse; matches saved pickupLocationLabel by name (case-insensitive). Each row (:1566 makeWarehouseRow): 88pt min, transparent bg, 1pt border (#f1855c selected / iconShape unselected), 29pt radio (solid orange outer + white inner dot when selected), name (subtitle1) + address (subtitle2 gray). Tap → onWarehouseTapped sets selectedWarehouseID + pickupLocationLabel=name, re-render.

DELIVERY SECTION (:651): header "Select Delivery Location *". "Service areas: Kingston, Montego Bay, Ocho Rios, Portmore + 13 more". "Search for your location" header + right-aligned "Use Current Location" pill (border-only orange, navigator icon). Search field (:1305) placeholder "Search by Town or Parish", magnifier icon. Below: search-results dropdown table (hidden until results), then 201pt map, then selected-location card (hidden until validated), then hint "You can also click or drag the marker on the map to select your exact location.".

SEARCH (:966 onSearchEditingChanged → searchPlaces): 0.35s debounce, min 2 chars → POST /delivery/search-places → dropdown. Guards: only apply if latestSearchQuery==query AND current field text==query (stale-response guard). Tap a result (:didSelectRowAt) → set field, deliveryAddressText, validatedAddress=pick.address, dropMarker at (lat,lng), validate(coord, address). Editing invalidates validatedCoordinate=nil (Continue forces fresh validate). Return key (:textFieldShouldReturn) → lookupTypedAddressAndValidate (:1002): searches, auto-picks results.first, validates; alert "Address not found" if empty.

MAP (:727 buildMapCard, MKMapView Apple tiles): default Jamaica region (18.1096,-77.2975 span 1.8). Orange draggable marker. Tap-to-move (onMapTapped) + drag-end (didChange .ending) → handleMarkerMoved (:906): sets deliveryAddressText=fallback coord, validate(coord), then reverseGeocodeTask (cancellable — cancels prior on new move) → POST /delivery/reverse-geocode (Apple CLGeocoder fallback if Laravel empty) → commits address to card only if validatedCoordinate still equals this move's coord. Custom +/- zoom card (26x58) multiplies span 0.5/2.0.

USE CURRENT LOCATION (:1719 onUseCurrentLocation): requestCurrentLocation via CLLocationManager (requestWhenInUseAuthorization if notDetermined; nil if denied → alert "Location unavailable… enable Location Services"). On coord → dropMarker + handleMarkerMoved.

VALIDATE (:1751 validate): lockContinue("Validating…"), sums FigmaCartStore weightKg → totalKg → POST /delivery/validate-location{lat,lng,address,total_weight_kg}. If result.valid: commit validatedCoordinate/Address/DistanceKm/DeliveryFee/FeeCurrency. Else: clear + alert "Delivery not available" with result.reason. Catch → alert "Couldn't validate location".

SELECTED LOCATION CARD (:1641): InfoNotice styled, pin icon, "Selected Location:" + place name (prefers validatedAd

**Kotlin already-ported (reuse):**

FULLY PORTED, ZERO CONSUMERS (verified: git grep for DeliveryRepository finds only its own def; the whole data layer is orphaned):

MODELS — app/src/main/java/com/ga/airdrop/data/model/Delivery.kt: DeliveryWarehouse(id,name,address,latitude,longitude,isPrimary,distanceKm,durationMinutes), DeliverySettings(maxDeliveryDistanceKm,deliveryEnabled,warehouses,hasApiKey), DeliverySettingsPayload(settings,googleMapsApiKey), ValidateLocationRequest(latitude,longitude,address,totalWeightKg), DeliveryRange, DeliveryValidationResponse(success,valid,reason,message,nearestWarehouse,distanceKm,deliveryFee,feeCurrency,deliveryFeeUsd,deliveryRange), DeliveryLocation(address,latitude,longitude,formattedAddress,parish), SaveDeliveryPreferenceRequest(deliveryMode,deliveryLocation,pickupLocation,totalWeightKg), DeliveryPreference(deliveryMode,deliveryLocation,pickupLocation), ReverseGeocodeRequest/Result, SearchPlacesRequest, PlaceResult(address,latitude,longitude,placeId), PlaceSearchResults(results). All use Flexible*Serializer — no model work needed.

REPO — app/src/main/java/com/ga/airdrop/data/repo/DeliveryRepository.kt(private val service: AirdropApiService): deliverySettings():Result<DeliverySettingsPayload>, validateLocation(lat,lng,address,totalWeightKg):Result<DeliveryValidationResponse>, savePickupPreference(pickupLocation):Result<DeliveryPreference>, saveDeliveryPreference(location,totalWeightKg):Result<DeliveryPreference>, preference():Result<DeliveryPreference>, reverseGeocode(lat,lng):Result<ReverseGeocodeResult>, searchPlaces(query):Result<List<PlaceResult>>. Uses apiResult{} wrapper. Every method the ViewModel needs already exists — construct with DeliveryRepository(ApiClient.service).

ENDPOINTS — app/src/main/java/com/ga/airdrop/data/api/AirdropApiService.kt (~:388-410): deliverySettings, validateDeliveryLocation, saveDeliveryPreference, deliveryPreference, reverseGeocode, searchPlaces. Complete.

INTEGRATION POINT — app/src/main/java/com/ga/airdrop/feature/cart/CartViewModel.kt: pay() currently calls checkout.createCheckout(packageIds, "USD", isAuction=true) → checkoutUrl → Stripe Custom Tab directly. THIS is where Delivery Method must be inserted (cart → delivery method → currency → checkout). Cart items: CartStore.items (StateFlow<List<CartStore.CartLine>>), CartLine has packageId; CartStore.totalUsd(). Note: Kotlin CartLine has NO weightKg field (Swift sums FigmaCartStore weightKg) — so total_weight_kg = null on Android unless a weight field is added to 

**API:** All endpoints EXIST and are wired in AirdropApiService.kt + DeliveryRepository.kt — no new API code, only consumers.

GET delivery/settings → DataEnvelope<DeliverySettingsPayload{settings:DeliverySettings(warehouses[],maxDeliveryDistanceKm,deliveryEnabled,hasApiKey), googleMapsApiKey}>. Repo: repo.deliverySettings(). Use warehouses for the pickup radio list. googleMapsApiKey is informational (Android map SDK uses manifest key).

POST delivery/search-places {q:String} → DataEnvelope<PlaceSearchResults{results:List<PlaceResult{address,latitude,longitude,placeId}>}>. Repo: repo.searchPlaces(query). Debounced 350ms, min 2 chars.

POST delivery/reverse-geocode {latitude,longitude} → DataEnvelope<ReverseGeocodeResult{address,parish,latitude,longitude}>. Repo: repo.reverseGeocode(lat,lng). Called on map tap/drag to name the point.

POST delivery/validate-location {latitude,longitude,address?,total_weight_kg?} → FLAT DeliveryValidationResponse (NO data wrapper) {valid,reason,message,nearestWarehouse,distanceKm,deliveryFee,feeCurrency,deliveryFeeUsd,deliveryRange}. Repo: repo.validateLocation(lat,lng,address,totalWeightKg). If !valid → show reason. Fee preview → validatedFee/FeeCurrency.

POST delivery/save-preference {delivery_mode:"pickup"|"delivery", delivery_location?, pickup_location?, total_weight_kg?} → DataEnvelope<DeliveryPreference>. Repo: repo.savePickupPreference(label) / repo.saveDeliveryPreference(DeliveryLocation(address,lat,lng,formattedAddress), totalWeightKg).

GET d

**Nav wiring:** Route constant: add `Routes.DELIVERY_METHOD = "deliveryMethod"` (no args — the screen reads cart state + server preference; matches Swift init being arg-optional). 

Registration: in ShopNav.kt shopGraph, `composable(Routes.DELIVERY_METHOD){ DeliveryMethodScreen(onBack={navController.popBackStack()}, onNavigate={navController.navigate(it)}) }`.

How it's reached: My Cart "Make Payment" no longer goes straight to Stripe. Flow becomes Cart → DELIVERY_METHOD → (currency popup, in-screen dialog, no route) → JMD branch = Profile Information route / USD branch = Order Summary route. 

CRITICAL GAP: Kotlin has NO Profile Information (FigmaBillingDetailsViewController) or Order Summary (FigmaCartViewController mode:.orderSummary) screens yet — the Kotlin cart is single-mode "My Cart" and pays via Stripe hosted checkout directly (RN parity, not the Swift orderSummary path). So the currency branch has no destinations to push to. RECOMMENDATION: for THIS work order, wire the currency popup's onSelect to call the EXISTING terminal action — i.e. after currency is chosen, run the current Stripe createCheckout (CartViewModel.pay's old body) with the chosen currency, opening the Custom Tab. Add Ro

**Edge cases:** - Currency branch has no destinations yet (no Profile Information / Order Summary in Kotlin): route currency onSelect to the existing Stripe createCheckout with chosen currency; flag JMD/USD split as a follow-up. Do not push to nonexistent routes.
- total_weight_kg = null on Android (CartStore.CartLine lacks weightKg that Swift's FigmaCartStore has). Server re-validates the fee, so acceptable; note the fee preview may differ slightly from the final. Add a weight field to CartLine only if exact parity is demanded.
- Location permission: Screen owns rememberLauncherForActivityResult; on notDetermined→request, on denied→vm.onUseCurrentLocation(null)→error "Location unavailable… enable Location Services". Handle emulator/no-fix (lastLocation null) same as denied. ViewModel stays Android-free.
- validate-location !valid → alert with reason ("This location can't be served…"); clear validatedCoordinate so Continue re-checks.
- Stale search responses: guard latestQuery==query AND field text unchanged before applying results (Swift does both). Debounce 350ms, min 2 chars.
- Reverse-geocode race: cancel the prior reverse-geocode Job on each new marker move; commit the resolved address only if the marker coord is unchanged (Swift's reverseGeocodeTask cancel + coord-equality guard).
- Editing the search field invalidates validatedCoordinate=null so Continue forces a fresh validate (Swift onSearchEditingChanged).
- loadSettings failure → fall back to the 4 hard-coded warehouses (Kingston/
</details>

---

## Payment Success — post-Stripe-Hosted-Checkout confirmation screen reached via the `airdrop://payment-success?session_id=…` return deeplink, which verifies the checkout session server-side then shows celebratory copy with order reference + formatted amount.  (effort: M)

# Payment Success — Kotlin Implementation Spec

Post-Stripe-Hosted-Checkout confirmation. Reached when the Stripe Custom Tab redirects to `airdrop://payment-success?session_id=cs_…`, the app verifies the session server-side, then shows a celebratory screen and clears the cart.

**Source of truth:** `SWIFT_APP` `FigmaPaymentSuccessViewController.swift` + `SceneDelegate.swift:432-620`.
**Effort:** M. **No new Gradle deps. No new API/model — all ported.**

## How Kotlin learns payment succeeded
Custom Tab (Chrome) → Stripe redirects to `airdrop://payment-success?session_id=…` → manifest `VIEW`+`BROWSABLE` intent-filter on the **singleTask** `MainActivity` → `onNewIntent` → capture the URI → verify via `GET /payments/{sessionId}/status` → navigate. **Deeplink-driven, not a poll.** The session id is echoed by Stripe into the return URL, so no client-side persistence of session_id is needed.

## Already ported (reuse, do NOT rebuild)
- `AirdropApiService.checkoutSessionStatus` (`AirdropApiService.kt:303`) → `GET payments/{sessionId}/status`.
- `CheckoutSessionStatus` model (`Payments.kt:81`) — 1:1 with Swift (sessionId/status/paymentStatus/invoiceId/amountTotal/currency, Flexible serializers).
- `PaymentsRepository.checkoutSessionStatus(sessionId): Result<CheckoutSessionStatus>` (`PaymentsRepository.kt:72`).
- `CartStore.clear()` (`CartStore.kt:91`) = Swift `removeAll()`.
- `launchExternalUrl` Custom Tab helper (`ShopComponents.kt:74`) — already launches Stripe from `CartScreen.kt:83`.
- Deeplink plumbing to mirror: `PushDeepLink` (pending StateFlow + capture/consume) consumed in `AppRoot.kt:76-80`.
- Tokens: `AirdropTheme.colors.gray150/textDarkTitle/textDescription`; green `Alert.Completed = 0xFF39A634` (`Color.kt:63`); `GradientButton` (`Buttons.kt:31`); `AirdropType.title1/subtitle2/body2`.

## Files to CREATE
- [ ] `feature/cart/PaymentSuccessScreen.kt` — stateless `@Composable PaymentSuccessScreen(orderReference: String?, formattedAmount: String?, onDone: () -> Unit)`:
  - Full-screen `Column`, bg `colors.gray150`.
  - Centered block: `Box(120.dp, CircleShape, bg = Alert.Completed.copy(alpha=0.12f))` wrapping a 64.dp `CheckCircle` icon tinted `Alert.Completed`; `Spacer(28.dp)`; headline **"You have successfully paid for this"** (bold ~22sp, textDarkTitle, center); `Spacer(20.dp)`; subline (body2, textDescription, center) — see copy branch; if `orderReference` non-blank: `Spacer(20.dp)` + **"Order reference: $orderReference"** (subtitle2, textDarkTitle, 1 line, ellipsis).
  - Bottom `GradientButton("Done", onDone)` with `padding(horizontal=20.dp, bottom=24.dp).navigationBarsPadding()`.
- [ ] `feature/cart/PaymentReturnViewModel.kt` (recommended) — owns the retry-3x verify; exposes `sealed interface PaymentReturnResult { Success(ref, amount); NotPaid(text); Unconfirmed(text) }` + `suspend fun verify(sessionId): PaymentReturnResult`. Plus a `PaymentReturnHost` composable (spinner while verifying, then dispatch).

## Files to EDIT
- [ ] `AndroidManifest.xml` — add to the existing `.MainActivity` (already `singleTask`/`exported`):
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:scheme="airdrop" />
      <data android:scheme="airdropexpress" />
  </intent-filter>
  ```
- [ ] `MainActivity.kt` — in `onCreate` (after `PushDeepLink.capture(intent)`, ~L22) and `onNewIntent` (~L59) also call `PushDeepLink.captureUri(intent)`.
- [ ] `PushDeepLink.kt` — add `captureUri(intent)` + `resolveUri(uri)`:
  - scheme must be `airdrop`/`airdropexpress`; host `payment-success`/`payment_success`/`payment-complete`/`payment_complete` → `Routes.paymentReturn(uri.getQueryParameter("session_id"))`; host `payment-cancelled`/`payment_cancelled`/… → `Routes.paymentCancelled()`; else `null`.
- [ ] `Routes.kt` — add:
  ```kotlin
  const val PAYMENT_RETURN  = "paymentReturn/{sessionId}"
  const val PAYMENT_SUCCESS = "paymentSuccess?ref={ref}&amount={amount}"
  fun paymentReturn(sessionId: String?) = "paymentReturn/${sessionId.orEmpty()}"
  fun paymentSuccess(ref: String?, amount: String?): String { /* URLEncoder, +→%20 like invoiceViewer */ }
  fun paymentCancelled() = Routes.CART   // v1: back to Cart + snackbar
  ```
- [ ] `AppRoot.kt` `mainGraph` — register `PAYMENT_RETURN` (`PaymentReturnHost`) and `PAYMENT_SUCCESS` (`PaymentSuccessScreen`) composables (see nav block below). Existing `LaunchedEffect(pendingPush, token)` (:76) already navigates once token present — no change there.
- [ ] `CartViewModel.kt` — no behavior change; update the stale comment at L158-167 (drop "not yet landed"; note the verified-paid clear now lives in the payment-return flow). `onCheckoutOpened` still just nulls `checkoutUrl`.

## Verify logic (exact Swift parity — do not collapse)
```kotlin
suspend fun verify(sessionId: String): PaymentReturnResult {
    if (sessionId.isBlank()) return Unconfirmed("We didn't receive a Stripe session id on return.")
    var lastErr: Throwable? = null
    repeat(3) { attempt ->
        payments.checkoutSessionStatus(sessionId)
            .onSuccess { s ->
                val paid = s.paymentStatus?.lowercase()=="paid" || s.status?.lowercase()=="paid"
                return if (paid) {
                    val amt = if (s.amountTotal!=null && s.currency!=null)
                        String.format(Locale.US, "%s %.2f", s.currency!!.uppercase(Locale.US), s.amountTotal) else null
                    Success(sessionId, amt)
                } else NotPaid(s.paymentStatus ?: s.status ?: "unknown")
            }
            .onFailure { lastErr = it; if (attempt < 2) delay((attempt+1)*800L) }
    }
    return Unconfirmed(lastErr?.message ?: "network error")
}
```
**Rule:** `Result.success` + not-paid ⇒ `NotPaid` **immediately** (authoritative, no retry). Only `Result.failure` retries. After 3 failures ⇒ `Unconfirmed` (payment may have gone through — never imply failure).

## Nav block (AppRoot.mainGraph)
```kotlin
composable(Routes.PAYMENT_RETURN, listOf(navArgument("sessionId"){type=NavType.StringType})) { e ->
    PaymentReturnHost(sessionId = e.arguments?.getString("sessionId").orEmpty(),
        onPaid = { ref, amt -> CartStore.clear(); navController.navigate(Routes.paymentSuccess(ref, amt)){ popUpTo(Routes.PAYMENT_RETURN){inclusive=true} } },
        onNotPaid = { navController.navigate(Routes.CART){ popUpTo(Routes.PAYMENT_RETURN){inclusive=true} } },   // + "Payment incomplete" snackbar; cart NOT cleared
        onUnconfirmed = { navController.navigate(Routes.SHIPMENTS){ popUpTo(Routes.PAYMENT_RETURN){inclusive=true} } }) // "check Shipments before paying again"
}
composable(Routes.PAYMENT_SUCCESS,
    listOf(navArgument("ref"){type=NavType.StringType;defaultValue=""}, navArgument("amount"){type=NavType.StringType;defaultValue=""})) { e ->
    val ref = e.arguments?.getString("ref")?.takeIf{it.isNotBlank()}
    val amt = e.arguments?.getString("amount")?.takeIf{it.isNotBlank()}
    PaymentSuccessScreen(ref, amt, onDone = { CartStore.clear(); navController.navigate(Routes.HOME){ popUpTo(0){inclusive=true}; launchSingleTop=true } })
}
```

## Copy strings (verbatim)
- Headline: `You have successfully paid for this`
- Subline (amount present): `Your payment of {amount} has been received. A receipt has been emailed to you.`
- Subline (no amount): `Your payment has been received. A receipt has been emailed to you.`
- Reference: `Order reference: {ref}` (hidden when blank)
- Not-paid: `Stripe reports status "{status}". Try again from the cart.`
- Cancelled: `No payment was completed. Your cart is still available.`
- Unconfirmed: `Your payment may have completed — please check your Shipments before paying again.`
- Missing id: `We didn't receive a Stripe session id on return.`

## Edge cases (checklist)
- [ ] Blank session_id ⇒ Unconfirmed (do not verify).
- [ ] Transient failure vs authoritative not-paid distinguished (retry only on failure).
- [ ] Cart cleared ONLY on verified paid (+ defensively in onDone). Never on cancel/not-paid/unconfirmed.
- [ ] amount_total is MAJOR units — do not divide by 100.
- [ ] Order-reference row hidden when ref blank; subline falls back when amount null.
- [ ] singleTask ⇒ warm return via onNewIntent; cold via onCreate — both call captureUri. VIEW intents lack EXTRA_ROUTE so no clash with FCM capture().
- [ ] `popUpTo(PAYMENT_RETURN){inclusive}` so Back doesn't re-verify; success has no back affordance (Done is the only exit); `launchSingleTop` guards duplicate intents.
- [ ] Logged-out on return: pending consume gated on token!=null; verify would 401 anyway — acceptable v1.

## Verification
Emulate the redirect without real Stripe:
`adb shell am start -a android.intent.action.VIEW -d "airdrop://payment-success?session_id=cs_test_123" com.ga.airdrop`
Confirm: verify fires → on paid mock, success screen shows headline + amount + reference, cart empties, Done returns to Home. Test cancel host and a not-paid mock (cart retained). Light + dark.

---

<details><summary>Full Swift flow + Kotlin-existing (audit detail)</summary>

**Swift flow:**

Source of truth: SWIFT_APP origin/main. Two files drive this.

FigmaPaymentSuccessViewController.swift (the screen):
- init(orderReference: String? = nil, formattedAmount: String? = nil). orderReference = the Stripe session id (cs_…); formattedAmount = already-formatted "USD 458.88".
- viewDidLoad: view.backgroundColor = DesignTokens.Color.gray150; nav bar hidden.
- buildLayout(): a 120x120 "check ring" View, cornerRadius 60, bg = Alert.completed.withAlphaComponent(0.12); centered 64x64 SF Symbol "checkmark.circle.fill" tinted Alert.completed (green). Vertical stack (checkRing, headline, subline, referenceLabel), alignment .center, spacing 20, custom spacing 28 after checkRing, centered at view.centerY - 40, side insets 32.
  - headline: "You have successfully paid for this", bold 22, textDarkTitle, centered, multiline.
  - subline (body2, textDescription, centered): if amount non-empty → "Your payment of \(amount) has been received. A receipt has been emailed to you." else → "Your payment has been received. A receipt has been emailed to you."
  - referenceLabel (subtitle2, textDarkTitle, 1 line): hidden when orderReference nil/empty; else "Order reference: \(ref)".
- "Done" button: pinned bottom (leading 20, trailing 20, bottom safeArea -24), height 52, cornerRadius 10, white bold title, vertical orange gradient [rgb(255,120,62) → rgb(241,81,20)]. onDone(): FigmaCartStore.shared.removeAll(); then popToRoot / dismiss / select Home tab (index 0) fallback.

SceneDelegate.swift (the deeplink + verify pipeline; lines ~432-620):
- scene(_:openURLContexts:) [warm] and connectionOptions.urlContexts [cold] both call handleIncomingURL(url).
- handleIncomingURL(url) at :455: guard scheme == "airdrop" || "airdropexpress". host lowercased. sessionID = query item "session_id".
  - case host in {"payment_success","payment-success","payment_complete","payment-complete"}: if sessionID empty → alert "Payment status unknown". Else Task: retry loop 1...3 calling `AirdropAPI.shared.checkoutSessionStatus(sessionId:)` (:488). On a returned status: paid = (paymentStatus?.lowercased == "paid") || (status?.lowercased == "paid"). If paid → build formatted = String(format: "%@ %.2f", currency.uppercased, amountTotal) when both present else nil → presentPaymentSuccessScreen(sessionID, amountPaid: formatted); return. If a 200 says not-paid → alert "Payment incomplete" (authoritative, NOT retried). On THROWN error (network/transient): retry with backoff sleep attempt*0.8s (0.8s, 1.6s); after 3 throws → alert "Couldn't confirm payment / Your payment may have completed — please check your Shipments before paying again."  KEY RULE (Kemar 2026-05-27 stuck-cart #3): a flaky verify must NOT be treated as "not paid"; a 200 "not paid" is authoritative, a thrown error is retried.
  - case host in {"payment_cancelled","payment-cancelled",…}: alert "Payment cancelled / No payment was completed. Your cart is still available." (cart NOT cleared).
  - default: forward to AirdropPushNotificationRouter (shared push route map).
- presentPaymentSuccessScreen(sessionID:amountPaid:) at :589: FigmaCartStore.shared.removeAll() FIRST (verify-then-clear — this is the ONLY cart-clear path on Stripe return), dismiss any SFSafariViewController, then push FigmaPaymentSuccessViewController(orderReference: sessionID, formattedAmount: amountPaid) onto the topmost nav (modal fullscreen fallback).
- normalizedPaymentReturnURL(from:) at :569: universal-link path — maps https .../payment-success?… into an airdrop://payment-success?… URL then re-dispatches. (Not needed for Kotlin v1 — custom scheme only.)

Return mechanism: the Custom Tab (SFSafariViewController on iOS) is dismissed by iOS routing the airdrop:// URL to the scene; the app learns payment succeeded via the DEEPLINK carrying session_id, then a server verify — NOT a poll. amountTotal from Stripe is already in major units (Laravel divided by 100). API contract: GET /payments/{sessionId}/status → { success, message, data: { session

**Kotlin already-ported (reuse):**

Almost the entire data layer is already ported — this is a wiring + UI gap, not a backend gap. Reuse:
- Verify endpoint: app/src/main/java/com/ga/airdrop/data/api/AirdropApiService.kt:303 — `@GET("payments/{sessionId}/status") suspend fun checkoutSessionStatus(@Path("sessionId") id): DataEnvelope<CheckoutSessionStatus>`. EXISTS.
- Model: app/src/main/java/com/ga/airdrop/data/model/Payments.kt:81 — `CheckoutSessionStatus(sessionId, status, paymentStatus, invoiceId, amountTotal, currency)` with @SerialName mapping + Flexible serializers. EXISTS, exact 1:1 with Swift.
- Repo wrapper: app/src/main/java/com/ga/airdrop/data/repo/PaymentsRepository.kt:72 — `suspend fun checkoutSessionStatus(sessionId): Result<CheckoutSessionStatus>` (unwraps envelope, errors on success==false/null data). EXISTS.
- createCheckout: PaymentsRepository.kt:57 returns full `Result<CheckoutResponse>` (with sessionId) — but ShopCheckoutRepository/ShopRepoBinding.kt:111-117 currently drop it to `Result<String>` (checkout_url only). Session id is not needed client-side (Stripe echoes it in the deeplink), so no repo change strictly required for v1.
- CartStore: app/src/main/java/com/ga/airdrop/feature/cart/CartStore.kt:91 — `fun clear()` = Swift removeAll(). Reuse for verified-paid clear.
- Custom Tab launch: app/src/main/java/com/ga/airdrop/feature/shop/ShopComponents.kt:74 `launchExternalUrl(context,url)` (CustomTabsIntent). Already used by CartScreen. This is the SFSafariViewController parity.
- Deeplink plumbing to copy: app/src/main/java/com/ga/airdrop/core/push/PushDeepLink.kt (MutableStateFlow<String?> pending + capture(intent) + consume()) and its consumption in app/src/main/java/com/ga/airdrop/core/navigation/AppRoot.kt:76-80 (LaunchedEffect(pendingPush, token) → navController.navigate). MainActivity.kt onCreate:22 + onNewIntent:58 already call PushDeepLink.capture(intent) — but only reads the FCM route extra, NOT the Custom-Tab URI intent.data. This is the core missing hop.
- Design tokens: AirdropTheme.colors.gray150 (Color.kt:115), textDarkTitle (:123), textDescription (:125); green = BrandPalette/Alert `Completed = Color(0xFF39A634)` (Color.kt:63). GradientButton(text,onClick,…) at core/designsystem/components/Buttons.kt:31 (height 52, orange SignInButton gradient) = the "Done" CTA. AirdropType.title1/subtitle1/subtitle2/body2 for typography.
- Nav pattern: Routes.kt object; graphs via NavGraphBuilder extensions; AppRoot mainGraph.

**API:** All endpoints/models/repo wrappers EXIST — no API-layer work.
- Verify: PaymentsRepository.checkoutSessionStatus(sessionId): Result<CheckoutSessionStatus> → GET /payments/{sessionId}/status. Response data: { session_id, status, payment_status, invoice_id, amount_total: Double (major units), currency }.
- Paid predicate (exact Swift parity): `val paid = status.paymentStatus?.lowercase()=="paid" || status.status?.lowercase()=="paid"`.
- Formatted amount: `if (status.amountTotal != null && status.currency != null) String.format(Locale.US, "%s %.2f", status.currency!!.uppercase(Locale.US), status.amountTotal) else null`.
- Retry loop (put in PaymentReturnViewModel.verify or AppRoot LaunchedEffect):
    var lastErr: Throwable? = null
    repeat(3) { attempt ->
        val r = payments.checkoutSessionStatus(sessionId)   // Result<>
        r.onSuccess { status -> return if (paid) Success(sessionId, formatted) else NotPaid(status.paymentStatus ?: status.status ?: "unknown") }
         .onFailure { lastErr = it; if (attempt < 2) delay((attempt+1)*800L) }
    }
    return Unconfirmed(lastErr?.message ?: "network error")
  CRITICAL: a Result.success that is not-paid returns NotPaid immediately (NOT retried); only Result.failure retries. Do NOT collapse these.
- createCheckout unchanged. session_id is echoed by Stripe into the return deeplink `?session_id=`, so no client-side persistence needed.

**Nav wiring:** Add to Routes.kt:
   const val PAYMENT_RETURN = "paymentReturn/{sessionId}"        // internal: runs verify then swaps to success/failure
   const val PAYMENT_SUCCESS = "paymentSuccess?ref={ref}&amount={amount}"
   fun paymentReturn(sessionId: String?) = "paymentReturn/${sessionId.orEmpty()}"
   fun paymentCancelled() = // reuse a simple result — v1 can route to CART with a toast, or a PAYMENT_CANCELLED info route. Simplest: return Routes.CART and surface an inline message. (Swift shows an alert; a Compose SnackbarHost or a one-shot dialog on Cart is acceptable.)
   fun paymentSuccess(ref: String?, amount: String?): String { val r = URLEncoder.encode(ref ?: "", "UTF-8").replace("+","%20"); val a = URLEncoder.encode(amount ?: "", "UTF-8").replace("+","%20"); return "paymentSuccess?ref=$r&amount=$a" }

Register in AppRoot.mainGraph (or a new paymentGraph(navController) extension called from mainGraph):
   composable(Routes.PAYMENT_RETURN, arguments = listOf(navArgument("sessionId"){type=NavType.StringType})) { entry ->
       val sid = entry.arguments?.getString("sessionId").orEmpty()
       PaymentReturnHost(sessionId = sid,
           onPaid = { ref, amount -> CartStore.clear(); na

**Edge cases:** - Missing session_id on return: paymentReturn(null) → sessionId "" → verify would 404; guard in PaymentReturnHost: if sessionId.isBlank() → treat as Unconfirmed ("We didn't receive a Stripe session id on return.") and route to Cart, matching Swift's "Payment status unknown" alert.
- Transient network vs authoritative not-paid: MUST distinguish. Result.failure → retry 3x (0.8s,1.6s backoff); Result.success && !paid → NotPaid immediately, no retry. After 3 failures → Unconfirmed → route to Shipments with "Your payment may have completed — please check your Shipments before paying again." (never imply failure — prevents double-pay).
- Cart clear timing: clear ONLY on verified paid (in onPaid, before navigating to success) and in onDone (defensive, Swift removeAll in onDone too). NEVER clear on cancel/not-paid/unconfirmed — Swift keeps the cart ("Your cart is still available").
- Cancel deeplink (payment-cancelled): route back to Cart with an inline/snackbar "No payment was completed. Your cart is still available." — do not clear cart.
- Logged-out on return: pending consume is gated on token!=null (AppRoot:78). If session expired during the Stripe hop, verify would 401 anyway; leaving it pending until re-auth is acceptable v1 (Swift would also fail verify). Optionally surface "Sign in to view your payment."
- Currency/amount absent: subline falls back to "Your payment has been received. A receipt has been emailed to you." and formattedAmount=null; order-reference row hidden when
</details>

---

