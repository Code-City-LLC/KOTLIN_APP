# KOTLIN_APP Verification Ledger — Problems, Cautions & Lessons

**Maintainer:** BlueDeer (Swift/Figma/device verification lane, ORC fleet) · **Updated:** 2026-07-06 (rev 11 — §1c Swift→Kotlin feature-parity audit added (#15525, lead-dev-confirmed): 2 HIGH mainline-missing (Delivery Method, Payment Success) → Kemar checkout-HOLD queue; P1-P4/P6 all closed)
**State at writing:** `origin/main` src @ `f78e514`. Since rev 5: (1) **quick-track** flow (`030c6ee`) — Track Shipment tile opens a bottom-sheet (≡ Swift `onTapStatTile:1904 presentQuickTrack`), Swift-parity confirmed. (2) A shipped PackageCard **status-color regression** (all statuses forced green, `34e9620`) + an ungated **cart affordance** (fake plus-button on every status) were caught; fixes ACKed then stalled in a merge-gap (orphaned 2nd-verifier) — flagged as [RISK] #15230 → owner rebased+merged (`f78e514`). Color ≡ Swift `statusColor(for:):576`; cart-gate `packageCanAddToCart = status 7\|\|18` ≡ Swift `:563/:724` (device-verified #15266). (3) BUG_AUDIT/SignUp from rev 5 still stand. PR #1 branch `codex/refer-friend-parity` moves fast — always `git ls-remote` before citing its head.
**Source-of-truth hierarchy (Kemar rulings #14540/#14553/#14578):** Swift app (`/Users/codecityceo/Documents/GitHub/SWIFT_APP`, `Figma*ViewController.swift`) = behavior + PRECEDENCE → Figma (fileKey `N4k6jzpeLZgeRS5O1xfyIv`) = visual reference → Laravel = API contract. Where Swift does not ship a screen, Figma is the authority. **Buttons must function; no fake/dead pages; no duplication; verify before closing (#14639).**

> How to use this doc: before touching any screen listed here, read its entry. The CAUTIONS section is a do-NOT-do list — several "obvious fixes" below are traps that were investigated and rejected with evidence.

---

## 1. OPEN PROBLEMS

### P0 — 🔐 SECURITY: hardcoded Trengo live-chat widget key in BOTH apps (KEMAR #14792 — remove Trengo → Autopilot/Hermes)
- **Key:** `window.Trengo.key` = a Trengo **client-side widget** key (redacted `VEoe…`, len 15) — not a REST/server secret, but **hardcoded, shipped-in-binary, and in git history of both repos.** True invalidation = **rotate the widget key in the Trengo dashboard** (source scrub alone won't undo the exposure).
- **Swift footprint** (all in `Airdrop/FigmaRouteViewController.swift`): key :1051 · `embed.js` :1057 · `loadHTMLString` baseURL airdropja.com :1068 · route case `"LiveAgentChatView"` :808 → VC class :942-1074. The route is **orphaned** in Swift (no in-app callers), but route strings arrive dynamically from push/server → **retarget** the :808 case to the replacement, don't just delete.
- **Kotlin footprint:** [LiveAgentChatScreen.kt](app/src/main/java/com/ga/airdrop/feature/contacts/LiveAgentChatScreen.kt) (key :47 in `TRENGO_HTML` :40-63, `embed.js` :53, `Trengo.Api.Widget.open` :55, WebView baseURL airdropja.com ~:128, KDoc :34-37) · [Routes.kt](app/src/main/java/com/ga/airdrop/core/navigation/Routes.kt):25-26 · [AppRoot.kt](app/src/main/java/com/ga/airdrop/core/navigation/AppRoot.kt) composable · **branch-only** `RouteResolver.kt:19` + `PushDeepLinkParityTest.kt:39` (added by `c403099`) · docs `HANDOFF.md:40` (key verbatim).
- **Reachability:** origin/main = **dead** (no resolver, no Contacts card); PR branch = push-deep-link reachable; the **uncommitted working-tree** rework of `ContactsScreen.kt` (:107-129) adds a **tappable Live Chat card** → whoever removes Trengo must reconcile that local rework.
- **Replacement rail:** `hermes`/`autopilot` = **ZERO hits in both repos** — the Autopilot+Hermes routing does not exist yet. Per Kemar rule #5 **do not guess** the replacement API; get the spec first.
- **DO NOT remove:** call/email/WhatsApp/location Help-tab actions, and `FigmaIcon_Chat`/`FigmaIcon.chat` notification-category icons — those are NOT Trengo.
- **My role:** inventory-only (verifier). A senior agent owns key-rotation + the Hermes swap; I verify "no Trengo key remains + support/chat flow intact" once removal lands. (Full 33-ref map: ORC [TRENGO INVENTORY] post.)

### P1 — ✅ FIXED & DEVICE-VERIFIED (2026-07-06) — Featured Product Details "Product not found"
- **Was:** Shop → Feature Products → tap any product → "Product not found" for EVERY featured product. Root cause ([ShopRepoBinding.kt:77](app/src/main/java/com/ga/airdrop/feature/shop/ShopRepoBinding.kt) `productBySlug`): featured slugs 404 on `GET /products/{slug}`; `featured-products?slug=` 200-but-empty (Laravel `/featured-products` has no show route). Chain ended in `error("Product not found")`.
- **Fix:** commit **`14d81d8`** "Hand tapped product into details like Swift (LEDGER P1)". `ShopProductHandoffStore` ([ShopData.kt:175](app/src/main/java/com/ga/airdrop/feature/shop/ShopData.kt), one-shot `consume` keyed by `routeSlug` :184) — `ProductListScreen.kt:187` `put(product)` before nav; `AuctionProductDetailsViewModel.load():46` `consume(slug)` FIRST → renders the handed product and **skips** the broken slug re-fetch (fallback only when store empty). Swift-style pass-from-list.
- **Device-verified:** built origin/main `14d81d8`, emulator-5554/Tamzid — tapped AstroAI Tire Inflator (the exact repro): full detail renders (image, title, model, $31.99, qty, stock, description, Purchase). OkHttp shows **no `GET /products/{slug}` 404** on detail open (hydrated from the store). No regression. **CLOSED.**

### P2 — ✅ FIXED & VERIFIED (2026-07-06) — Preferences read-only Email field fill (gray100 → gray300)
- **Fix:** commit **`7bdad92`** "Preferences disabled-field fill + Documents info-icon (ledger P2/P3)". [MoreComponents.kt:278](app/src/main/java/com/ga/airdrop/feature/more/MoreComponents.kt) `MoreSelectField` field Row: `.background(if (enabled) colors.gray100 else colors.gray300)` — exactly the documented 1-liner.
- **Verified (code):** ≡ Swift `FigmaPreferencesViewController` (`!isEditable → gray300`) + Figma 40000994:19044. Blast radius correct — only the Preferences "Email Address" row passes `enabled=false` (code comment confirms; DropAlert Consignee uses `CalcInputField`, ProfileScreen's fields are all editable). Verifier #15502. Device L+D on next build. **CLOSED.**

### P3 — ✅ FIXED & VERIFIED (2026-07-06) — Documents card info icon (squircle → circle)
- **Fix:** same commit **`7bdad92`**. [DocumentsScreen.kt:252](app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt) `ic_info` → `R.drawable.ic_calc_info_circle` (circle).
- **Verified (code):** ≡ Figma 40000975:7748 (circle) + Swift `FigmaDocumentsViewController` `.infoCircle`. **Screen-specific** (Documents only — per **C9**, NOT a blanket `ic_info` swap; Warehouses/Calculator-BlueInfoCard keep the squircle). This is the icon-SHAPE fix; the Documents info POPUP-behavior is a bottom sheet by ruling #15410 (RN-parity, Figma-silent-on-opened-state) — independent, both correct. Verifier #15502. **CLOSED.**

### P4 — ✅ FIXED & VERIFIED (2026-07-06) — SignUp identity registration fields
- **Fix:** commit **`7154978`** "Fix SignUp identity registration fields" (Codex). [Auth.kt](app/src/main/java/com/ga/airdrop/data/model/Auth.kt) adds `user_trn_number` / `user_identity_type` / `user_identity_number` **and** legacy misspelled `indentity_type`; [SignUpScreen.kt](app/src/main/java/com/ga/airdrop/feature/auth/SignUpScreen.kt) adds TRN / ID Type / ID Number fields; `signUpIdTypeOptions = ["National ID","Drivers License","Passport"]`; parity test `SignUpScreenIdentityParityTest.kt`.
- **Verified vs Swift + Laravel (code-level, 3 repos):** field names match Swift register payload (`AirdropAPI.swift:1621-1623` / `:1696-1698`); ID options match Swift (`FigmaAddAuthorizedUserViewController.swift:39`). Both apps `POST /api/v1/auth/register` (`routes/api.php:85`).
- ⚠️ **CORRECTION — rev-4's P4 note was BACKWARDS.** The V1 register endpoint reads the **misspelled** key: `RegisterRequest.php:36` validates `'indentity_type'` (Laravel's own comment: `// ! Note: typo in field name matches existing API`) and `AuthController.php:200` does `'user_identity_type' => $request->input('indentity_type')`. Sending ONLY the correctly-spelled key drops the value. Kotlin sends BOTH → correct. See **C11**. **CLOSED.**
- 🔎 **Swift-side bug (courtesy finding, code-level):** Swift sends ONLY `user_identity_type` to the same endpoint → the ID *type* is **silently dropped on register** (TRN + identity_number use matching keys, so only identity_type is lost). Flagged to Swift owners in ORC.

### P5 — Device verification pending on PR #1 merge to main
- `eac8248` placeholder-tap guard: needs an **empty-shipments account** to render placeholder cards (the standing test account has real shipments → not reproducible on it). Code-verified only.
- `c8a99b1` invoice-delete gate: needs a status<7 package (delete visible) AND a status≥7 package with invoices (delete hidden). Code-verified only, hash-tied.
- `148a509` Payments "View History" CTA color (`textDarkTitle`): code-verified, not device-seen.
- All visible changes on the branch (Home hero `9ea44e6`, chrome rails `efef7f1`, header+AirCoins `99068c8`, Refer spacing `5ddc57a`/`c7944f0`/`a07abbe`, FAQ `25ea84b`, deep links `53c3ccb`) need L+D device passes after merge.

### P6 — 🔴 REGRESSION (confirmed): Notifications inbox LIST deleted at `22657cf` — only empty-state renders
- **Confirmed on current `origin/main`** (independent of TealSnow lead-dev work order R1, #15293): [NotificationsScreen.kt](app/src/main/java/com/ga/airdrop/feature/homedetails/NotificationsScreen.kt) now renders **only `EmptyState`** (`:76` call, `:86` def) — NO `LazyColumn`/`items`/`NotificationRow`. So the inbox shows the empty-state **even when notifications exist**. `NotificationsViewModel` (pagination / markRead / route-resolution) is referenced only in a comment (`:58`) → **orphaned, zero list-callers**.
- **Cause:** commit **`22657cf`** "Fix Notifications Swift parity" (Codex, 2026-07-06 05:54) deleted **306 lines** of `NotificationsScreen.kt` on a STALE Swift reading. Current **Swift `origin/main` renders a populated list** (`FigmaNotificationsListViewController.swift:370` fetch, `:328` empty-state only when actually empty, `:617` markRead, `:625` deep-link).
- **⚠️ LEDGER CORRECTION:** the §3 "Notifications empty-state" **3W verified-good** entry was INCOMPLETE — my device pass saw only the empty-state (standing test account had no notifications) and did not catch that the populated-list path was gone. Downgraded (see §3 note).
- **Fix (owner = whoever claims R1; verifier = me):** restore the pre-`22657cf` list rendering (`git show 22657cf~1`), keep empty-state as the items-empty branch only, replace `NotificationsParityTest` `assertNoBackendInboxSurface` with populated-list assertions, honor **C5** per-type icons (#14729). I'll device-verify L+D on a notification-bearing account once the fix lands.

---

## 1b. MISSING-FUNCTIONS SWEEP — CLOSED 2026-07-06 (TealSnow; do not re-derive)

RN→Kotlin function sweep is **complete: all 66 RN useCases accounted for** (50 `modules/` + 15 `modules_old/` + `getExchangeRate`). Evidence in ORC msgs #14798/#14859/#14880. Key verdicts so anyone re-checking starts from facts:

- **The ONE true gap found:** token-refresh 401 recovery — RN `refreshToken.ts` + Swift `AirdropAPI:347/:678` (single-flight) existed; Kotlin's `POST auth/refresh` had ZERO callers. Fixed (AuthInterceptor + TokenRefresher, 5 scripted-chain JVM tests) and now landed on main with the foreground-refresh + reactive-logout follow-up.
- **Rejected as gaps (do not reopen without new evidence):** Calculator product search (Kotlin `CalculatorViewModel.kt:76` implements Swift's 500ms/≥3-chars/top-8 spec); `restorePassword` (= forgot-password email trigger; reset-password UI exists in NEITHER RN nor Swift — typed wrapper only, web flow).
- **Stale-audit warning:** `SWIFT_APP/FUNCTION_AUDIT.md` + `AIRDROP_RN_TO_SWIFT_FULL_AUDIT.md` (both 2026-05-22) list gaps that are ALL closed on current heads — grep evidence in #14798. Do not use them as to-do lists.

## 1c. SWIFT→KOTLIN FEATURE/SCREEN PARITY GAPS — audit 2026-07-06 (BlueDeer #15525; lead-dev-confirmed #15554)

13-feature adversarial audit (each feature: read Swift VC + searched Kotlin before ruling). A **screen/feature** axis — orthogonal to §1b's RN-useCase sweep. TealSnow independently re-verified HIGH-1 and escalated both HIGHs to Kemar (#15554).

- 🔴 **HIGH — mainline-missing, in Kemar's decision queue (behind the checkout HOLD, C7):**
  - **Delivery Method** — Swift `FigmaDeliveryMethodViewController` (~2047 lines): pickup/delivery tiles, warehouse radio list, address search + interactive map (draggable marker) + reverse-geocode + validate-location fee preview + "Use Current Location" + Choose-Currency popup → JMD=Profile / USD=Order-Summary. Kotlin cart (`CartViewModel.onMakePayment`) jumps STRAIGHT to Stripe. Data layer ported but **ORPHANED**: `Delivery.kt`/`DeliveryRepository.kt`/`AirdropApiService:388-410` have **zero consumers**.
  - **Payment Success** — Swift post-Stripe success screen via a session-verifying deeplink (order ref + amount). Kotlin: no success screen/copy, `MainActivity`/`PushDeepLink` don't handle the payment-success deeplink.
- 🟠 **MEDIUM — missing (queued):** **Barcode Scanner** (Quick-Track camera scan; `QuickTrackInput` is type-only — camera-dep decision queued); **Saved For Later** (cart parked-list — RubyHeron first refusal); **Upload Source sheet** (file/photo/camera picker + in-app camera + image compress before invoice/doc upload; Kotlin uses the system picker directly).
- 🟡 **PARTIAL:** **Payment Filter** (works but renders the old action-sheet, not the bottom sheet); **My Cart "Your Note"** (Kotlin inline field vs Swift row+popup — @RubyHeron branch `3b810b3`); **Result Modal** (`AuthSuccessSheet` rich port but auth-scoped; DropAlert-success degrades to a plain AlertDialog).
- ⚪ **NOT bugs — do NOT chase:** **Biometric Lock** + **Active Sessions** = **deliberately excluded** (Kotlin `AboutScreen.kt:46-51` documents "until Kemar wants them in Kotlin"). **Report Damage** = absent here BUT **ships dark in Swift too** (feature-flag OFF, no Laravel `damage-reports` endpoint).
- ✅ **False alarms (full parity):** AirCoin History (`AirCoinHistoryDetailScreen`), Document Downloading.

**My role:** verifier — found+documented (read-only). Implementation = Kemar/fleet; I verify on landing.

## 2. CAUTIONS — DO NOT DO THESE

### C1 — Do NOT swap the shared `BlueInfoCard` icon (Calculator flow) ⚠️ investigated & rejected
`BlueInfoCard` ([CalculatorUi.kt](app/src/main/java/com/ga/airdrop/feature/calculator/CalculatorUi.kt), `ic_info` squircle) is shared by **Calculator-main (40001464:29102), Calculator Results (40001817:19439) and Government Charges (40001817:20681)**. Government Charges is **Figma-authority** (Swift does not ship it; reached from the Results disclaimer link) and Figma shows the disclaimer icon as a **SQUIRCLE on all three frames**. The CIF Value info button already uses `ic_calc_info_circle` (circle) matching Figma. The only residual is Swift `FigmaCalculatorViewController:268/:507` using `infoCircle` on the main screen — a Swift-vs-Figma tie on a shared component; honoring it would need a per-call icon param AND would break Government Charges parity. **Resolution: NO CHANGE.** (ORC #14613/#14620; evidence handed to SwiftOwl.)

### C2 — Chrome (header/footer) is OPAQUE by ruling
The old translucent/`SCRIM_ALPHA` locks are **DEAD** — Kemar revoked the Figma-supreme chrome exception; opaque `gray200` per Swift accepted at `2af3110`. Do not restore any alpha band or flag opaque chrome as a regression.

### C3 — Refer-a-Friend is current Swift + Figma landing
Current Swift `FigmaReferAFriendViewController.swift` and Figma node `40001940:26885` agree on the Refer landing: three carousel cards, exact copy `Earn $2 USD Per Invite`, body copy, and one bottom `Invite` CTA. The live Swift CTA pushes `InviteFriendView`, so Android should route to the dedicated Invite Friend / Send Invitation flow. Do **not** restore the old referral-link card, Copy action, inline `Invite Friends` CTA, or embedded `Your Referrals` list on the Refer landing; keep referral history on the separate route.

### C4 — Warehouses: keep the tab strip; ignore two Figma errors
- ONE screen with Standard/SeaDrop/Express tabs = **approved Kemar deviation** (2026-05-22). Audits must NOT remove the tabs.
- Figma 40000944:3571 Express subtitle "2 to 3 weeks" is a **mockup error** — Swift+Android "1 to 2 business days" is correct.
- The "Please note" card **correctly** uses the squircle `ic_info` (Swift `FigmaIcon.info` + Figma agree). Do not "fix" it to a circle.

### C5 — Per-status / notification icons must not regress (KEMAR #14729)
Every notification/status keeps its **own specific icon** (invoice-required, package-ready-for-pickup, shipment-receipt, customs-processing, …). Mapping lives in Kotlin `ShipmentStatusCatalog.iconRes` ([ShipmentsUi.kt](app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt)) ↔ Swift `FigmaPackagesFilterViewController.statusIcon(for:)`. Figma 40001753:15716 timeline corroborates. Replacing these with a generic icon = regression; Kemar explicitly ordered no-regress.

### C6 — Stale/aspirational Figma frames — do NOT "fix" the app toward them
| Figma frame | What it shows | Reality (wins) |
|---|---|---|
| 40001428:9188 Payment Methods | PayPal/ApplePay/VISA card-manager + "Add New Card" | **Never built.** Swift ships an informational empty-state ("No saved payment methods" + "Go to Checkout" → cart). Android matches Swift. |
| 40001383:9894 / 40001387:9042 Terms & Privacy | "etoy app"-style placeholder copy | Cold-start fallback = Swift/RN `sectionsData` verbatim; live `/content/*` API replaces it. |
| 40000994:19044 Preferences | red `*` asterisks, gradient Save | Swift has no asterisks and a SOLID orange Save — Android correct. |
| 40000975:7748 Documents | card order, Upload-disabled-with-file | Swift order + Upload-always-enabled win. |
| 40001940:26885/26797 Refer | landing carousel | See C3 — current Swift and Figma agree on this page. |
| 40001464:30381 SeaDrop calc info copy | differing delivery copy | Swift wins ("2 to 4 weeks…"). |

### C7 — Restricted boundaries (standing)
- **Payment / checkout / account-deletion = HOLD, view-only.** Verify wiring by code; do NOT exercise checkout, submit forms, or trigger deletes/mutations (invoice-delete gating is verified read-only).
- **Shipments payment-placeholder navigation** is on a Kemar hold (restricted-boundary decision pending); `eac8248`'s inert guard deliberately avoids payment-detail navigation.
- Prod backend (`com.ga.airdrop.app` / app.airdropja.com) off-limits. Test credentials live in the ORC `test-credentials` topic — **never commit them**.
- KOTLIN_APP pushes go only to `origin main`; AIRDROP-LARAVEL merges only via GreenPuma to `pre_staging`.

### C8 — Shipments hub placeholder cards are intentional
When packages/payments lists are EMPTY, the hub shows Swift's sample cards (Earpod/Scrubber/MacBook, ids −1/−2/−3; payments ARD00000057961/2) — layout keep-alive per `FigmaShipmentsViewController`. Do not "fix" them away and do not route their taps to detail-by-id: taps are inert-guarded (`eac8248`; Swift opens an id-less empty shell — accepted deviation, safer under C7). The cart affordance on a placeholder opens the Cart.

### C9 — The two info glyphs (check BOTH sources per screen before any swap)
Swift has **two** info glyphs in `FigmaIcons.swift`: `FigmaIcon_Info` (**squircle**, :1352) and `FigmaIcon_InfoCircle` (**circle**, :1498). Android: `ic_info` = squircle, `ic_calc_info_circle` = circle.

| Screen (Swift call site) | Swift glyph | Figma shows | Correct Android icon |
|---|---|---|---|
| Warehouses "Please note" (:681) | `.info` | squircle | `ic_info` ✅ keep |
| Calculator BlueInfoCard (main :268/:507 · Results/GovCharges no-Swift) | `.infoCircle` (main only) | **squircle** (all 3 frames) | `ic_info` ✅ keep (C1) |
| CIF Value button (Results/GovCharges) | `.infoCircle` | circle | `ic_calc_info_circle` ✅ |
| Documents cards (:271) | `.infoCircle` | circle | `ic_calc_info_circle` — **deferred fix P3** |
| DropAlert (:252/:299) · Cart (:657) · PackageDetails (:730) · PaymentPackageDetails (:487) · RestrictedItems (:59) · AuctionCheckout (:347) | `.infoCircle` | **verify per-screen** | check Figma before any swap |

**Lesson:** a shared Compose component can serve screens with different authorities. Check EVERY consumer (especially Figma-authority screens with no Swift) before flagging or swapping.

### C10 — Do NOT revert Kotlin `parseMoneyString` to Swift's trim logic (Kotlin is intentionally ahead)
`parseMoneyString` ([Flexible.kt](app/src/main/java/com/ga/airdrop/data/model/Flexible.kt), BUG_AUDIT H2 `5e459e5`) strips all but digits/`.`/`-`. Swift's `decodeFlexibleDouble` (`AirdropAPI.swift:6814-6826`) and `AuctionProduct.currencyDouble` (`:2081-2084`) still use `replacingOccurrences(",","").trimmingCharacters(in:"$ ")`, which only strips a leading `$`/space — so `"J$1,550.00"` / `"US$156.50"` → nil → **Swift renders JMD/US$ prices as $0.00.** Kotlin's fix diverges deliberately. Reverting it toward "source-of-truth parity" would restore the $0.00 bug. This is a documented exception to Swift-precedence (a Swift bug, not a contract). USD `"$31.99"` → 31.99 unchanged. *(BUG_AUDIT pass `3df8b75` — H2/H30 atomic-cart/H8 callTimeout/401-retry — 4-agent adversarially verified 0 my-lane regressions.)*

### C11 — The SignUp `indentity_type` misspelling is LOAD-BEARING — do NOT "fix" the typo
The V1 register endpoint (`POST /api/v1/auth/register`) validates and reads the **misspelled** `indentity_type` (`RegisterRequest.php:36` with Laravel's own `// ! Note: typo in field name matches existing API`; `AuthController.php:200` `$request->input('indentity_type')`). Kotlin's SignUp (`7154978`) correctly sends BOTH `user_identity_type` and `indentity_type`. Removing the misspelled `@SerialName("indentity_type")` (Auth.kt) would silently drop the ID type on registration — exactly Swift's latent bug (P4). Keep both keys.

---

## 3. VERIFIED-GOOD LEDGER (do not re-audit without cause)

Verification levels: **D-L/D-D** = device light/dark seen · **3W** = Figma node + Swift code + device · **C** = code+Figma (device blocked/N-A, reason noted).

| Screen | Figma node | Level | Notes |
|---|---|---|---|
| Shipments placeholder-tap guard | — | C (`eac8248`) | device needs empty-list acct (P5) |
| Package Details invoice-delete gate | — | C (`ed1b534`→`cd3e497`) | `canDeleteInvoices` ≡ Swift `canDeleteInvoices(for:)` (status≥7 + name ready/pickup/delivered/complete); hidden+guarded; upload ungated. `cd3e497` **tolerance-hardened** ≡ Swift `5496ed0 statusLocksInvoiceDeletion`: numeric-≥7 lock checked on BOTH `status`+`statusName` with comma-decimal/floating normalize — rule preserved (Kemar #15084) |
| Package Details charges + Add-to-Cart gate | — | C (`3184b9e` on main, BrownHawk) | `showChargesAndCart = statusInt==7\|\|==18` ≡ Swift `showCharges` :1265 (NOT ≥7). SEPARATE predicate from delete (which stays ≥7). Verified vs Swift origin/main `dc8a0e3` |
| BUG_AUDIT hardening pass (my-lane) | — | C (`3df8b75`; H2 `5e459e5`/H30 `1a01165`/H8 `97326d5`/401 `0dd7e42`) | 4-agent adversarial verify, 0 Shop/Cart/PkgDetails/Shipments regressions; H2 fixes JMD/US$ $0.00 (see C10) |
| SignUp identity registration fields | — | C (`7154978`, Codex) | fields+options match Swift; handles Laravel V1 misspelled `indentity_type` (P4 CLOSED, C11) |
| Shipments quick-track flow | 40000823:9633 (`Shipment`) | C (`030c6ee`) | Track Shipment tile → `openQuickTrack` bottom-sheet (NOT push Packages) ≡ Swift `onTapStatTile:1904 presentQuickTrack`; submit-by-code→detail ≡ Swift `submit:460/518`; other tiles push. SageSpring-ruled #15212 |
| PackageCard cart-gate | — | **D** (`f78e514`, #15266) | `packageCanAddToCart = status 7\|\|18`, plus-button omitted otherwise ≡ Swift `FigmaPackagesViewController:563/:724` (Kemar round-11). DEVICE: status-2 cards show no per-card cart button. Fixes ungated fake affordance |
| PackageCard status color | — | C+test (`f78e514`, #15266) | `ShipmentsUi.kt:707 packageStatusColor(pkg.statusName?:pkg.status)` ≡ Swift `statusColor(for:):576` (39a634/0049d9/d92a2a/b8b8b8/f2a813 L+D); instrumented captureToImage test + SageSpring #15246. Fixes shipped all-green regression `34e9620`. Device A/B not achievable (test acct all status-2) |
| Package Details Shipment Timeline | — | **D-L** (`0fc2324`, #15314) | `PackageTimelineProgression` renders visible-status prefix through current status ≡ Swift `PackageStatusCatalog`. DEVICE (pkg ARD00000149880 status-2): rows `[Drop Alerted→Shipment Received]` (future hidden); per-status icons `ShipmentStatusCatalog.iconRes` (#14729); colors past=green `39a634` / current-id≤4=orange `F07F17`; green connector. SageSpring-ruled #15298. Dark = theme-aware AlertPalette (code) |
| Notifications inbox list + per-type icons (P6/C5) | — | C (`3e0aaf9`+`cd3e497`, #15325/#15379) | R1 list restored (`3e0aaf9`): ViewModel wired, LazyColumn+items, empty-state=items-empty-branch-only ≡ Swift `:328`. C5 (#14729) per-type icons (`cd3e497` `NotificationIconCatalog`) ≡ Swift `notificationIcon(for:)`:424 (invoice/paid-pickup/ready/received/MIA-3/JAM-4/delivered-8/detained-10/customs/transit-12/auction-17/dangerous-16/payment/document/promotion-bell/else-packages). Device list L+D pending notif-bearing acct |
| Restricted Items legal-info page | — | C (`569f5c3`, fleet+RestrictedLegalInfoTest) | new missing-function page ≡ Swift `FigmaRestrictedItemsLegalInfoViewController` (pushed from list :201); CalmGlacier Swift-parity `813030d`. Not a regression to C9 info-icon items |
| Calculator (main) | 40001464:29102 | C | icon question resolved (C1) |
| Calculator Results | 40001817:19439 | C | Fuel row ✓, CIF circle ✓; Android uses Figma CIF bottom-sheet (40001817:20191) vs Swift native alert — accepted platform adaptation |
| Government Charges | 40001817:20681 | C (Figma-authority) | Swift doesn't ship it; 3-row charges (no Fuel) is correct here |
| Gold Priority / Customer Tier | 40001432:23506 | 3W | 7-tier pager diamond-first = Swift CODE order (its comment lists a different order — trust code); pre-scrolls to user tier |
| Sales Taxes / Ship Tax | 40001531:11704 | 3W (D-D) | 6-step how-to; copy verbatim |
| Services | — (no Figma frame exists) | Swift+D-L | marketing page; planning node 40000798:7711 is a Shop layout, not this screen |
| Warehouses | 40000944:3571 | 3W (D-L) | see C4 |
| Privacy Policy | 40001387:9042 | 3W (D-L) | live `/content/privacy-policy` render |
| Terms & Conditions | 40001383:9894 | 3W (D-L) | live render; sub-items display-only = Swift parity; double intro heading is backend+Swift-consistent, not a bug |
| Payment Methods | 40001428:9188 | 3W (D-L) | Figma frame unbuilt (C6); Swift empty-state is the real page |
| Auction Product Details | 40002072:24025 | 3W | |
| Shop root / ProductList / Cart empty | 40001846:53519 / — / 40008284:26547 | 3W | |
| Edit Profile / Preferences | 40007189:63763 / 40000994:19044 | 3W | Preferences email-fill deferred (P2) |
| Refer a Friend / Invite Friend | 40001940:26885/26797 + Swift route (C3) | 3W (D-L/D-D) | Refer landing is carousel; Invite opens dedicated flow |
| Documents | 40000975:7748 | 3W (L+D) | info-icon deferred (P3) |
| Authorized Users / Promotions / Shipping Rates | 40001185:4935 / 40001646:14035 / 40001567:54206 | 3W (L+D) | |
| Chrome (header/footer) | — | 3W (L+D) | opaque per C2 |
| PackagesFilterSheet / PaymentPackageDetails timeline | — | 3W | |
| Restricted Items / FAQs | 40001432:14025 / 40001387:8896 | 3W (L+D) | |
| Drop Alert | 40001826:22497 | 3W (D-L) | form view-only (C7) |
| Notifications ~~empty-state~~ / Notification Settings | 40007174:63447 / — | ⚠️ DOWNGRADED | **inbox LIST deleted (`22657cf`) → P6 REGRESSION**; only the empty-state was device-seen (test acct had no notifications). Notification Settings toggle still OK. Re-verify list on fix + notification-bearing acct |
| Account Deletion endpoint | — | C (`65c11bb`) | `POST auth/login` fix; restricted flow (C7) |
| Bottom-tab icons / AirCoins / Help / Home hero cards | — | 3W / Codex-verified | |

---

## 4. OPERATIONAL LESSONS (emulator, Figma MCP, ORC bus)

### Emulator (emulator-5554, staging build)
- **Session loss:** repeated `force-stop`/relaunch chains (esp. after a Google-Lens hijack) can log the app out → auth landing. Recover by re-logging with the standing test account (ORC `test-credentials` topic). `adb install -r` **without** force-stop preserves the session.
- **Google Lens hijack:** if a dump shows "Couldn't connect to Discover"/launcher icons, `am force-stop com.google.android.googlequicksearchbox` then `am start` the app **once** and poll past the "Welcome to AirDrop" splash (match "Good Morning").
- **Gesture-nav zone:** taps near y≈2346 (Home grid bottom labels) drop the app to the launcher. Tap the card body (~y2280) instead; always dump-verify the resulting screen and that focus is `com.ga.airdrop` after each tap.
- **More-hub scroll drift:** row y-coords go stale after BACK from a sub-screen — fresh `uiautomator dump`, compute the row center, then dump-verify the header you landed on.
- Pushed sub-screens have NO tab bar — BACK returns to the hub/tab root. Space taps 1.5–2s apart; rapid taps glitch Compose nav (recover: HOME + `am start`, session preserved).

### Reading the sources
- **⚠️ Verify Swift against `SWIFT_APP` origin/main, NOT a local branch working tree.** A local checkout can sit on a stale branch (seen 2026-07-06: `staging/figma-redesign-testflight` @ `b87e56d`, 1295 lines — **485 lines behind** Swift `origin/main dc8a0e3`, 1780 lines). Reading the stale tree makes correct Kotlin parity look like a "regression/fabrication" (e.g. `canDeleteInvoices`, `showCharges == 7 || == 18` are absent on the stale branch but present on origin/main at `:1475`/`:1265`). **Always `git -C SWIFT_APP fetch && git show origin/main:<path>`** (or checkout origin/main) before citing Swift line numbers. Swift origin/main HEAD = `dc8a0e3` (contains `ccb55a1`/`deb1327`); it moves — re-check.
- **Trust Swift CODE, not comments.** Examples: Gold Priority's header comment lists a different tier order than the rendered `tiers` array (`FigmaGoldPriorityViewController.swift:57`); node IDs in Swift comments can resolve to sub-components/wrong frames.
- **Figma frames can be stale or aspirational** (see C6) — never treat a Figma diff as a defect without checking the Swift code; never treat Swift as a defect on a screen Swift doesn't ship (Figma-authority).

### Figma MCP
- `get_screenshot` works with **view** access; `get_metadata` requires **edit** access — agents hitting "no editor access" should use screenshots (or ask BlueDeer to pull a node).
- Always record the node ID used in each audit.

### ORC / Agent Mail bus
- `send_message` can **time out while the message still lands** — check the topic before retrying (avoid duplicate posts); use 60s timeouts.
- API shapes: `fetch_topic {project_key, topic_name, limit, include_bodies}` · `send_message {sender_name, sender_token, project_key, to:[], broadcast:true, topic, subject, body_md}` · `fetch_inbox {registration_token, project_key, agent_name, limit}`.
- **Hash-tie your proofs** to the actual remote branch (`git ls-remote`), not `/tmp` copies — unverifiable-hash claims get rejected.
- Governance rulings can be **reversed** (chrome changed; Refer was revalidated against current Swift + Figma). Before enforcing any remembered lock, check the CURRENT ORC ruling; escalate, don't edit-war.

### Verification discipline (#14639)
- Device proof over code inspection for interactive rails; when the device path is genuinely unreachable (needs special account state, screen not on main yet), say so explicitly and mark the verification level honestly — no fast done-claims.
