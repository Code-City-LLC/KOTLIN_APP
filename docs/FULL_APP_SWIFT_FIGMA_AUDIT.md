# Full App Swift/Figma Parity Audit

Date: 2026-07-05
Owner lane: KOTLIN_APP UI parity

## Acceptance Rule

No screen is done until it has been seen against all three sources:

1. Android device/emulator rendering in light and dark.
2. Figma MCP screenshot or design context for the exact screen node.
3. Swift `Figma*ViewController.swift` behavior/layout source.

Swift is the behavior and flow guide. Figma is the visual source of truth,
especially where Swift is missing a designed element. If Swift and Figma
conflict, follow the latest room mandate from BlueDeer: Swift wins as the
updated implementation truth, and the conflict must be documented in this file
instead of silently choosing either side. Preserve working Android flows and
assets; only repair the parts that are visibly or functionally wrong.

## Current Evidence

- Figma MCP broad app-canvas metadata call for node `40000002:83125` timed out
  with HTTP 504, so use per-screen Figma MCP calls.
- Figma MCP Home screenshot succeeded for node `40001464:28899`.
- Figma MCP screenshots now exist for:
  - Home: `/tmp/kotlin_ui_proof/figma_home_light.png`
  - Shipments: `/tmp/kotlin_ui_proof/figma_shipments_light.png`
  - Help: `/tmp/kotlin_ui_proof/figma_help_light.png`
  - AirCoins: `/tmp/kotlin_ui_proof/figma_aircoins_light.png`
  - Shop root: `/tmp/kotlin_ui_proof/figma_shop_light.png`
  - Auction product details:
    `/tmp/kotlin_ui_proof/figma_auction_product_details_light.png`
  - Auction product details refreshed for commit `a1768d2` verification:
    `/tmp/kotlin_ui_proof/figma_auction_product_details_a1768d2.png`
- Local proof screenshots:
  - `/tmp/kotlin_ui_proof/figma_home_light.png`
  - `/tmp/kotlin_ui_proof/android_home_light_correct.png`
  - `/tmp/kotlin_ui_proof/more_light.png`
- Valid Android dark-mode proof screenshots captured from `emulator-5554`:
  - `/tmp/kotlin_ui_proof/android_home_proof_dark.png`
  - `/tmp/kotlin_ui_proof/android_shipments_proof_dark.png`
  - `/tmp/kotlin_ui_proof/android_help_proof_dark.png`
  - `/tmp/kotlin_ui_proof/android_aircoins_proof_dark.png`
  - Matching UIAutomator XML files use the same names with `.xml`.
- Pushed `a1768d2` Shop route proof:
  - `/tmp/kotlin_ui_proof/android_shop_root_a1768d2.png`
  - `/tmp/kotlin_ui_proof/android_product_detail_a1768d2.png`
  - `/tmp/kotlin_ui_proof/android_product_detail_a1768d2.xml`
  - `/tmp/kotlin_ui_proof/logcat_product_detail_a1768d2.filtered.txt`
- Earlier installed-WIP Shop route proof:
  - `/tmp/kotlin_ui_proof/android_shop_after_visual_product_tap.png`
  - `/tmp/kotlin_ui_proof/android_shop_after_visual_product_tap.xml`
- Android checks already run by Codex before this audit doc:
  - `:app:compileStagingDebugKotlin`
  - `:app:testProdDebugUnitTest`
  - `:app:assembleStagingDebug`
  - staging debug APK installed on `emulator-5554`
- Android checks run after `HEAD e7357a5` plus current WIP:
  - `:app:compileStagingDebugKotlin`
  - `:app:assembleStagingDebug`
  - staging debug APK installed on `emulator-5554`
- Android checks run after pushed `HEAD a1768d2`:
  - `:app:compileStagingDebugKotlin :app:assembleStagingDebug`
  - staging debug APK installed on `emulator-5554`

## Latest Device/Figma Findings

### Home

- Android dark proof does not match the Figma Home node. Figma uses the
  shopping-icon hero image, compact white warehouse cards, and smaller service
  tiles. Android dark proof currently shows a different orange-cart/books hero,
  dark warehouse cards, and large highlight tiles.
- The Android warehouse card title says `Standard`; Figma says `AirDrop`.
  Swift source currently uses `Standard`, so this needs a source-of-truth
  decision or a deliberate Swift-vs-Figma reconciliation before editing labels.
- The Standard destination exists: fresh launch resumed on the Standard detail
  screen during inspection. The user-reported "nothing opens" bug must be
  reproduced from the Home card/Read More tap path before changing routes.

### Shipments

- Android dark proof reaches the Shipments hub and pulls live-looking data, but
  it differs from Figma in counts, sample content, card proportions, visible
  sections, icon contrast, and dark surface treatment.
- The first viewport in Android dark proof shows only summary and the first
  package cards; Figma first viewport also establishes payments/orders sections
  lower in the scroll. Tap checks are still required for Track Shipment,
  Packages, Payments, Orders, View More links, package add-to-cart, and detail
  cards.

### Help

- Android dark proof confirms the user complaint that the screen reads too bold:
  section labels and values are visually heavier than the Figma Help node.
- Several dark-mode help/contact icons render as very low-contrast dark glyphs
  on dark surfaces. They need duotone/tint correction, not duplicated rows.
- Copy/tap actions still need a functional pass: phone, WhatsApp, email,
  locations, business hours, socials, and Live Chat.

### AirCoins

- Android dark proof is not pixel-perfect against the Figma AirCoins node.
  Android uses a much taller coin hero and dark radial background; Figma shows a
  compact light hero, tighter conversion controls, and smaller stat/tip cards.
- Data mapping works enough to render current values (`1 AirCoin`, `1 USD`,
  accumulated/redeemed/available rows), but endpoint verification and history
  tap proof are still outstanding.

### Shop/Product Detail

- Pushed commit `a1768d2` was checked against Figma MCP Shop node
  `40001846:53519` and product-detail node `40002072:24025`.
- After installing the `a1768d2` staging debug APK, a settled visual tap on the
  first Auction card opened a real product detail page:
  `/tmp/kotlin_ui_proof/android_product_detail_a1768d2.png`.
- The UIAutomator XML for that proof does not contain `Product not found` and
  contains the product detail fields (`Elegant Retro Velvet Clutch Bag`,
  `Model: ELEGANT-RETRO-VELVET-CLUTCH-BAG`, `$9.20`, `Stock Quantity: 0`,
  `Related Products`, `Add to Cart`). This verifies the tested route/data lookup
  is no longer failing.
- Filtered OkHttp proof confirms the backend path changed to the Laravel show
  route and returned 200:
  `GET https://pre-staging.airdropja.com/api/v1/products/elegant-retro-velvet-clutch-bag`
  -> `200` in 317ms. Full filtered log is saved at
  `/tmp/kotlin_ui_proof/logcat_product_detail_a1768d2.filtered.txt`.
- This does not close Shop as pixel-perfect: root and detail geometry still need
  the owner/verifier pass against Swift + Figma in light and dark, and the
  add-to-cart/cart path still needs proof.

## Reopened Defects From User Review

### Home

Source files:
- Android: `app/src/main/java/com/ga/airdrop/feature/home/HomeScreen.kt`
- Android chrome: `app/src/main/java/com/ga/airdrop/core/designsystem/components/AirdropHeader.kt`
- Android tabs: `app/src/main/java/com/ga/airdrop/core/designsystem/components/AirdropBottomBar.kt`
- Swift: `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaHomeViewController.swift`
- Swift chrome: `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaTabHeader.swift`
- Figma: node `40001464:28899`

Findings to verify/fix:
- Header treatment is not matching the Figma Home screenshot. Android currently
  renders an opaque light header over Home, while the Figma node shows the
  header integrated with the dark hero treatment.
- Warehouse carousel vertical placement must be rechecked. User says the
  Standard/SeaDrop/Express section and Standard image are too high; the captured
  Android Home shows a large hero image band before the card row, so this needs
  exact Figma and Swift y-coordinate measurement rather than trusting comments.
- Standard/SeaDrop/Express cards must be tappable as whole cards and route to
  the warehouse detail flow with the correct type.
- Activity/highlight boxes need measured size and spacing against Figma/Swift;
  user reports they are too large.
- Header/footer opacity must be checked in light and dark. The bottom tab bar
  must be opaque where Swift uses an opaque surface and must not wash through
  content.
- Functional bug observed during Codex inspection: a bottom-tab tap sequence
  appeared to leave More/FAQ content visible while Home was selected. Reproduce
  carefully and fix `AppRoot.switchTab` or navigation state if confirmed.

### Shipments

Source files:
- Android: `feature/shipments/*`
- Swift: `FigmaShipmentsViewController.swift`, `FigmaPackagesViewController.swift`,
  `FigmaPackageDetailsViewController.swift`, `FigmaPaymentsViewController.swift`,
  `FigmaOrdersViewController.swift`
- Figma nodes: `40000823:9633` and shipment drill-down nodes in backlog.

Findings to verify/fix:
- Tapping Shipment exposes several user-visible bugs; every summary tile, package
  card, payment card, order card, filter, detail row, invoice action, and cart
  action needs a tap check.
- Existing backlog already flags package detail surfaces, timeline rendering,
  charges footer, filter sheet row icons/fonts, payments refresh/invoice button,
  and multiple detail-screen parity issues.
- Backend endpoints must be checked for each visible section: summary, packages,
  payments, orders, invoice URL, package detail, payment detail, order detail.

### Help

Source files:
- Android: `feature/contacts/ContactsScreen.kt`
- Swift: `FigmaContactsViewController.swift`
- Figma: node `40001617:20377`

Findings to verify/fix:
- User still sees "everything bold." Recheck title/value/body typography against
  Swift. Values should not all use heavy weights.
- Verify every contact action: phone, WhatsApp, email, maps, social URLs, copy
  buttons, and Live Chat route.
- Check dark-mode icon rendering for all duotone contact icons.

### AirCoins

Source files:
- Android: `feature/homedetails/AirCoinScreen.kt`
- Swift: `FigmaAirCoinHistoryViewController.swift`
- Figma: node `40001911:22972`

Findings to verify/fix:
- User reports AirCoins is not pixel-perfect. Recheck hero spacer, conversion
  pills, stat card rows, tip card, background image crop, header opacity, and
  history navigation.
- Verify `/aircoins/status` and history endpoint data mapping.
- Verify light and dark screenshots.

### Dark Theme Icons

Source files:
- Android drawables in `app/src/main/res/drawable*`
- Android night colors in `app/src/main/res/values-night/colors.xml`
- Swift icon truth: `FigmaIcons.swift` plus per-screen `Figma*ViewController`.

Findings to verify/fix:
- Do a page-by-page dark pass; no icon should disappear, flatten incorrectly, or
  keep a light-only hardcoded stroke/fill.
- Existing backlog still flags Notification Settings icon variants.
- New icons added after the earlier dark pass must be audited for
  `@color/icon_duotone` or explicit Swift-matching orange/dark role colors.

## Work Split Notes

- BlueDeer/Claude owns broad Android/KOTLIN_APP parity context.
- Codex/MagentaCastle has claimed More/Legal/Profile work, but it remains
  blocked from "done" until Figma MCP and device screenshots are completed.
- Other agents are now touching Shop files; Codex must not edit Shop unless the
  room hands that slice over.
- Keep POS, production, paid-provider/model config, secrets, and unrelated
  Laravel work out of scope.

## Page Checklist

For each page, fill this before claiming completion:

| Page | Android file(s) | Swift file | Figma node | Backend/API | Light seen | Dark seen | Taps verified | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Home | `feature/home/HomeScreen.kt`, chrome components | `FigmaHomeViewController.swift`, `FigmaTabHeader.swift` | `40001464:28899` | `/user/me`, `/aircoins/status`, auctions, warehouses | partial | yes | partial | unassigned | reopened; Figma/Swift conflict on Standard label |
| Shipments hub | `feature/shipments/ShipmentsScreen.kt` | `FigmaShipmentsViewController.swift` | `40000823:9633` | summary/packages/payments/orders | no | yes | no | unassigned | reopened; dark proof captured |
| Help | `feature/contacts/ContactsScreen.kt` | `FigmaContactsViewController.swift` | `40001617:20377` | contact/static routes/live chat | no | yes | no | unassigned | reopened; typography/icons wrong |
| AirCoins | `feature/homedetails/AirCoinScreen.kt` | `FigmaAirCoinHistoryViewController.swift` | `40001911:22972` | `/aircoins/status`, history | no | yes | partial | unassigned | reopened; geometry wrong |
| More/Profile/Legal | `feature/more/*`, `feature/more2/*` | matching `Figma*ViewController.swift` files | see backlog | user/profile/content/faqs/etc. | partial | no | partial | Codex | in progress |
| Shop | `feature/shop/*` | shop/auction/product detail Swift files | `40001846:53519`, `40002072:24025` | products/auction/cart | no | partial | partial | BlueDeer/others | `a1768d2` route proof captured; visual parity/cart still open |
