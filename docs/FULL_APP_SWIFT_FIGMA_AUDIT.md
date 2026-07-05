# Full App Swift/Figma Parity Audit

Date: 2026-07-05
Owner lane: KOTLIN_APP UI parity

## Acceptance Rule

No screen is done until it has been seen against all three sources:

1. Android device/emulator rendering in light and dark.
2. Figma MCP screenshot or design context for the exact screen node.
3. Swift `Figma*ViewController.swift` behavior/layout source.

Swift is the behavior, flow, and implementation-precedence guide. Figma is the
pixel-measurement and visual-comparison source, especially where Swift is
missing a designed element. If Swift and Figma conflict, Swift wins as the
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
- Android checks run for the Home activity-icon dark-mode fix:
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:assembleStagingDebug`
  - `:app:compileStagingDebugAndroidTestKotlin`
  - targeted `HomeActivityTilesScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.home.HomeActivityTilesScreenshotTest ...`:
    `OK (2 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/home_activity_tiles/android_home_activity_tiles_light_after_fix.png`,
    `/tmp/kotlin_ui_proof/home_activity_tiles/android_home_activity_tiles_dark_after_fix.png`
- Android checks run for the Home warehouse-card tap/geometry proof:
  - Figma MCP metadata for Home node `40001464:28899`.
  - Figma MCP design context for Standard card node `40001464:28907`.
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `HomeActivityTilesScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 4 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.home.HomeActivityTilesScreenshotTest ...`:
    `OK (4 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_light_warehouse_geometry.png`,
    `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_dark_warehouse_geometry.png`,
    `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_standard_after_tap.png`
- Android checks run for the Help Swift/Figma pass:
  - Figma MCP design context for Help node `40001617:20377`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaContactsViewController.swift`.
  - `git diff --check`
  - dark drawable vector path parity check for all 11 Help icon variants
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `ContactsScreenScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 4 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.contacts.ContactsScreenScreenshotTest ...`:
    `OK (4 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/help_contacts/android_help_top_light_final.png`,
    `/tmp/kotlin_ui_proof/help_contacts/android_help_top_dark_final.png`,
    `/tmp/kotlin_ui_proof/help_contacts/android_help_social_light_final.png`,
    `/tmp/kotlin_ui_proof/help_contacts/android_help_social_dark_final.png`
- Android checks run for the Home activity/highlight geometry proof:
  - Figma MCP design context and screenshot for Home node `40001464:28899`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaHomeViewController.swift`.
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `HomeActivityTilesScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 6 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.home.HomeActivityTilesScreenshotTest ...`:
    `OK (6 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/home_tiles_geometry/figma_home_light_geometry.png`,
    `/tmp/kotlin_ui_proof/home_tiles_geometry/android_home_top_light_geometry.png`,
    `/tmp/kotlin_ui_proof/home_tiles_geometry/android_home_top_dark_geometry.png`,
    `/tmp/kotlin_ui_proof/home_tiles_geometry/android_home_activity_tiles_light_geometry.png`,
    `/tmp/kotlin_ui_proof/home_tiles_geometry/android_home_activity_tiles_dark_geometry.png`,
    `/tmp/kotlin_ui_proof/home_tiles_geometry/android_home_warehouse_standard_after_tap_geometry.png`
- Android checks run for the Documents card/action-row Swift-precedence pass:
  - Figma MCP design context and screenshot for Documents node `40000975:7748`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaDocumentsViewController.swift`.
  - Swift/Figma conflict documented: the Figma node still shows the older
    edge-to-edge action footer, but Swift uses an inset `48`pt bordered actions
    row inside the card content; Swift wins.
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `DocumentsScreenScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 3 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.more.DocumentsScreenScreenshotTest ...`:
    `OK (3 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/documents_swift_geometry/figma_documents_light.png`,
    `/tmp/kotlin_ui_proof/documents_swift_geometry/android_documents_card_swift_geometry_light.png`,
    `/tmp/kotlin_ui_proof/documents_swift_geometry/android_documents_card_swift_geometry_dark.png`
- Android checks run for the Documents info-alert Swift label pass:
  - Figma MCP design context for Documents node `40000975:7748`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaDocumentsViewController.swift`.
  - Swift `onInfoTapped` uses `UIAlertAction(title: "Got it")`; Android now
    passes `confirmLabel = "Got it"` only for the Documents info alert while the
    shared More alert keeps its default `OK` label.
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `DocumentsScreenScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 4 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.more.DocumentsScreenScreenshotTest ...`:
    `OK (4 tests)`
- Android checks run for the Documents refresh/reload Swift behavior pass:
  - Figma MCP design context for Documents node `40000975:7748`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaDocumentsViewController.swift`.
  - Swift `viewDidAppear` calls `loadDocuments()` and the scroll view attaches an
    orange `UIRefreshControl`; Android now reloads on lifecycle resume and exposes
    pull-to-refresh through the same repository-backed ViewModel path.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `DocumentsScreenScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 6 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.more.DocumentsScreenScreenshotTest ...`:
    `OK (6 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/documents_refresh_reload/figma_documents_40000975_7748.png`,
    `/tmp/kotlin_ui_proof/documents_refresh_reload/android_documents_card_light_after_refresh_patch.png`,
    `/tmp/kotlin_ui_proof/documents_refresh_reload/android_documents_card_dark_after_refresh_patch.png`

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
- Home activity tile icon colors now match Swift/Figma in app light and app dark
  mode. Swift `FigmaIcons.swift` keeps Services as orange person + secondary
  gear and Ship Tax as orange package + secondary hull/waves; Figma Services
  nodes `40001464:28913`/`40000798:6509` and Ship Tax nodes
  `40001464:28914`/`40000798:6510` show the same light/dark split. Android now
  selects explicit light/dark drawables from `ThemeController`-resolved app
  theme, so app-dark no longer depends on Android resource-night mode.
- Home warehouse carousel geometry matches Swift and Figma for the inspected
  values: Figma Home node `40001464:28899` places the warehouse section at
  `y=326`, height `346`, inner row `x=20/y=20`, cards `238x326` with 10 gap;
  Swift `FigmaHomeViewController.swift` uses the same constraints. Android
  `HomeScreen.kt` already matched those numbers, so no geometry churn was made.
- Home activity and Auction Highlights card geometry matches Swift and Figma.
  Figma Home node `40001464:28899` uses a two-column activity grid with outer
  padding `20`, gap `10`, tile width `162.5` on the 375pt frame, tile height
  `108`, icon `32`, stack gap `10`, and tile padding `10x20`.
  Swift `FigmaHomeViewController.swift` uses the same `226` grid height, `10`
  row/column gap, `108` tile height, `32` icon, and `10x20` content insets.
  Auction Highlights cards match at `160x245`, image height `124`, corner
  radius `14`, image radius `10`, padding `8`, and stack spacing `6`. Android
  already matched those values, so the code change only adds test tags and
  geometry regression coverage.
- Home warehouse tap path is verified in instrumentation: Standard/SeaDrop/
  Express cards emit `warehouses?type=standard|seadrop|express`, and a Standard
  tap through `AppRoot` opens the Warehouse detail screen with the Standard tab
  selected. Proof:
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_standard_after_tap.png`.

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

- Help dark-mode icon rendering root cause was app-theme/resource-night drift:
  the shared `@color/icon_duotone` vectors stayed dark when the app was in
  `ThemeController` dark mode. Android now uses Help-specific dark vector
  variants for the same glyph paths, preserving orange accents and turning the
  secondary strokes white.
- Swift/Figma conflict documented: Figma Help node `40001617:20377` labels
  values as SubTitle2 and visually includes Live Chat plus Business Hours copy.
  Swift `FigmaContactsViewController.swift` renders contact values, business
  hours, and social rows as `Typography.subtitle1()` with `iconSelected`, has no
  Live Chat row, has no Business Hours copy action, and separates Contact /
  WhatsApp / Email into individual cards. This pass follows Swift for
  typography and icon semantics, while preserving the existing Figma/Android
  layout until the larger Help layout conflict is handled as a separate pass.
- Email now uses the Swift/Figma envelope glyph (`ic_mail`) instead of the
  message/chat glyph.
- Functional coverage added for the safe Help actions: Live Chat route emission
  and copy-toast behavior. External phone, WhatsApp, email, maps, and social URL
  intents still need a deeper intent-intercept audit.

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

### More/Profile/Legal

- Documents card/action-row geometry now follows Swift precedence over the older
  Figma node. Swift `FigmaDocumentsViewController.swift` uses `15`pt card
  radius, `12`pt list spacing, `16x14` card content inset, a `48`pt inset
  actions row with `10`pt radius and `1`pt iconShape border, and a center
  divider inset `8`pt top/bottom. Android now matches those values.
- Documents uploaded-file row now follows Swift: `56`pt height, `8`pt radius,
  no border, `28`pt PDF icon, body2 filename, body3 textDescription metadata,
  and `18`pt trash/eye glyphs in `28`pt hit boxes with `6`pt gap.
- The Figma Documents node `40000975:7748` still shows the older edge-to-edge
  footer. Keep that conflict documented; future edits must not revert Android
  from the Swift implementation back to the stale Figma footer.
- Documents info alert now follows Swift behavior: the button says `Got it`.
  Other More alerts still use the shared default `OK` unless their Swift source
  requires a different label.
- Documents refresh/reload now follows Swift behavior: returning to the screen
  calls the same `load()` path used by the initial Documents fetch, and pull-to-
  refresh calls `refresh()` with the orange Material3 indicator. Figma confirms
  the same Documents scroll surface; Swift takes precedence for the invisible
  lifecycle behavior.

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
- Header treatment conflict resolved by Swift precedence: Figma node
  `40001464:28926` shows a translucent dark Home header, but Swift
  `FigmaTabHeader.swift` uses an opaque semantic `gray200` surface for both
  hero and solid styles. Android must follow Swift here, not the Figma-only
  translucent treatment. Device proof:
  `/tmp/kotlin_ui_proof/android_swift_precedence_home_header.png` and
  `/tmp/kotlin_ui_proof/android_swift_precedence_home_header_app_dark.png`.
- Warehouse carousel vertical placement rechecked against Figma and Swift:
  both sources place the section at `y=326` over the 534pt hero, with 20pt top
  inset inside a 346pt carousel and 238x326 cards. Android matches those values;
  no movement was made. Proof:
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_light_warehouse_geometry.png`,
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_dark_warehouse_geometry.png`.
- Standard/SeaDrop/Express cards are tappable as whole cards and route to the
  warehouse detail flow with the correct type in instrumentation. Standard was
  additionally verified through `AppRoot`.
- Activity/highlight boxes were measured against Figma and Swift. Android
  matches the Swift/Figma values: activity tiles are `(screen - 40 - 10) / 2`
  wide and `108` high, with `32` icons, `10` stack gap, and `10x20` padding;
  Auction Highlights cards are `160x245` with `124` image height, `8` padding,
  and `6` stack spacing. No visual size change was made because Swift/Figma
  already match the Android implementation. Regression tests now lock this in.
- Header/footer opacity must be checked in light and dark. The bottom tab bar
  must be opaque where Swift uses an opaque surface and must not wash through
  content.
- Dark-mode Home activity icon issue fixed: Services gear, Ship Tax hull/waves,
  Calculator frame, and Drop Alert secondary strokes now flip to white in app
  dark mode while primary orange layers remain orange. Proof:
  `/tmp/kotlin_ui_proof/home_activity_tiles/android_home_activity_tiles_light_after_fix.png`,
  `/tmp/kotlin_ui_proof/home_activity_tiles/android_home_activity_tiles_dark_after_fix.png`.
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
- Help typography/icon pass completed for the confirmed Swift-precedence items:
  value rows, Business Hours, and social rows now use Android `AirdropType.subtitle1`
  and `colors.iconSelected`, matching Swift `Typography.subtitle1()` /
  `DesignTokens.Color.iconSelected`.
- Email icon corrected from message/chat bubble to the Swift/Figma mail envelope.
- Dark-mode Help icons now use screen-specific dark variants because shared
  resource-night vectors do not follow app-level `ThemeController` dark mode.
- Live Chat route and copy-toast behavior are covered by instrumentation.
- Still open: resolve whole-layout Swift/Figma conflicts deliberately. Swift
  source omits Live Chat and Business Hours copy and splits Contact / WhatsApp /
  Email into separate cards; the saved Figma screen and current Android layout
  include Live Chat, Business Hours copy, and the grouped contact card.
- Still open: intent-intercept tests for phone, WhatsApp, email, maps, and
  social URLs.

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
- Codex/MagentaCastle is working through More/Legal/Profile. Documents
  card/action-row geometry, info alert, and refresh/reload behavior now have
  Figma MCP + Swift comparison and targeted device-test proof; the rest of the
  More/Legal/Profile lane remains open until each screen has the same evidence.
- Other agents are now touching Shop files; Codex must not edit Shop unless the
  room hands that slice over.
- Keep POS, production, paid-provider/model config, secrets, and unrelated
  Laravel work out of scope.

## Page Checklist

For each page, fill this before claiming completion:

| Page | Android file(s) | Swift file | Figma node | Backend/API | Light seen | Dark seen | Taps verified | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Home | `feature/home/HomeScreen.kt`, chrome components | `FigmaHomeViewController.swift`, `FigmaTabHeader.swift` | `40001464:28899` | `/user/me`, `/aircoins/status`, auctions, warehouses | yes | yes | partial | MagentaCastle | header Swift-precedence, activity icons, warehouse card tap/geometry, and activity/highlight geometry verified; remaining Home content/navigation issues still open |
| Shipments hub | `feature/shipments/ShipmentsScreen.kt` | `FigmaShipmentsViewController.swift` | `40000823:9633` | summary/packages/payments/orders | no | yes | no | unassigned | reopened; dark proof captured |
| Help | `feature/contacts/ContactsScreen.kt` | `FigmaContactsViewController.swift` | `40001617:20377` | contact/static routes/live chat | no | yes | no | unassigned | reopened; typography/icons wrong |
| AirCoins | `feature/homedetails/AirCoinScreen.kt` | `FigmaAirCoinHistoryViewController.swift` | `40001911:22972` | `/aircoins/status`, history | no | yes | partial | unassigned | reopened; geometry wrong |
| More/Profile/Legal | `feature/more/*`, `feature/more2/*` | matching `Figma*ViewController.swift` files | see backlog | user/profile/content/faqs/etc. | partial | partial | partial | Codex | Documents card/action-row geometry, info alert, and refresh/reload behavior verified; remaining More/Legal/Profile screens still open |
| Shop | `feature/shop/*` | shop/auction/product detail Swift files | `40001846:53519`, `40002072:24025` | products/auction/cart | no | partial | partial | BlueDeer/others | `a1768d2` route proof captured; visual parity/cart still open |
