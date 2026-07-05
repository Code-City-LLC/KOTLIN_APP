# Full App Swift/Figma Parity Audit

Date: 2026-07-05
Owner lane: KOTLIN_APP UI parity

## Acceptance Rule

No screen is done until it has been seen against all three sources:

1. Android device/emulator rendering in light and dark.
2. Figma MCP screenshot or design context for the exact screen node.
3. Swift `Figma*ViewController.swift` behavior/layout source.

Swift is the behavior and flow guide. Figma is the visual source of truth,
especially where Swift is missing a designed element. Preserve working Android
flows and assets; only repair the parts that are visibly or functionally wrong.

## Current Evidence

- Figma MCP broad app-canvas metadata call for node `40000002:83125` timed out
  with HTTP 504, so use per-screen Figma MCP calls.
- Figma MCP Home screenshot succeeded for node `40001464:28899`.
- Local proof screenshots:
  - `/tmp/kotlin_ui_proof/figma_home_light.png`
  - `/tmp/kotlin_ui_proof/android_home_light_correct.png`
  - `/tmp/kotlin_ui_proof/more_light.png`
- Android checks already run by Codex before this audit doc:
  - `:app:compileStagingDebugKotlin`
  - `:app:testProdDebugUnitTest`
  - `:app:assembleStagingDebug`
  - staging debug APK installed on `emulator-5554`

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
| Home | `feature/home/HomeScreen.kt`, chrome components | `FigmaHomeViewController.swift`, `FigmaTabHeader.swift` | `40001464:28899` | `/user/me`, `/aircoins/status`, auctions, warehouses | partial | no | partial | unassigned | reopened |
| Shipments hub | `feature/shipments/ShipmentsScreen.kt` | `FigmaShipmentsViewController.swift` | `40000823:9633` | summary/packages/payments/orders | no | no | no | unassigned | reopened |
| Help | `feature/contacts/ContactsScreen.kt` | `FigmaContactsViewController.swift` | `40001617:20377` | contact/static routes/live chat | no | no | no | unassigned | reopened |
| AirCoins | `feature/homedetails/AirCoinScreen.kt` | `FigmaAirCoinHistoryViewController.swift` | `40001911:22972` | `/aircoins/status`, history | no | no | no | unassigned | reopened |
| More/Profile/Legal | `feature/more/*`, `feature/more2/*` | matching `Figma*ViewController.swift` files | see backlog | user/profile/content/faqs/etc. | partial | no | partial | Codex | in progress |
| Shop | `feature/shop/*` | shop/auction/product detail Swift files | see backlog | products/auction/cart | no | no | no | BlueDeer/others | in progress elsewhere |
