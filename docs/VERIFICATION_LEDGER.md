# KOTLIN_APP Verification Ledger — Problems, Cautions & Lessons

**Maintainer:** BlueDeer (Swift/Figma/device verification lane, ORC fleet) · **Updated:** 2026-07-06 (rev 2 — added P0 Trengo security)
**State at writing:** `origin/main` = `22657cf` · PR #1 branch `codex/refer-friend-parity` = `c403099` (DRAFT, hold-merge pending Kemar restricted-boundary ruling; branch moves fast — always `git ls-remote` before citing its head)
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

### P1 — 🐞 Featured Product Details: "Product not found" for EVERY featured product (TOP, unassigned)
- **Repro:** Shop → Feature Products → "View More" → tap any product (e.g. AstroAI Tire Inflator) → error state.
- **Root cause** ([ShopRepoBinding.kt:77](app/src/main/java/com/ga/airdrop/feature/shop/ShopRepoBinding.kt) `productBySlug(slug, featured)`): featured slugs are not general products, so `GET /products/{slug}` → **404**; the fallback `featured-products?slug=` returns **200-but-empty** (Laravel `/featured-products` has only index+stats — **no show route**, and the index slug-filter is ineffective). The chain ends in `error("Product not found")`.
- **Fix (Swift-style):** pass the `ShopProduct` object from the featured **list** into the detail screen (Swift `FigmaFeatureProductDetailsViewController(product:)`), bypassing the slug re-fetch. Do NOT try to fix by re-fetching — there is no endpoint for it.
- **Verify after fix:** device repro path above + OkHttp logcat (`adb logcat -d | grep -iE 'okhttp|products/'`).

### P2 — Deferred: Preferences read-only Email field fill (gray100 → gray300)
- Swift `FigmaPreferencesViewController` PreferenceRow: `if !isEditable { card.backgroundColor = gray300 }`. Figma 40000994:19044 agrees (grey disabled fill). Android `MoreSelectField` uses `gray100` for all fields.
- **1-line fix** in [MoreComponents.kt](app/src/main/java/com/ga/airdrop/feature/more/MoreComponents.kt): `.background(if (enabled) colors.gray100 else colors.gray300)`. Blast radius = only the Preferences email field.
- **Blocked:** MoreComponents.kt has a large unpushed local rework (theme-aware icons) in a parallel session. Apply only after that lands; do not push a competing version.

### P3 — Deferred: Documents card info icon (squircle → circle)
- [DocumentsScreen.kt:249](app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt) (origin/main) uses `ic_info` (rounded-square). Swift `FigmaDocumentsViewController:271` uses `FigmaIcon.infoCircle` (circle); Figma 40000975:7748 shows a circle — both agree.
- **Fix:** `ic_info` → `R.drawable.ic_calc_info_circle` (drawable exists). **Blocked** on the same unpushed DocumentsScreen.kt rework as P2.
- ⚠️ Re-verify the Figma frame before applying (see C1 — the Calculator taught us Figma sometimes wants the squircle).

### P4 — SignUp ID Type payload key
- The SignUp ID Type must serialize as `user_identity_type` (NOT `indentity_type`). Tracked as task_d262dc97; device-verify on land.

### P5 — Device verification pending on PR #1 merge to main
- `eac8248` placeholder-tap guard: needs an **empty-shipments account** to render placeholder cards (the standing test account has real shipments → not reproducible on it). Code-verified only.
- `c8a99b1` invoice-delete gate: needs a status<7 package (delete visible) AND a status≥7 package with invoices (delete hidden). Code-verified only, hash-tied.
- `148a509` Payments "View History" CTA color (`textDarkTitle`): code-verified, not device-seen.
- All visible changes on the branch (Home hero `9ea44e6`, chrome rails `efef7f1`, header+AirCoins `99068c8`, Refer spacing `5ddc57a`/`c7944f0`/`a07abbe`, FAQ `25ea84b`, deep links `53c3ccb`) need L+D device passes after merge.

---

## 2. CAUTIONS — DO NOT DO THESE

### C1 — Do NOT swap the shared `BlueInfoCard` icon (Calculator flow) ⚠️ investigated & rejected
`BlueInfoCard` ([CalculatorUi.kt](app/src/main/java/com/ga/airdrop/feature/calculator/CalculatorUi.kt), `ic_info` squircle) is shared by **Calculator-main (40001464:29102), Calculator Results (40001817:19439) and Government Charges (40001817:20681)**. Government Charges is **Figma-authority** (Swift does not ship it; reached from the Results disclaimer link) and Figma shows the disclaimer icon as a **SQUIRCLE on all three frames**. The CIF Value info button already uses `ic_calc_info_circle` (circle) matching Figma. The only residual is Swift `FigmaCalculatorViewController:268/:507` using `infoCircle` on the main screen — a Swift-vs-Figma tie on a shared component; honoring it would need a per-call icon param AND would break Government Charges parity. **Resolution: NO CHANGE.** (ORC #14613/#14620; evidence handed to SwiftOwl.)

### C2 — Chrome (header/footer) is OPAQUE by ruling
The old translucent/`SCRIM_ALPHA` locks are **DEAD** — Kemar revoked the Figma-supreme chrome exception; opaque `gray200` per Swift accepted at `2af3110`. Do not restore any alpha band or flag opaque chrome as a regression.

### C3 — Refer-a-Friend is SWIFT-structure
Accepted at `2999286` (+ branch hardening `ea74cd5`/`c7944f0`/`a07abbe`/`5ddc57a`): referral link + Copy, Invite Friends, Your Referrals list, reload-on-resume, middle-truncated long links (Swift `.byTruncatingMiddle`), 22dp status pill. The Figma-only carousel (nodes 40001940:26885/26797) is the **stale landing-only mockup Kemar called fake** — do not restore it.

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
| 40001940:26885/26797 Refer | landing carousel | See C3 — the fake page. |
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

---

## 3. VERIFIED-GOOD LEDGER (do not re-audit without cause)

Verification levels: **D-L/D-D** = device light/dark seen · **3W** = Figma node + Swift code + device · **C** = code+Figma (device blocked/N-A, reason noted).

| Screen | Figma node | Level | Notes |
|---|---|---|---|
| Shipments placeholder-tap guard | — | C (`eac8248`) | device needs empty-list acct (P5) |
| Package Details invoice-delete gate | — | C (`c8a99b1`, hash-tied) | matches Swift `ccb55a1`; delete hidden+guarded @status≥7, upload stays |
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
| Refer a Friend / Invite Friend | Swift-structure (C3) | 3W (D-L/D-D) | |
| Documents | 40000975:7748 | 3W (L+D) | info-icon deferred (P3) |
| Authorized Users / Promotions / Shipping Rates | 40001185:4935 / 40001646:14035 / 40001567:54206 | 3W (L+D) | |
| Chrome (header/footer) | — | 3W (L+D) | opaque per C2 |
| PackagesFilterSheet / PaymentPackageDetails timeline | — | 3W | |
| Restricted Items / FAQs | 40001432:14025 / 40001387:8896 | 3W (L+D) | |
| Drop Alert | 40001826:22497 | 3W (D-L) | form view-only (C7) |
| Notifications empty-state / Notification Settings | 40007174:63447 / — | 3W (D-L) | watch per-type icons (C5) |
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
- **Trust Swift CODE, not comments.** Examples: Gold Priority's header comment lists a different tier order than the rendered `tiers` array (`FigmaGoldPriorityViewController.swift:57`); node IDs in Swift comments can resolve to sub-components/wrong frames.
- **Figma frames can be stale or aspirational** (see C6) — never treat a Figma diff as a defect without checking the Swift code; never treat Swift as a defect on a screen Swift doesn't ship (Figma-authority).

### Figma MCP
- `get_screenshot` works with **view** access; `get_metadata` requires **edit** access — agents hitting "no editor access" should use screenshots (or ask BlueDeer to pull a node).
- Always record the node ID used in each audit.

### ORC / Agent Mail bus
- `send_message` can **time out while the message still lands** — check the topic before retrying (avoid duplicate posts); use 60s timeouts.
- API shapes: `fetch_topic {project_key, topic_name, limit, include_bodies}` · `send_message {sender_name, sender_token, project_key, to:[], broadcast:true, topic, subject, body_md}` · `fetch_inbox {registration_token, project_key, agent_name, limit}`.
- **Hash-tie your proofs** to the actual remote branch (`git ls-remote`), not `/tmp` copies — unverifiable-hash claims get rejected.
- Governance rulings can be **reversed** (chrome, Refer both flipped). Before enforcing any remembered lock, check the CURRENT ORC ruling; escalate, don't edit-war.

### Verification discipline (#14639)
- Device proof over code inspection for interactive rails; when the device path is genuinely unreachable (needs special account state, screen not on main yet), say so explicitly and mark the verification level honestly — no fast done-claims.
