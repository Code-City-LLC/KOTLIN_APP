# Pixel-Parity Backlog

Source: adversarially-verified full-app audit (117 confirmed findings,
Kotlin vs Swift `Figma*ViewController`s vs Figma file N4k6jzpeLZgeRS5O1xfyIv),
2026-07-04. 63 findings were fixed in commits 48db012 + 9222e1d (+ follow-ups);
the backlog below remains the living ledger. Each entry carries the exact fix
the verifying agent specified — apply, build, and verify on the emulator in
light AND dark.

---

## STATUS LEDGER (updated 2026-07-05 — MagentaCastle/Codex)

> The list below was catalogued at `08e36e2`. Since then **40 items are fixed or verified on-device** and locked by regression proof. Do not redo them.

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
  all three card routes and Standard/SeaDrop/Express-through-`AppRoot` detail
  opening with the Swift detail title selected. Proof:
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_light_warehouse_geometry.png`,
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_top_dark_warehouse_geometry.png`,
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_standard_after_tap.png`,
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_seadrop_after_tap.png`,
  `/tmp/kotlin_ui_proof/home_warehouse/android_home_warehouse_express_after_tap.png`.
- **Warehouse detail Swift-precedence hero/badge:** Figma Warehouse node
  `40000944:3571` still shows the larger `90`px badge over a shorter photo
  area, but Swift `FigmaWarehousesViewController.makeHero` is the shipped
  guide: `240`pt hero photo, `60`pt overlapping circle, `28`pt method glyph,
  and `h5` method title. Android now follows Swift while preserving the
  approved Standard/SeaDrop/Express tab addition and existing data/copy path.
  `WarehousesScreenParityTest` locks light/dark hero geometry and tab switching.
  Proof:
  `/tmp/kotlin_ui_proof/warehouse_detail_swift/warehouses_swift/warehouse_express_swift_light.png`,
  `/tmp/kotlin_ui_proof/warehouse_detail_swift/warehouses_swift/warehouse_standard_swift_dark.png`.
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
- **Home live-data/viewDidAppear reload:** Home live-data behavior was compared
  against Swift `FigmaHomeViewController.viewDidAppear` first and Figma Home
  node `40001464:28899` second. Swift reloads auction products, AirCoins, and
  user header data on every appearance, and calls `renderAuctionProducts([])`
  if the auction fetch fails. Android now refreshes on lifecycle `ON_RESUME`
  while preserving the initial ViewModel load, and clears stale auction
  highlights on failed auction reload. `HomeLiveDataParityTest` locks both
  rails; adjacent `HomeActivityTilesScreenshotTest` and
  `HomeChromeOpacityParityTest` still pass. Proof:
  `/tmp/kotlin_ui_proof/home_live_data/figma/figma_home_40001464_28899.png`.
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
  contract in both app light and app dark with explicit app-theme light/dark
  vectors, so a system-dark emulator cannot turn the app-light handset white.
  Proof:
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
- **More root tap rails:** More root was compared against Figma node
  `40001948:22354` and Swift `FigmaMoreViewController`. Figma confirms the
  `375` frame, `80` profile card, `335x59` menu rows, and exact 12-row order.
  Swift takes behavior precedence for profile-card `ProfileView`, avatar
  photo-picker split, header tier/bell/cart/AirCoins callbacks, and every menu
  row's `FigmaRouteResolver` destination. Android now exposes those existing
  targets with stable tags and `MoreRootTapRailsParityTest` locks light/dark
  geometry plus profile/header/menu route callbacks. Proof:
  `/tmp/kotlin_ui_proof/more_root/figma/figma_more_40001948_22354.png`,
  `/tmp/kotlin_ui_proof/more_root/android/more_root/more_root_swift_light.png`,
  `/tmp/kotlin_ui_proof/more_root/android/more_root/more_root_swift_dark.png`.
- **More root app-dark menu icons:** Swift/Figma More root icons preserve orange
  accents while their `iconSelected` strokes flip with the active app theme.
  Android previously let those strokes read from resource-night
  `@color/icon_duotone`, so forced app-dark mode could keep black strokes.
  Android now selects explicit dark variants for all 12 More root menu icons,
  and `MoreRootTapRailsParityTest` pixel-checks every icon in light and dark.
  Figma proof:
  `/tmp/kotlin_ui_proof/more_root_dark_icons/figma/figma_more_root_40001948_22354.png`.
- **Sales Taxes / Ship Tax detail app-dark step icons:** Sales Taxes was
  compared against Swift `FigmaSalesTaxesViewController.swift` first and Figma
  node `40001531:11704` second. Swift step icons use orange primary paths plus
  dynamic `textDarkTitle` secondary strokes (`#292929` light, `#FFFFFF` dark).
  Android now selects explicit app-dark variants for all six step icons instead
  of relying on Android resource-night, and `SalesTaxesParityTest`
  pixel-checks every step icon in app light and app dark. Proof:
  `/tmp/kotlin_ui_proof/sales_taxes_icons/figma/figma_sales_taxes_40001531_11704.png`,
  `/tmp/kotlin_ui_proof/sales_taxes_icons/android/sales_taxes_icons/sales_taxes_icons_swift_light.png`,
  `/tmp/kotlin_ui_proof/sales_taxes_icons/android/sales_taxes_icons/sales_taxes_icons_swift_dark.png`.
- **Shared HomeDetailsHeader long-title autoscale:** Sales Taxes was compared
  against Swift `FigmaSalesTaxesViewController.buildHeader()` first and Figma
  node `40001531:11704` second. Figma's static header component is 16 semibold,
  but Swift ships `DesignTokens.Typography.title1()` plus
  `adjustsFontSizeToFitWidth` and `minimumScaleFactor = 0.8`, so Swift wins.
  Android now preserves the existing 56dp bar, 36dp back rail, 24dp chevron,
  52dp title gutters, screen-specific color/style overrides, and adds the
  missing shrink-to-fit behavior before allowing the title to wrap. Proof:
  `/tmp/kotlin_ui_proof/home_details_header/figma/figma_sales_taxes_40001531_11704.png`,
  `/tmp/kotlin_ui_proof/home_details_header/android/home_details_header/home_details_header_sales_taxes_swift_light.png`,
  `/tmp/kotlin_ui_proof/home_details_header/android/home_details_header/home_details_header_sales_taxes_swift_dark.png`.
- **Promotions Swift/Figma proof:** Promotions was compared against Swift
  `FigmaPromotionsViewController.swift` first and Figma node
  `40001646:14035` second. Figma `get_design_context` returned HTTP 504 on
  this pass, but screenshot/metadata succeeded and confirmed the static node
  still shows a 252px hero image. Swift takes precedence for the active app:
  160pt hero image, shared More2 header, 3-line collapsed description,
  `View Details` / `View Less` rail, and active-only
  `/promotional-banners` filtering. Android already matched Swift visually and
  functionally; this pass added non-visual tags plus `PromotionsParityTest` to
  lock light/dark geometry, active filtering, empty state, expansion, and Back.
  Proof:
  `/tmp/kotlin_ui_proof/promotions/figma/figma_promotions_40001646_14035.png`,
  `/tmp/kotlin_ui_proof/promotions/android/promotions/promotions_swift_light_collapsed.png`,
  `/tmp/kotlin_ui_proof/promotions/android/promotions/promotions_swift_light_expanded.png`,
  `/tmp/kotlin_ui_proof/promotions/android/promotions/promotions_swift_dark.png`.
- **Calculator Standard entry Swift/Figma proof:** Standard Calculator was
  compared against Swift `FigmaCalculatorViewController.swift` first and Figma
  node `40001464:29102` second. Figma still shows stale two-column
  `Select Unit` / `Total Weight` fields and a translucent sticky footer CTA,
  while Swift ships full-width `Invoice Amount USD`, full-width
  `Actual Weight (lbs)`, and a solid orange `Calculate` button inside the
  scroll stack. Android now follows Swift for the Standard branch, keeps the
  existing route/ViewModel/repository/results flow, preserves SeaDrop and
  Express branches, and locks the shared 32dp/24dp inner-header back rail plus
  Swift field/info-card primitives. Proof:
  `/tmp/kotlin_ui_proof/calculator_cta/figma/figma_calculator_standard_40001464_29102.png`,
  `/tmp/kotlin_ui_proof/calculator_cta/android/calculator_entry/calculator_standard_swift_light.png`,
  `/tmp/kotlin_ui_proof/calculator_cta/android/calculator_entry/calculator_standard_swift_dark.png`.
- **Drop Alert consignee/profile-failure Swift/Figma proof:** Drop Alert was
  compared against Swift `FigmaDropAlertViewController.swift` first and Figma
  node `40001826:22497` second. Figma still shows a filled, disabled-looking
  Consignee value and sticky translucent footer, while Swift leaves Consignee
  editable when profile prefill fails, submits the manual value, and clears
  every field after success. Android now follows Swift on the existing Drop
  Alert path, keeps the repository/multipart upload/picker/method/company rails,
  and adds only nonvisual proof tags to lock the manual Consignee flow. Proof:
  `/tmp/kotlin_ui_proof/drop_alert/figma/figma_drop_alert_40001826_22497.png`,
  `/tmp/kotlin_ui_proof/drop_alert/android/drop_alert/drop_alert_consignee_manual_light.png`,
  `/tmp/kotlin_ui_proof/drop_alert/android/drop_alert/drop_alert_consignee_manual_dark.png`.
- **Payment Methods Swift-precedence empty-state/Cart rail:** Payment Methods
  was compared against Figma node `40001428:9188` and Swift
  `FigmaPaymentMethodsViewController`. Swift takes precedence over the visible
  Figma conflict: Figma shows a saved-card chooser with PayPal/Apple Pay/Visa/
  Mastercard/AmEx rows and an `Add New Card` CTA, while Swift ships an
  informational empty-state card plus `Go to Checkout`. Android already matched
  Swift; this pass removed the stale duplicate `MoreRoutes.PAYMENT_METHODS`
  alias, keeps canonical `Routes.PAYMENT_METHODS`, and locks light/dark layout
  plus Cart navigation. Proof:
  `/tmp/kotlin_ui_proof/payment_methods/figma/payment_methods_40001428_9188.png`,
  `/tmp/kotlin_ui_proof/payment_methods/android/payment_methods/payment_methods_swift_light.png`,
  `/tmp/kotlin_ui_proof/payment_methods/android/payment_methods/payment_methods_swift_dark.png`.
- **Authorized Users refresh/list rails:** Authorized Users was compared
  against Figma node `40000975:7859` and Swift
  `FigmaAuthorizedUsersViewController.swift`. Swift takes precedence for
  behavior: reload on `viewWillAppear`, orange pull-to-refresh attached to the
  scroll view, card tap opens detail, and `Add User` only lives in the bottom
  CTA. Figma confirms the 20pt gutters, 56pt card header, active/inactive card
  stack, and bottom CTA band. Android now keeps the existing card/detail/add
  rails and adds pull-to-refresh through the same repository-backed ViewModel
  path. Proof:
  `/tmp/kotlin_ui_proof/authorized_users_figma.png`,
  `/tmp/kotlin_ui_proof/authorized_users/android/authorized_users/authorized_users_swift_light.png`,
  `/tmp/kotlin_ui_proof/authorized_users/android/authorized_users/authorized_users_swift_dark.png`.
- **Authorized User Detail one-load/action rails:** Authorized User Detail was
  compared against Swift `FigmaAuthorizedUserDetailViewController.swift`; the
  documented Figma detail node `40001185:5345` currently resolves to the
  Authorized Users list, so Swift takes precedence for the detail runtime. The
  root cause was a duplicate first-entry load: `AuthorizedUserDetailViewModel`
  already loads in `init`, while `AuthorizedUserDetailScreen` also called
  `viewModel.load()` from composition. Android now loads once on entry, keeps
  the Swift read-only header with no Edit action, refreshes after
  Activate/Deactivate, and pops after Delete. Proof:
  `/tmp/kotlin_ui_proof/authorized_user_detail/android/authorized_user_detail/authorized_user_detail_swift_light.png`,
  `/tmp/kotlin_ui_proof/authorized_user_detail/android/authorized_user_detail/authorized_user_detail_swift_dark.png`.
- **Add Authorized User add/edit payload rails:** Add Authorized User was
  compared against Figma node `40001541:45296` and Swift
  `FigmaAddAuthorizedUserViewController.swift`. Swift takes precedence over the
  Figma conflict: the static node omits `Email Address`, while Swift/RN include
  Email and send `user_email` to Laravel. Android keeps Email, preserves the
  existing add form, and locks the hidden edit-mode prefill/PUT rail without
  adding an Edit button to the read-only detail page. Proof:
  `/tmp/kotlin_ui_proof/add_authorized_user/android/add_authorized_user/add_authorized_user_swift_light.png`,
  `/tmp/kotlin_ui_proof/add_authorized_user/android/add_authorized_user/add_authorized_user_edit_swift_dark.png`.
- **Background Images Swift-precedence picker:** Background Images was compared
  against Figma section `40006644:65735` / frame `40006644:67051` and Swift
  `FigmaBackgroundImagesViewController.swift`. Swift takes precedence over the
  Figma-only expanded wallpaper list: Android now uses the two-column `220`dp
  portrait picker, 44dp selection controls, bottom Save rail, and Swift IDs
  `0..13`; stale saved IDs from the previous Figma-only `14..32` set normalize
  to the default image. Proof:
  `/tmp/kotlin_ui_proof/background_images/figma/figma_background_images_40006644_67051.png`,
  `/tmp/kotlin_ui_proof/background_images/android/background_images/background_images_swift_light.png`,
  `/tmp/kotlin_ui_proof/background_images/android/background_images/background_images_swift_dark.png`.
- **Restricted Items Swift-precedence list/search/detail:** Restricted Items was
  compared against Swift `FigmaRestrictedItemsViewController.swift` and
  `FigmaRestrictedItemsInfoViewController.swift`, plus Figma MCP nodes
  `40001432:14025` and `40001432:14918`. Swift takes precedence because the
  current Figma nodes disagree with the shipped iOS flow: `40001432:14025` is an
  Information/legal page and `40001432:14918` is a tabbed Restricted Items
  variant, while Swift uses a searchable category list that pushes category
  details. Android now keeps that Swift flow, removes the stale low-polish glyph
  carve-out, reuses the existing circular info and two-color dangerous-goods
  vectors, follows Swift note-card dark token behavior, and locks entry/search/
  detail geometry plus taps in `RestrictedItemsParityTest`. Proof:
  `/tmp/kotlin_ui_proof/restricted_items/figma/figma_restricted_information_40001432_14025.png`,
  `/tmp/kotlin_ui_proof/restricted_items/figma/figma_restricted_tabbed_40001432_14918.png`,
  `/tmp/kotlin_ui_proof/restricted_items/android/restricted_items/restricted_items_entry_swift_light.png`,
  `/tmp/kotlin_ui_proof/restricted_items/android/restricted_items/restricted_items_search_results_swift_light.png`,
  `/tmp/kotlin_ui_proof/restricted_items/android/restricted_items/restricted_items_restricted_detail_from_search_swift_light.png`,
  `/tmp/kotlin_ui_proof/restricted_items/android/restricted_items/restricted_items_permitted_detail_swift_dark.png`.
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
  shares a `content://` FileProvider stream instead of a raw text URL. Follow-up
  2026-07-05: Android also renders downloaded PDFs from that same local action
  file with `PdfRenderer` instead of Google Docs Viewer, closing the lingering
  HTTP 403 proof risk and matching Swift's local QuickLook path. Same-host
  Airdrop invoice downloads receive bearer auth; external invoice URLs do not.
  Proof:
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
- **Packages filter live flow + dark status icons:** Packages node
  `40001666:42198` and Packages filter node `40006358:75618` were refreshed
  through Figma MCP, then compared against Swift
  `FigmaPackagesViewController.swift` and
  `FigmaPackagesFilterViewController.swift`. Swift takes precedence for the
  runtime rails: the top-right filter affordance opens the sheet, status taps
  reload packages with a server-backed status value, shipment-method taps stay
  client-side, and close dismisses with the filtered list visible. Android now
  exposes the existing header affordance as `Filter`, proves the status/method
  rail split in `PackagesFilterFlowParityTest`, and fixes the app-dark status
  glyph bug by selecting explicit dark status vectors from `colors.isDark`
  instead of relying on resource-night `@color/icon_duotone`. Proof:
  `/tmp/kotlin_ui_proof/packages_filter_flow/figma/packages_40001666_42198.png`,
  `/tmp/kotlin_ui_proof/packages_filter_flow/figma/packages_filter_40006358_75618.png`,
  `/tmp/kotlin_ui_proof/packages_filter_flow/android/run_1783277832945/packages_filter_flow_swift_light.png`,
  `/tmp/kotlin_ui_proof/packages_filter_flow/android/run_1783277832945/packages_filter_flow_swift_dark.png`,
  `/tmp/kotlin_ui_proof/packages_filter_flow/android/run_1783277832945/packages_filter_flow_filtered_light.png`,
  `/tmp/kotlin_ui_proof/packages_filter_flow/android/run_1783277832945/packages_filter_flow_filtered_dark.png`.
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
- **Bottom-tab app-dark icon roles:** Shared tab chrome was compared against
  Swift `FigmaBottomTabBar` in `FigmaTabHeader.swift` first, then Figma Home
  node `40001464:28899`. Swift takes precedence: active icon + label are
  `orangeMain`, inactive icons are `iconSelected` (black in app light, white in
  app dark), and both icon primary/secondary paths are passed the same tint.
  Android already matched through `ColorFilter.tint`, so no production bottom
  bar code changed; `AirdropBottomBarIconParityTest` now pixel-checks every tab
  icon and selected label in app light and app dark. Proof:
  `/tmp/kotlin_ui_proof/bottom_tab_icons/figma/figma_home_40001464_28899.png`,
  `/tmp/kotlin_ui_proof/bottom_tab_icons/android/bottom_tab_icons/bottom_tab_icons_swift_light.png`,
  `/tmp/kotlin_ui_proof/bottom_tab_icons/android/bottom_tab_icons/bottom_tab_icons_swift_dark.png`.
- **Home primary route callbacks:** Swift `FigmaHomeViewController` maps
  Services, Ship Tax, Calculator, Drop Alert, See More, Refer a friend, bell,
  and cart to their corresponding route destinations. Android already emitted
  the Swift-equivalent routes, and `HomeActivityTilesScreenshotTest` now locks
  those callbacks alongside the existing warehouse route/AppRoot proof. This
  does not close full authenticated end-to-end Home data proof.
- **Home auction card/cart behavior:** Swift `makeAuctionCard` opens product
  details from the card and toggles `FigmaCartStore` only from the plus button;
  Figma Home node `40001464:28899` supplies the visual plus-button placement.
  Android now exposes the existing plus hit target to instrumentation and locks
  the Swift flow split: plus toggles `CartStore` without navigation, while the
  card opens `auctionProductDetails/{slug}`.
- **Home live-data/viewDidAppear reload:** Swift `FigmaHomeViewController`
  calls `loadAuctionProducts()`, `loadAirCoins()`, and `loadUserHeader()` from
  `viewDidAppear`, and clears stale auction products by calling
  `renderAuctionProducts([])` on auction fetch failure. Android now refreshes
  Home on lifecycle `ON_RESUME` and clears stale auction highlights on failed
  auction reload. Figma Home node `40001464:28899` was refreshed through Figma
  MCP as visual proof. `HomeLiveDataParityTest` passed 2/2, with adjacent Home
  visual/tap and chrome tests still green.
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
- **Shipments hub summary icons/geometry:** Figma hub node `40000823:9633` was
  refreshed and compared against Swift
  `FigmaShipmentsViewController.makeStatTile`. Swift takes precedence for the
  runtime duotone icon contract: orange accent plus `textDarkTitle` secondary
  strokes. Android already matched the 20dp gutters, 10dp grid gaps, and 93dp
  tile height; this pass split the existing summary vectors into theme-tinted
  base layers plus orange accent layers so app-dark ThemeController mode stays
  visible without duplicating the summary UI.
- **Shipments backend pagination/search rails:** Figma MCP screenshots were
  refreshed for hub `40000823:9633`, Packages `40001666:42198`, Payments
  `40001753:18909`, and Orders `40001753:19595`; Swift takes precedence for
  runtime behavior because Figma cannot encode API pagination. Android now
  locks Swift's hub render limits (`packagesData.prefix(10)`,
  `paymentsData.prefix(4)`, `ordersData.prefix(6)`), Packages return-key server
  search/status pagination, Payments default `package` type + 300ms min-3
  search + page-2 loading, and Orders 10-row page size + refresh/search reset.
  `ShipmentsBackendPaginationParityTest` verifies this on device.

**🔲 OPEN — BlueDeer (Shipments detail), priority order:** remaining Shipments follow-ups not explicitly closed below.

**✅ CLOSED — MagentaCastle (More/Legal/Profile/AirCoins/HomeDetails/Calculator/Drop Alert/Shipments slices):** More root tap rails, Payment Methods Swift-precedence empty-state/Cart rail, Settings Swift/Figma geometry/icon/action rails, Authorized Users refresh/list rails, Add Authorized User add/edit payload rails, Background Images Swift-precedence picker, Account Deletion Reason confirmation/local-cleanup, Refer-a-Friend initial load lifecycle, §252/§423/§432/§468/§477 Notification Settings, Documents §216/§225, Documents refresh/reload, Profile avatar/DOB, Preferences §243, Invite Friend §261, More2 shared inner-header back glyph, Promotions Swift/Figma proof, Calculator Standard entry Swift/Figma proof, Drop Alert consignee/profile-failure Swift/Figma proof, Legal/T&C §270, FAQs §486, AirCoins balance/history, GoldPriority tier-name/status-bar, Home live-data/viewDidAppear reload, PackageDetails Swift/Figma screen pass, PaymentPackageDetails footer/timeline/payment-copy, ProductPaymentDetails/OrderDetails hero/payment-copy, InvoiceViewer surface/share-file, PackagesFilterSheet Swift/Figma, Packages filter live flow/dark status icons, Payments/Orders header/error follow-up, Shipments section-card divider, Shipments hub tap-rail, Shipments search-field split, Shipments hub summary icon/geometry, and Shipments backend pagination/search rails are closed by Swift-precedence proof above.

## [CLOSED] Settings
`app/src/main/java/com/ga/airdrop/feature/more/SettingsScreen.kt` and `MoreComponents.kt` — Settings was close to the right surface, but it was still following stale Figma/resource behavior in two Swift-visible details.

**Detail:** Figma node `40007388:24260` shows translucent header/footer chrome, orange-accent duotone Settings icons, 10pt card gaps, and a much lower Account Deletion row. Swift `FigmaSettingsViewController` is the runtime guide: solid `gray100` header/footer, 59pt cards, `stack.spacing = 14`, `setCustomSpacing(36)` after Mode, and executable template tinting of non-destructive icons to `DesignTokens.Color.iconSelected` (black light / white dark). Android used `Arrangement.spacedBy(10)` plus a 26dp spacer, which actually made the Mode-to-Account gap 46dp, and it passed `tint = null` for Settings icons, letting `@color/icon_duotone` follow system resource-night instead of app `ThemeController`.

**Fix:** Closed. Android now uses explicit 14dp row spacers and a 36dp Mode-to-Account gap, and Settings' Notification/Background/Mode leading icons are tinted from `AirdropTheme.colors.iconSelected`; Account Deletion stays destructive red. `MoreRowCard` gained optional test tags only, with no layout or behavior change for existing callers. `SettingsParityTest` verifies Swift geometry, light/dark icon pixels, route clicks, whole-row and toggle theme switching, clear-cache confirmation, and the logout alert.

**Verification 2026-07-05:** `git diff --check`, `:app:compileStagingDebugKotlin`, `:app:compileStagingDebugAndroidTestKotlin`, and focused Gradle `connectedStagingDebugAndroidTest` for `SettingsParityTest` passed 4/4 on `airdrop_test2(AVD) - 15`.

## [CLOSED] Background Images
`app/src/main/java/com/ga/airdrop/feature/more/BackgroundImagesScreen.kt` and `BackgroundStore.kt` — previous Android picker followed the Figma-expanded list instead of the Swift app that Kemar designated as the guide.

**Detail:** Figma section `40006644:65735` / frame `40006644:67051` shows a one-column `335x150` landscape list and extra wallpaper IDs. Swift `FigmaBackgroundImagesViewController.swift` ships default + IDs `1..13`, a two-column grid, `220`pt portrait tiles, 44pt selection controls, the "Default Image" pill only when the default tile is unselected, and a bottom Save rail. Swift takes precedence for this conflict.

**Fix:** Closed. Android now uses the Swift two-column picker, limits `BackgroundStore.choices` to IDs `0..13`, preserves the existing Home background resolution path, and normalizes stale Figma-only saved IDs back to default. `BackgroundImagesParityTest` verifies light/dark geometry, save behavior, tile reachability, and stale-ID migration; full `connectedStagingDebugAndroidTest` passed 107/107.

## [CLOSED] Account Deletion Reason
`app/src/main/java/com/ga/airdrop/feature/more2/AccountDeletionReasonScreen.kt` and `AccountDeletionReasonViewModel.kt` — destructive confirmation sheet kept stale Figma grab-handle chrome, and the successful deactivation path left local visual/cart state behind.

**Detail:** Figma MCP for Account Deletion Reason `40007388:27504` still shows stale duplicated reason labels and the typo "Why do you want you want...", plus a visible grab handle in the destructive sheet. Swift `FigmaAccountDeletionReasonViewController.swift` carries the runtime truth: canonical reason list, corrected question text, no drag handle, 28pt warning top inset, 24pt top sheet radius, and full local logout hygiene after deactivation. Swift also clears `BACKGROUND_IMAGE_ID` during account deletion; Android already had `BackgroundStore` but did not clear it here.

**Fix:** Closed. Android now preserves the Swift confirmation-sheet spacing without rendering the stale Figma handle, and successful deactivation clears bearer token, shared header session, persisted cart cache, background selection, and the in-memory deletion credential handoff. `AccountDeletionReasonParityTest` verifies the modal geometry/top-band pixels and the Swift logout cleanup path. Verification on 2026-07-05: `git diff --check`, `:app:compileStagingDebugKotlin :app:compileStagingDebugAndroidTestKotlin`, focused Gradle `connectedStagingDebugAndroidTest` for `AccountDeletionReasonParityTest` passed 2/2, proof PNG at `/tmp/kotlin_ui_proof/account_deletion_reason/account_deletion_reason_confirm_swift_light.png`.

## [CLOSED] Refer-a-Friend initial load lifecycle
`app/src/main/java/com/ga/airdrop/feature/more2/ReferAFriendViewModel.kt` — referred friends loaded twice on first entry.

**Detail:** Swift `FigmaReferAFriendViewController.swift` separates lifecycle work: `viewDidLoad` loads the account-number referral link, while `viewWillAppear` loads referred friends. Android loaded referred friends in `ReferAFriendViewModel.init` and again from `ReferAFriendScreen`'s `LaunchedEffect` that mirrors Swift `viewWillAppear`, causing duplicate `/refer-friend` calls on first entry. Figma node `40001940:26885` still renders the older bottom-CTA landing frame, so Swift takes precedence for this runtime behavior.

**Fix:** Closed. `ReferAFriendViewModel.init` now loads only the profile/account-number referral link; the referred-friends refresh remains on screen entry. `ReferAFriendParityTest` verifies one profile call, one referred-friends call, and the rendered account-number referral link on initial entry. Verification on 2026-07-05: Figma MCP design context for `40001940:26885`, Swift source comparison, `git diff --check`, compile gates, and focused Gradle `connectedStagingDebugAndroidTest` for `ReferAFriendParityTest` passed 1/1.

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

**✅ CLOSED — More root tap-rail/dark-icon follow-up:** Swift
`FigmaMoreViewController` wins for root behavior, while Figma node
`40001948:22354` provides the pixel/card order reference. Android now has
focused proof for the 80dp profile card, 48dp avatar, 335x59dp menu rows, the
12 menu row callbacks, the profile-card/avatar split, and header tier/bell/cart/
AirCoins callbacks. The More root menu icon set now also uses explicit app-dark
variants so orange accents survive while `iconSelected` strokes flip to white.
This closes the More root rail only; broader More subpage pixel parity remains
tracked separately.

**✅ CLOSED — Payment Methods Swift-precedence follow-up:** Swift
`FigmaPaymentMethodsViewController` wins over Figma node `40001428:9188`.
Figma still shows the saved-card chooser and `Add New Card`; Android must keep
Swift's informational empty-state card and `Go to Checkout` Cart row until Swift
changes. `PaymentMethodsParityTest` verifies light/dark geometry, rejects the
stale Figma chooser copy, and proves the Cart route.

**✅ CLOSED — Packages filter live flow / dark status-icon follow-up:** Swift
`FigmaPackagesViewController.swift` and `FigmaPackagesFilterViewController.swift`
win for runtime behavior, while Figma nodes `40001666:42198` and
`40006358:75618` provide the visual contract. Android now proves the top-right
filter button opens the sheet, status taps reload packages with the server-side
status parameter, shipment-method taps remain client-side, and close leaves the
filtered list visible. Status row glyphs now select explicit app-dark vectors
from `AirdropTheme.colors.isDark`, so resource-night cannot make app-dark icons
black-on-dark again. `PackagesFilterFlowParityTest` verifies light/dark sheet
and filtered-list screenshots.

**✅ CLOSED — Authorized Users refresh/list follow-up:** Swift
`FigmaAuthorizedUsersViewController.swift` wins for runtime behavior, while
Figma node `40000975:7859` provides the card/list measurements. Android now
preserves the active/inactive card layout, detail tap, and bottom `Add User`
rail while adding the missing orange pull-to-refresh path through
`AuthorizedUsersViewModel.refresh()`. `AuthorizedUsersParityTest` verifies light
and dark geometry, card/add taps, and manual refresh API calls; adjacent More
regressions passed 18/18 and the full connected staging suite passed 104/104.

**✅ CLOSED — Authorized User Detail one-load/action follow-up:** Swift
`FigmaAuthorizedUserDetailViewController.swift` wins for the detail page because
Figma node `40001185:5345` currently renders the Authorized Users list, not the
detail surface. Android removed the duplicate screen-side first load and keeps
the existing ViewModel-owned load/mutation/delete rails: one GET on entry,
Activate/Deactivate refreshes detail, Delete pops back, and no Edit action is
rendered. `AuthorizedUserDetailParityTest` verifies light/dark detail geometry,
one-load behavior, status mutation refreshes, and delete navigation.

**✅ CLOSED — Add Authorized User add/edit follow-up:** Swift
`FigmaAddAuthorizedUserViewController.swift` wins where Figma node
`40001541:45296` conflicts. Figma omits Email Address, but Swift/RN include it
and send `user_email`, so Android keeps the Email row and accepts the lower
initial TRN position caused by the extra Swift field. `AddAuthorizedUserParityTest`
verifies the 20dp gutters, 12dp name-row gap, 50dp field cards, 52dp CTA,
add-mode POST payload parsing, edit-mode prefill/PUT payload, and no detail-page
Edit affordance regression.

**✅ CLOSED — Invite Friend contacts icon follow-up:** Swift's
`FigmaInviteFriendViewController` renders ContactNumber with orange signal arcs
and an `iconSelected` handset. Android already removed the solid-orange tint;
this pass also switches the handset to the existing white dark vector under
app-dark `ThemeController` mode and adds pixel-level light/dark icon proof.

**✅ CLOSED — More2 shared inner-header back-glyph follow-up:** Swift's
drill-down controllers render the back affordance with
`FigmaIcon.chevronDown(size: 24)` rotated `pi / 2`; Figma Promotions node
`40001646:14035` still shows an `Arrow - Right`-derived left arrow, so Swift
takes precedence. Android now reuses `More2InnerHeader` with a 24dp
theme-tinted rotated chevron instead of the stale 20dp tailed arrow, preserving
the 36dp tap rail. `More2InnerHeaderParityTest` verifies light/dark tint,
chevron shape, click dispatch, and screenshots.

**✅ CLOSED — Promotions Swift/Figma proof follow-up:** Swift
`FigmaPromotionsViewController.swift` is the runtime source of truth for Figma
node `40001646:14035`. Figma still shows a stale 252px static hero image, while
Swift ships a 160pt hero image with shared More2 header, 3-line collapsed
description, active-only backend filtering, and the View Details/View Less
toggle. Android already matched Swift; this pass added focused proof tags and
`PromotionsParityTest`, with light/dark screenshots under
`/tmp/kotlin_ui_proof/promotions/android/promotions/`.

**✅ CLOSED — Calculator Standard entry Swift/Figma proof follow-up:** Swift
`FigmaCalculatorViewController.swift` is the runtime source of truth for
Standard Calculator node `40001464:29102`. Figma still shows stale
two-column `Select Unit` / `Total Weight` fields and a translucent sticky
footer CTA; Swift ships full-width Invoice + Actual Weight fields and the
solid orange Calculate button in the scroll stack. Android now matches Swift on
the existing Calculator path, with `CalculatorEntryParityTest` covering light,
dark, row removal, button geometry, and back dispatch.

**✅ CLOSED — Drop Alert consignee Swift/Figma proof follow-up:** Swift
`FigmaDropAlertViewController.swift` is the runtime source of truth for Drop
Alert node `40001826:22497`. Figma still shows a prefilled/disabled-looking
Consignee field and sticky translucent footer, while Swift keeps Consignee
editable when profile prefill fails, submits the user-entered value, and clears
the field after success. Android now matches that existing flow with
`DropAlertConsigneeParityTest` covering light, dark, manual input, submit, and
post-submit reset.

**✅ CLOSED — Shipping Rates Swift/Figma proof follow-up:** Swift's
`FigmaShippingRatesViewController.swift` is the runtime source of truth for
node `40001567:54206`. Figma still shows translucent chrome, pale table-label
rows, a static `0.5` / `$4.50` first row, and `$2.00` fuel copy, while Swift
uses solid `gray100` chrome, orange table headers, backend-first rates, the
runtime fallback table starting at `1` / `$5.00`, and a pinned solid-orange
`Calculate Now` rail. Android already matched Swift visually; this pass added
only non-visual proof tags plus `ShippingRatesParityTest`. The test verifies
fallback vs backend rows, dark rendering, 20dp gutters, 44dp rows, 52dp CTA,
back rail, and calculator route. Proof lives under
`/tmp/kotlin_ui_proof/shipping_rates/android/shipping_rates/`.

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

**Fix:** Done. `InvoiceViewerScreen` now prepares a local action file in cache for remote/content URLs and uses local files directly for `file://` URLs. The Share button stays disabled until that file exists, then `invoiceShareIntent` sends `ACTION_SEND` with `EXTRA_STREAM`, MIME type (`application/pdf`, `image/*`, or octet-stream fallback), `FLAG_GRANT_READ_URI_PERMISSION`, and `ClipData`; it no longer sends `EXTRA_TEXT` with the raw URL. Follow-up 2026-07-05: PDF preview now uses the downloaded local file through Android `PdfRenderer`, not Google Docs Viewer, and protected Airdrop-host invoice downloads attach the bearer header without leaking it to external invoice URLs. `InvoiceViewerParityTest` verifies the stream intent, local-file path, MIME mapping, local PDF preview, Airdrop-host auth guard, and light/dark rendered geometry.

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

## [CLOSED] Shop root + Auction/Feature Products lists
`app/src/main/java/com/ga/airdrop/feature/shop/ShopComponents.kt:299` — ShopProductCard uses one geometry everywhere, but Swift differs per context: Shop-root cards have a 1-line title with 15pt insets/10pt spacing, list cells have 2-line titles with 12pt side insets/10pt top/6pt spacing, and all cards are fixed at 245pt tall.

**Detail:** Kotlin: minLines = 2 / maxLines = 2 (ShopComponents.kt:299-300), text column padding 15dp all around, internal spacing 10dp, intrinsic height. Swift Shop root: title.numberOfLines = 1 (FigmaShopViewController.swift:555), textColumn top 15 / sides 15 / spacing 10 (:615-617, :592), card height 245 (:510). Swift grid cells: titleLabel.numberOfLines = 2 (FigmaAuctionViewController.swift:572), textColumn top 10 / sides 12 / spacing 6 (:613-616, :591), cell height 245 (:410). Net effect: Shop-root cards reserve an extra title line vs iOS, and full-list cells have looser text insets than iOS.

**Fix:** Closed in current Android and re-verified with Swift precedence plus Figma MCP. Figma Shop node `40001846:53519`, Auction node `40001846:54117`, and Feature Products node `40001846:54396` all show the 160-wide card family with 135 image band and the same Cairo text tokens; Swift remains the runtime authority for the context split. Current `ShopProductCard` is parameterized with `titleLines` and `rootInsets`, uses fixed 245.dp height, root 1-line/15/10 spacing, and list/detail 2-line/12/10/6 spacing. `ShopRootListParityTest` now locks the root one-line height, list two-line height, and 245dp card height.

---

## [CLOSED] Shop root
`app/src/main/java/com/ga/airdrop/feature/shop/ShopScreen.kt:74` — Shop root content starts at 146dp from the top (126dp spacer + 20dp column padding) instead of Swift's 126pt.

**Detail:** Swift pins contentStack.topAnchor at constant 126 from the scroll content top with 20pt side insets only (FigmaShopViewController.swift:109-113). Kotlin stacks Spacer(126.dp) THEN a Column with .padding(Spacing.md) on all four sides, adding an extra 20dp above the search field. (The bottom currently works out to 140dp = Swift's 120 tail + 20 inset, so only the top drifts.)

**Fix:** Closed in current Android. The current tree uses `Spacer(126.dp)` and horizontal-only `Spacing.md` padding, so the search field starts at Swift's 126pt effective top. Refreshed Swift source shows `contentStack.bottomAnchor` is `-20` and `contentInsetAdjustmentBehavior = .never`; the older proposed 140dp tail bump is not Swift-backed and was not applied. Figma Shop node `40001846:53519` also shows content at `top-[106px]` with 20px padding, producing the same 126px effective search top.

---

## [CLOSED] Auction checkout
`app/src/main/java/com/ga/airdrop/feature/shop/ShopComponents.kt:474` — The Payment Currency field diverges from Swift's makeField spec in label typography, asterisk color, container color/radius/height, value font and chevron color.

**Detail:** ShopDropdownField vs FigmaAuctionProductCheckoutViewController.makeField (:542-618): label subtitle1 vs subtitle2 (:553); required '*' AlertPalette.Error red vs orangeMain (:556-559); container gray150 / Radius.xs(10dp) / minHeight 50 vs gray100 / radius 12 / height 48 (:571-577); value text body2 vs body1 (:564); chevron tinted iconSelected (near-black light mode) vs gray500 (:585-587).

**Fix:** Closed with Swift precedence. Figma MCP `get_design_context` for the documented checkout node `40001846:54756` confirmed the node is actually Feature Products + sort sheet, not Auction Checkout; Figma Cart node `40008284:26547` still shows the older red-star/gray150/50px component. Swift wins here: `FigmaAuctionProductCheckoutViewController.makeField`, `FigmaCartViewController.makeField`, and `FigmaProfileViewController.makeField` all use subtitle2 labels, orange required stars, gray100 48pt cards, body1 field text, and gray500 trailing icons. Android now matches those reusable Swift tokens in `ShopDropdownField` and the shared `TypeInputField`; `AuctionCheckoutParityTest.sharedCheckoutAndCartFieldsUseSwiftMakeFieldTokens` locks the 48dp card height, orange required stars, gray100 card fill, 20dp gray500 dropdown chevron, and existing checkout behavior tests remain green.

---

## [CLOSED] Auction Product Details
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:257` — Hero image has no placeholder or error fallback — blank gray card when the product has no image or the load fails

**Detail:** Swift buildHeroImage (FigmaAuctionProductDetailsViewController.swift:276-294) shows a 96pt gray400 airplane glyph placeholder whenever displayImageURL is nil, and un-hides it if sd_setImage returns a nil image. Kotlin renders a bare AsyncImage with no fallback, so null/blank imageUrl or a failed fetch leaves an empty gray150 box.

**Fix:** Closed in current Android and re-verified with Swift precedence plus Figma MCP. Figma Product Details node `40002072:24025` confirms the 240dp hero area and Product Details visual structure; Swift `buildHeroImage` is the runtime authority for the fallback. Current Kotlin shows the centered gray400 airplane placeholder when `imageUrl` is blank and restores it from `AsyncImage.onError`. `AuctionProductDetailsRelatedParityTest` now locks null-image and failed-image fallback behavior, including the Swift 96dp placeholder size.

---

## [CLOSED] Feature Product Details
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:157` — 'Purchase Product' silently does nothing when amazonUrl is blank; Swift shows a 'Product link unavailable' alert

**Detail:** Swift onPurchaseProduct (FigmaAuctionProductDetailsViewController.swift:817-831) presents an alert 'Product link unavailable' / 'No purchase link was returned for this feature product.' when the URL is empty, and a second alert when it's not a valid URL. Kotlin's CTA click checks `raw.isNotEmpty()` and simply no-ops otherwise — a dead-feeling button for featured products missing amazon_url.

**Fix:** Closed in current Android and re-verified against Swift `onPurchaseProduct`. The featured CTA now shows `Product link unavailable` with Swift's exact missing-link message for blank URLs and Swift's invalid-link message for malformed URLs. `AuctionProductDetailsRelatedParityTest` now covers both blank and invalid purchase links.

---

## [CLOSED] Auction Checkout
`app/src/main/java/com/ga/airdrop/feature/shop/ShopComponents.kt:474` — ShopDropdownField (Payment Currency) diverges from Swift makeField: gray150 fill blends into the checkout background, label/value/radius/star tokens all drift

**Detail:** Swift makeField (FigmaAuctionProductCheckoutViewController.swift:542-618, identical in FigmaCartViewController:1004-1080): label subtitle2 textDarkTitle with an ORANGE ' *' (orangeMain) when required; field card gray100, radius 12, height 48, value font body1, chevron 20pt gray500, label→card gap 6. Kotlin ShopDropdownField: label subtitle1, red AlertPalette.Error asterisk, gray150 fill (invisible against the gray150 Auction Checkout background — only the border shows), radius 10 (Radius.xs), minHeight 50, value body2, chevron 24dp iconSelected. TypeInputField (cart billing form) shares the same gray150/red-star/subtitle1 drift.

**Fix:** Closed with the same shared-field patch as the duplicate Auction checkout row above. The dropdown and core `TypeInputField` now both follow Swift's active reusable `makeField` contract. Figma conflict is recorded explicitly: the checkout node maps to Feature Products, and the Cart screen node still contains the stale old Type Input Field, so Swift takes precedence.

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

**Fix:** Done. `NotificationSettingsScreen` now uses the plain bell tinted `iconSelected` for the master row, screen-specific mail vectors with `iconSelected` flap + orange body, explicit app-light/app-dark chat vectors, and the plain bell tinted orange for Push. `NotificationSettingsParityTest` verifies the icon colors in app light and app dark, including the system-dark emulator case where `@color/icon_duotone` previously leaked a white SMS bubble into app light mode.

**Verification 2026-07-05:** Direct instrumentation and Gradle targeted reruns passed on the original system-dark emulator. The full connected suite passed 112/112 on a system-dark emulator after this fix. The focused system-light gate for Notification Settings, Invite Friend, and More2 inner header passed 7/7.

---

## [CLOSED] Invite Friend
`app/src/main/java/com/ga/airdrop/feature/more2/InviteFriendScreen.kt:129` — Contacts row icon is tinted solid orange, flattening the duotone glyph; Swift renders orange signal waves with a dark handset.

**Detail:** Earlier Android builds applied `ColorFilter.tint(BrandPalette.OrangeMain)` to `ic_contact_number`, flattening the duotone glyph. Current Android had already removed that tint, so the remaining risk was app-dark mode: `@color/icon_duotone` follows Android resource-night, while this app flips themes through `ThemeController`. Swift uses `FigmaIcon.contactNumber(primary: orangeMain, secondary: iconSelected)` (FigmaInviteFriendViewController.swift lines 364-366), and `FigmaIcon_ContactNumber` paints the waves with primary orange and the handset with secondary iconSelected (FigmaIcons.swift lines 968-973). Figma MCP screenshots for documented nodes `40001940:26797` and `40001940:26885` both render the Refer-a-Friend landing screen, not the Send Invitation form, so Swift is the authoritative form/icon source for this item.

**Fix:** Done. The Contacts row uses explicit app-light/app-dark vectors, preserving orange arcs while switching the handset from Swift `iconSelected` dark to Swift `iconSelected` white from `ThemeController`, not Android resource-night. `InviteFriendParityScreenshotTest` verifies 59dp row height, 24dp icon size, orange arcs, light dark-handset pixels, dark white-handset pixels, and emits light/dark screenshots.

**Verification 2026-07-05:** Direct instrumentation and Gradle targeted reruns passed on the original system-dark emulator. The full connected suite passed 112/112 on a system-dark emulator after this fix. The focused system-light gate for Notification Settings, Invite Friend, and More2 inner header passed 7/7.

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

## [CLOSED] Shop root — Feature Products row
`app/src/main/java/com/ga/airdrop/feature/shop/ShopScreen.kt:134` — The featured-row empty card is rendered full-width; Swift renders a fixed 240pt-wide card inside the horizontal row.

**Detail:** Swift: row.addArrangedSubview(makeEmptyCard(text: "No featured products", fixedWidth: 240)) (FigmaShopViewController.swift:490). Kotlin ShopEmptyCard fills max width (ShopScreen.kt:181-198).

**Fix:** Closed in current Android. `ShopScreen` passes `Modifier.width(240.dp)` for the featured empty card while leaving the auction empty card full-width, matching Swift's `makeEmptyCard(... fixedWidth: 240)` only in the featured horizontal row. `ShopRootListParityTest.featuredEmptyCardKeepsSwiftFixedWidth` locks the 240dp width and 200dp height.

---

## [CLOSED] Product details — description markdown
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:579` — Description markdown '**bold**' spans were stripped to plain text; Swift renders them as bold Cairo-Bold spans.

**Detail:** Swift's renderProductDescription parses \*\*(.+?)\*\* and applies Cairo-Bold to the inner text (FigmaAuctionProductDetailsViewController.swift:840-891) — this was an explicit user-flagged fix on iOS.

**Fix:** Closed in current Android. `descriptionAnnotated` now splits on the same regex and applies `FontWeight.Bold` to matched inner spans instead of deleting the markers.

---

## [CLOSED] Auction list / Feature Products list
`app/src/main/java/com/ga/airdrop/feature/shop/ProductListViewModel.kt:64` — Typing 1-2 characters in list search does not trigger a reload, leaving stale filtered results when backspacing from 3 chars to 2.

**Detail:** onQueryChange early-returns when 0 < trimmed.length < 3 (ProductListViewModel.kt:64). Swift list VCs debounce-reload on EVERY change and only omit the search param when <3 chars (FigmaAuctionViewController.swift:458-464 + searchQuery :335-338), so backspacing from 'abc' to 'ab' restores the unfiltered list on iOS but keeps showing 'abc' results on Android. (The Shop root's gating IS correct — Swift gates there: FigmaShopViewController.swift:762.)

**Fix:** Closed in current Android. `ProductListViewModel.onQueryChange` now debounces every change and lets `searchQuery()` omit sub-3-character strings, matching Swift Auction/Feature list controllers while preserving the intentionally gated Shop-root search. `ShopRootListParityTest.fullListSearchBackspaceReloadsUnfilteredLikeSwift` locks the `null -> "abc" -> null` request sequence.

---

## [CLOSED] Auction list / Feature Products list
`app/src/main/java/com/ga/airdrop/feature/shop/ProductListScreen.kt:133` — Pull-to-refresh spinner uses default Material3 colors instead of Swift's orangeMain refresh tint.

**Detail:** PullToRefreshBox is used without a custom indicator (ProductListScreen.kt:133-137); Swift sets refreshControl.tintColor = DesignTokens.Color.orangeMain (FigmaAuctionViewController.swift:313, FigmaFeatureProductsViewController.swift:319).

**Fix:** Closed in current Android. `ProductListScreen` now provides a `PullToRefreshDefaults.Indicator` with `color = BrandPalette.OrangeMain`, matching Swift's Auction and Feature Products `refreshControl.tintColor = DesignTokens.Color.orangeMain`. The adjacent list search/card parity tests compile through this screen cluster, and the source-level Swift/Figma evidence is recorded here to keep the backlog from reopening stale duplicate work.

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
