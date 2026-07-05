# Pixel-Parity Backlog

Source: adversarially-verified full-app audit (117 confirmed findings,
Kotlin vs Swift `Figma*ViewController`s vs Figma file N4k6jzpeLZgeRS5O1xfyIv),
2026-07-04. 63 findings were fixed in commits 48db012 + 9222e1d (+ follow-ups);
the backlog below remains the living ledger. Each entry carries the exact fix
the verifying agent specified — apply, build, and verify on the emulator in
light AND dark.

---

## STATUS LEDGER (updated 2026-07-05 — MagentaCastle/Codex)

> The list below was catalogued at `08e36e2`. Since then **29 items are fixed or verified on-device** and locked by regression proof. Do not redo them.

**✅ DONE (pushed):**
- Package details §45 (gray200/gray100 surfaces), §54 (status-tinted bullet dots), §63 (inline titles/no dividers/title2 values), §72 (Exchange-Rate + plain Total footer) → `db84b0d`
- Payments §81 (download top-right), Payments/Orders §90 (pull-to-refresh) → `6605dd4`
- Shop root+lists §162 (245dp card + per-context title lines), Shop root §171 (top inset), ShopDropdownField §180/§207 (restyle), Auction Product Details §189 (hero placeholder), Feature Product Details §198 (link-unavailable alert) → `e7357a5`
- **Auction Product Details related-products empty-state:** Product detail node
  `40002072:24025` was checked in Figma and Swift
  `FigmaAuctionProductDetailsViewController.swift` takes precedence for runtime
  behavior: auction mode always renders `Related Products`, and when no related
  endpoint/data exists it shows two placeholder cards. Android now keeps the
  existing real related-card path when data loads and renders two 220dp
  `ShopSkeletonCard`s when `related` is empty. Proof:
  `/tmp/kotlin_ui_proof/auction_related_empty/figma/auction_product_details_40002072_24025.png`,
  `/tmp/kotlin_ui_proof/auction_related_empty/android/auction_related_empty_swift_light.png`.
- **Auction Product Details description fallback:** Product detail node
  `40002072:24025` was checked through Figma MCP context/screenshot for the
  Description block, and Swift
  `FigmaAuctionProductDetailsViewController.swift:574` takes precedence for the
  null-description runtime copy. Android now preserves the existing bold
  markdown span support and uses Swift's long `/products/:id` fallback instead
  of `No description available.` Proof:
  `/tmp/kotlin_ui_proof/product_description_fallback/figma/auction_product_details_40002072_24025.png`,
  `/tmp/kotlin_ui_proof/product_description_fallback/android/auction_description_fallback_swift_light.png`.
- **Live bug (not in the 54):** product-detail dead feature + HTML-entity decode → `a1768d2`
- **Swift-precedence conflict:** Home header must use Swift's opaque `gray200`
  semantic surface even though Figma node `40001464:28926` is translucent.
  Verified light and app-dark proof:
  `/tmp/kotlin_ui_proof/android_swift_precedence_home_header.png`,
  `/tmp/kotlin_ui_proof/android_swift_precedence_home_header_app_dark.png`.
- **Home activity dark icons:** Services, Ship Tax, Calculator, and Drop Alert
  activity icons now use explicit app-theme light/dark drawables matching Swift
  `FigmaIcons.swift` and Figma tile nodes. Verified proof:
  `/tmp/kotlin_ui_proof/home_activity_tiles/android_home_activity_tiles_light_after_fix.png`,
  `/tmp/kotlin_ui_proof/home_activity_tiles/android_home_activity_tiles_dark_after_fix.png`.
- **Home warehouse cards:** Standard/SeaDrop/Express carousel geometry was
  compared against Figma Home node `40001464:28899` and Swift
  `FigmaHomeViewController.swift`; both sources agree on y=326, carousel 346,
  card 238x326. Android already matched those values. Instrumentation verified
  all three card routes and Standard-through-`AppRoot` detail opening. Proof:
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_light_warehouse_geometry.png`,
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_dark_warehouse_geometry.png`,
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_standard_after_tap.png`.
- **Help full Swift-precedence layout/icons/intents:** Help was compared against
  Figma node `40001617:20377` and Swift
  `FigmaContactsViewController.swift`. Swift wins over Figma where they differ:
  Android now removes the Figma-only Live Chat row from the Help list, splits
  Contact / WhatsApp / Email into individual cards, uses Swift 20dp card gaps
  and 15dp card padding, removes the Business Hours copy button, uses Swift's
  compact Business Hours copy, keeps Swift `subtitle1` parity for values/social
  rows, preserves Help-specific dark icon variants, and verifies phone/email/
  social outbound URI rails. Proof:
  `/tmp/kotlin_ui_proof/help_contacts/android_help_top_light_final.png`,
  `/tmp/kotlin_ui_proof/help_contacts/android_help_top_dark_final.png`,
  `/tmp/kotlin_ui_proof/help_contacts/android_help_social_light_final.png`,
  `/tmp/kotlin_ui_proof/help_contacts/android_help_social_dark_final.png`,
  `/tmp/kotlin_ui_proof/help_contacts_swift/android_help_swift_top_light.png`,
  `/tmp/kotlin_ui_proof/help_contacts_swift/android_help_swift_top_dark.png`,
  `/tmp/kotlin_ui_proof/help_contacts_swift/android_help_swift_social_light.png`,
  `/tmp/kotlin_ui_proof/help_contacts_swift/android_help_swift_social_dark.png`.
- **Home activity/highlight geometry:** Activity tiles and Auction Highlights
  cards were compared against Figma Home node `40001464:28899` and Swift
  `FigmaHomeViewController.swift`. Android already matches the shared geometry:
  activity tiles are `(screen - 40 - 10) / 2` wide by `108` high; Auction
  Highlights cards are `160x245` with `124` image height. Instrumentation now
  locks the sizes with geometry assertions. Proof:
  `/tmp/kotlin_ui_proof/home_tiles_geometry/figma_home_light_geometry.png`,
  `/tmp/kotlin_ui_proof/home_tiles_geometry/android_home_top_light_geometry.png`,
  `/tmp/kotlin_ui_proof/home_tiles_geometry/android_home_top_dark_geometry.png`,
  `/tmp/kotlin_ui_proof/home_tiles_geometry/android_home_activity_tiles_light_geometry.png`,
  `/tmp/kotlin_ui_proof/home_tiles_geometry/android_home_activity_tiles_dark_geometry.png`.
- **Documents card/action-row geometry:** Documents was compared against Figma
  node `40000975:7748` and Swift `FigmaDocumentsViewController.swift`. Swift
  takes precedence because Figma still shows the older edge-to-edge footer while
  Swift uses the newer inset actions row. Android now matches Swift card radius
  `15`, list gap `12`, content inset `16x14`, inset bordered actions row
  `48` high, and uploaded-file row `56` high with `28` PDF icon and `18` action
  glyphs. Proof:
  `/tmp/kotlin_ui_proof/documents_swift_geometry/figma_documents_light.png`,
  `/tmp/kotlin_ui_proof/documents_swift_geometry/android_documents_card_swift_geometry_light.png`,
  `/tmp/kotlin_ui_proof/documents_swift_geometry/android_documents_card_swift_geometry_dark.png`.
- **Documents info alert label:** Documents info was compared against Swift
  `FigmaDocumentsViewController.swift`; Swift uses `Got it`, and Android now
  passes that label only for the Documents info alert. `MoreAlertDialog` keeps
  default `OK` behavior for other callers. Verified by
  `DocumentsScreenScreenshotTest` click coverage.
- **Documents refresh/reload behavior:** Documents was rechecked against Figma
  node `40000975:7748` and Swift `FigmaDocumentsViewController.swift`. Swift
  takes precedence for behavior: `viewDidAppear` calls `loadDocuments()`, and the
  scroll view has an orange `UIRefreshControl`. Android now reloads through the
  same `load()` path on lifecycle resume and exposes a pull-to-refresh container
  that calls `refresh()`.
- **Profile avatar + DOB:** Edit Profile was compared against Figma node
  `40007189:63763` and Swift `FigmaProfileViewController.swift`. Swift wins the
  avatar conflict because Figma still shows the older 107px avatar; Android now
  locks Swift's 88dp wrap / 80dp gray300 circle / 24dp edit badge geometry, uses
  Swift orange for the edit glyph in dark mode, and rejects future DOB dates.
- **Preferences select fields:** Preferences was compared against Figma node
  `40000994:19044` and Swift `FigmaPreferencesViewController.swift`. Android
  now matches Swift `SelectableRow` geometry: subtitle2 labels, gray100 editable
  cards, gray300 disabled email card, 12dp radius, exact 52dp card height, and
  12dp textDarkTitle chevrons. Figma shows required asterisks on all labels, but
  Swift does not render them, so Android keeps no asterisks by Swift precedence.
  Proof:
  `/tmp/kotlin_ui_proof/preferences_swift_field/figma_preferences_40000994_19044.png`,
  `/tmp/kotlin_ui_proof/preferences_swift_field/android_preferences_select_field_swift_light.png`,
  `/tmp/kotlin_ui_proof/preferences_swift_field/android_preferences_select_field_swift_dark.png`.
- **Invite Friend contacts icon:** Invite Friend was compared against Swift
  `FigmaInviteFriendViewController.swift` and `FigmaIcons.swift`; Figma MCP
  confirmed the documented referral nodes `40001940:26797` and `40001940:26885`
  are Refer-a-Friend landing frames, not the Send Invitation form. Android now
  follows Swift's `contactNumber(primary: orangeMain, secondary: iconSelected)`
  contract in both app light and app dark by reusing the existing white-handset
  dark vector when `ThemeController` dark mode is active. Proof:
  `/tmp/kotlin_ui_proof/invite_friend_icon/figma_referral_40001940_26797.png`,
  `/tmp/kotlin_ui_proof/invite_friend_icon/figma_referral_40001940_26885.png`,
  `/tmp/kotlin_ui_proof/invite_friend_icon/android_invite_friend_contacts_icon_light.png`,
  `/tmp/kotlin_ui_proof/invite_friend_icon/android_invite_friend_contacts_icon_dark.png`.
- **Legal live CMS heading colors + FAQ chevron gap:** Terms, Privacy, and FAQ
  were compared against Figma nodes `40001383:9894`, `40001387:9042`, and
  `40001387:8896`, then against Swift
  `FigmaTermsConditionsViewController.swift`,
  `FigmaPrivacyPolicyViewController.swift`, and `FigmaFAQViewController.swift`.
  Android now has regression proof that live CMS legal headings are recolored to
  `textDarkTitle` while body text stays `textDescription`, frozen CMS colors are
  stripped before theme recoloring, Terms/Privacy keep Swift's 5dp title-to-
  chevron gap, and FAQ uses Swift's 10dp gap. Proof:
  `/tmp/kotlin_ui_proof/legal_content/screenshots/legal_live_html_light.png`,
  `/tmp/kotlin_ui_proof/legal_content/screenshots/legal_live_html_dark.png`.
- **Notification Settings Swift parity:** Notification Settings was compared
  against Swift `FigmaNotificationSettingsViewController.swift` and
  `FigmaIcons.swift`. Figma MCP metadata/screenshot proved the documented node
  `40001587:18074` is stale and renders `Home - Light Mode`, so Swift takes
  precedence for this repair until the node map is corrected. Android now uses
  Swift row metrics (`60`dp master/section, `56`dp sub rows, `12`dp normal
  gaps, `20`dp section gaps), Swift icon roles in app light/dark, and the Swift
  Push self-heal behavior through the existing FCM registration endpoint. Proof:
  `/tmp/kotlin_ui_proof/notification_settings/figma_node_40001587_18074_is_home_stale_mapping.png`,
  `/tmp/kotlin_ui_proof/notification_settings/screenshots/notification_settings_swift_light.png`,
  `/tmp/kotlin_ui_proof/notification_settings/screenshots/notification_settings_swift_dark.png`.
- **AirCoins balance/history Swift parity:** AirCoins was compared against
  Swift `FigmaAirCoinHistoryViewController.swift`, Figma balance node
  `40001911:22972`, and Figma history node `40006461:26563`. Swift takes
  precedence where Figma differs: balance keeps Swift's 120x44 conversion pills,
  standalone 24dp arrow, 40dp tip icon, and `subtitle1` header title; history
  keeps Swift's 170dp hero wrap / 150dp image, `Invoice No` / `Used Date`
  ledger labels, unsigned text-colored amounts, and one clipped 15dp card.
  The history button is covered by instrumentation. Proof:
  `/tmp/kotlin_ui_proof/aircoins_swift_history/aircoins_swift/aircoin_balance_swift_light.png`,
  `/tmp/kotlin_ui_proof/aircoins_swift_history/aircoins_swift/aircoin_balance_swift_dark.png`,
  `/tmp/kotlin_ui_proof/aircoins_swift_history/aircoins_swift/aircoin_history_swift_light.png`,
  `/tmp/kotlin_ui_proof/aircoins_swift_history/aircoins_swift/aircoin_history_swift_dark.png`.
- **PaymentPackageDetails Swift/Figma slice:** Payment Package Details was
  compared against Swift `FigmaPaymentPackageDetailsViewController.swift` and
  Figma node `40001761:29389` for the View History timeline. Swift takes
  precedence for the detail screen and runtime timeline behavior. Android now
  keeps the Swift fixed 96dp footer with 50dp `View History` outline button,
  keeps the already-correct full `Invoice Amount (Declared Value/Cost)` label
  and 48dp CIF pill, removes the incorrect Status `title2` override, uses
  Swift ungrouped `USD %.2f / JMD %.2f` formatting on this screen, and fixes
  timeline `Pick Up` copy, `-` missing-date fallback, body3 dates, 74dp rows,
  status-colored connectors, and status-tinted icons. Proof:
  `/tmp/kotlin_ui_proof/payment_package_details/payment_package_details_swift_light.png`,
  `/tmp/kotlin_ui_proof/payment_package_details/payment_package_details_swift_dark.png`,
  `/tmp/kotlin_ui_proof/payment_package_details/payment_package_history_swift_light.png`,
  `/tmp/kotlin_ui_proof/payment_package_details/payment_package_history_swift_dark.png`.
- **InvoiceViewer Swift/Figma slice:** Figma MCP has no dedicated reachable
  InvoiceViewer frame in the app canvas; the full page metadata timed out and
  Swift explicitly says this viewer follows the document-download shell. Figma
  MCP was still checked through the related Package Details invoice-entry node
  `40001753:15716`; Swift `FigmaInvoiceViewerScreenViewController.swift` takes
  precedence for the viewer. Android now keeps the Swift gray100 page / gray150
  preview panel, prepares a local action file before enabling actions, and
  shares a `content://` FileProvider stream instead of a raw text URL. Proof:
  `/tmp/kotlin_ui_proof/invoice_viewer/invoice_viewer_swift_light.png`,
  `/tmp/kotlin_ui_proof/invoice_viewer/invoice_viewer_swift_dark.png`.
- **PackagesFilterSheet Swift/Figma slice:** Figma MCP design context for node
  `40006358:75618` and Swift
  `FigmaPackagesFilterViewController.swift` were compared, with Swift taking
  precedence where the Figma header/glass frame differs from the shipped iOS
  implementation. Android now uses the Swift filter label/copy (`AirDrop`,
  `Shipment Method`), no stale `All Packages` row, opaque adaptive header,
  20dp content insets, 16dp card gap, 54dp collapsible bars, 50dp option rows,
  24dp icons, iconShape borders/dividers, and preserved tap-to-clear/apply
  callbacks. Proof:
  `/tmp/kotlin_ui_proof/packages_filter_sheet/packages_filter_swift_light.png`,
  `/tmp/kotlin_ui_proof/packages_filter_sheet/packages_filter_swift_dark.png`.
- **Payments/Orders Swift/Figma follow-up:** Payments node `40001753:18909`
  and Orders node `40001753:19595` were refreshed through Figma MCP, then
  compared against Swift
  `FigmaPaymentsViewController.swift` /
  `FigmaOrdersViewController.swift`. Swift takes precedence where Figma lacks
  the runtime invoice download glyph and uses static search copy/sample data.
  Android now keeps Payments' top-right invoice download, orange
  pull-to-refresh, filter accessory, and Swift `Download failed` invoice alert,
  suppresses list-load failure modals to the empty state, and adds the Orders
  trailing more-square accessory as a decorative visual element. Proof:
  `/tmp/kotlin_ui_proof/payments_orders_swift/figma/figma_payments_40001753_18909.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/figma/figma_orders_40001753_19595.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/payments_orders/payments_swift_light.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/payments_orders/payments_swift_dark.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/payments_orders/orders_swift_light.png`,
  `/tmp/kotlin_ui_proof/payments_orders_swift/payments_orders/orders_swift_dark.png`.
- **Shipments section-card divider follow-up:** Swift
  `FigmaOrderDetailsViewController.swift`,
  `FigmaProductPaymentDetailsViewController.swift`, and
  `FigmaPaymentPackageDetailsViewController.swift` all place a 1pt `gray300`
  divider immediately under the gray200 section header bar. Figma Order Details
  node `40001761:28814` confirms the same visual. Android had already removed
  the stale doubled header border, but it still lacked the Swift divider.
  `ShipmentsSectionCardParityTest` now locks a full-width 1dp divider in app
  light and dark. Proof:
  `/tmp/kotlin_ui_proof/shipments_section_cards/figma/figma_order_details_40001761_28814.png`,
  `/tmp/kotlin_ui_proof/shipments_section_cards/android/shipments_section_cards/shipments_section_card_swift_light.png`,
  `/tmp/kotlin_ui_proof/shipments_section_cards/android/shipments_section_cards/shipments_section_card_swift_dark.png`.
- **Home bottom-tab navigation state:** The earlier observation that Home could
  appear selected while More/FAQ content remained visible was rechecked against
  Swift `FigmaBottomTabBar` + `FigmaRouteResolver.switchToTabRoute` first, then
  Figma Home node `40001464:28899`. Swift takes precedence: tab taps root-swap
  the controller with `setViewControllers([destination])`. The visible
  Home-selected/More-visible state did not reproduce on current `origin/main`,
  but the hidden back stack still retained the previous tab instead of matching
  Swift's root replacement. Android now clears the stack on tab switches and
  ignores already-selected tab taps like Swift's `guard route != activeRoute`.
  `AppRootNavigationParityTest` verifies real `AppRoot` More -> Home clears the
  More/FAQ row, `switchTab` reaches Home with no previous tab left in the test
  back stack, and More -> FAQ hides the bottom tab bar. Proof:
  `/tmp/kotlin_ui_proof/home_tab_navigation/figma/figma_home_40001464_28899.png`,
  `/tmp/kotlin_ui_proof/home_tab_navigation/android/home_tab_navigation/app_root_more_before_home_tab.png`,
  `/tmp/kotlin_ui_proof/home_tab_navigation/android/home_tab_navigation/app_root_home_after_more_tab.png`,
  `/tmp/kotlin_ui_proof/home_tab_navigation/android/home_tab_navigation/harness_home_after_more_start.png`.
- **Home header/footer chrome opacity:** Figma Home node `40001464:28899` still
  shows translucent Home chrome, but Swift `FigmaTabHeader` and
  `FigmaBottomTabBar` both render blur underneath an opaque `gray200` overlay.
  Swift takes precedence. Android already matched Swift, so no production
  chrome code changed; `HomeChromeOpacityParityTest` now locks header and
  bottom tab/footer opacity with high-contrast underlay pixel samples in app
  light and app dark. Proof:
  `/tmp/kotlin_ui_proof/home_chrome_opacity/figma/figma_home_40001464_28899.png`,
  `/tmp/kotlin_ui_proof/home_chrome_opacity/android/home_chrome_opacity/home_chrome_opacity_swift_light.png`,
  `/tmp/kotlin_ui_proof/home_chrome_opacity/android/home_chrome_opacity/home_chrome_opacity_swift_dark.png`.
- **Home primary route callbacks:** Swift `FigmaHomeViewController` maps
  Services, Ship Tax, Calculator, Drop Alert, See More, Refer a friend, bell,
  and cart to their corresponding route destinations. Android already emitted
  the Swift-equivalent routes, and `HomeActivityTilesScreenshotTest` now locks
  those callbacks alongside the existing warehouse route/AppRoot proof. This
  does not close the broader Home live-data audit.
- **Home auction card/cart behavior:** Swift `makeAuctionCard` opens product
  details from the card and toggles `FigmaCartStore` only from the plus button;
  Figma Home node `40001464:28899` supplies the visual plus-button placement.
  Android now exposes the existing plus hit target to instrumentation and locks
  the Swift flow split: plus toggles `CartStore` without navigation, while the
  card opens `auctionProductDetails/{slug}`.
- **Shipments hub tap rails:** Shipments hub node `40000823:9633` was refreshed
  through Figma MCP and compared against Swift
  `FigmaShipmentsViewController.swift`. Swift takes precedence for behavior:
  Track Shipment and Packages summary tiles open Packages, Payments opens
  Payments, Orders opens Orders; each section `View More` opens its list; package
  cards open Package Details; package plus toggles cart without navigation;
  package-payment cards open Payment Package Details; product-payment cards open
  Product Payment Details; order cards open Order Details. Android already used
  those route rails, so this pass exposes the existing production targets and
  locks them with `ShipmentsHubTapRailsParityTest`.
- **Shipments search-field split:** Figma MCP screenshots were refreshed for
  Packages `40001666:42198`, Payments `40001753:18909`, and Orders
  `40001753:19595`. Swift takes precedence over the static Figma search copy and
  the Packages icon-side conflict: `FigmaPackagesViewController.makeSearchCard`
  uses a leading 22pt magnifier, while `FigmaPaymentsViewController` and
  `FigmaOrdersViewController` use a trailing 18pt magnifier. Android now
  parameterizes the shared `ShipmentsSearchField` instead of duplicating it or
  blind-flipping all callers; Packages keeps the leading 22dp variant, and
  Payments/Orders use the trailing 18dp variant with Swift spacing.

**🔲 OPEN — BlueDeer (Shipments detail), priority order:** remaining Shipments follow-ups not explicitly closed below.

**✅ CLOSED — MagentaCastle (More/Legal/Profile/AirCoins/HomeDetails/Shipments slices):** §252/§423/§432/§468/§477 Notification Settings, Documents §216/§225, Documents refresh/reload, Profile avatar/DOB, Preferences §243, Invite Friend §261, Legal/T&C §270, FAQs §486, AirCoins balance/history, GoldPriority tier-name/status-bar, PackageDetails Swift/Figma screen pass, PaymentPackageDetails footer/timeline/payment-copy, ProductPaymentDetails/OrderDetails hero/payment-copy, InvoiceViewer surface/share-file, PackagesFilterSheet Swift/Figma, Payments/Orders header/error follow-up, Shipments section-card divider, Shipments hub tap-rail, and Shipments search-field split slices are closed by Swift-precedence proof above.

**🔲 OPEN — unassigned (AmberOtter first-pass / TopazGlacier audit):** remaining LOW batch §279–§486.

**✅ CLOSED — Home dark icon follow-up:** Services tile gear layer is no longer
dark-on-dark in app dark mode; the duotone activity icons were compared against
Swift first and Figma second, then verified on emulator in light and dark.

**✅ CLOSED — Help Swift layout / dark icon / Email glyph follow-up:** Contact,
WhatsApp, Email, Location, Business Hours, Social Media, and social row icons
now render with visible secondary strokes in app dark mode. Email uses the
Swift/Figma envelope glyph. Android now follows Swift over Figma for the whole
Help layout conflict: no Live Chat row in the list, separate Contact/WhatsApp/
Email cards, 20dp card gaps, 15dp card padding, no Business Hours copy button,
and Swift compact Business Hours copy.

**✅ CLOSED — Home activity/highlight geometry follow-up:** Activity tile and
Auction Highlights card sizes were measured against Swift first and Figma
second. Android already matched, so only regression tags/assertions were added.

**✅ CLOSED — Documents card/action-row geometry follow-up:** Swift's newer
`FigmaDocumentsViewController.swift` wins over the older Figma edge-to-edge
footer. Android now uses the inset Swift action row and uploaded-file geometry,
with light/dark instrumentation proof.

**✅ CLOSED — Documents info alert label follow-up:** Swift's Documents info
alert uses `Got it`; Android now matches that one alert without changing the
default label for other shared More alerts.

**✅ CLOSED — Documents refresh/reload follow-up:** Swift's
`FigmaDocumentsViewController.swift` reloads documents on every
`viewDidAppear` and attaches an orange refresh control. Android now reloads on
lifecycle resume and pull-to-refresh through the same repository-backed
ViewModel path.

**✅ CLOSED — Legal live CMS heading colors + FAQ gap follow-up:** Swift's
legal controllers recolor parsed heading runs (`font.pointSize > 15`) to
`textDarkTitle` and body runs to `textDescription`, and Swift FAQ uses a
10pt question-to-chevron gap while Terms/Privacy use 5pt. Figma MCP confirmed
the relevant Terms/Privacy/FAQ screen nodes, but Swift remains the conflict
authority. Android has focused instrumentation proof for the span colors, CMS
color stripping, 5dp Legal gap, 10dp FAQ gap, and light/dark legal rendering.

**✅ CLOSED — Preferences select-field follow-up:** Swift's
`FigmaPreferencesViewController.SelectableRow` is the source of truth for the
field shape and no-required-star behavior. Figma node `40000994:19044` still
shows red asterisks, but Swift does not render them, so Android keeps them off.
`PreferencesParityScreenshotTest` verifies light/dark geometry and row clicks.
Proof lives under `/tmp/kotlin_ui_proof/preferences_swift_field/`.

**✅ CLOSED — Profile avatar + DOB follow-up:** Swift's
`FigmaProfileViewController.swift` wins over the older Figma avatar node. Android
now keeps the Swift-sized avatar geometry under instrumentation, tints the edit
glyph orange for dark-mode visibility, and rejects future DOB dates like Swift's
`dobPicker.maximumDate = Date()`.

**✅ CLOSED — Invite Friend contacts icon follow-up:** Swift's
`FigmaInviteFriendViewController` renders ContactNumber with orange signal arcs
and an `iconSelected` handset. Android already removed the solid-orange tint;
this pass also switches the handset to the existing white dark vector under
app-dark `ThemeController` mode and adds pixel-level light/dark icon proof.

(Section numbers are the source-line anchors printed by `grep -nE '^## ' docs/PARITY_BACKLOG.md`.)

---

## [CLOSED] GoldPriority / Customer Tier
`app/src/main/java/com/ga/airdrop/feature/homedetails/GoldPriorityScreen.kt:258` — Tier name has no auto-shrink — 'Platinum Priority' at fixed 28sp Cairo Bold clips on narrow screens; Swift shrinks the font to fit.

**Detail:** Swift nameLabel (FigmaGoldPriorityViewController.swift:477-481) sets adjustsFontSizeToFitWidth = true with minimumScaleFactor 0.7 and low compression resistance, so long tier names ('Platinum Priority', 'Diamond Elite') scale down rather than truncate. Kotlin (GoldPriorityScreen.kt:258-263) renders Text at a fixed 28.sp with maxLines = 1 and no overflow handling — on compact-width devices (~360dp, minus 2×30dp padding, 70dp badge, 12dp gap ≈ 208dp available) the name hard-clips mid-glyph.

**Fix:** Verified closed. Swift `FigmaGoldPriorityViewController.swift` remains the source of truth where Figma differs; Android now measures the tier name into the remaining badge-row width and selects the largest 28sp→20sp size that avoids visual overflow, matching Swift's 0.7 minimum scale behavior. `GoldPriorityParityTest` verifies `Platinum Priority` at 360dp in light and dark, with 64dp badge geometry and 12dp badge/name spacing. Proof: `/tmp/kotlin_ui_proof/gold_priority/android_gold_priority_platinum_swift_light_360.png`, `/tmp/kotlin_ui_proof/gold_priority/android_gold_priority_platinum_swift_dark_360.png`.

---

## [CLOSED] GoldPriority / Customer Tier
`app/src/main/java/com/ga/airdrop/feature/homedetails/GoldPriorityScreen.kt:164` — Status bar icons are not forced light — in light theme the system clock/icons render dark on the 35%-black header overlay (near-invisible on Diamond/Corporate tiers).

**Detail:** Swift overrides preferredStatusBarStyle to .lightContent (FigmaGoldPriorityViewController.swift:34) because the tier gradient plus the black-0.35 overlay behind the status bar is dark on every tier. The Kotlin app only calls enableEdgeToEdge() in MainActivity (MainActivity.kt:16), which picks status-bar icon color from the app theme — in light mode GoldPriorityScreen gets dark icons over the dark translucent header (Diamond top #6B6B6B and Corporate #6C46C5 + 35% black make them unreadable). No screen-level status-bar handling exists anywhere in the module (no isAppearanceLightStatusBars usage).

**Fix:** Verified closed. `GoldPriorityContent` now applies a screen-scoped `WindowCompat` status-bar effect, forces `isAppearanceLightStatusBars = false` while composed, unwraps `ContextWrapper` activity contexts, and restores the previous value on dispose. `GoldPriorityParityTest.goldPriorityForcesSwiftLightStatusBarIcons` sets the activity to light status icons first, verifies the screen flips them to light-content behavior, then removes the composable and verifies restoration.

---

## [CLOSED] Packages filter sheet
`app/src/main/java/com/ga/airdrop/feature/shipments/PackagesFilterSheet.kt:211` — Status rows place the status icon at the trailing edge; Swift places it leading (icon, then label)

**Detail:** StatusRow (PackagesFilterSheet.kt:196-216) lays out Text(weight 1f) then Image — icon on the right. Swift makeOptionRow (FigmaPackagesFilterViewController.swift:498-511) pins the icon at leading (20pt inset) with the title 10pt after it, for both method and status rows. Kotlin's MethodRow already does leading-icon correctly, so the two lists inside the same sheet are visibly inconsistent with each other and with Swift.

**Fix:** Verified closed. Current Android already had the leading status icon; this pass locked that behavior with `PackagesFilterSheetParityTest`, which asserts the 24dp leading status icon and 10dp icon/text gap in light and dark.

---

## [CLOSED] Packages filter sheet
`app/src/main/java/com/ga/airdrop/feature/shipments/PackagesFilterSheet.kt:74` — Method rows use inconsistent fonts: Standard row is title2 but SeaDrop and Express rows are title1 (18sp)

**Detail:** PackagesFilterSheet.kt passes labelStyle = AirdropType.title2 for Standard (line 73) but AirdropType.title1 for SeaDrop (line 81) and Express (line 88), so adjacent rows render at 16sp vs 18sp. Swift renders all method rows with Typography.title2() (FigmaPackagesFilterViewController.swift:494, bold=true path).

**Fix:** Verified closed. Android now hardens all method rows to Swift's `title2`
style in this sheet, renders the Swift/Figma `AirDrop` filter label instead of
the package-detail `AirDrop Standard` label, and verifies the absence of stale
`All Packages` / `AirDrop Standard` rows plus row-click callbacks in
`PackagesFilterSheetParityTest`.

---

## [CLOSED] Package details
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:130` — Details rounded sheet uses gray150 (screen gray150) but Swift's body card is gray100 on a gray200 screen

**Detail:** PackageDetailsScreen.kt:103 sets the screen background to colors.gray150 and line 130 paints the rounded content sheet gray150. Swift truth: view background gray200 (FigmaPackageDetailsViewController.swift:72) and the rounded body card is gray100 (line 110). Because the inner section cards are gray100 with iconShape borders in both apps, on Android they melt into an equally-light sheet in light mode and the tonal hierarchy (gray200 page → gray100 card) is lost; also flips wrong in dark mode.

**Fix:** Verified closed. Android now keeps the page on `gray200` and the rounded body sheet on `gray100`, matching Swift `FigmaPackageDetailsViewController.swift`. `PackageDetailsParityTest` covers the sheet in light/dark, and proof lives under `/tmp/kotlin_ui_proof/package_details_swift/package_details/`.

---

## [CLOSED] Package details (Shipment Timeline)
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:286` — Timeline renders metro-style status icons with connector lines; the shipped Swift design renders 10pt status-tinted bullet dots with comment and raw date rows

**Detail:** Kotlin builds MetroStep rows (icon glyph per status + 1dp connector, date formatted "12th Jan, 2024, 3:14pm" in textPlaceholder, comment merged into the same body2 Text — PackageDetailsScreen.kt:286-315, ShipmentsUi.kt:724-768). Swift's shipped screen (FigmaPackageDetailsViewController.swift:441-487, applyDetail:971-990) renders a 10x10 rounded bullet filled with statusColor, the status name in subtitle1 tinted the same color, then optional comment and date as separate body3 textDescription lines. The two renderings look completely different at a glance.

**Fix:** Verified closed. Swift wins over Figma here: Figma node `40001753:15716` still shows the older metro/icon timeline, while Swift renders 10dp bullets, status-tinted subtitle1 labels, optional comment/date body3 rows, and no connector. Android now matches Swift and omits Figma's static `N/A` fallback for missing timeline dates. `PackageDetailsParityTest` covers this in light/dark.

---

## [CLOSED] Package details (Summary/Timeline/Charges cards)
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:263` — Section cards get a gray200 banded header and per-row dividers with subtitle1 values; Swift renders inline titles inside plain gray100 cards, no dividers, title2 values

**Detail:** PackageDetailsContent uses ShipmentsSectionCard (ShipmentsUi.kt:413-451), which draws a full-width gray200 header band with its own border, and ShipmentsListRow (ShipmentsUi.kt:455-482) adds a 1dp divider under every row and styles values as subtitle1 (semibold). Swift's Summary panel (FigmaPackageDetailsViewController.swift:321-398) puts the 'Summary ▾' title INSIDE the gray100 card (16pt padding, small 14pt chevron), rows spaced 10pt with no dividers, and values in Typography.title2() (bold). Same inline-title pattern for 'Shipment Timeline' (402-438) and 'Breakdown of Charges' (748-780). The banded style belongs only to the filter sheet's collapsible cards (FigmaPackagesFilterViewController.swift:404-407).

**Fix:** Verified closed. Swift wins over Figma's older banded header/card screenshot. Android now uses plain gray100 cards with inline title2 titles, Summary's 14dp chevron, no row dividers, title2 values, Swift card spacing, and test tags locked by `PackageDetailsParityTest`.

---

## [CLOSED] Package details (charges/total footer)
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:376` — Footer omits the 'Exchange Rate' row, wraps Total in an orange box instead of a plain row, and hides the charges panel entirely when there are no itemized charges

**Detail:** Swift (FigmaPackageDetailsViewController.swift:834-890, 1004-1035) shows, when status >= 7: the Breakdown panel (always, with header row + bold Subtotal even if additionalCharges is empty), then an 'Exchange Rate' key/value row ('1 USD = 161.00 JMD', title2 dark) and a 'Total' row (title2 key dark / value orange, right-aligned) followed by the CTA — no orange background box. Kotlin (PackageDetailsScreen.kt:376-381) skips ChargesCard when charges are empty and total is 0 (losing the exchange-rate note that only lives inside ChargesCard at line 577), and renders Total via TotalChargesBox (orange OrangeTertiary6 pill, ShipmentsUi.kt:773-794) which is the Payment-details Figma component (40001464:31296), not this screen's design.

**Fix:** Verified closed. When ready for pickup, Android always renders the Breakdown card, removes the duplicate exchange-rate note inside the card, shows one horizontal `Exchange Rate` row, and renders a plain Total row with the orange value. Empty charges now show Swift's `USD 0.00 / JMD 0.00` instead of `-`. `PackageDetailsParityTest` covers zero-charge totals and single exchange-rate rendering.

---

## [CLOSED] Payments
`app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt:642` — Invoice download button sits bottom-right beside Amount; Swift places it in the card's top-right corner, smaller and gray

**Detail:** Swift (FigmaPaymentsViewController.swift:328-355): download button pinned top-right (trailing -16, top 14, 28pt hit area, 22pt DownloadFile glyph tinted textDescription), with the key/value rows inset 52pt on the right to clear it. Kotlin renders the icon in the Amount row at the card bottom (ShipmentsUi.kt:634-651), 24dp tinted iconSelected.

**Fix:** Verified closed. `PaymentCard` now renders the invoice download as a top-right overlay with a 22dp glyph tinted `colors.textDescription`, matching Swift's runtime implementation even though the static Figma Payments node does not show the glyph. `PaymentsOrdersParityTest` asserts the control in light/dark and preserves the invoice viewer rail.

---

## [CLOSED] Payments / Orders
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentsScreen.kt:88` — No pull-to-refresh on either list; Swift attaches a UIRefreshControl (orange tint) that resets to page 1

**Detail:** FigmaPaymentsViewController.swift:72-74 and FigmaOrdersViewController.swift:72-74 wire refreshControl → loadPayments/loadOrders(reset: true). Kotlin PaymentsScreen/OrdersScreen expose viewModel.refresh() but nothing in the UI triggers it — swipe-down does nothing.

**Fix:** Verified closed. Both lists are wrapped in `PullToRefreshBox` with `BrandPalette.OrangeMain` indicators wired to `viewModel.refresh()`, preserving the existing pagination/search rails. The same follow-up proof now verifies the Payments filter header accessory and Orders trailing accessory in light/dark.

---

## [CLOSED] PaymentPackageDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:240` — 'View History' button scrolls with the content; Swift pins it in a fixed 96pt footer with a top divider

**Detail:** FigmaPaymentPackageDetailsViewController.swift:262-300 builds a fixed footer (gray100 bg, 1px gray300 divider on top, 50pt outline button inset 20pt, scrollView bottom anchored to footer top). Kotlin places OutlineButton inside the vertical scroll after TotalChargesBox (PaymentPackageDetailsScreen.kt:240), so on long content the primary action is off-screen.

**Fix:** Done. The button now lives in a bottom-aligned 96dp `PaymentPackageDetailsFooter` with a 1dp gray300 divider and 50dp outline button, while scroll content keeps footer clearance. `PaymentPackageDetailsParityTest` verifies the footer pin/height in light and dark.

---

## [CLOSED] PaymentPackageDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:144` — Row label 'Invoice Amount' truncates Swift's 'Invoice Amount (Declared Value/Cost)'

**Detail:** FigmaPaymentPackageDetailsViewController.swift:794 labels the row "Invoice Amount (Declared Value/Cost)" (mirrors RN SummaryDetailsPaymentPackages). Kotlin shows just "Invoice Amount" (PaymentPackageDetailsScreen.kt:143-150).

**Fix:** Verified closed. Android already had the full Swift label in the current tree; this pass added light/dark PaymentPackageDetails proof so it stays locked.

---

## [CLOSED] InvoiceViewer
`app/src/main/java/com/ga/airdrop/feature/shipments/InvoiceViewerScreen.kt:98` — Page/content surface colors are inverted versus Swift (page should be gray100, preview panel gray150)

**Detail:** Swift: view.backgroundColor = gray100 (FigmaInvoiceViewerScreenViewController.swift:68) and contentContainer.backgroundColor = gray150 with iconShape border (lines 86-89). Kotlin: Column background colors.gray150 (line 98) and inner Box background colors.gray100 (line 108) — swapped in both light and dark modes.

**Fix:** Verified closed in the current tree and locked by `InvoiceViewerParityTest`: root Column is `colors.gray100`, preview Box is `colors.gray150` with iconShape border, 20dp side insets, 15dp radius, and 52dp action buttons. Light/dark proof is saved under `/tmp/kotlin_ui_proof/invoice_viewer/`.

---

## [CLOSED] InvoiceViewer
`app/src/main/java/com/ga/airdrop/feature/shipments/InvoiceViewerScreen.kt:346` — 'Share' sends the invoice URL as plain text; Swift shares the actual downloaded file

**Detail:** Swift downloads the invoice to cache and shares the file itself via UIActivityViewController (FigmaInvoiceViewerScreenViewController.swift:383-391), letting the user AirDrop/save the PDF. Kotlin shareInvoice() (InvoiceViewerScreen.kt:346-355) fires ACTION_SEND type text/plain with the raw URL — recipients get a link (possibly an auth-protected or file:/ URL that they cannot open), not the document.

**Fix:** Done. `InvoiceViewerScreen` now prepares a local action file in cache for remote/content URLs and uses local files directly for `file://` URLs. The Share button stays disabled until that file exists, then `invoiceShareIntent` sends `ACTION_SEND` with `EXTRA_STREAM`, MIME type (`application/pdf`, `image/*`, or octet-stream fallback), `FLAG_GRANT_READ_URI_PERMISSION`, and `ClipData`; it no longer sends `EXTRA_TEXT` with the raw URL. `InvoiceViewerParityTest` verifies the stream intent, local-file path, MIME mapping, and light/dark rendered geometry.

---

## [CLOSED] PaymentPackageDetails › View History timeline
`app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt:755` — Timeline connector is always the gray divider color; Swift colors the connector with the step's status color, and the missing-date fallback/date font also drift

**Detail:** Swift timeline row (FigmaPaymentPackageDetailsViewController.swift:1164-1166) sets line.backgroundColor = color — the same green/orange/placeholder color as the row title — so completed segments read as a colored progress spine. Kotlin MetroStep hardcodes colors.divider for the connector (ShipmentsUi.kt:750-757). Additionally Swift shows "-" for stops without a history date (line 1232-1238) while Kotlin shows "N/A" (PaymentPackageDetailsScreen.kt:318), and Swift renders the date in body3 (12pt, line 1178) while MetroStep uses body2 (14pt, ShipmentsUi.kt:765).

**Fix:** Done. `MetroStep` now uses the step color for connector and icon tint, keeps a 74dp minimum row height, and renders dates in body3. `PaymentShipmentTimeline` now passes `-` for missing dates. Light/dark proof screenshots match Figma node `40001761:29389` where it applies to the timeline; Swift is the runtime source of truth.

---

## [CLOSED] OrderDetails / ProductPaymentDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/OrderDetailsScreen.kt:62` — Hero product image is a fixed 245x149dp box; Swift renders a full-width image in a 209pt (OrderDetails, 20pt insets) / 219pt (ProductPaymentDetails, 30pt insets) panel

**Detail:** Swift OrderDetails: imageWrap height 209 with 20pt insets on all sides → image ~169pt tall spanning screen width minus 40 (FigmaOrderDetailsViewController.swift:103-109). Swift ProductPaymentDetails: wrap height 219 with 30pt insets → ~159pt tall, width minus 60 (FigmaProductPaymentDetailsViewController.swift:118-124). Kotlin uses Modifier.size(245.dp, 149.dp) inside a Spacing.lg(30dp)-padded Box on both screens (OrderDetailsScreen.kt:61-82, ProductPaymentDetailsScreen.kt:64-85) — narrower and shorter than iOS on typical devices, and the padding is wrong (20 vs 30) on OrderDetails.

**Fix:** Done. OrderDetails now uses a 209dp wrap with 20dp insets and a fill-width 169dp image. ProductPaymentDetails now uses a 219dp wrap with 30dp insets and a fill-width 159dp image. `ProductOrderDetailsParityTest` verifies the Swift dimensions in light and dark, and screenshots are saved under `/tmp/kotlin_ui_proof/product_order_details/`. The Figma MCP nodes for this slice still show the old fixed 245x149 image geometry, so Swift is documented as the precedence source.

---

## [CLOSED] PaymentPackageDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:176` — CIF pill is 59dp tall with a 24dp info icon; Swift is 48pt with a 20pt icon

**Detail:** FigmaPaymentPackageDetailsViewController.swift:479 sets the pill height to 48 and the infoCircle to 20pt tinted textDarkTitle (lines 487, 500-501). Kotlin uses .height(59.dp) and a 24dp ic_info tinted colors.iconSelected (PaymentPackageDetailsScreen.kt:173-192).

**Fix:** Verified closed. Android already had the Swift 48dp/20dp/textDarkTitle implementation in the current tree; this pass added light/dark PaymentPackageDetails proof around that row.

---

## [MEDIUM] Shop root + Auction/Feature Products lists
`app/src/main/java/com/ga/airdrop/feature/shop/ShopComponents.kt:299` — ShopProductCard uses one geometry everywhere, but Swift differs per context: Shop-root cards have a 1-line title with 15pt insets/10pt spacing, list cells have 2-line titles with 12pt side insets/10pt top/6pt spacing, and all cards are fixed at 245pt tall.

**Detail:** Kotlin: minLines = 2 / maxLines = 2 (ShopComponents.kt:299-300), text column padding 15dp all around, internal spacing 10dp, intrinsic height. Swift Shop root: title.numberOfLines = 1 (FigmaShopViewController.swift:555), textColumn top 15 / sides 15 / spacing 10 (:615-617, :592), card height 245 (:510). Swift grid cells: titleLabel.numberOfLines = 2 (FigmaAuctionViewController.swift:572), textColumn top 10 / sides 12 / spacing 6 (:613-616, :591), cell height 245 (:410). Net effect: Shop-root cards reserve an extra title line vs iOS, and full-list cells have looser text insets than iOS.

**Fix:** Parameterize ShopProductCard: titleLines: Int (1 for ShopScreen usages, 2 for ProductListScreen/related), textPadding + textSpacing values per context (15/10 for shop root, 12x10/6 for grid cells), and give the card a fixed 245.dp height to match Swift.

---

## [MEDIUM] Shop root
`app/src/main/java/com/ga/airdrop/feature/shop/ShopScreen.kt:74` — Shop root content starts at 146dp from the top (126dp spacer + 20dp column padding) instead of Swift's 126pt.

**Detail:** Swift pins contentStack.topAnchor at constant 126 from the scroll content top with 20pt side insets only (FigmaShopViewController.swift:109-113). Kotlin stacks Spacer(126.dp) THEN a Column with .padding(Spacing.md) on all four sides, adding an extra 20dp above the search field. (The bottom currently works out to 140dp = Swift's 120 tail + 20 inset, so only the top drifts.)

**Fix:** Change ShopScreen.kt:74 to .padding(horizontal = Spacing.md) and bump the tail Spacer (line 151) from 120.dp to 140.dp to preserve the current (correct) bottom clearance.

---

## [MEDIUM] Auction checkout
`app/src/main/java/com/ga/airdrop/feature/shop/ShopComponents.kt:474` — The Payment Currency field diverges from Swift's makeField spec in label typography, asterisk color, container color/radius/height, value font and chevron color.

**Detail:** ShopDropdownField vs FigmaAuctionProductCheckoutViewController.makeField (:542-618): label subtitle1 vs subtitle2 (:553); required '*' AlertPalette.Error red vs orangeMain (:556-559); container gray150 / Radius.xs(10dp) / minHeight 50 vs gray100 / radius 12 / height 48 (:571-577); value text body2 vs body1 (:564); chevron tinted iconSelected (near-black light mode) vs gray500 (:585-587).

**Fix:** In ShopDropdownField: label style AirdropType.subtitle2; asterisk color BrandPalette.OrangeMain; container background colors.gray100, RoundedCornerShape(12.dp), height 48.dp; value AirdropType.body1; chevron tint colors.gray500 (or the equivalent token).

---

## [MEDIUM] Auction Product Details
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:257` — Hero image has no placeholder or error fallback — blank gray card when the product has no image or the load fails

**Detail:** Swift buildHeroImage (FigmaAuctionProductDetailsViewController.swift:276-294) shows a 96pt gray400 airplane glyph placeholder whenever displayImageURL is nil, and un-hides it if sd_setImage returns a nil image. Kotlin renders a bare AsyncImage with no fallback, so null/blank imageUrl or a failed fetch leaves an empty gray150 box.

**Fix:** Mirror ShopProductCard's pattern: when imageUrl.isNullOrBlank() or onError fires, show the placeholder glyph (airplane/logo drawable, ~96dp, tinted colors.gray400) centered in the card.

---

## [MEDIUM] Feature Product Details
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:157` — 'Purchase Product' silently does nothing when amazonUrl is blank; Swift shows a 'Product link unavailable' alert

**Detail:** Swift onPurchaseProduct (FigmaAuctionProductDetailsViewController.swift:817-831) presents an alert 'Product link unavailable' / 'No purchase link was returned for this feature product.' when the URL is empty, and a second alert when it's not a valid URL. Kotlin's CTA click checks `raw.isNotEmpty()` and simply no-ops otherwise — a dead-feeling button for featured products missing amazon_url.

**Fix:** When amazonUrl is blank/invalid, surface the same AlertDialog used for addedDialog with title 'Product link unavailable' and Swift's message text instead of doing nothing.

---

## [MEDIUM] Auction Checkout
`app/src/main/java/com/ga/airdrop/feature/shop/ShopComponents.kt:474` — ShopDropdownField (Payment Currency) diverges from Swift makeField: gray150 fill blends into the checkout background, label/value/radius/star tokens all drift

**Detail:** Swift makeField (FigmaAuctionProductCheckoutViewController.swift:542-618, identical in FigmaCartViewController:1004-1080): label subtitle2 textDarkTitle with an ORANGE ' *' (orangeMain) when required; field card gray100, radius 12, height 48, value font body1, chevron 20pt gray500, label→card gap 6. Kotlin ShopDropdownField: label subtitle1, red AlertPalette.Error asterisk, gray150 fill (invisible against the gray150 Auction Checkout background — only the border shows), radius 10 (Radius.xs), minHeight 50, value body2, chevron 24dp iconSelected. TypeInputField (cart billing form) shares the same gray150/red-star/subtitle1 drift.

**Fix:** In ShopDropdownField and TypeInputField: label → AirdropType.subtitle2; required star → BrandPalette.OrangeMain; box background → colors.gray100 with RoundedCornerShape(12.dp) and 48.dp height; value text → AirdropType.body1; chevron 20.dp tinted colors.gray500.

---

## [CLOSED] Documents
`app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt:221` — The Download|Upload action bar is a flush edge-to-edge card footer with a full-height divider; Swift renders it as an inset, bordered, radius-10, 48pt split row inside the card padding.

**Detail:** FigmaDocumentsViewController.makeActionsRow (FigmaDocumentsViewController.swift:422-473): the actions row is its own view with cornerRadius 10, 1pt iconShape border, height 48, added INSIDE the card's 16pt-padded content stack; the vertical divider is inset 8pt top/bottom. Kotlin instead draws a full-width divider (colors.divider) across the card and a 54dp Row spanning the card edge-to-edge with a full-height 1dp separator (lines 220-248), which visibly changes the card design.

**Fix:** Done. The split actions Row now lives inside the padded content Column, is 48dp high, has a radius-10 iconShape border, uses a 32dp center divider (8dp inset top/bottom), and no longer has an edge-to-edge top divider.

---

## [CLOSED] Documents
`app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt:173` — Card and uploaded-file-row geometry drifts from Swift: card radius 10 vs 15, card gap 20dp vs 12pt, file row has an extra border and oversized trash/eye icons (24dp spaced 30dp vs 18pt spaced ~6pt).

**Detail:** Swift: card cornerRadius 15 (FigmaDocumentsViewController.swift:252), listStack.spacing 12 (line 131); file row is peachLight, radius 8, height 56, NO border, pdf icon 28pt, name = body2, size line = body3 textDescription, trash/eye are 18pt glyphs in 28pt buttons 6pt apart (lines 330-419). Kotlin: card RoundedCornerShape(Radius.xs=10), verticalArrangement spacedBy(Spacing.md=20) (line 106), file row has 1dp iconShape border + radius 10, name = subtitle2, size line = textPlaceholder, trash/eye 24dp spaced Spacing.lg=30dp.

**Fix:** Done. Card now uses RoundedCornerShape(Radius.s=15), list spacing 12.dp, and Swift content inset. UploadedFileRow drops the border, uses radius 8.dp and height 56.dp, renders the filename as AirdropType.body2, metadata as body3/textDescription, and uses 18.dp trash/eye glyphs in 28.dp hit boxes with a 6.dp gap.

---

## [CLOSED] Edit Profile
`app/src/main/java/com/ga/airdrop/feature/more/ProfileScreen.kt:289` — Avatar geometry drifts from Swift: 107dp circle with gray200 fill and iconShape ring + 28dp badge, vs Swift's 80pt gray300 circle (no ring) with a 24pt edit badge tucked at bottom-right +2pt.

**Detail:** FigmaProfileViewController.makeAvatar (FigmaProfileViewController.swift:295-365): circle width/height 80, cornerRadius 40, backgroundColor gray300, no border; edit badge constrained to 24x24 at trailing/bottom +2; wrap height 88; placeholder glyph 44pt tinted gray500. Kotlin renders a 107dp circle, gray200 fill, 1dp iconShape border, and a 28dp badge offset (79,79).

**Fix:** Done. `ProfileAvatar` uses an 88.dp wrap, 80.dp gray300 circle, 44.dp placeholder glyph, and a 24.dp edit badge with 2.dp trailing/bottom overshoot. Swift's orange edit glyph is now applied so the badge remains visible in dark mode. `ProfileParityScreenshotTest` verifies the geometry in light and dark.

---

## [CLOSED] Preferences
`app/src/main/java/com/ga/airdrop/feature/more/MoreComponents.kt:248` — MoreSelectField styling drifts from Swift's SelectableRow: label 16sp vs 14pt, box gray150 vs gray100, radius 10 vs 12, min-height 50 vs 52, chevron 24dp vs 12pt.

**Detail:** Swift SelectableRow (FigmaPreferencesViewController.swift): titleLabel.font = Typography.subtitle2() (14pt SemiBold, line 478); editable card backgroundColor = gray100 with cornerRadius 12 (lines 483-484); disabled card backgroundColor = gray300 and value text uses textPlaceholder (lines 535-537); card.heightAnchor >= 52 (line 512); trailing chevron is a 12x12 FigmaIcon.chevronDown tinted textDarkTitle (lines 520-521, 542-544). Current Kotlin MoreSelectField already used AirdropType.subtitle2, colors.gray100, RoundedCornerShape(12.dp), a 12.dp chevron tinted colors.textDarkTitle, and colors.textPlaceholder for disabled values, but rendered proof showed the `defaultMinSize(52.dp)` row expanded to ~54.5dp from Compose text/padding. Figma Preferences node `40000994:19044` shows required asterisks on each label, but Swift's titleStack only adds titleLabel and no asterisk label; Swift takes precedence.

**Fix:** Done. The stale backlog text described an older Kotlin implementation for most values. The real rendered drift was field height, so `MoreSelectField` now uses an exact 52.dp card height and exposes optional test tags for proof. `PreferencesParityScreenshotTest` verifies 335dp field width, 52dp field height, 12dp chevrons, no email chevron, no required asterisks by Swift precedence, and selectable-row click dispatch in light and dark.

---

## [CLOSED] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsScreen.kt:80` — Row icons use wrong glyphs/duotone color assignments versus Swift: master bell should be solid iconSelected, Email envelope body should be orange with dark flap, Push bell should be solid orange.

**Detail:** Swift (FigmaNotificationSettingsViewController.swift): master row uses FigmaIcon.bell(primary: orangeMain, secondary: iconSelected) and FigmaIcon_Bell paints ALL paths with `secondary` (FigmaIcons.swift lines 634-641), so the master bell renders entirely iconSelected (dark/white) — and it is the plain bell glyph, not a bell-with-sound-waves. Email rows use mail(primary: iconSelected, secondary: orangeMain) — FigmaIcon_Mail paints the flap with primary (dark) and the envelope body with secondary (orange) (FigmaIcons.swift lines 1011-1016). Push rows use bell(primary: iconSelected, secondary: orangeMain) → fully ORANGE bell. Kotlin instead uses: ic_settings_notifications (bell-with-waves, orange accent strokes + duotone body) for the master row; ic_mail whose flap is orange and body duotone (inverted vs Swift); ic_notifications whose every stroke is @color/icon_duotone (no orange at all) for Push. Only the SMS chat icon (orange dots, duotone bubble) matches Swift. Users comparing the two apps see three of the four icon styles wrong in both light and dark mode.

**Fix:** Done. `NotificationSettingsScreen` now uses the plain bell tinted `iconSelected` for the master row, screen-specific mail vectors with `iconSelected` flap + orange body, existing chat light/dark vectors, and the plain bell tinted orange for Push. `NotificationSettingsParityTest` verifies the icon colors in app light and app dark.

---

## [CLOSED] Invite Friend
`app/src/main/java/com/ga/airdrop/feature/more2/InviteFriendScreen.kt:129` — Contacts row icon is tinted solid orange, flattening the duotone glyph; Swift renders orange signal waves with a dark handset.

**Detail:** Earlier Android builds applied `ColorFilter.tint(BrandPalette.OrangeMain)` to `ic_contact_number`, flattening the duotone glyph. Current Android had already removed that tint, so the remaining risk was app-dark mode: `@color/icon_duotone` follows Android resource-night, while this app flips themes through `ThemeController`. Swift uses `FigmaIcon.contactNumber(primary: orangeMain, secondary: iconSelected)` (FigmaInviteFriendViewController.swift lines 364-366), and `FigmaIcon_ContactNumber` paints the waves with primary orange and the handset with secondary iconSelected (FigmaIcons.swift lines 968-973). Figma MCP screenshots for documented nodes `40001940:26797` and `40001940:26885` both render the Refer-a-Friend landing screen, not the Send Invitation form, so Swift is the authoritative form/icon source for this item.

**Fix:** Done. The Contacts row keeps the untinted base vector in light mode and reuses the existing `ic_contacts_contact_number_dark` vector in app-dark mode, preserving orange arcs and switching the handset to Swift `iconSelected` white. `InviteFriendParityScreenshotTest` verifies 59dp row height, 24dp icon size, orange arcs, light dark-handset pixels, dark white-handset pixels, and emits light/dark screenshots.

---

## [CLOSED] Terms & Conditions / Privacy Policy (live CMS content)
`app/src/main/java/com/ga/airdrop/feature/more2/LegalContent.kt:113` — Live CMS legal content renders ALL text — including headings — in textDescription gray; Swift colors headings textDarkTitle and only body textDescription.

**Detail:** Current Android already post-processes the parsed `Spanned` in `colorLegalHeadings`, matching Swift's `applyDynamicColors`: headings detected from `RelativeSizeSpan > 1.0` are recolored to `textDarkTitle`, while body text remains on the TextView's `textDescription` color. Swift source checked: `FigmaTermsConditionsViewController.swift` and `FigmaPrivacyPolicyViewController.swift` color runs with `pointSize > 15` as headings. Figma MCP checked Terms node `40001383:9894` and Privacy node `40001387:9042`; Figma confirms the page/card/text tokens, while Swift is the live CMS behavior authority.

**Fix:** Done and verified. `colorLegalHeadings` is covered by `LegalContentParityTest`: headings receive `ForegroundColorSpan(textDarkTitle)`, body runs do not, inline CMS colors are stripped before theme recoloring, and light/dark screenshots were captured from the real padded live-card path.

---

## [CLOSED] Package details (invoice file rows)
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:508` — Invoice row action icons are ordered eye-then-trash; Swift orders trash-then-eye

**Detail:** Kotlin InvoiceFileRow renders ic_eye then ic_trash (PackageDetailsScreen.kt:508-523). Swift stacks [icon, text, spacer, trashBtn, viewBtn] (FigmaPackageDetailsViewController.swift:687), i.e. trash first, eye at the far trailing edge.

**Fix:** Verified closed. Android now renders the invoice row at Swift's 56dp height with the trailing actions ordered trash then eye, and keeps the existing view/delete callback rails. `PackageDetailsParityTest.invoiceAndCartButtonsKeepSwiftRuntimeRails` verifies invoice-view navigation, delete confirmation/repository call, and Add-to-Cart insertion.

---

## [CLOSED] Orders
`app/src/main/java/com/ga/airdrop/feature/shipments/OrdersScreen.kt:90` — Orders header is missing the trailing rounded-square ellipsis accessory that Swift renders for Figma parity

**Detail:** FigmaOrdersViewController.swift:154-181 draws the same more-square icon as Payments in the header trailing slot (decorative, no action — comment: 'Figma renders the same trailing accessory for visual parity'). Kotlin OrdersScreen calls ShipmentsDetailHeader without rightIconRes, leaving an empty spacer.

**Fix:** Verified closed. `OrdersScreen` passes the Swift/Figma more-square glyph into `ShipmentsDetailHeader` as a non-clicking decorative accessory, matching Swift's visual parity comment without adding a fake action. `PaymentsOrdersParityTest` asserts the accessory in light and dark.

---

## [CLOSED] ProductPaymentDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/ProductPaymentDetailsScreen.kt:96` — Section titles 'Product Summary'/'Payment Summary' overridden to subtitle1; Swift uses title2 (Bold)

**Detail:** FigmaProductPaymentDetailsViewController.swift:288 and :370 set barTitle.font = Typography.title2(). Kotlin passes titleStyle = AirdropType.subtitle1 to both ShipmentsSectionCard calls (lines 96 and 126), diverging from every other section card that keeps the title2 default.

**Fix:** Closed in the current Android tree and preserved in this pass. ProductPaymentDetails now renders both cards with `AirdropType.title2`, matching Swift; the top-state light/dark screenshots in `/tmp/kotlin_ui_proof/product_order_details/` show the bold section headers.

---

## [CLOSED] PaymentPackageDetails › View History timeline
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:265` — Timeline stop 18 labelled 'Paid and Ready for Pickup'; Swift timeline says 'Paid and Ready for Pick Up'

**Detail:** FigmaPaymentShipmentTimelineViewController timelineOrder (FigmaPaymentPackageDetailsViewController.swift:977) uses "Paid and Ready for Pick Up" (with space). Kotlin timelineStops uses "Paid and Ready for Pickup".

**Fix:** Done. Timeline stop 18 now uses Swift's `Paid and Ready for Pick Up`; `PaymentPackageDetailsParityTest` asserts the old `Pickup` string is absent.

---

## [CLOSED] PaymentPackageDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:155` — Package-summary Status value uses title2 (Bold); Swift renders all summary values, including Status, in subtitle1

**Detail:** Swift makeSummaryRow (FigmaPaymentPackageDetailsViewController.swift:671) uses Typography.subtitle1() for every value; the Status row only changes color to Alert.completed (line 795). Kotlin passes valueStyle = AirdropType.title2 for the Status row (PaymentPackageDetailsScreen.kt:151-157).

**Fix:** Done. The Status row now keeps the shared `ShipmentsListRow` subtitle1 value style and only overrides the value color to `AlertPalette.Completed`.

---

## [CLOSED] All section cards in this group
`app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt:432` — Section-card header row draws a full 4-sided border inside the already-bordered card, doubling the outline; Swift has only a 1px divider under the bar

**Detail:** ShipmentsUi.kt:432 applies .border(1.dp, colors.iconShape) to the gray200 header Row, adding visible extra lines at the card's top and sides on top of the outer card border. Swift cards (e.g. FigmaOrderDetailsViewController.swift:310-333) draw a single gray300 divider between bar and body only.

**Fix:** Verified closed. Current Android had already removed the old doubled 4-sided header border, so this pass added the missing Swift 1dp `gray300` divider under the section header bar and locked it with `ShipmentsSectionCardParityTest` in light/dark. Swift is the runtime source; Figma Order Details node `40001761:28814` confirms the same header/body separator.

---

## [CLOSED] PaymentPackageDetails / ProductPaymentDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt:198` — 'Amount Paid'/Total strings use comma-grouped numbers and render zero amounts; Swift prints ungrouped %.2f and shows '-' when the amount is 0

**Detail:** Swift: String(format: "USD %.2f / JMD %.2f") (FigmaPaymentPackageDetailsViewController.swift:812, FigmaProductPaymentDetailsViewController.swift:592) → "JMD 64841.58" without thousands separators, and ProductPaymentDetails guards `usd > 0 else "-"` (lines 587-593, 617-623). Kotlin ShipmentsFormat.usdJmd groups with commas and formats any non-null value including 0.0. (Note OrderDetails is the opposite — Swift explicitly comma-groups there, which Kotlin already matches.)

**Fix:** Done. PaymentPackageDetails uses `ShipmentsFormat.usdJmdPlain` / `moneyPlain`; ProductPaymentDetails now uses `usdJmdPlainPositive` for Amount Paid and Total and `moneyPlain` for Exchange Rate. `PaymentPackageDetailsParityTest` and `ProductOrderDetailsParityTest` assert the ungrouped `USD 100.00 / JMD 16100.00` form, reject the comma-grouped variant on payment-detail screens, and verify the ProductPaymentDetails zero guard returns `-`. OrderDetails intentionally keeps `ShipmentsFormat.usdJmd` because Swift groups JMD there.

---

## [CLOSED] Payments
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentsScreen.kt:142` — List fetch failures pop a modal alert; Swift fails silently to the empty state (alerts only for invoice-download failures)

**Detail:** Kotlin surfaces every repo error (including pagination/search failures) as a ShipmentsAlertDialog titled 'Payments' (PaymentsScreen.kt:142-150 wired to state.error set in PaymentsViewModel.load onFailure). Swift only prints list-fetch errors and shows 'No payments found' (FigmaPaymentsViewController.swift:563-571); the only alert is 'Download failed' for the invoice action (lines 393-400). A flaky connection on Android interrupts the user with modals iOS never shows.

**Fix:** Verified closed. `PaymentsViewModel.load` no longer writes list-load failures into `state.error`; it clears loading flags and lets `No payments found` render. `downloadInvoice` remains the only path that raises the alert, now titled `Download failed` like Swift. `PaymentsOrdersParityTest` covers both failure paths.

---

## [LOW] Shop root — Feature Products row
`app/src/main/java/com/ga/airdrop/feature/shop/ShopScreen.kt:134` — The featured-row empty card is rendered full-width; Swift renders a fixed 240pt-wide card inside the horizontal row.

**Detail:** Swift: row.addArrangedSubview(makeEmptyCard(text: "No featured products", fixedWidth: 240)) (FigmaShopViewController.swift:490). Kotlin ShopEmptyCard fills max width (ShopScreen.kt:181-198).

**Fix:** Pass a width modifier: ShopEmptyCard(text = "No featured products", modifier = Modifier.width(240.dp)) for the featured section (auction section stays full-width, matching Swift's grid empty card).

---

## [CLOSED] Product details — description markdown
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:579` — Description markdown '**bold**' spans were stripped to plain text; Swift renders them as bold Cairo-Bold spans.

**Detail:** Swift's renderProductDescription parses \*\*(.+?)\*\* and applies Cairo-Bold to the inner text (FigmaAuctionProductDetailsViewController.swift:840-891) — this was an explicit user-flagged fix on iOS.

**Fix:** Closed in current Android. `descriptionAnnotated` now splits on the same regex and applies `FontWeight.Bold` to matched inner spans instead of deleting the markers.

---

## [LOW] Auction list / Feature Products list
`app/src/main/java/com/ga/airdrop/feature/shop/ProductListViewModel.kt:64` — Typing 1-2 characters in list search does not trigger a reload, leaving stale filtered results when backspacing from 3 chars to 2.

**Detail:** onQueryChange early-returns when 0 < trimmed.length < 3 (ProductListViewModel.kt:64). Swift list VCs debounce-reload on EVERY change and only omit the search param when <3 chars (FigmaAuctionViewController.swift:458-464 + searchQuery :335-338), so backspacing from 'abc' to 'ab' restores the unfiltered list on iOS but keeps showing 'abc' results on Android. (The Shop root's gating IS correct — Swift gates there: FigmaShopViewController.swift:762.)

**Fix:** Remove the early return in ProductListViewModel.onQueryChange; searchQuery() already drops sub-3-char values from the request.

---

## [LOW] Auction list / Feature Products list
`app/src/main/java/com/ga/airdrop/feature/shop/ProductListScreen.kt:133` — Pull-to-refresh spinner uses default Material3 colors instead of Swift's orangeMain refresh tint.

**Detail:** PullToRefreshBox is used without a custom indicator (ProductListScreen.kt:133-137); Swift sets refreshControl.tintColor = DesignTokens.Color.orangeMain (FigmaAuctionViewController.swift:313, FigmaFeatureProductsViewController.swift:319).

**Fix:** Provide the indicator slot with PullToRefreshDefaults.Indicator(color = BrandPalette.OrangeMain, containerColor = AirdropTheme.colors.gray100).

---

## [CLOSED] Product details — related-products empty-state
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:454` — Related Products section disappeared entirely when the related fetch failed or returned empty; Swift always renders the section (with two placeholder cards) in auction mode.

**Detail:** Kotlin gated on `!featured && related.isNotEmpty()`; Swift unconditionally adds buildRelatedHeader() + buildRelatedRow() for mode == .auction (FigmaAuctionProductDetailsViewController.swift:238-241), rendering 2 skeleton cards (:653-656). Android's use of real shortlist data is an improvement worth keeping, but the section must not vanish.

**Fix:** Closed. Android now renders the Related Products header + two 220dp ShopSkeletonCard placeholders when `related` is empty in auction mode, keeps the real-data path when it loads, keeps featured mode without the section, and verifies `View More` routes to `Routes.AUCTION`.

---

## [CLOSED] Product details (featured mode) — purchase-link unavailable alerts
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:158` — 'Purchase Product' silently does nothing when the Amazon URL is missing; Swift shows a 'Product link unavailable' alert.

**Detail:** Kotlin: `if (raw.isNotEmpty()) { ... }` with no else branch (AuctionProductDetailsScreen.kt:157-165). Swift presents UIAlertController(title: "Product link unavailable", message: "No purchase link was returned for this feature product.") for empty/invalid URLs (FigmaAuctionProductDetailsViewController.swift:817-831). A dead-feeling button is user-visible.

**Fix:** Closed. The empty-link branch was already present in current Android; this pass closes the remaining Swift runtime branch by validating the normalized purchase URL before launching it and surfacing `The purchase link is not a valid URL.` for invalid strings. Figma MCP Product Details node `40002072:24025` was checked for the visual surface, and Swift `onPurchaseProduct` takes precedence for the runtime alert behavior. `AuctionProductDetailsRelatedParityTest.featuredInvalidPurchaseLinkShowsSwiftUnavailableAlert` locks the invalid-link alert copy.

---

## [CLOSED] Auction Product Details — null-description fallback
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:558` — Description null fallback copy differed from Swift

**Detail:** Swift's nil-description fallback is the long `Detailed product description will be loaded…` sentence (FigmaAuctionProductDetailsViewController.swift:574). Android already preserved `**...**` bold spans in the current tree, but still showed `No description available.` for null/blank descriptions.

**Fix:** Closed. `cleanDescription` now returns Swift's full fallback copy for null/blank descriptions while preserving the existing annotated bold-span path. `AuctionProductDetailsRelatedParityTest.nullDescriptionUsesSwiftFallbackCopy` verifies the Swift copy and rejects the old Android-only fallback.

---

## [CLOSED] Auction Checkout — hero placeholder and unauthenticated alert
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionCheckoutScreen.kt:109` — Hero placeholder is the Airdrop logo instead of Swift's gift emoji, no image-error fallback, and the unauthenticated error isn't special-cased

**Detail:** Swift shows an 80pt '🎁' label as the hero placeholder and re-shows it when the image load fails (FigmaAuctionProductCheckoutViewController.swift:267-285). Kotlin shows img_airdrop_logo and has no onError handling on the AsyncImage. Swift also maps APIError.unauthenticated to a 'Sign in required' alert (lines 488-494); Kotlin surfaces every failure as generic 'Checkout failed'.

**Fix:** Closed. Figma MCP `get_design_context` on the documented checkout node `40001846:54756` confirmed that node is actually the Feature Products list + sort sheet, so Swift is the controlling source for this checkout slice. Android now uses an 80sp gift placeholder for null hero images, restores it from `AsyncImage.onError`, and maps 401/unauthenticated checkout failures to Swift's `Sign in required` alert copy. `AuctionCheckoutParityTest` verifies the null placeholder, failed-image fallback, and unauthenticated alert.

---

## [CLOSED] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsViewModel.kt:104` — Enabling a Push toggle never re-registers the FCM device token with the backend, which Swift does on every sync when push is wanted.

**Detail:** Swift syncToBackend (FigmaNotificationSettingsViewController.swift:446-456) calls AirdropAPI.shared.registerFCMToken(deviceToken:deviceType:deviceInfo:) when pushWanted and a stored token exists. The Kotlin app has the plumbing (MiscRepository.registerFcmToken, used by AirdropMessagingService.onNewToken) but NotificationSettingsViewModel.syncToBackend only PUTs the three profile flags; if the token was issued before login (onNewToken skips when unauthenticated), enabling Push here never registers it.

**Fix:** Done. `syncToBackend()` now computes `pushWanted`, requests the current `FirebaseMessaging` token best-effort after the profile update, trims it, and calls `MiscRepository.registerFcmToken(token, "android", deviceInfo)`. `NotificationSettingsParityTest.enablingPushReregistersFcmTokenLikeSwift` verifies the hook with injected fakes.

---

## [CLOSED] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsScreen.kt:187` — Row heights and section gaps drift from Swift: all rows are 59dp with 10dp base spacing and ~30dp section gaps; Swift uses 60pt master/section rows, 56pt sub rows, 12pt base spacing, 20pt section gaps.

**Detail:** FigmaNotificationSettingsViewController.makeRow sets card height = style == .sub ? 56 : 60 (line 282), stack.spacing 12 (line 154), setCustomSpacing(20) after the master row and after packagePush (lines 167, 187). Kotlin ToggleRow is uniformly height(59.dp), Column spacedBy(Spacing.sm=10), and the extra Spacer(Modifier.height(Spacing.sm)) rows produce ~30dp section gaps.

**Fix:** Done. `ToggleRow` is now style-dependent: `60.dp` master/section rows, `56.dp` sub rows, explicit `12.dp` normal gaps, and explicit `20.dp` section breaks. The focused screenshot test asserts the row heights, gaps, and icon leading insets.

---

## [CLOSED] Documents
`app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt:135` — Info dialog confirm button says 'OK'; Swift uses 'Got it' for the document info alert.

**Detail:** FigmaDocumentsViewController.onInfoTapped (FigmaDocumentsViewController.swift:550-555) adds UIAlertAction(title: "Got it"). Kotlin reuses MoreAlertDialog whose confirm label is hard-coded 'OK'.

**Fix:** Done. `MoreAlertDialog` has a `confirmLabel` parameter defaulting to `OK`, and Documents info passes `Got it`. `DocumentsScreenScreenshotTest` verifies the real Documents info click path shows `Got it` and no `OK`.

---

## [CLOSED] Documents
`app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt:103` — No pull-to-refresh and no reload when returning to the screen; Swift attaches a UIRefreshControl and reloads documents on every viewDidAppear.

**Detail:** FigmaDocumentsViewController sets scrollView.refreshControl (lines 124-126) and calls loadDocuments() from viewDidAppear (line 112). Kotlin DocumentsViewModel loads once in init and only reloads after its own upload/delete; documents changed elsewhere (or a failed first load) require leaving and recreating the screen.

**Fix:** Done. `DocumentsScreen` wraps the existing scroll column in `PullToRefreshBox` with the Swift orange indicator and observes lifecycle `ON_RESUME` to call `viewModel.load()`. `DocumentsViewModel` now separates `loading` from `refreshing` and routes both refresh and resume reloads through the same `DocumentsRepository`/`MoreRepository` API path. `DocumentsScreenScreenshotTest` verifies lifecycle resume reload and direct refresh reload counts.

---

## [CLOSED] Edit Profile
`app/src/main/java/com/ga/airdrop/feature/more/ProfileScreen.kt:347` — Date-of-birth picker allows future dates; Swift caps the DOB wheel at today.

**Detail:** FigmaProfileViewController sets dobPicker.maximumDate = Date() (FigmaProfileViewController.swift:233). Kotlin's rememberDatePickerState() has no bound, so users can pick a future birthday.

**Fix:** Done. `DobPickerDialog` creates `rememberDatePickerState(selectableDates = ...)` backed by `isSelectableDobDate`/`isSelectableDobYear`, so today and past dates are allowed while future dates/years are rejected. `ProfileParityScreenshotTest` covers the boundary with a fixed UTC date.

---

## [CLOSED] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsScreen.kt:187` — Row heights and inter-row spacing drift: all rows 59dp with 10dp gaps vs Swift 60pt (master/section) and 56pt (sub) rows with 12pt gaps.

**Detail:** Swift makeRow sets card height 60 for master/section styles and 56 for sub rows (FigmaNotificationSettingsViewController.swift line 282), with stack spacing 12 and custom 20pt spacing after the master row and after the Package Push row (lines 154, 167, 187). Kotlin ToggleRow is a uniform 59.dp (line 187) and the Column uses Arrangement.spacedBy(Spacing.sm = 10.dp) (line 75); the section breaks come out at 20dp via extra Spacers (correct) but every other gap is 10dp instead of 12dp.

**Fix:** Closed by the same `NotificationSettingsScreen` row-style repair and `NotificationSettingsParityTest` geometry assertions above.

---

## [CLOSED] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsViewModel.kt:104` — Push-enable never (re)registers the FCM device token, and the RECONCILE comment claiming 'no FCM stack in the Android app yet' is stale.

**Detail:** Swift syncToBackend registers the FCM token with device info whenever any push toggle is enabled (FigmaNotificationSettingsViewController.swift lines 449-456: registerFCMToken(deviceToken:deviceType:deviceInfo:) guarded by pushWanted). The Android app DOES have an FCM stack now — core/push/AirdropMessagingService.kt calls MiscRepository.registerFcmToken on onNewToken, and MiscRepository.kt:128 exposes the endpoint — but NotificationSettingsViewModel.syncToBackend only PUTs the three profile flags and the class doc still says FCM is absent. If the initial registration failed or the user re-enables push after the server pruned the token, iOS self-heals and Android does not.

**Fix:** Closed by the same `NotificationSettingsViewModel` Push-token repair above; the stale RECONCILE comment was removed.

---

## [CLOSED] FAQs
`app/src/main/java/com/ga/airdrop/feature/more2/LegalContent.kt:146` — FAQ accordion question→chevron gap is 5dp (Spacing.xs) but Swift FAQ uses 10pt (Spacing.sm).

**Detail:** The shared `AccordionCard` defaults to `Spacing.xs` (5dp), which matches Terms (`FigmaTermsConditionsViewController.swift`) and Privacy (`FigmaPrivacyPolicyViewController.swift`). Swift FAQ is different: `FigmaFAQViewController.swift` constrains `questionLabel.trailingAnchor` to the chevron with `-DesignTokens.Spacing.sm` (10pt). Figma FAQ node `40001387:8896` shows the visual accordion card and text tokens, but Swift is the implementation source for the conflicting exact gap.

**Fix:** Done and verified. `AccordionCard` keeps its 5dp default for Terms/Privacy, and `FaqScreen` passes `Spacing.sm`. `LegalContentParityTest` measures the default Legal gap at 5dp and the real `FaqScreen` `faq-1` gap at 10dp.

---
