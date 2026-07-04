# Airdrop Kotlin App — Functional Bug Audit Report

**Date:** July 4, 2026
**Scope:** Full codebase audit (~175 Kotlin source files)
**Total bugs found:** 85

---

## Summary by Severity

| Severity | Count | Description |
|----------|-------|-------------|
| **Critical** | 11 | Crashes, permanent data loss, fatal navigation states |
| **High** | 26 | Silently incorrect behavior, token/auth failures, broken features |
| **Medium** | 34 | Error handling gaps, race conditions, UX dead-ends |
| **Low** | 14 | Minor UX glitches, performance, maintainability |

---

## CRITICAL BUGS (11)

### C1 — Process-death data loss in checkout (ShopData.kt:162, AuctionCheckoutViewModel.kt:36-38)
`ShopCheckoutStore.product` is a global mutable field used to pass the product from details to checkout. After Android process death, this value is `null` and checkout screen is dead with "Product unavailable."

### C2 — Crash on deep-link to calculator results (CalculatorNav.kt:37, 53)
`navController.getBackStackEntry(Routes.CALCULATOR)` throws `IllegalArgumentException` if the CALCULATOR backstack entry doesn't exist. Deep-linking directly to results or government charges crashes the app.

### C3 — Token refresh race condition erases valid tokens (AuthInterceptor.kt:17, 21-25)
On 401, `AuthTokenStore.clear()` wipes the current token without checking if it was the one that failed. Thread A's stale token triggers `clear()` which erases Thread B's freshly-refreshed token, logging users out unnecessarily.

### C4 — Reactive logout never triggers navigation (AppRoot.kt:42-45, 72-79)
`token` is collected but never used to navigate away from authenticated screens when it becomes `null`. After 401 clears the token, user stays on authenticated tabs indefinitely while all API calls fail.

### C5 — Crash on ThemeController.set() before init() (ThemeController.kt:21-34)
`prefs` is `lateinit var`. If `set()` is called before `Application.onCreate()` runs `init()`, the app crashes with `UninitializedPropertyAccessException`.

### C6 — Crash on AuthTokenStore.save()/clear() before init() (AuthTokenStore.kt:20, 48, 53)
Same `lateinit` issue as C5. Background services or ContentProviders initializing before `Application` would crash.

### C7 — DropAlert submission permanently blocked if consignee prefetch fails (DropAlertScreen.kt:156, DropAlertViewModel.kt:56-62, 108)
Consignee field is `enabled = false` (read-only). If `consigneeName()` API fails, consignee stays blank. `submit()` rejects blank consignee. User can never submit — must kill the app.

### C8 — GoldPriorityScreen crash on missing "gold" tier (GoldPriorityScreen.kt:161-169)
`defaultTierIndex = tierPages.indexOfFirst { it.id == "gold" }` returns `-1` if "gold" entry is removed. Passed to `rememberPagerState(initialPage = -1)` which throws `IllegalArgumentException` at composition time.

### C9 — MoreRepository logout sends empty body as JSON (MoreRepository.kt:203)
`ByteArray(0).toRequestBody(jsonMedia)` sends empty body `""` with `Content-Type: application/json`. Strict JSON parsers reject this. Compare with `More2Api.kt:65` which correctly sends `{}`.

### C10 — Account deletion password stored in singleton, never cleared on cancel (AccountDeletionViewModel.kt:16-23)
`AccountDeletionFlow` singleton holds email/password. `clear()` only called on success. Cancellation, back navigation, or API failure leaves plaintext credentials in memory indefinitely.

### C11 — AvatarPicker silent failure on corrupt images (AvatarPicker.kt:47-49)
`BitmapFactory.decodeStream` returns `null` (not an exception) for corrupt images. `runCatching.getOrNull()?.let(onPicked)` silently fails — sheet dismisses, nothing happens, no feedback.

---

## HIGH BUGS (26)

### H1 — Country code default "JM" for all unlisted countries (User.kt:34-41)
`when` block handles only 4 countries. All others fall to `else -> "JM"`. Users in Trinidad, Barbados, etc. get wrong country code, breaking shipping, currency, phone formatting.

### H2 — `J$` currency prefix not stripped in price parsing (Flexible.kt:46, Products.kt:76-79)
`parseFlexDouble` only trims `$` and spaces. Jamaican dollars arrive as `J$1,550.00`. After comma removal → `J$1550.00` → `toDoubleOrNull()` returns null. Prices silently show as $0.00.

### H3 — Featured products filter only applies when search is present (ProductsRepository.kt:38-52)
`in_stock` and `on_sale` filters are inside `normalizedSearch(search)?.let {}`. Without search, all products returned unfiltered — including out-of-stock.

### H4 — Hardcoded 45% custom duty for all shipments (MiscRepository.kt:76)
Every shipment gets `customDutyPercentage = 45.0` unconditionally, ignoring per-item `CustomDutyRate` from the API.

### H5 — Token loss on process death (AuthTokenStore.kt:48, 53)
`SharedPreferences.edit().apply()` writes async. Process killed immediately after save → token never persisted. User re-authenticates on next launch.

### H6 — AuthNav splash token check after fixed delay (AuthNav.kt:30)
After 1.6s delay, reads `tokenFlow.value` synchronously. If token loading is deferred, authenticated users are routed to LOGIN.

### H7 — ForgotPassword logo not dark-mode aware (ForgotPasswordScreen.kt:74)
Hardcodes `img_airdrop_logo` (light). All other screens switch to `img_airdrop_logo_dark` in dark mode. Logo invisible on dark background.

### H8 — HomeViewModel/HomeUiState has no error field (HomeViewModel.kt:17-25)
Three API calls fail → loading spins then stops → stale/empty data with zero feedback. No retry affordance.

### H9 — GoldPriorityViewModel has no loading/error state (GoldPriorityViewModel.kt:29-43)
API fails → `resolvedTierIndex` stays `null` → silently defaults to Gold Standard with no indication lookup failed.

### H10 — NotificationsViewModel.loadMore() failure silently swallowed (NotificationsViewModel.kt:86-87)
`.onFailure` only sets `loadingMore = false`. `error` not updated. User can never load more items and has no error indication.

### H11 — Warehouse matching by substring picks wrong warehouse (WarehousesViewModel.kt:47-50)
`warehouseFor()` uses `contains(typeKey.lowercase())`. "Standard Express" matches both "standard" and "express" tabs. `firstOrNull` returns whichever comes first.

### H12 — PaymentsViewModel/OrdersViewModel race condition in debounce (PaymentsViewModel.kt:65+, OrdersViewModel.kt:47-48+)
`searchJob` cancels outer coroutine but inner `viewModelScope.launch` coroutines can overlap. Rapid typing → two concurrent API calls → state corruption, duplicate items.

### H13 — ProductPaymentDetails: `packageId` used as fallback for `orderId` (ProductPaymentDetailsViewModel.kt:60)
`val orderId = payment.orderId ?: payment.packageId` — looks up order with package ID when `orderId` is genuinely null. Wrong or failed lookup.

### H14 — ShipmentsNav: invoice URL with `/` breaks navigation route (ShipmentsNav.kt:89-106)
Route `{url}/{title}` splits on `/`. URL like `https://cdn.example.com/a/b.pdf` produces multiple segments, parsing url/title incorrectly.

### H15 — Add-to-cart ignores stock inventory (AuctionProductDetailsViewModel.kt:85)
`addToCart()` never checks `product.inventory`. User can add out-of-stock product (inventory=0) to cart.

### H16 — Cannot update quantity of existing cart item (AuctionProductDetailsViewModel.kt:83-87, CartStore.kt:69-73)
`CartStore.add()` returns `false` if product already present. No `updateQuantity` method. Adding qty=5 when qty=1 exists → "Already in cart" message, cart retains qty=1.

### H17 — Cart billing currency ignored at checkout (CartViewModel.kt:132)
`pay()` always sends `currency = "USD"` regardless of user's dropdown selection. Form field collected but never used.

### H18 — ProfileScreen UTC timezone causes off-by-one in date picker (ProfileScreen.kt:353-354)
`SimpleDateFormat` with UTC timezone formats `DatePicker` millis (local timezone). Selecting July 4 in EST → displays July 3.

### H19 — AuthorizedUserDetail: failed refresh after successful mutation wipes user data (AuthorizedUserDetailViewModel.kt:86-87)
Mutation succeeds, then `load()` refreshes. If refresh fails → `user = null` and `emptyMessage = error`. Successful mutation's data is discarded.

### H20 — Preferences: server values silently dropped if not in hardcoded list (PreferencesViewModel.kt:45-48)
If server returns location/currency not in hardcoded `pickupLocations`/`paymentCurrencies`, silently ignored. Local SharedPreferences diverges from source of truth.

### H21 — AccountDeletionView: email stored but never sent to deactivation API (AccountDeletionReasonViewModel.kt:63)
`AccountDeletionFlow.email` set but `deactivateAccount(password)` never passes email. API contract mismatch — credential verification may fail.

### H22 — ShipmentsRepoBinding: payment lookup scans only 5 pages (ShipmentsRepoBinding.kt:239-251)
After cold deep-link, scans pages 1-5 only. Payment on page 6+ never found. Original Swift app passed payment through navigation.

### H23 — Mutable ByteArray in UploadFile data class (Uploads.kt:9-20)
`ByteArray` field without defensive copy. Data class `copy()` shares reference. Mutating copy mutates original. Breaks equality/hash contracts.

### H24 — Deep link consumed before navigation succeeds (AppRoot.kt:63)
`PushDeepLink.consume()?.let { navigate(it) }` atomically removes pending link. If `navigate()` throws → deep link permanently lost.

### H25 — ConsigneeName raw HTTP request skips auth pipeline (DropAlertRepository.kt:130-147)
Direct `OkHttpClient` request, no `AuthInterceptor`. Returns 401 if endpoint requires auth → triggers C7 critical bug.

### H26 — Featured products search never sent to server (ShopRepoBinding.kt:56-62)
Search only applied to client-side title filter on fetched page. Capped to one page of results. Server-side search not used.

---

## MEDIUM BUGS (34)

### M1 — SessionStore lost update on concurrent access (SessionStore.kt:24-26)
`_header.value = transform(_header.value)` is not atomic. Two concurrent `update()` calls → second silently overwrites first.

### M2 — ApiError timeout exceptions disguised as network errors (ApiError.kt:40)
All `IOException` subclasses map to generic "Network error." Users get no indication of timeout vs. connectivity.

### M3 — Unencoded path parameters in Routes (Routes.kt:82-87)
IDs interpolated directly into routes. Characters like `/`, `#`, `?` produce malformed navigation routes.

### M4 — Tab switch removes intermediate destinations (AppRoot.kt:132-143)
`switchTab()` pops to HOME, losing drill-down context. Back button goes to HOME instead of restoring.

### M5 — AirCoinsStatus drops tier when wrapped response (AirCoins.kt:44)
When `payload != null`, serializer always sets `tier = null`, even if payload contains tier key.

### M6 — AuctionProduct.displayPriceUsd defaults to 0.0 silently (Products.kt:63-69)
Fallback cascade returns `0.0` when all price fields null/unparseable. Products display as free with no error.

### M7 — ShipmentCalculation fields all default to 0.0 (Shipping.kt:180-212)
Cannot distinguish "shipping costs $0.00" from "server returned null." Silent zero-cost estimates.

### M8 — Package/DropAlert id defaults to 0 (Packages.kt:19, 152, 376)
Multiple items sharing `id=0` break DiffUtil, cause wrong item clicks, UI corruption.

### M9 — DataEnvelopeSerializer swallows parse errors (Envelopes.kt:112-116)
When `data` key fails to parse, entire root object decoded as T. Fields like `success`/`message` fed into wrong serializer → garbled objects.

### M10 — OrderDetailEnvelopeSerializer misses bare decode with null data (Orders.kt:128-132)
Check is `!obj.containsKey("data")`. When `{"data": null, ...}` the key exists, bare fallback skipped → null order.

### M11 — LoginResponse message extracted only from top-level (Auth.kt:86)
If backend sends `{data: {message: "Invalid credentials"}}`, error message is null, no error text shown.

### M12 — AuthBottomButtonBar lacks IME insets (AuthComponents.kt:109-137)
Button bar only pads for nav bar insets, not IME. Keyboard may hide Sign Up button. Users can't submit.

### M13 — Missing password strength validation (SignUpViewModel.kt:175-211)
No min-length or complexity check. Server error leaks raw exception message. User sees cryptic "Bad Request."

### M14 — hashCode() as LazyList key fallback (HomeScreen.kt:349)
When `id` is null, `hashCode()` used. Two distinct products can collide → visual glitches, wrong animations.

### M15 — Intent launch exceptions swallowed silently (ContactsScreen.kt:73)
`startActivity()` in `runCatching`. No app handles `tel:` intent → nothing happens, no feedback.

### M16 — Invoice file preview may fail after URI permission expires (DropAlertScreen.kt:206-217)
Content URI grant from `OpenDocument` valid only in callback. Preview after delay → `SecurityException`.

### M17 — Invoice file read failure silently ignored (DropAlertScreen.kt:500-504)
`readInvoice()` wraps entire read in `runCatching`. Null inputStream → never calls `addInvoice` → sheet closes, nothing happens.

### M18 — NotificationsViewModel unrecognized route types silently ignored (NotificationsViewModel.kt:166)
Returns `null` for unknown routes. New push type added backend → tap does nothing, no explanation.

### M19 — Shipments list missing error handlers for 3 of 5 API calls (ShipmentsViewModel.kt:49-57)
`packagesShortlist`, `paymentsShortlist`, `ordersShortlist` have no `.onFailure`. Failures silently swallowed.

### M20 — PackagesViewModel method filter triggers unnecessary server reload (PackagesViewModel.kt:107-111)
`selectMethod()` calls `load(reset=true)` for client-side-only filter. Wastes bandwidth, resets pagination.

### M21 — PaymentsViewModel debounce fires even for <3 char input (PaymentsViewModel.kt:63-69)
Any text change triggers server request after 300ms. 1-char input → expensive full-list request.

### M22 — PackageDetailsScreen reads file bytes on main thread (PackageDetailsScreen.kt:80-81)
`readBytes()` on UI thread. 10MB file → ANR. Should use `Dispatchers.IO`.

### M23 — PaymentPackageDetails timeline shows wrong completed steps (PaymentPackageDetailsScreen.kt:282-285)
`indexOfLast` matches on history dates regardless of status. Step 8 (Delivered) dated while status is 7 → all intermediate steps shown as completed.

### M24 — InvoiceViewer MIME detection based on URL path extension (InvoiceViewerScreen.kt:80-82)
CDN URLs without extension → wrong viewer. Image sent to PDF viewer, docs forced through PDF viewer.

### M25 — InvoiceViewer URLEncoder produces malformed URLs (InvoiceViewerScreen.kt:139)
`java.net.URLEncoder` is for form data encoding. Spaces → `+` instead of `%20`. Google Docs Viewer URL malformed.

### M26 — ShipmentsCartStore not thread-safe (ShipmentsContracts.kt:240-265)
`linkedSetOf<Int>()` mutated without synchronization. Concurrent coroutines can corrupt the set.

### M27 — PaymentPackageDetailsViewModel package details fetch has no error handler (PaymentPackageDetailsViewModel.kt:65-69)
Nested `.onSuccess` for details fetch inside payment fetch. No `.onFailure` — detail stays null silently.

### M28 — More2Api NPE crashes entire FAQ fetch on null question (More2Api.kt:152-153)
`it.question.isNotBlank()` throws NPE on null question. `runCatching` catches it → entire FAQ list fails, not just bad item.

### M29 — DocumentsViewModel non-atomic load guard (DocumentsViewModel.kt:78-79)
Check `_state.value.loading` and subsequent update not atomic. Two rapid calls both pass guard.

### M30 — Confirm dialog dismisses before async action completes (More2Components.kt:341-344, SettingsScreen.kt:131)
`onDismiss()` then `onConfirm()`. Async failure shows error in separate dialog with no retry context.

### M31 — ReferAFriendViewModel double invocation of loadReferredFriends() (ReferAFriendViewModel.kt:28-29,44)
Called from both `init {}` and `LaunchedEffect(Unit)`. Two simultaneous network requests, failing second overwrites successful first.

### M32 — FCM token dropped if user logged out during refresh (AirdropMessagingService.kt:35)
`if (AuthTokenStore.token == null) return` silently drops new token. User logs in moments later, push doesn't work until next `onNewToken` (weeks away).

### M33 — PushDeepLink consume() not thread-safe (PushDeepLink.kt:23)
`_pending.value.also { _pending.value = null }` two-step non-atomic. Two concurrent consumers process same deep link twice.

### M34 — ShippingRates fallback values hardcoded, no indication they are fallbacks (ShippingRatesScreen.kt:70-89)
When API fails, hardcoded rates shown silently. Pricing changes → users see incorrect rates with no awareness.

---

## LOW BUGS (14)

### L1 — Missing window insets handling (MainActivity.kt:16)
`enableEdgeToEdge()` without ensuring root composable consumes system bar insets.

### L2 — Overly broad `contains` check for login route (AuthInterceptor.kt:22)
`!url.encodedPath.contains("/auth/login")` matches unintended endpoints. Fragile.

### L3 — Unused `retrofit` property + URL normalization inconsistency (ApiClient.kt:40-46)
Second Retrofit instance never used. Different base URL normalization from `AirdropApiFactory`.

### L4 — AuthOptionSheet select-then-dismiss ordering (AuthComponents.kt:226-228)
`onSelect` before `onDismiss` → stale UI for one frame in dependent fields.

### L5 — Splash LaunchedEffect fires after disposal (SplashScreen.kt:58-61)
`delay(1600)` completes after composable destroyed → `onFinished()` called on stale nav controller.

### L6 — Onboarding rapid Next taps cause animation jank (OnboardingScreen.kt:185-191)
No debounce. Double-tap launches two `animateScrollToPage` → visible jank.

### L7 — LoginViewModel no client-side input validation (LoginViewModel.kt:32-61)
Relies solely on UI button disable. Programmatic call sends empty credentials.

### L8 — Dark-mode gradient color hardcoded (HomeScreen.kt:98)
`Color(0xFF343538)` always dark. Visible seam with theme-aware gradient in light mode.

### L9 — Empty slug passed to navigation (HomeScreen.kt:350)
`product.slug.orEmpty()` → empty string route → nav graph doesn't match → tap does nothing.

### L10 — SimpleDateFormat allocation on every list item (AirCoinScreen.kt:441-457)
New `SimpleDateFormat` per item. 100+ item list → GC pressure.

### L11 — Invoice removal uses referential equality (DropAlertViewModel.kt:96-97)
`filterNot { it === invoice }`. State reconstruction → identical data different reference → removal silently fails.

### L12 — Calculator stale navigateToResults flag (CalculatorViewModel.kt:205)
Flag set during async API call persists after navigation away. Returning to screen → navigates with stale data.

### L13 — CartStore.init() race when called concurrently (CartStore.kt:53-64)
`if (prefs != null) return` not synchronized. Two LaunchedEffect blocks could both restore.

### L14 — Scaled bitmap never recycled (MoreViewModel.kt:117-128)
`Bitmap.createScaledBitmap` creates new bitmap never `recycle()`'d. Memory accumulates.

---

## Top 10 Recommended Fixes (Priority Order)

1. **C3/C4** — Fix token refresh race condition + add reactive logout navigation
2. **C5/C6** — Add `isInitialized` guards to all `lateinit var prefs` in stores/controllers
3. **H1** — Fix country code fallback; handle all countries or make it nullable
4. **H2** — Strip `J$` and other currency prefixes in `parseFlexDouble`
5. **C1/C2** — Fix process-death persistence for checkout + calculator deep-link crash
6. **H12** — Fix overlapping coroutine race conditions in PaymentsViewModel/OrdersViewModel
7. **H5** — Use `commit()` instead of `apply()` for token persistence
8. **H8/H9/H10** — Add error state fields to all ViewModels missing them
9. **C7** — Make consignee field editable when prefetch fails
10. **C9** — Fix logout to send `{}` instead of empty body
