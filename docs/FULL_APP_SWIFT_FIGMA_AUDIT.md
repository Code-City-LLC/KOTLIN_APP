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
  - Auction product details related-products slice:
    `/tmp/kotlin_ui_proof/auction_related_empty/figma/auction_product_details_40002072_24025.png`
  - Auction product details description fallback slice:
    `/tmp/kotlin_ui_proof/product_description_fallback/figma/auction_product_details_40002072_24025.png`
  - Packages filter live-flow slice:
    `/tmp/kotlin_ui_proof/packages_filter_flow/figma/packages_40001666_42198.png`,
    `/tmp/kotlin_ui_proof/packages_filter_flow/figma/packages_filter_40006358_75618.png`
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
- Android checks run for the Auction detail related-products empty-state:
  - Figma MCP screenshot for product-detail node `40002072:24025`.
  - Swift source checked in
    `SWIFT_APP/Airdrop/FigmaAuctionProductDetailsViewController.swift`:
    `mode == .auction` always adds the related header/row, and the row renders
    two skeleton placeholders until a related endpoint exists.
  - `:app:compileStagingDebugAndroidTestKotlin`
  - targeted `AuctionProductDetailsRelatedParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 2 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.shop.AuctionProductDetailsRelatedParityTest ...`:
    `OK (2 tests)`
  - proof PNG:
    `/tmp/kotlin_ui_proof/auction_related_empty/android/auction_related_empty_swift_light.png`
- Android checks run for the Auction detail null-description fallback:
  - Figma MCP design context and screenshot for product-detail node
    `40002072:24025` confirmed the Description block style and `See all`
    affordance.
  - Swift source checked in
    `SWIFT_APP/Airdrop/FigmaAuctionProductDetailsViewController.swift:574`:
    null descriptions use the full `/products/:id` fallback copy.
  - Targeted `AuctionProductDetailsRelatedParityTest` now verifies Swift's
    fallback copy and rejects the old `No description available.` Android copy.
  - proof PNG:
    `/tmp/kotlin_ui_proof/product_description_fallback/android/auction_description_fallback_swift_light.png`
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
  - Figma MCP design context for Home node `40001464:28899` and Standard card
    node `40001464:28907`.
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `HomeActivityTilesScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 11 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.home.HomeActivityTilesScreenshotTest ...`:
    `OK (11 tests)`
  - full `:app:connectedStagingDebugAndroidTest`: 95 tests passed
  - proof PNGs:
    `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_light_warehouse_geometry.png`,
    `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_dark_warehouse_geometry.png`,
    `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_standard_after_tap.png`,
    `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_seadrop_after_tap.png`,
    `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_express_after_tap.png`
- Android checks added for the Warehouse detail Swift-precedence pass:
  - Figma MCP design context for Warehouse node `40000944:3571`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaWarehousesViewController.swift`.
  - Conflict documented: Figma still shows a larger `90`px badge/shorter photo,
    while Swift ships a `240`pt hero, `60`pt overlapping circle, `28`pt method
    glyph, and `h5` method title. Android follows Swift.
  - Focused proof test:
    `WarehousesScreenParityTest` (light/dark hero geometry plus tab switching).
  - Visual proof:
    `/tmp/kotlin_ui_proof/warehouse_detail_swift/warehouses_swift/warehouse_express_swift_light.png`,
    `/tmp/kotlin_ui_proof/warehouse_detail_swift/warehouses_swift/warehouse_standard_swift_dark.png`.
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
- Android checks run for the Help full Swift-precedence layout/intent pass:
  - Figma MCP design context and screenshot checked for Help node
    `40001617:20377`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaContactsViewController.swift`.
  - Swift/Figma conflict documented: Figma still shows a Live Chat row, grouped
    Contact/WhatsApp/Email card, 10pt card gaps, 20pt card padding, and a
    Business Hours copy icon. Swift ships no Live Chat row in the Help list,
    separate Contact/WhatsApp/Email cards, 20pt card gaps, 15pt card padding,
    no Business Hours copy button, and compact Business Hours copy; Swift wins.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `ContactsScreenScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 5 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.contacts.ContactsScreenScreenshotTest ...`:
    `OK (5 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/help_contacts_swift/figma_help_contacts_40001617_20377.png`,
    `/tmp/kotlin_ui_proof/help_contacts_swift/android_help_swift_top_light.png`,
    `/tmp/kotlin_ui_proof/help_contacts_swift/android_help_swift_top_dark.png`,
    `/tmp/kotlin_ui_proof/help_contacts_swift/android_help_swift_social_light.png`,
    `/tmp/kotlin_ui_proof/help_contacts_swift/android_help_swift_social_dark.png`
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
- Android checks run for the Home auction card/cart behavior proof:
  - Figma MCP screenshot/context for Home node `40001464:28899` was used as the
    plus-button/card visual reference.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaHomeViewController.swift`.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `HomeActivityTilesScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 9 tests passed
  - manual `adb shell am instrument -w -r -e class
    com.ga.airdrop.feature.home.HomeActivityTilesScreenshotTest ...`:
    `OK (9 tests)`
- Android checks run for the Shipments hub tap-rail proof:
  - Figma MCP design context for Shipments hub node `40000823:9633`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaShipmentsViewController.swift`.
  - Swift behavior takes precedence: summary tiles, section `View More` actions,
    package/payment/order cards, and package plus/cart actions must follow the
    shipped iOS callbacks even where Figma is static.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `ShipmentsHubTapRailsParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 2 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.shipments.ShipmentsHubTapRailsParityTest ...`:
    `OK (2 tests)`
  - adjacent manual regression:
    `ShipmentsSectionCardParityTest` + `PaymentsOrdersParityTest`: `OK (8 tests)`
- Android checks run for the Shipments hub summary Swift/Figma pass:
  - Figma MCP screenshot refreshed for Shipments hub node `40000823:9633`:
    `/tmp/kotlin_ui_proof/shipments_hub_visual/figma/figma_shipments_hub_40000823_9633.png`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaShipmentsViewController.swift`.
  - Swift `makeStatTile` takes precedence over Android resource-night behavior:
    summary icons must render orange accents plus `textDarkTitle` secondary
    strokes in both light and app-dark ThemeController modes.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `ShipmentsHubTapRailsParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 4 tests passed
  - visual-method rerun through `:app:connectedStagingDebugAndroidTest`: 2 tests
    passed
  - proof PNGs:
    `/tmp/kotlin_ui_proof/shipments_hub_visual/android_media/shipments_hub_swift_light.png`,
    `/tmp/kotlin_ui_proof/shipments_hub_visual/android_media/shipments_hub_swift_dark.png`
- Android checks run for the Shipments search-field Swift/Figma split:
  - Figma MCP screenshots refreshed for Packages node `40001666:42198`,
    Payments node `40001753:18909`, and Orders node `40001753:19595`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPackagesViewController.swift`,
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPaymentsViewController.swift`,
    and `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaOrdersViewController.swift`.
  - Swift/Figma conflict documented: Figma list nodes show static trailing
    `Item search` fields, but Swift ships Packages with a leading 22pt magnifier
    and long tracking/courier placeholder; Payments/Orders use trailing 18pt
    magnifiers with runtime-specific placeholders. Swift wins.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `ShipmentsSearchFieldParityTest` + `PaymentsOrdersParityTest`
    through `:app:connectedStagingDebugAndroidTest`: 7 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.shipments.ShipmentsSearchFieldParityTest,com.ga.airdrop.feature.shipments.PaymentsOrdersParityTest ...`:
    `OK (7 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/shipments_search_field/figma/packages_40001666_42198.png`,
    `/tmp/kotlin_ui_proof/shipments_search_field/figma/payments_40001753_18909.png`,
    `/tmp/kotlin_ui_proof/shipments_search_field/figma/orders_40001753_19595.png`,
    `/tmp/kotlin_ui_proof/shipments_search_field/android/packages_search_swift_light.png`,
    `/tmp/kotlin_ui_proof/shipments_search_field/android/payments_search_swift_light.png`,
    `/tmp/kotlin_ui_proof/shipments_search_field/android/payments_search_swift_dark.png`,
    `/tmp/kotlin_ui_proof/shipments_search_field/android/orders_search_swift_light.png`,
    `/tmp/kotlin_ui_proof/shipments_search_field/android/orders_search_swift_dark.png`
- Android checks run for the Shipments backend pagination Swift/Figma proof:
  - Figma MCP screenshots refreshed for Shipments hub `40000823:9633`,
    Packages `40001666:42198`, Payments `40001753:18909`, and Orders
    `40001753:19595`.
  - Swift source compared first:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaShipmentsViewController.swift`,
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPackagesViewController.swift`,
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPaymentsViewController.swift`,
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaOrdersViewController.swift`,
    and `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/AirdropAPI.swift`.
  - Swift precedence documented: Figma proves the visible hub/list surfaces, but
    Swift owns the runtime request contract. Android now locks hub shortlist
    render limits (`packagesData.prefix(10)`, `paymentsData.prefix(4)`,
    `ordersData.prefix(6)`), Packages return-key server search and status
    filter pagination, Payments default `package` filter / 300ms min-3 search
    / page-2 loading, and Orders 10-row pagination / refresh / min-3 search.
  - `git diff --check`
  - `:app:compileStagingDebugAndroidTestKotlin`
  - targeted `ShipmentsBackendPaginationParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 4 tests passed
  - proof PNGs:
    `/tmp/kotlin_ui_proof/shipments_backend_pagination/figma/figma_shipments_hub_40000823_9633.png`,
    `/tmp/kotlin_ui_proof/shipments_backend_pagination/figma/figma_packages_40001666_42198.png`,
    `/tmp/kotlin_ui_proof/shipments_backend_pagination/figma/figma_payments_40001753_18909.png`,
    `/tmp/kotlin_ui_proof/shipments_backend_pagination/figma/figma_orders_40001753_19595.png`
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
- Android checks run for the Profile avatar/DOB Swift-precedence pass:
  - Figma MCP design context and screenshot for Profile node `40007189:63763`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaProfileViewController.swift`.
  - Swift/Figma conflict documented: Figma still shows the older `107`px avatar,
    but Swift uses an `88`pt wrap, `80`pt gray300 circle, `24`pt edit badge, and
    `44`pt placeholder; Swift wins. Swift also caps DOB with
    `dobPicker.maximumDate = Date()`.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `ProfileParityScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 3 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.more.ProfileParityScreenshotTest ...`:
    `OK (3 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/profile_swift_geometry/figma_profile_40007189_63763.png`,
    `/tmp/kotlin_ui_proof/profile_swift_geometry/android_profile_avatar_swift_geometry_light.png`,
    `/tmp/kotlin_ui_proof/profile_swift_geometry/android_profile_avatar_swift_geometry_dark.png`
- Android checks run for the Preferences select-field Swift-precedence pass:
  - Figma MCP screenshot for Preferences node `40000994:19044`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPreferencesViewController.swift`.
  - Swift/Figma conflict documented: Figma shows red required asterisks on all
    three labels, but Swift `SelectableRow` only adds `titleLabel` to the
    `titleStack` and never renders an asterisk; Swift wins, so Android keeps
    Preferences labels unstarred.
  - Rendered proof caught the real Android drift: `defaultMinSize(52.dp)`
    allowed the field card to expand to about 54.5dp. Android now uses an exact
    52dp card height to match the Swift row.
  - `PreferencesParityScreenshotTest` verifies 335dp fields, 52dp card heights,
    12dp chevrons, no email chevron, no required asterisks, and row click
    dispatch in light and dark.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `PreferencesParityScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 3 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.more.PreferencesParityScreenshotTest ...`:
    `OK (3 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/preferences_swift_field/figma_preferences_40000994_19044.png`,
    `/tmp/kotlin_ui_proof/preferences_swift_field/android_preferences_select_field_swift_light.png`,
    `/tmp/kotlin_ui_proof/preferences_swift_field/android_preferences_select_field_swift_dark.png`
- Android checks run for the Invite Friend contacts-icon Swift pass:
  - Figma MCP screenshot/design-context was checked for documented referral nodes
    `40001940:26797` and `40001940:26885`; both render the Refer-a-Friend landing
    frame, not the Send Invitation form. Keep this mismatch documented.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaInviteFriendViewController.swift`
    and `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaIcons.swift`.
  - Swift icon contract: `contactNumber(size: 24, primary: orangeMain,
    secondary: iconSelected)` paints orange signal arcs plus an iconSelected
    handset. Android had already removed the stale solid-orange tint, but
    app-dark mode still needed a ThemeController-aware handset source.
  - Android now selects explicit app-theme light/dark contact-number vectors
    instead of relying on Android resource-night resolution. This keeps the
    Swift `iconSelected` handset dark in app light mode even on a system-dark
    emulator, and white in app dark mode.
  - `InviteFriendParityScreenshotTest` verifies 59dp row height, 24dp icon size,
    orange arcs, light dark-handset pixels, and dark white-handset pixels.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `InviteFriendParityScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 2 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.more2.InviteFriendParityScreenshotTest ...`:
    `OK (2 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/invite_friend_icon/figma_referral_40001940_26797.png`,
    `/tmp/kotlin_ui_proof/invite_friend_icon/figma_referral_40001940_26885.png`,
    `/tmp/kotlin_ui_proof/invite_friend_icon/android_invite_friend_contacts_icon_light.png`,
    `/tmp/kotlin_ui_proof/invite_friend_icon/android_invite_friend_contacts_icon_dark.png`
- Android checks run for the Legal/FAQ Swift/Figma pass:
  - Figma MCP design context/screenshots checked for Terms node
    `40001383:9894`, Privacy node `40001387:9042`, and FAQ node
    `40001387:8896`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaTermsConditionsViewController.swift`,
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPrivacyPolicyViewController.swift`,
    and `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaFAQViewController.swift`.
  - Swift precedence documented: live CMS legal headings are recolored by parsed
    font size (`pointSize > 15`) to `textDarkTitle`, body runs are
    `textDescription`, and FAQ uses a 10pt question-to-chevron gap while
    Terms/Privacy keep the 5pt default.
  - Android already had the production Legal/FAQ behavior; this pass made the
    gap explicit/testable and added targeted proof without duplicating UI.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `LegalContentParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 6 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.more2.LegalContentParityTest ...`:
    `OK (6 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/legal_content/screenshots/legal_live_html_light.png`,
    `/tmp/kotlin_ui_proof/legal_content/screenshots/legal_live_html_dark.png`
- Android checks run for the AirCoins Swift/Figma pass:
  - Figma MCP design context checked for balance node `40001911:22972` and
    history node `40006461:26563`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaAirCoinHistoryViewController.swift`.
  - Swift precedence documented: Figma balance uses a circular arrow pill and
    different tip/stat sizing, while Swift uses 120x44 conversion pills, a
    standalone 24pt arrow, 40pt tip/stat icons, and `subtitle1` headers.
    Figma history uses static mock labels/rows, while Swift uses `Invoice No`,
    `Used Date`, unsigned text-colored amounts, a 170pt hero wrap, 150pt image,
    and one clipped 15pt table card.
  - `git diff --check`
  - clean `:app:clean :app:compileStagingDebugKotlin
    :app:compileStagingDebugAndroidTestKotlin`
  - targeted `AirCoinParityScreenshotTest` through
    `:app:connectedStagingDebugAndroidTest`: 4 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.homedetails.AirCoinParityScreenshotTest ...`:
    `OK (4 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/aircoins_swift_history/aircoins_swift/aircoin_balance_swift_light.png`,
    `/tmp/kotlin_ui_proof/aircoins_swift_history/aircoins_swift/aircoin_balance_swift_dark.png`,
    `/tmp/kotlin_ui_proof/aircoins_swift_history/aircoins_swift/aircoin_history_swift_light.png`,
    `/tmp/kotlin_ui_proof/aircoins_swift_history/aircoins_swift/aircoin_history_swift_dark.png`
- Android checks run for the More root Swift/Figma tap-rail pass:
  - Figma MCP design context and screenshot checked for More node
    `40001948:22354`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaSpecificPages.swift`
    `FigmaMoreViewController`.
  - Figma confirms the `375` frame, `80` profile card, `335x59` menu rows,
    and row order: Preferences, Promotions, Settings, Documents, Users,
    Refer a friend, Shipping Rates, Restricted Items, Payment Methods, FAQs,
    Terms & Conditions, Privacy Policy.
  - Swift takes behavior precedence: profile-card tap opens `ProfileView`,
    avatar tap opens the photo-picker path, tier/bell/cart/AirCoins header taps
    open `MembershipTierView`, `NotificationsView`, `MyCartView`, and
    `AirCoinView`, and row taps push the `FigmaRouteResolver` route list.
  - Android already emitted the matching canonical routes; this pass extracted
    the existing content shell for network-free instrumentation and added stable
    test tags without duplicating UI or route logic.
  - `:app:compileStagingDebugAndroidTestKotlin`
  - targeted `MoreRootTapRailsParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 4 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.more.MoreRootTapRailsParityTest ...`:
    `OK (4 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/more_root/figma/figma_more_40001948_22354.png`,
    `/tmp/kotlin_ui_proof/more_root/android/more_root/more_root_swift_light.png`,
    `/tmp/kotlin_ui_proof/more_root/android/more_root/more_root_swift_dark.png`
- Android checks run for the Authorized Users Swift/Figma refresh pass:
  - Figma MCP design context, metadata, and screenshot checked for Authorized
    Users node `40000975:7859`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaAuthorizedUsersViewController.swift`.
  - Swift takes behavior precedence: `viewWillAppear` reloads through
    `loadUsers(showLoadingState: !hasLoadedOnce)`, the scroll view attaches an
    orange `UIRefreshControl`, cards open details, and `Add User` is the only
    add entry point in the bottom bar. Figma confirms the 20pt content gutters,
    56pt card header, active/inactive section stack, and 124pt bottom CTA band.
  - Android now preserves the existing card/detail/add rails and adds the
    missing pull-to-refresh state through the same repository-backed ViewModel
    path, without duplicating list UI or routes.
  - `:app:compileStagingDebugKotlin
    :app:compileStagingDebugAndroidTestKotlin`
  - targeted `AuthorizedUsersParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 3 tests passed
  - adjacent More regression sweep
    `MoreRootTapRailsParityTest`, `DocumentsScreenScreenshotTest`,
    `InviteFriendParityScreenshotTest`, and `LegalContentParityTest`: 18 tests
    passed
  - full `:app:connectedStagingDebugAndroidTest`: 104 tests passed
  - proof PNGs:
    `/tmp/kotlin_ui_proof/authorized_users_figma.png`,
    `/tmp/kotlin_ui_proof/authorized_users/android/authorized_users/authorized_users_swift_light.png`,
    `/tmp/kotlin_ui_proof/authorized_users/android/authorized_users/authorized_users_swift_dark.png`
- Android checks run for the Authorized User Detail Swift/Figma load/action
  pass:
  - Figma MCP design context checked for documented detail node
    `40001185:5345`; that node currently resolves to the Authorized Users list,
    not the detail surface. The Add Authorized User node `40001541:45296` also
    omits Swift's Email field, so Swift remains the runtime/detail authority.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaAuthorizedUserDetailViewController.swift`.
  - Swift takes precedence: `viewDidLoad` calls `loadUser()` once, the detail
    screen is read-only with no Edit affordance, Activate/Deactivate refreshes
    the detail after mutation, and Delete pops back after success.
  - Android had a real runtime drift: `AuthorizedUserDetailViewModel.init`
    already loaded the user, and `AuthorizedUserDetailScreen` also triggered
    `viewModel.load()` on first composition. The screen-side duplicate load was
    removed while preserving the existing ViewModel, repository, mutation, and
    delete rails.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `AuthorizedUserDetailParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 2 tests passed on
    `airdrop_test2(AVD) - 15`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/authorized_user_detail/android/authorized_user_detail/authorized_user_detail_swift_light.png`,
    `/tmp/kotlin_ui_proof/authorized_user_detail/android/authorized_user_detail/authorized_user_detail_swift_dark.png`
- Android checks run for the Add Authorized User Swift/Figma add/edit pass:
  - Figma MCP design context checked for Add Authorized User node
    `40001541:45296`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaAddAuthorizedUserViewController.swift`.
  - Swift takes precedence over a visible Figma conflict: the Figma static node
    omits `Email Address`, while Swift and RN include it and send
    `user_email` in the Laravel payload. Android keeps Email, even though that
    means the TRN field starts lower than the Figma-only static frame.
  - Swift edit mode also exists only when the controller is initialized with an
    existing authorized user; the active detail page remains read-only and no
    Edit affordance is introduced there. Android preserves the existing hidden
    `editId` route rail for deep-link/RN parity without adding a duplicate
    detail-page edit button.
  - Android already followed Swift visually/behaviorally; this pass added
    optional non-visual test tags and focused proof for add and edit payloads.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `AddAuthorizedUserParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 3 tests passed on
    `airdrop_test2(AVD) - 15`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/add_authorized_user/android/add_authorized_user/add_authorized_user_swift_light.png`,
    `/tmp/kotlin_ui_proof/add_authorized_user/android/add_authorized_user/add_authorized_user_edit_swift_dark.png`
- Android checks run for the Payment Methods Swift/Figma proof pass:
  - Figma MCP design context and screenshot checked for Payment Methods node
    `40001428:9188`. Figma shows the stale saved-payment chooser:
    PayPal/Apple Pay/Visa/Mastercard/AmEx rows, repeated `Number` labels, and
    a bottom `Add New Card` CTA.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaSpecificPages.swift`
    `FigmaPaymentMethodsViewController`.
  - Swift takes precedence for this conflict: the shipped iOS page renders an
    informational empty-state card, then a `Go to Checkout` row that opens
    `MyCartView`; it does not render the static Figma saved-card selector.
  - Android already followed Swift visually and functionally. This pass removed
    the stale duplicate `MoreRoutes.PAYMENT_METHODS` alias so the More row and
    graph both use canonical `Routes.PAYMENT_METHODS`, then added proof tags
    and focused instrumentation without changing payment/auth/provider logic.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `PaymentMethodsParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 3 tests passed on
    `airdrop_test2(AVD) - 15`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/payment_methods/figma/payment_methods_40001428_9188.png`,
    `/tmp/kotlin_ui_proof/payment_methods/android/payment_methods/payment_methods_swift_light.png`,
    `/tmp/kotlin_ui_proof/payment_methods/android/payment_methods/payment_methods_swift_dark.png`
- Android checks run for the Restricted Items Swift-precedence pass:
  - Figma MCP design context and screenshots checked for nodes `40001432:14025`
    and `40001432:14918`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaRestrictedItemsViewController.swift`
    and
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaRestrictedItemsInfoViewController.swift`.
  - Swift precedence documented: Figma node `40001432:14025` currently renders an
    Information/legal page, while node `40001432:14918` renders a tabbed
    Restricted Items variant. Swift ships the searchable category-list entry
    screen with push-style per-category detail pages, so Android stays on the
    Swift flow instead of adopting the Figma tab strip.
  - Android now removes the stale low-polish carve-out, reuses the existing
    Swift-matching info-circle and two-color dangerous-goods vectors, keeps
    category/search/detail geometry locked to Swift values, and follows Swift's
    note-card color-token behavior in dark mode.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `RestrictedItemsParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 3 tests passed
  - adjacent More regression sweep `MoreRootTapRailsParityTest`,
    `AuthorizedUsersParityTest`, and `LegalContentParityTest`: 13 tests passed
  - full `:app:connectedStagingDebugAndroidTest`: 110 tests passed
  - proof PNGs:
    `/tmp/kotlin_ui_proof/restricted_items/figma/figma_restricted_information_40001432_14025.png`,
    `/tmp/kotlin_ui_proof/restricted_items/figma/figma_restricted_tabbed_40001432_14918.png`,
    `/tmp/kotlin_ui_proof/restricted_items/android/restricted_items/restricted_items_entry_swift_light.png`,
    `/tmp/kotlin_ui_proof/restricted_items/android/restricted_items/restricted_items_search_results_swift_light.png`,
    `/tmp/kotlin_ui_proof/restricted_items/android/restricted_items/restricted_items_restricted_detail_from_search_swift_light.png`,
    `/tmp/kotlin_ui_proof/restricted_items/android/restricted_items/restricted_items_permitted_detail_swift_dark.png`
- Android checks run for the More2 shared inner-header back-glyph pass:
  - Figma MCP design context checked for Promotions node `40001646:14035`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPromotionsViewController.swift`,
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaShippingRatesViewController.swift`,
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaAccountDeletionViewController.swift`,
    and `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaIcons.swift`.
  - Swift precedence documented: Figma's static Promotions node still renders an
    `Arrow - Right`-derived left arrow, but the Swift controllers build the
    runtime back affordance from `FigmaIcon.chevronDown(size: 24)` rotated
    `pi / 2`; Swift wins.
  - Android now reuses the existing shared `More2InnerHeader` path and renders a
    24dp theme-tinted chevron instead of a 20dp tailed arrow, preserving the
    existing 36dp tap target and `onBack` rail without duplicating headers.
  - `More2InnerHeaderParityTest` locks light/dark glyph size, chevron shape,
    active-theme tint, click dispatch, and proof screenshots.
- Android checks run for the Shipping Rates Swift/Figma proof pass:
  - Figma MCP design context checked for node `40001567:54206`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaShippingRatesViewController.swift`.
  - Swift precedence documented: the static Figma node still shows translucent
    header/footer chrome, pale table label rows, a static `0.5` / `$4.50`
    first-row table, and `$2.00` fuel copy. Swift's executable controller uses
    solid `gray100` chrome, orange table headers, backend-first rates, the
    runtime fallback table starting at `1` / `$5.00`, and a pinned solid-orange
    `Calculate Now` rail. Android already followed Swift visually; this pass
    added non-visual test tags and focused proof rather than changing the UI.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `ShippingRatesParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 2 tests passed on
    `airdrop_test2(AVD) - 15`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/shipping_rates/android/shipping_rates/shipping_rates_swift_fallback_light.png`,
    `/tmp/kotlin_ui_proof/shipping_rates/android/shipping_rates/shipping_rates_live_dark.png`
- Android checks run for the GoldPriority / Customer Tier Swift-precedence pass:
  - Figma MCP design context and screenshot checked for Customer Tier node
    `40001432:23506`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaGoldPriorityViewController.swift`.
  - Swift/Figma conflict documented: the Figma node shows 32px tier title text,
    30px content insets, and decorative/status mock layers; Swift uses a 28pt
    `nameLabel` with 0.7 minimum scale, 24pt page inset, native light-content
    status bar, and runtime gradient, so Swift wins.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `GoldPriorityParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 3 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.homedetails.GoldPriorityParityTest ...`:
    `OK (3 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/gold_priority/figma_gold_priority_customer_tier_40001432_23506.png`,
    `/tmp/kotlin_ui_proof/gold_priority/android_gold_priority_platinum_swift_light_360.png`,
    `/tmp/kotlin_ui_proof/gold_priority/android_gold_priority_platinum_swift_dark_360.png`
- Android checks run for the PaymentPackageDetails Swift/Figma pass:
  - Figma MCP design context checked for node `40001761:29389`; this node is
    the `View History` timeline screen, not the full payment-detail screen.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPaymentPackageDetailsViewController.swift`.
  - Swift precedence documented: full detail layout/behavior comes from Swift,
    while the Figma node confirms the timeline visual. Android now pins the
    `View History` action in a 96dp footer, keeps the full Swift invoice label
    and 48dp CIF pill, uses subtitle1 for Status values, applies Swift's
    ungrouped payment-detail USD/JMD format, and matches the Swift/Figma
    timeline copy, `-` fallback, body3 date text, 74dp row minimum,
    status-colored connectors, and status-tinted icons.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `PaymentPackageDetailsParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 4 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.shipments.PaymentPackageDetailsParityTest ...`:
    `OK (4 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/payment_package_details/payment_package_details_swift_light.png`,
    `/tmp/kotlin_ui_proof/payment_package_details/payment_package_details_swift_dark.png`,
    `/tmp/kotlin_ui_proof/payment_package_details/payment_package_history_swift_light.png`,
    `/tmp/kotlin_ui_proof/payment_package_details/payment_package_history_swift_dark.png`
- Android checks run for the ProductPaymentDetails / OrderDetails Swift/Figma
  pass:
  - Figma MCP design context/screenshots checked for ProductPaymentDetails
    `Auction Order Details` node `40004950:25064` and OrderDetails node
    `40001761:28814`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaProductPaymentDetailsViewController.swift`
    and
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaOrderDetailsViewController.swift`.
  - Swift precedence documented: both Figma nodes still show the older fixed
    245x149 image geometry, but Swift uses a 219pt ProductPaymentDetails wrap
    with 30pt insets/159pt image and a 209pt OrderDetails wrap with 20pt
    insets/169pt image. Android follows Swift.
  - ProductPaymentDetails also keeps Swift `title2` section headers and now
    uses Swift's ungrouped, positive-only payment-detail USD/JMD formatter.
    OrderDetails intentionally keeps the comma-grouped JMD total because Swift
    does.
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `ProductOrderDetailsParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 4 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.shipments.ProductOrderDetailsParityTest ...`:
    `OK (4 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/product_order_details/product_payment_details_swift_light.png`,
    `/tmp/kotlin_ui_proof/product_order_details/product_payment_details_swift_dark.png`,
    `/tmp/kotlin_ui_proof/product_order_details/order_details_swift_light.png`,
    `/tmp/kotlin_ui_proof/product_order_details/order_details_swift_dark.png`
- Android checks run for the InvoiceViewer Swift/Figma pass:
  - Figma MCP top-level page metadata succeeded, but the full app-canvas page
    metadata timed out with HTTP 504 again. No dedicated InvoiceViewer frame was
    reachable from the current Figma evidence; Figma MCP was checked through
    the related Package Details invoice-entry node `40001753:15716`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaInvoiceViewerScreenViewController.swift`
    and
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaDocumentDownloadingViewController.swift`.
  - Swift precedence documented: both Swift controllers say this viewer follows
    the document-download shell, with gray100 page, gray150 rounded preview
    container, 52pt action buttons, disabled alpha, and local file sharing via
    the system activity sheet.
  - Android already had the surface token swap in the current tree; this pass
    preserved it, added test tags/proof, changed Share from raw `text/plain`
    URL to a `content://` FileProvider stream, and disables Save/Share until a
    local action file is prepared.
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `InvoiceViewerParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 5 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.shipments.InvoiceViewerParityTest ...`:
    `OK (5 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/invoice_viewer/invoice_viewer_swift_light.png`,
    `/tmp/kotlin_ui_proof/invoice_viewer/invoice_viewer_swift_dark.png`
- Android checks run for the PackagesFilterSheet Swift/Figma pass:
  - Figma MCP design context checked for Packages filter node
    `40006358:75618`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPackagesFilterViewController.swift`.
  - Swift precedence documented: Figma still carries the older glass/header
    frame and static list screenshot, but Swift is the shipped implementation
    source for the opaque adaptive header, 56pt header row, 20pt content inset,
    16pt card gap, 54pt collapsible bars, 50pt option rows, 24pt icons, no
    rendered `All Packages` row, tap-again-to-clear behavior, and immediate
    delegate apply on row taps.
  - Android keeps the live `/package-statuses` order/cached data flow shared by
    Swift and Android rather than hardcoding the Figma screenshot order.
  - Android now renders the Swift/Figma filter label `AirDrop` without changing
    the broader package-detail `AirDrop Standard` method label.
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `PackagesFilterSheetParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 2 tests passed
  - manual `adb shell am instrument -w -e class
    com.ga.airdrop.feature.shipments.PackagesFilterSheetParityTest ...`:
    `OK (2 tests)`
  - proof PNGs:
    `/tmp/kotlin_ui_proof/packages_filter_sheet/packages_filter_swift_light.png`,
    `/tmp/kotlin_ui_proof/packages_filter_sheet/packages_filter_swift_dark.png`
- Android checks run for the Packages filter live Swift/Figma flow:
  - Figma MCP design context and screenshot checked for Packages node
    `40001666:42198` and Packages filter node `40006358:75618`.
  - Swift source compared:
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPackagesViewController.swift`
    and
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPackagesFilterViewController.swift`.
  - Swift precedence documented: header More Square opens the filter sheet;
    status taps reload packages with the selected server-backed status; shipment
    method taps remain client-side; close commits/dismisses with the filtered
    result visible.
  - Root cause fixed: status row vectors used `@color/icon_duotone`, which
    follows Android resource-night instead of app-level `ThemeController` dark
    mode. Android now chooses explicit dark status vectors from
    `AirdropTheme.colors.isDark`, preserving orange accents and making dark
    outlines visible.
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - targeted `PackagesFilterFlowParityTest` through
    `:app:connectedStagingDebugAndroidTest`: 2 tests passed
  - proof PNGs:
    `/tmp/kotlin_ui_proof/packages_filter_flow/figma/packages_40001666_42198.png`,
    `/tmp/kotlin_ui_proof/packages_filter_flow/figma/packages_filter_40006358_75618.png`,
    `/tmp/kotlin_ui_proof/packages_filter_flow/android/run_1783277832945/packages_filter_flow_swift_light.png`,
    `/tmp/kotlin_ui_proof/packages_filter_flow/android/run_1783277832945/packages_filter_flow_swift_dark.png`,
    `/tmp/kotlin_ui_proof/packages_filter_flow/android/run_1783277832945/packages_filter_flow_filtered_light.png`,
    `/tmp/kotlin_ui_proof/packages_filter_flow/android/run_1783277832945/packages_filter_flow_filtered_dark.png`

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
  Express cards emit `warehouses?type=standard|seadrop|express`, and all three
  taps through `AppRoot` open the Warehouse detail screen with the matching Swift
  detail title selected. Proof:
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_standard_after_tap.png`,
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_seadrop_after_tap.png`,
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_express_after_tap.png`.

### Shipments

- Android dark proof reaches the Shipments hub and pulls live-looking data, but
  it differs from Figma in counts, sample content, card proportions, visible
  sections, icon contrast, and dark surface treatment.
- Shipments hub tap rails now have Swift-precedence proof against Figma hub node
  `40000823:9633` and
  `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaShipmentsViewController.swift`.
  Figma confirms the summary tile, Packages/Payments/Orders sections, underlined
  `View More` actions, package plus, and bottom-tab state. Swift takes behavior
  precedence: Track Shipment and Packages summary tiles open Packages, Payments
  opens Payments, Orders opens Orders; section `View More` actions open their
  lists; package cards open Package Details; package plus toggles cart without
  navigation; package-payment cards open Payment Package Details; product-payment
  cards open Product Payment Details; order cards open Order Details. Android
  already used the correct rails, so this pass added production test tags and
  connected regression proof without duplicating UI.
  Checks run:
  `git diff --check`,
  `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`,
  targeted `ShipmentsHubTapRailsParityTest` through
  `:app:connectedStagingDebugAndroidTest` (2 tests passed), manual
  `adb shell am instrument -w -e class
  com.ga.airdrop.feature.shipments.ShipmentsHubTapRailsParityTest ...`
  (`OK (2 tests)`), and adjacent manual
  `ShipmentsSectionCardParityTest` + `PaymentsOrdersParityTest` (`OK (8 tests)`).
- Shipments hub summary icons now have Swift-precedence proof for the dark-theme
  visibility drift. Figma MCP screenshot `40000823:9633` confirms the four
  summary tiles and Swift `FigmaShipmentsViewController.makeStatTile` defines the
  implementation contract: 20dp horizontal gutters, 10dp grid gaps, 93dp tiles,
  22dp icons, orange primary paths, and `textDarkTitle` secondary paths. Android
  already matched the geometry, but the previous single-vector drawables used a
  resource color that did not follow in-process ThemeController app-dark changes.
  Android now reuses the existing summary UI and splits the existing glyph paths
  into theme-tinted base layers plus orange accent layers. Proof:
  `/tmp/kotlin_ui_proof/shipments_hub_visual/figma/figma_shipments_hub_40000823_9633.png`,
  `/tmp/kotlin_ui_proof/shipments_hub_visual/android_media/shipments_hub_swift_light.png`,
  `/tmp/kotlin_ui_proof/shipments_hub_visual/android_media/shipments_hub_swift_dark.png`.
- Shipments search fields now have Swift-precedence proof for the known shared
  component conflict. Figma MCP screenshots for Packages `40001666:42198`,
  Payments `40001753:18909`, and Orders `40001753:19595` show a static trailing
  `Item search` field; Swift is the shipped runtime source. Android now keeps
  Packages on `FigmaPackagesViewController.makeSearchCard` behavior (leading
  22dp magnifier and tracking/courier placeholder) while Payments and Orders use
  `makeSearchRow` behavior (trailing 18dp magnifier, 14dp trailing inset, 8dp
  text/icon gap, screen-specific placeholders). The shared `ShipmentsSearchField`
  is parameterized rather than duplicated, preserving working callers.
  Checks run:
  `git diff --check`,
  `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`,
  targeted `ShipmentsSearchFieldParityTest` + `PaymentsOrdersParityTest` through
  `:app:connectedStagingDebugAndroidTest` (7 tests passed), and manual
  `adb shell am instrument -w -e class
  com.ga.airdrop.feature.shipments.ShipmentsSearchFieldParityTest,com.ga.airdrop.feature.shipments.PaymentsOrdersParityTest ...`
  (`OK (7 tests)`).
- PaymentPackageDetails now has Swift-precedence proof for the footer,
  payment-summary copy, and View History timeline. Figma node `40001761:29389`
  is a timeline screen, so Swift `FigmaPaymentPackageDetailsViewController.swift`
  remains authoritative for the full detail screen. Android keeps the existing
  correct invoice label/CIF pill, fixes the pinned footer, Status value style,
  ungrouped payment-detail amount formatting, timeline `Pick Up` copy, `-`
  fallback, body3 dates, 74dp rows, status-colored connectors, and status-tinted
  timeline icons.
- ProductPaymentDetails and OrderDetails now have Swift-precedence proof for
  their hero geometry in app light/dark. Figma nodes `40004950:25064` and
  `40001761:28814` still show stale 245x149 fixed images, so Android follows
  Swift's 219/30/159 ProductPaymentDetails geometry and 209/20/169 OrderDetails
  geometry. ProductPaymentDetails now uses the Swift no-grouping positive-only
  payment formatter; OrderDetails keeps Swift's grouped JMD total.
- InvoiceViewer now has Swift-precedence proof for the document-download shell
  and Share behavior. The dedicated viewer frame was not reachable in Figma MCP;
  related Package Details invoice-entry node `40001753:15716` was checked, while
  Swift `FigmaInvoiceViewerScreenViewController.swift` remains authoritative for
  the viewer. Android keeps gray100 root / gray150 preview surfaces and now
  shares a FileProvider stream instead of a raw URL string.
- PackagesFilterSheet now has Swift-precedence proof against Figma node
  `40006358:75618` and `FigmaPackagesFilterViewController.swift`. Android uses
  the Swift opaque header and geometry, the Figma/Swift `AirDrop` filter label,
  no stale `All Packages` row, 24dp leading icons, 50dp option rows, iconShape
  dividers/borders, and verified row/close callbacks in light and dark.
- Packages filter live flow now has Swift-precedence proof against Figma nodes
  `40001666:42198` and `40006358:75618`, plus Swift
  `FigmaPackagesViewController.swift` and
  `FigmaPackagesFilterViewController.swift`. Android now proves the top-right
  filter button opens the sheet, status taps reload through the repository with
  the selected status ID, shipment-method taps remain client-side like Swift,
  and close leaves the filtered list visible. The dark status glyph issue is
  fixed by selecting app-theme dark status vectors from `colors.isDark` instead
  of relying on resource-night `@color/icon_duotone`.
- PackageDetails now has Swift-precedence proof against Figma node
  `40001753:15716` and
  `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPackageDetailsViewController.swift`.
  Figma still shows older banded section headers and a metro/icon timeline, so
  Swift wins for the shipped detail screen. Android now uses Swift's Standard
  hero title `AirDrop`, method-tinted hero glyph, gray200 page / gray100 rounded
  sheet hierarchy, plain inline section cards, bullet timeline rows, upload
  zone icon/dash sizing, 56dp invoice file rows ordered trash-then-eye, 48dp
  CIF row, single exchange-rate row, zero-charge `USD 0.00 / JMD 0.00` total,
  and the existing invoice view/delete/Add-to-Cart runtime rails.
  Checks run:
  `git diff --check`,
  `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`,
  targeted `PackageDetailsParityTest` through
  `:app:connectedStagingDebugAndroidTest` (3 tests passed), and manual
  `adb shell am instrument -w -r -e class
  com.ga.airdrop.feature.shipments.PackageDetailsParityTest ...` (`OK (3 tests)`).
  Proof PNGs:
  `/tmp/kotlin_ui_proof/package_details_swift/package_details/package_details_swift_top_light.png`,
  `/tmp/kotlin_ui_proof/package_details_swift/package_details/package_details_swift_top_dark.png`,
  `/tmp/kotlin_ui_proof/package_details_swift/package_details/package_details_swift_charges_light.png`,
  `/tmp/kotlin_ui_proof/package_details_swift/package_details/package_details_swift_charges_dark.png`.
- Payments/Orders now have Swift-precedence proof against Figma Payments node
  `40001753:18909`, Figma Orders node `40001753:19595`,
  `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaPaymentsViewController.swift`,
  and `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaOrdersViewController.swift`.
  Figma confirms the trailing more-square header accessory, but its static
  Payments card omits Swift's runtime invoice-download glyph and its search
  placeholders/sample content differ from Swift. Swift wins: Android keeps the
  Payments top-right invoice download, filter header action, and orange
  pull-to-refresh; list-load failures render the empty state without a modal;
  only invoice-download failures show `Download failed`; Orders renders the
  more-square as a visual accessory without adding a fake action.
  Checks run:
  `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`,
  targeted `PaymentsOrdersParityTest` through
  `:app:connectedStagingDebugAndroidTest` (6 tests passed), and manual
  `adb shell am instrument -w -r -e class
  com.ga.airdrop.feature.shipments.PaymentsOrdersParityTest ...` (`OK (6 tests)`).
  Proof PNGs:
  `/tmp/kotlin_ui_proof/payments_orders_swift/figma/figma_payments_40001753_18909.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/figma/figma_orders_40001753_19595.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/payments_orders/payments_swift_light.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/payments_orders/payments_swift_dark.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/payments_orders/orders_swift_light.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/payments_orders/orders_swift_dark.png`.
- Shipments section cards now have Swift-precedence proof for the header/body
  divider. Swift `FigmaOrderDetailsViewController.swift`,
  `FigmaProductPaymentDetailsViewController.swift`, and
  `FigmaPaymentPackageDetailsViewController.swift` all build a gray200 section
  bar followed by a 1pt gray300 divider before the body. Figma Order Details
  node `40001761:28814` confirms the visual. Android had already removed the
  stale doubled header border; this pass adds the missing shared divider while
  preserving existing card collapse behavior.
  Checks run:
  `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`,
  targeted `ShipmentsSectionCardParityTest` through
  `:app:connectedStagingDebugAndroidTest` (2 tests passed), and manual
  `adb shell am instrument -w -r -e class
  com.ga.airdrop.feature.shipments.ShipmentsSectionCardParityTest ...`
  (`OK (2 tests)`). Proof PNGs:
  `/tmp/kotlin_ui_proof/shipments_section_cards/figma/figma_order_details_40001761_28814.png`,
  `/tmp/kotlin_ui_proof/shipments_section_cards/android/shipments_section_cards/shipments_section_card_swift_light.png`,
  `/tmp/kotlin_ui_proof/shipments_section_cards/android/shipments_section_cards/shipments_section_card_swift_dark.png`.

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
  WhatsApp / Email into individual cards. The later full-layout pass now follows
  Swift for those structural conflicts too.
- Email now uses the Swift/Figma envelope glyph (`ic_mail`) instead of the
  message/chat glyph.
- Functional coverage now covers copy-toast behavior, no stale Live Chat row,
  11 Swift copy buttons, Swift's compact Business Hours copy, 20dp card gaps,
  phone/email/social outbound URI rails, and light/dark screenshots.

### AirCoins

- AirCoins balance/history now has Swift-precedence proof. Figma balance node
  `40001911:22972` conflicts with Swift on the conversion strip and tip/stat
  sizing; Swift wins, so Android keeps the full-screen AirCoin art, 280dp hero
  spacer, 120x44 conversion pills, standalone arrow, and 40dp tip/stat icons.
- The AirCoins header typography was corrected only for this screen pair:
  Swift uses `Typography.subtitle1()` for `AirCoin Balance` and `History`,
  while other home-detail screens still keep their Swift `title1` headers.
- History node `40006461:26563` is a static Figma mock; Swift wins for runtime
  copy and ledger behavior. Android now uses `Invoice No`, `Used Date`, no
  plus/minus amount coloring, a 170dp hero wrap, 150dp hero image, and one
  clipped 15dp ledger card. The balance history button is covered by
  instrumentation.
- Backend request paths were preserved: balance still reads
  `MiscRepository.airCoinsStatus()` / `/aircoins/status`, and history still reads
  `MiscRepository.airCoinHistory()` / `/aircoins/history`.

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
- Related Products empty-state is now Swift-first. Figma node `40002072:24025`
  shows the section with real related cards, but Swift runtime currently has no
  related endpoint and renders two placeholders. Android preserves its existing
  real shortlist path when data is available, and now falls back to two 220dp
  `ShopSkeletonCard`s instead of hiding the section when `related` is empty.
  Proof:
  `/tmp/kotlin_ui_proof/auction_related_empty/figma/auction_product_details_40002072_24025.png`,
  `/tmp/kotlin_ui_proof/auction_related_empty/android/auction_related_empty_swift_light.png`.
- Description fallback is now Swift-first. Figma node `40002072:24025` supplies
  the Description block visual and static lorem ipsum copy, while Swift defines
  runtime nil-data behavior. Android now keeps the existing markdown bold-span
  parsing and uses Swift's full authenticated `/products/:id` fallback instead
  of `No description available.` Proof:
  `/tmp/kotlin_ui_proof/product_description_fallback/figma/auction_product_details_40002072_24025.png`,
  `/tmp/kotlin_ui_proof/product_description_fallback/android/auction_description_fallback_swift_light.png`.
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
- Profile avatar geometry now follows Swift precedence over stale Figma: Android
  keeps the `88`dp wrap, `80`dp gray300 circle, `24`dp badge with `2`dp overshoot,
  and `44`dp placeholder. The edit badge glyph is orangeMain so it stays visible
  in dark mode. The DOB picker rejects future dates/years like Swift's
  `maximumDate`.
- Preferences select fields now have Swift-precedence proof. Android matches
  Swift `SelectableRow` geometry: `subtitle2` label, `gray100` editable field,
  `gray300` disabled email field, `12` radius, exact `52` card height, and `12`
  chevron tinted `textDarkTitle`. Figma node `40000994:19044` shows required
  asterisks, but Swift does not render them, so Android keeps Preferences rows
  unstarred and documents the conflict.
- Invite Friend contacts icon now follows Swift's duotone contract in app light
  and app dark. The stale solid-tint backlog text described an older Android
  implementation; the remaining defect was app-dark source selection because
  `@color/icon_duotone` follows resource-night while `ThemeController` controls
  the app theme. Android now selects the existing white-handset dark vector when
  `colors.isDark` is true.
- Legal live CMS content now has Swift-precedence proof: Android strips frozen
  CMS colors, recolors parsed heading spans to `textDarkTitle`, leaves body text
  on `textDescription`, and keeps orange links through `setLinkTextColor`.
  Light/dark screenshots confirm headings remain readable instead of washing out
  into body gray.
- FAQ now has Swift gap proof. `AccordionCard` keeps the 5dp default used by
  Terms/Privacy, and `FaqScreen` passes the Swift-specific `Spacing.sm` 10dp
  question-to-chevron gap.
- Notification Settings now has Swift-precedence proof. Figma MCP metadata and
  screenshot show the documented node `40001587:18074` is stale and renders
  `Home - Light Mode`, so Swift `FigmaNotificationSettingsViewController.swift`
  and `FigmaIcons.swift` are the authoritative source for this pass. Android now
  matches Swift row heights/gaps, icon color roles in app light/dark, and Push
  FCM token re-registration after profile sync. Proof:
  `/tmp/kotlin_ui_proof/notification_settings/figma_node_40001587_18074_is_home_stale_mapping.png`,
  `/tmp/kotlin_ui_proof/notification_settings/screenshots/notification_settings_swift_light.png`,
  `/tmp/kotlin_ui_proof/notification_settings/screenshots/notification_settings_swift_dark.png`.
- Settings now has Swift-precedence proof against Figma node `40007388:24260`
  and Swift `FigmaSettingsViewController`. Figma still shows translucent
  header/footer chrome and orange-accent duotone Settings icons, but Swift's
  executable page uses solid `gray100` chrome, `stack.spacing = 14`, a custom
  `36`pt gap after Mode, and template-tints non-destructive Settings icons to
  `iconSelected` (black in light, white in dark). Android now follows Swift for
  these conflicts, so app-dark mode no longer depends on resource-night
  `@color/icon_duotone`; `SettingsParityTest` verifies geometry, icon pixels,
  route taps, row/toggle theme switching, cache confirmation, and logout alert.
- More root tap rails now have Swift-precedence proof against Figma node
  `40001948:22354` and Swift `FigmaMoreViewController`. Android locks the Figma
  profile/menu geometry, the exact menu order, the profile-card versus avatar
  tap split, all 12 row route callbacks, and the tier/bell/cart/AirCoins header
  callbacks. This does not close every More subpage as pixel-perfect; it closes
  the root More tap/geometry rail.
- Payment Methods now has Swift-precedence proof against Figma node
  `40001428:9188` and Swift `FigmaPaymentMethodsViewController`. Figma still
  shows a saved-card chooser with card brands, `Number` labels, and an
  `Add New Card` CTA, but Swift ships an informational empty-state card plus a
  `Go to Checkout` rail that opens Cart. Android follows Swift, keeps the screen
  informational, uses canonical `Routes.PAYMENT_METHODS`, and verifies light,
  dark, and Cart navigation without touching payment internals.
- Authorized Users now has Swift-precedence proof against Figma node
  `40000975:7859` and Swift `FigmaAuthorizedUsersViewController.swift`. Android
  keeps the Swift/Figma 20dp content gutters, 56dp card header, active/inactive
  section layout, card-detail taps, bottom-only `Add User` action, and adds the
  missing Swift orange pull-to-refresh rail through the existing repository path.
- Authorized User Detail now has Swift-precedence proof against
  `FigmaAuthorizedUserDetailViewController.swift`; Figma node `40001185:5345`
  is currently stale/misnamed and renders the list rather than detail. Android
  now loads detail once on entry, keeps the Swift read-only no-Edit header,
  refreshes after Activate/Deactivate, and pops after Delete.
- Add Authorized User now has Swift-precedence proof against Figma node
  `40001541:45296` and
  `FigmaAddAuthorizedUserViewController.swift`. Figma omits the Email field,
  but Swift/RN include Email and send `user_email`, so Android keeps Email,
  locks add POST payload parsing, and verifies the hidden edit-mode prefill/PUT
  rail without adding an edit affordance to the read-only detail page.
- Background Images now has Swift-precedence proof against Figma section
  `40006644:65735` / frame `40006644:67051` and Swift
  `FigmaBackgroundImagesViewController.swift`. Figma shows a one-column `335x150`
  landscape list with extra wallpaper IDs, but Swift ships a two-column `220`pt
  portrait picker with IDs `0..13`. Android now follows Swift, preserves the
  existing Home background store path, and normalizes stale Figma-only IDs back
  to the default image. Proof:
  `/tmp/kotlin_ui_proof/background_images/figma/figma_background_images_40006644_67051.png`,
  `/tmp/kotlin_ui_proof/background_images/android/background_images/background_images_swift_light.png`,
  `/tmp/kotlin_ui_proof/background_images/android/background_images/background_images_swift_dark.png`.

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
  warehouse detail flow with the correct type in instrumentation. All three were
  additionally verified through `AppRoot`.
- Warehouse detail hero/badge conflict fixed by Swift precedence: Figma node
  `40000944:3571` shows a `90`px badge and shorter photo, but Swift
  `FigmaWarehousesViewController.makeHero` ships a `240`pt hero, `60`pt
  overlapping circle, `28`pt glyph, and `h5` title. Android now matches Swift
  while preserving the approved Standard/SeaDrop/Express tab strip. Proof:
  `/tmp/kotlin_ui_proof/warehouse_detail_swift/warehouses_swift/warehouse_express_swift_light.png`,
  `/tmp/kotlin_ui_proof/warehouse_detail_swift/warehouses_swift/warehouse_standard_swift_dark.png`.
- Home route buttons were rechecked against Swift `FigmaHomeViewController`
  callbacks and Figma Home node `40001464:28899`: Services -> `SERVICES`,
  Ship Tax -> `SALES_TAXES`, Calculator -> `CALCULATOR`, Drop Alert ->
  `DROP_ALERT`, See More -> `AUCTION`, Refer a friend -> `REFER_A_FRIEND`,
  bell -> `NOTIFICATIONS`, and cart -> `CART`. Android already emitted the
  Swift-equivalent routes; `HomeActivityTilesScreenshotTest` now locks the
  route callbacks in addition to the all-three Standard/SeaDrop/Express-through
  `AppRoot` warehouse proof.
- Home auction card/cart behavior was rechecked against Swift
  `FigmaHomeViewController.makeAuctionCard`, `onTapAuctionCard`, and
  `onTapAddToCart`, with Figma Home node `40001464:28899` as the visual source
  for the 34pt plus button pinned at the card bottom-trailing edge. Swift takes
  behavior precedence: card taps open product details, and plus taps only toggle
  `FigmaCartStore`/cart badge. Android keeps the existing slug detail route and
  `CartStore` path, and `HomeActivityTilesScreenshotTest` now proves the plus
  hit target toggles cart without leaking into the card route while the card
  still opens `auctionProductDetails/{slug}`.
- Activity/highlight boxes were measured against Figma and Swift. Android
  matches the Swift/Figma values: activity tiles are `(screen - 40 - 10) / 2`
  wide and `108` high, with `32` icons, `10` stack gap, and `10x20` padding;
  Auction Highlights cards are `160x245` with `124` image height, `8` padding,
  and `6` stack spacing. No visual size change was made because Swift/Figma
  already match the Android implementation. Regression tests now lock this in.
- Header/footer opacity was checked on 2026-07-05. Figma Home node
  `40001464:28899` still shows translucent Home chrome, but Swift
  `FigmaTabHeader` and `FigmaBottomTabBar` both layer a blur underneath an
  opaque `gray200` overlay, so Swift takes precedence. Android already uses
  opaque `gray200` for both header and bottom tab/footer; no production chrome
  change was needed. `HomeChromeOpacityParityTest` locks this with a
  high-contrast underlay and pixel samples in app light and app dark so future
  transparency wash-through fails. Proof:
  `/tmp/kotlin_ui_proof/home_chrome_opacity/figma/figma_home_40001464_28899.png`,
  `/tmp/kotlin_ui_proof/home_chrome_opacity/android/home_chrome_opacity/home_chrome_opacity_swift_light.png`,
  `/tmp/kotlin_ui_proof/home_chrome_opacity/android/home_chrome_opacity/home_chrome_opacity_swift_dark.png`.
- Dark-mode Home activity icon issue fixed: Services gear, Ship Tax hull/waves,
  Calculator frame, and Drop Alert secondary strokes now flip to white in app
  dark mode while primary orange layers remain orange. Proof:
  `/tmp/kotlin_ui_proof/home_activity_tiles/android_home_activity_tiles_light_after_fix.png`,
  `/tmp/kotlin_ui_proof/home_activity_tiles/android_home_activity_tiles_dark_after_fix.png`.
- Home bottom-tab navigation state was rechecked on 2026-07-05 against Swift
  `FigmaBottomTabBar` + `FigmaRouteResolver.switchToTabRoute` first, then
  Figma Home node `40001464:28899`. Swift takes precedence here: tab changes
  root-swap the `UINavigationController` with `setViewControllers([destination])`.
  The earlier visible Home-selected/More-visible state did not reproduce on
  current `origin/main`, but the strengthened hidden-stack check confirmed the
  real drift: `switchTab` could leave the previous tab underneath instead of
  matching Swift's root replacement. Android now clears the stack with the same
  root-swap behavior and ignores already-selected tab taps like Swift's
  `guard route != activeRoute`. `AppRootNavigationParityTest` verifies real
  `AppRoot` More -> Home clears the More/FAQ row, `switchTab` reaches Home with
  no previous tab left in the test back stack, and the More -> FAQ drill-down
  hides the bottom tab bar instead of showing a selected Home tab.
  Proof:
  `/tmp/kotlin_ui_proof/home_tab_navigation/figma/figma_home_40001464_28899.png`,
  `/tmp/kotlin_ui_proof/home_tab_navigation/android/home_tab_navigation/app_root_more_before_home_tab.png`,
  `/tmp/kotlin_ui_proof/home_tab_navigation/android/home_tab_navigation/app_root_home_after_more_tab.png`,
  `/tmp/kotlin_ui_proof/home_tab_navigation/android/home_tab_navigation/harness_home_after_more_start.png`.

### Shipments

Source files:
- Android: `feature/shipments/*`
- Swift: `FigmaShipmentsViewController.swift`, `FigmaPackagesViewController.swift`,
  `FigmaPackageDetailsViewController.swift`, `FigmaPaymentsViewController.swift`,
  `FigmaOrdersViewController.swift`
- Figma nodes: `40000823:9633` and shipment drill-down nodes in backlog.

Findings to verify/fix:
- Shipments hub summary tiles, section `View More` actions, package cards,
  payment cards, order cards, and package cart toggles now have connected
  Swift-precedence tap proof. The Packages filter header-to-sheet/apply/close
  path is also verified. Full-list backend pagination/search/reset contracts
  are now locked for Packages, Payments, and Orders. Remaining tap checks are
  deeper flows: detail rows, invoice actions, and detail-screen CTAs.
- Shared Shipments search-field icon placement is now closed: Packages keeps
  Swift's leading 22pt icon and Payments/Orders use Swift's trailing 18pt icon.
- Existing backlog already flags package detail surfaces, timeline rendering,
  charges footer, filter sheet row icons/fonts, payments refresh/invoice button,
  and multiple detail-screen parity issues.
- Backend endpoints have source/test proof for visible hub shortlists and full
  list pagination/search/reset. Remaining live-auth endpoint checks: invoice
  URL, package detail mutations, payment detail, order detail, and complete
  authenticated refresh paths.

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
- Whole-layout Swift/Figma conflicts are now resolved in Swift's favor: Android
  removes the Figma-only Live Chat row from the Help list, splits Contact /
  WhatsApp / Email into individual Swift cards, uses 20dp card gaps and 15dp
  card padding, removes the Business Hours copy button, and uses Swift's compact
  Business Hours text.
- Instrumentation covers copy-toast behavior, no stale Live Chat row, 11 Swift
  copy buttons, 20dp card gaps, phone/email/social outbound URI rails, and
  light/dark screenshots.

### AirCoins

Source files:
- Android: `feature/homedetails/AirCoinScreen.kt`
- Swift: `FigmaAirCoinHistoryViewController.swift`
- Figma: nodes `40001911:22972`, `40006461:26563`

Findings to verify/fix:
- Closed by Swift-precedence pass: balance and history were compared against
  Swift first and Figma second. Android now matches Swift-specific header
  typography, balance control/card geometry, history hero/table geometry, and
  history-copy behavior where Figma conflicts.
- History navigation is covered by instrumentation through the top-right balance
  action.
- `/aircoins/status` and `/aircoins/history` request paths were checked in code
  and preserved; live authenticated endpoint validation remains outside this UI
  slice unless a backend defect is separately assigned.
- Light and dark screenshots are verified under
  `/tmp/kotlin_ui_proof/aircoins_swift_history/aircoins_swift/`.

### GoldPriority / Customer Tier

Source files:
- Android: `feature/homedetails/GoldPriorityScreen.kt`
- Swift: `FigmaGoldPriorityViewController.swift`
- Figma: node `40001432:23506`

Findings verified/fixed:
- Swift is the precedence source for the pager. Figma MCP confirms the intended
  Customer Tier visual structure, but its generated node differs from Swift on
  tier-title size, content inset, and decorative/status mock layers; Android
  follows Swift for those conflicts.
- Tier names now match Swift's `adjustsFontSizeToFitWidth` behavior by measuring
  the remaining badge-row width and selecting the largest 28sp-to-20sp font size
  that avoids single-line overflow. The `Platinum Priority` 360dp regression is
  locked in light and dark.
- Status-bar icons now match Swift's `.lightContent` override while the screen
  is composed, including wrapped activity contexts, and restore the previous
  setting on dispose.

### Restricted Items

Source files:
- Android: `feature/more2/RestrictedItemsScreen.kt`, `feature/more2/RestrictedItemsData.kt`
- Swift: `FigmaRestrictedItemsViewController.swift`, `FigmaRestrictedItemsInfoViewController.swift`
- Figma: nodes `40001432:14025`, `40001432:14918`, `40001432:15045`,
  `40001432:18025`, `40001432:18303`, `40001432:18506`

Findings verified/fixed:
- Swift is the precedence source for the active app flow. Figma MCP confirms
  node-map drift: `40001432:14025` is an Information/legal page, and
  `40001432:14918` is a tabbed Restricted Items variant. Android keeps Swift's
  searchable category-list entry screen and push detail pages.
- The old low-polish glyph carve-out is removed. License Required now uses the
  existing circular info vector, Restricted Commodities uses the existing
  two-color dangerous-goods vector without flattening its dark-mode stroke, and
  the detail note card follows Swift's `textDarkTitle`/`textDescription` token
  behavior in light and dark.
- `RestrictedItemsParityTest` locks the Swift list/search/detail geometry, route
  behavior, absence of the Figma tab labels, and light/dark proof screenshots.

### More2 Inner Header

Source files:
- Android: `feature/more2/More2Components.kt`
- Swift: every More2/drill-down `Figma*ViewController` using
  `FigmaIcon.chevronDown(size: 24)` rotated `pi / 2`; `FigmaIcons.swift`
  `FigmaIcon_ChevronDown`
- Figma: Promotions node `40001646:14035`

Findings verified/fixed:
- Swift is the precedence source for the shared back affordance. Figma MCP for
  Promotions still shows a static `Arrow - Right`-derived left arrow, but Swift
  runtime controllers render a 24pt rotated chevron for the active app.
- `More2InnerHeader` now uses the existing chevron vector at 24dp, rotated left
  and tinted through `AirdropTheme.colors.textDarkTitle`, so app dark mode follows
  the Compose theme instead of resource-night assumptions.
- `More2InnerHeaderParityTest` verifies the 36dp tap target, 24dp chevron, narrow
  chevron shape rather than wide tailed arrow, light/dark tint, click dispatch,
  and screenshots.
- Full connected-suite parity on `airdrop_test` caught two remaining icon
  failures after the header fix: Notification Settings SMS and Invite Friend
  Contacts rendered their `@color/icon_duotone` secondary strokes from Android
  resource-night while `ThemeController` was set to app light. The repaired
  rows now use explicit Swift-role light vectors plus their existing dark
  variants, so Swift/Figma duotone roles are controlled by app theme, not the
  emulator's system theme.
- Final verification on 2026-07-05:
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - direct instrumentation on the original failing system-dark emulator:
    `NotificationSettingsParityTest` + `InviteFriendParityScreenshotTest`:
    5 tests passed
  - Gradle targeted on the original failing system-dark emulator:
    `NotificationSettingsParityTest` + `InviteFriendParityScreenshotTest`:
    5 tests passed
  - More2 adjacent sweep on system-dark emulator:
    `More2InnerHeaderParityTest`, `RestrictedItemsParityTest`,
    `AuthorizedUsersParityTest`, `InviteFriendParityScreenshotTest`,
    `LegalContentParityTest`: 16 tests passed
  - full connected suite on system-dark emulator: 112 tests passed
  - system-light focused gate after switching the remaining emulator back to
    light: `NotificationSettingsParityTest`, `InviteFriendParityScreenshotTest`,
    `More2InnerHeaderParityTest`: 7 tests passed
  - isolated Home auction highlight test passed after an interrupted full
    system-light rerun stalled in runner state; no Home assertion regression was
    reproduced.

### Account Deletion Reason

Source files:
- Android: `feature/more2/AccountDeletionReasonScreen.kt`,
  `AccountDeletionReasonViewModel.kt`, `feature/more/BackgroundStore.kt`
- Swift: `FigmaAccountDeletionReasonViewController.swift`,
  `FigmaAccountDeletionViewController.swift`
- Figma: Account Deletion Reason `40007388:27504`; documented success-modal
  node `40007462:64371`

Findings verified/fixed:
- Swift is the precedence source. Figma MCP for `40007388:27504` still shows
  duplicated reason labels plus the typo "Why do you want you want...", and
  shows a grab handle in the destructive sheet. Swift carries the canonical
  reason list, corrected question text, and runtime confirmation sheet without
  a drag handle; Android now follows Swift.
- The confirmation sheet now starts the warning graphic 28dp below the rounded
  sheet top without rendering the stale Figma handle, while preserving the
  Swift 24dp top radius, 225dp warning graphic, H5 title, Body2 description,
  full-bleed divider, and 50dp side-by-side destructive buttons.
- Deactivation now performs Swift-level local logout hygiene: bearer token,
  shared header session, persisted cart cache, background image selection
  (`BACKGROUND_IMAGE_ID`), and the in-memory deletion credential handoff are
  cleared after the backend success path.
- Verification on 2026-07-05:
  - Figma MCP design context checked for `40007388:27504` and `40007462:64371`
  - Swift source compared in
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaAccountDeletionReasonViewController.swift`
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - Gradle focused device run:
    `AccountDeletionReasonParityTest`: 2 tests passed
  - proof PNG:
    `/tmp/kotlin_ui_proof/account_deletion_reason/account_deletion_reason_confirm_swift_light.png`

### Refer A Friend

Source files:
- Android: `feature/more2/ReferAFriendScreen.kt`,
  `ReferAFriendViewModel.kt`
- Swift: `FigmaReferAFriendViewController.swift`
- Figma: Refer a Friend `40001940:26885`

Findings verified/fixed:
- Swift is the runtime precedence source. Figma MCP for `40001940:26885` still
  renders the older landing frame with a bottom "Invite" CTA, while Swift has
  the referral-link card, inline "Invite Friends" CTA, referrals section, and
  lifecycle split used by the app.
- Android was loading referred friends twice on first entry: once from
  `ReferAFriendViewModel.init` and again from the screen `LaunchedEffect` that
  intentionally mirrors Swift `viewWillAppear`. The profile/account-number
  referral-link load still belongs in init, matching Swift `viewDidLoad`.
- Android now keeps only the init referral-link load and leaves the referred
  friends call on the screen-entry effect, so initial entry performs one
  profile/account-number request and one referred-friends request.
- Verification on 2026-07-05:
  - Figma MCP design context checked for `40001940:26885`
  - Swift source compared in
    `/Users/codecityceo/Documents/GitHub/SWIFT_APP/Airdrop/FigmaReferAFriendViewController.swift`
  - `git diff --check`
  - `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`
  - Gradle focused device run:
    `ReferAFriendParityTest`: 1 test passed

### Dark Theme Icons

Source files:
- Android drawables in `app/src/main/res/drawable*`
- Android night colors in `app/src/main/res/values-night/colors.xml`
- Swift icon truth: `FigmaIcons.swift` plus per-screen `Figma*ViewController`.

Findings to verify/fix:
- Do a page-by-page dark pass; no icon should disappear, flatten incorrectly, or
  keep a light-only hardcoded stroke/fill.
- Notification Settings icon variants are closed with Swift-precedence proof.
  The stale Figma node mapping remains open as documentation debt only: node
  `40001587:18074` resolves to Home, not Notification Settings.
- Notification Settings SMS and Invite Friend Contacts now have explicit
  app-light resources (`ic_contacts_chat_light`,
  `ic_contacts_contact_number_light`) paired with app-dark resources, preventing
  resource-night leakage in app light mode.
- New icons added after the earlier dark pass must be audited for
  `@color/icon_duotone` or explicit Swift-matching orange/dark role colors.

## Work Split Notes

- BlueDeer/Claude owns broad Android/KOTLIN_APP parity context.
- Codex/MagentaCastle is working through More/Legal/Profile/AirCoins/HomeDetails and narrow
  Shipments parity slices. More root tap rails, Documents
  card/action-row geometry, info alert, refresh/reload behavior, plus Profile
  avatar/DOB, Preferences select fields, Invite Friend contacts icon, More2
  shared inner header, Restricted
  Items search/list/detail icons and notes, Legal live CMS heading colors, FAQ accordion gap, Notification Settings, AirCoins
  balance/history, GoldPriority tier-name/status-bar, PackageDetails
  Swift-precedence screen pass, Home chrome opacity, Home route callbacks, and
  Home auction card/cart behavior,
  PaymentPackageDetails footer/timeline/payment-copy,
  ProductPaymentDetails/OrderDetails hero/payment-copy geometry, and
  InvoiceViewer surface/share-file behavior, PackagesFilterSheet geometry plus
  callbacks, Packages filter live flow and dark status icons, Payments/Orders
  header/error follow-ups, and Shipments section-card dividers, plus Authorized Users refresh/list tap rails, Add Authorized User
  add/edit payload proof, and Background Images Swift-precedence picker, now
  have Figma MCP + Swift comparison and targeted device-test proof.
- Other agents are now touching Shop files; Codex must not edit Shop unless the
  room hands that slice over.
- Keep POS, production, paid-provider/model config, secrets, and unrelated
  Laravel work out of scope.

## Page Checklist

For each page, fill this before claiming completion:

| Page | Android file(s) | Swift file | Figma node | Backend/API | Light seen | Dark seen | Taps verified | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Home | `feature/home/HomeScreen.kt`, chrome components | `FigmaHomeViewController.swift`, `FigmaTabHeader.swift`, `FigmaWarehousesViewController.swift` | `40001464:28899`, Warehouse `40000944:3571` | `/user/me`, `/aircoins/status`, auctions, warehouses | yes | yes | partial | MagentaCastle | header Swift-precedence, header/footer chrome opacity, activity icons, warehouse card tap/geometry, Warehouse detail Swift-precedence hero/badge, activity/highlight geometry, primary route callbacks, auction card/cart tap separation, and bottom-tab state verified; remaining Home live-data/full-page endpoint issues still open |
| Shipments hub/details | `feature/shipments/ShipmentsScreen.kt`, `PackageDetailsScreen.kt`, `PackagesFilterSheet.kt`, `PaymentsScreen.kt`, `OrdersScreen.kt`, `PaymentPackageDetailsScreen.kt`, `ProductPaymentDetailsScreen.kt`, `OrderDetailsScreen.kt`, `InvoiceViewerScreen.kt`, `ShipmentsUi.kt` | `FigmaShipmentsViewController.swift`, `FigmaPackageDetailsViewController.swift`, `FigmaPackagesFilterViewController.swift`, `FigmaPaymentsViewController.swift`, `FigmaOrdersViewController.swift`, `FigmaPaymentPackageDetailsViewController.swift`, `FigmaProductPaymentDetailsViewController.swift`, `FigmaOrderDetailsViewController.swift`, `FigmaInvoiceViewerScreenViewController.swift` | `40000823:9633`, Packages `40001666:42198`, Package Details `40001753:15716`, Packages filter `40006358:75618`, Payments `40001753:18909`, Orders `40001753:19595`, `40001761:29389`, `40004950:25064`, `40001761:28814`, related invoice-entry `40001753:15716` | summary/packages/statuses/payments/orders/package detail/payment detail/order detail/invoice files | yes | yes | partial | BlueDeer/MagentaCastle | hub tap rails, summary icon/geometry, shared search-field split, PackagesFilterSheet geometry/callbacks, Packages filter live flow, backend pagination/search/reset contracts, and dark status icons now verified against Swift/Figma; PackageDetails, Payments/Orders header/error follow-ups, section-card dividers, PaymentPackageDetails footer/timeline/payment-copy, ProductPaymentDetails/OrderDetails hero/payment-copy, and InvoiceViewer surface/share-file slices closed; remaining broad live-auth/full-flow backend parity still open |
| Help | `feature/contacts/ContactsScreen.kt` | `FigmaContactsViewController.swift` | `40001617:20377` | contact/static routes/social URLs | yes | yes | yes | MagentaCastle | closed for Swift-precedence layout, typography, icons, copy actions, and phone/email/social URI rails; map/WhatsApp runtime app-handling can still be broadened if product wants native app preference |
| AirCoins | `feature/homedetails/AirCoinScreen.kt` | `FigmaAirCoinHistoryViewController.swift` | `40001911:22972`, `40006461:26563` | `/aircoins/status`, history path checked in code | yes | yes | yes | MagentaCastle | closed for balance/history Swift/Figma UI; live authenticated endpoint check not rerun |
| GoldPriority / Customer Tier | `feature/homedetails/GoldPriorityScreen.kt` | `FigmaGoldPriorityViewController.swift` | `40001432:23506` | `/user/me` tier resolution path preserved | yes | yes | yes | MagentaCastle | closed for tier-name autoscale and status-bar Swift parity; full pager data path preserved |
| More/Profile/Legal | `feature/more/*`, `feature/more2/*` | matching `Figma*ViewController.swift` files | see backlog, More root `40001948:22354`, Payment Methods `40001428:9188`, Settings `40007388:24260`, Authorized Users `40000975:7859`, Add Authorized User `40001541:45296`, Authorized User Detail stale node `40001185:5345`, Background Images `40006644:65735`/`40006644:67051`, Restricted Items `40001432:*`, Shipping Rates `40001567:54206` | user/profile/content/faqs/etc., device-tokens/register, local background prefs, static restricted-items data, `/shipping-rates`, `/authorized-users`, `/authorized-users/{id}` mutations, `/paymentMethods` UI rail to Cart | partial | partial | partial | Codex | More root profile/menu/header tap rails, Payment Methods Swift-precedence empty-state/Cart rail, Settings Swift/Figma geometry/icon/action rails, Documents card/action-row geometry, info alert, refresh/reload, Authorized Users pull-to-refresh/list taps, Add Authorized User add/edit payload rails, Authorized User Detail one-load/read-only/mutation/delete rails, Background Images Swift-precedence picker, Restricted Items Swift-precedence list/search/detail/icons/notes, Shipping Rates backend/fallback table and calculator CTA rail, Profile avatar/DOB, Preferences fields, Invite Friend contacts icon, Legal live CMS heading colors, FAQ gap, and Notification Settings verified |
| Shop | `feature/shop/*` | shop/auction/product detail Swift files | `40001846:53519`, `40002072:24025` | products/auction/cart | no | partial | partial | BlueDeer/others | `a1768d2` route proof captured; visual parity/cart still open |

---

## BlueDeer session log — 2026-07-05 (Swift-first; documented Figma↔Swift conflicts)

Per Kemar/MagentaCastle directive: Swift wins conflicts; conflicts documented here; Figma wins only where Swift lacks a designed element (Kemar's Gov-Charges precedent).

**Figma↔Swift CONFLICTS (Swift won):**
- **Home header** — Figma `40001464:28926` = dark frosted translucent `rgba(41,41,41,0.7)` + blur + white text. Swift `FigmaTabHeader.swift:129-131` = OPAQUE `gray200` (`#f5f5f5` light / `#333333` dark) + `textDarkTitle`/`iconSelected`. **Swift wins → opaque gray200** (origin/main `0184744`). My earlier Figma-translucent attempt (`bc430a4`) was reverted. Both Swift+Android default to follow-system theme.
- **Shipments search field** (shared `ShipmentsSearchField`) — Swift Packages `makeSearchCard` = LEADING 22pt magnifier; Swift Payments/Orders `makeSearchRow` = TRAILING 18pt. Component is shared by all three, so a blind flip breaks Packages. **CLOSED by parameterized shared component** — Figma nodes `40001666:42198`, `40001753:18909`, and `40001753:19595` were refreshed; Swift wins over static Figma search copy/icon-side conflicts.
- **Background Images** — Swift `FigmaBackgroundImagesViewController` =
  default + 13 `back_color` wallpapers, two-column `220`pt portrait grid, 44pt
  selection controls, and bottom Save. Figma `40006644:65735` /
  `40006644:67051` = one-column `335x150` landscape list with extra wallpaper
  IDs. **Swift wins → 14-choice two-column picker.** Android keeps the exported
  assets but exposes only Swift IDs `0..13`, normalizes stale Figma-only saved
  IDs back to default, and preserves Home background resolution through
  `BackgroundStore`.

**Figma WINS where Swift lacks (Kemar precedent):**
- **Onboarding** — Swift `SceneDelegate` collapsed LaunchApp/ChooseYourLook/Onboarding/AuthLanding→Login. Figma "Onboarding - Design Done" `40006240:*` exists. Wired first-run flow (`92bdaf0`), device-verified: Splash→carousel→Choose-Your-Look→AuthLanding→Login. NOTE: any AppRoot reactive-logout effect must exclude SPLASH+ONBOARDING.

**Swift-source-exact fixes proven (`34e9620`, adversarial audit + verify):** Home header icon spacing (14→20, 16→19dp), tier lineHeight (22→24); PackageDetails hero 262→240dp, CIF row 59→48dp, CIF icon 24→20dp, divider `#D9D9D9`→`gray300 #EBEBEB`; ShipmentsUi package-status always Completed-green (Swift :556), Order value `$`→`USD`, TotalChargesBox radius 15→10, borders→gray300, empty-label body2→body1; ProductPaymentDetails summary titles subtitle1→title2; InvoiceViewer Share button gradient→flat OrangeMain, height 50→52, 5 state labels body2→body1. + `6a21713` §108/§153/§99 (MagentaCastle verifier-accepted on device).

**Remaining OPEN / pending device proof (→ PearlFox):** deep-screen renders for `34e9620`; §117 InvoiceViewer blocked by HTTP 403; Orders decorative icon (low-confidence). CRITICAL payment bugs remain ON HOLD for Kemar.

Health @ origin/main `c085c88`: `assembleStagingDebug` PASS, unit tests 9/0-fail, all BlueDeer commits intact.
