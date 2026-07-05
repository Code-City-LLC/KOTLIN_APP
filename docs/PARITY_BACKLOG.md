# Pixel-Parity Backlog

Source: adversarially-verified full-app audit (117 confirmed findings,
Kotlin vs Swift `Figma*ViewController`s vs Figma file N4k6jzpeLZgeRS5O1xfyIv),
2026-07-04. 63 findings were fixed in commits 48db012 + 9222e1d (+ follow-ups);
the 54 below remain. Each entry carries the exact fix the verifying agent
specified — apply, build, and verify on the emulator in light AND dark.

---

## STATUS LEDGER (updated 2026-07-05 @ HEAD `a1768d2` — BlueDeer)

> The list below was catalogued at `08e36e2`. Since then **12 items are FIXED, on-device verified, and pushed.** Do not redo them.

**✅ DONE (pushed):**
- Package details §45 (gray200/gray100 surfaces), §54 (status-tinted bullet dots), §63 (inline titles/no dividers/title2 values), §72 (Exchange-Rate + plain Total footer) → `db84b0d`
- Payments §81 (download top-right), Payments/Orders §90 (pull-to-refresh) → `6605dd4`
- Shop root+lists §162 (245dp card + per-context title lines), Shop root §171 (top inset), ShopDropdownField §180/§207 (restyle), Auction Product Details §189 (hero placeholder), Feature Product Details §198 (link-unavailable alert) → `e7357a5`
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

**🔲 OPEN — BlueDeer (Shipments detail), priority order:** §99 View-History pinned footer · §108 "Invoice Amount (Declared Value/Cost)" · §153 CIF pill 48dp · §135 timeline connector color · §117 InvoiceViewer surfaces · §126 InvoiceViewer share-file · §144 hero image geometry · §27/§36 PackagesFilterSheet · §9/§18 GoldPriority.

**🔲 OPEN — MagentaCastle (More/Legal/Profile):** §216/§225 Documents · §234/§459 Edit Profile · §243 Preferences · §252/§423/§432/§468/§477 Notification Settings · §261 Invite Friend · §270 Legal/T&C · §486 FAQs.

**🔲 OPEN — unassigned (AmberOtter first-pass / TopazGlacier audit):** remaining LOW batch §279–§486.

**✅ CLOSED — Home dark icon follow-up:** Services tile gear layer is no longer
dark-on-dark in app dark mode; the duotone activity icons were compared against
Swift first and Figma second, then verified on emulator in light and dark.

(Section numbers are the source-line anchors printed by `grep -nE '^## ' docs/PARITY_BACKLOG.md`.)

---

## [MEDIUM] GoldPriority / Customer Tier
`app/src/main/java/com/ga/airdrop/feature/homedetails/GoldPriorityScreen.kt:258` — Tier name has no auto-shrink — 'Platinum Priority' at fixed 28sp Cairo Bold clips on narrow screens; Swift shrinks the font to fit.

**Detail:** Swift nameLabel (FigmaGoldPriorityViewController.swift:477-481) sets adjustsFontSizeToFitWidth = true with minimumScaleFactor 0.7 and low compression resistance, so long tier names ('Platinum Priority', 'Diamond Elite') scale down rather than truncate. Kotlin (GoldPriorityScreen.kt:258-263) renders Text at a fixed 28.sp with maxLines = 1 and no overflow handling — on compact-width devices (~360dp, minus 2×30dp padding, 70dp badge, 12dp gap ≈ 208dp available) the name hard-clips mid-glyph.

**Fix:** Use auto-sizing text: BasicText with autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 28.sp) (Compose 1.8+), or an onTextLayout-driven font-size reduction loop clamped at 0.7 × 28sp, matching Swift's minimumScaleFactor.

---

## [MEDIUM] GoldPriority / Customer Tier
`app/src/main/java/com/ga/airdrop/feature/homedetails/GoldPriorityScreen.kt:164` — Status bar icons are not forced light — in light theme the system clock/icons render dark on the 35%-black header overlay (near-invisible on Diamond/Corporate tiers).

**Detail:** Swift overrides preferredStatusBarStyle to .lightContent (FigmaGoldPriorityViewController.swift:34) because the tier gradient plus the black-0.35 overlay behind the status bar is dark on every tier. The Kotlin app only calls enableEdgeToEdge() in MainActivity (MainActivity.kt:16), which picks status-bar icon color from the app theme — in light mode GoldPriorityScreen gets dark icons over the dark translucent header (Diamond top #6B6B6B and Corporate #6C46C5 + 35% black make them unreadable). No screen-level status-bar handling exists anywhere in the module (no isAppearanceLightStatusBars usage).

**Fix:** In GoldPriorityScreen add a DisposableEffect that grabs WindowCompat.getInsetsController(window, view), stores the current isAppearanceLightStatusBars, sets it to false while the screen is composed, and restores the previous value on dispose (view = LocalView.current, window = (view.context as Activity).window).

---

## [MEDIUM] Packages filter sheet
`app/src/main/java/com/ga/airdrop/feature/shipments/PackagesFilterSheet.kt:211` — Status rows place the status icon at the trailing edge; Swift places it leading (icon, then label)

**Detail:** StatusRow (PackagesFilterSheet.kt:196-216) lays out Text(weight 1f) then Image — icon on the right. Swift makeOptionRow (FigmaPackagesFilterViewController.swift:498-511) pins the icon at leading (20pt inset) with the title 10pt after it, for both method and status rows. Kotlin's MethodRow already does leading-icon correctly, so the two lists inside the same sheet are visibly inconsistent with each other and with Swift.

**Fix:** In StatusRow reorder to Image(24dp) first, then Text with Modifier.weight(1f), matching MethodRow's arrangement and Swift's 20dp leading inset / 10dp gap.

---

## [MEDIUM] Packages filter sheet
`app/src/main/java/com/ga/airdrop/feature/shipments/PackagesFilterSheet.kt:74` — Method rows use inconsistent fonts: Standard row is title2 but SeaDrop and Express rows are title1 (18sp)

**Detail:** PackagesFilterSheet.kt passes labelStyle = AirdropType.title2 for Standard (line 73) but AirdropType.title1 for SeaDrop (line 81) and Express (line 88), so adjacent rows render at 16sp vs 18sp. Swift renders all method rows with Typography.title2() (FigmaPackagesFilterViewController.swift:494, bold=true path).

**Fix:** Pass AirdropType.title2 for all three MethodRow calls (or drop the labelStyle parameter and hardcode title2 inside MethodRow).

---

## [MEDIUM] Package details
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:130` — Details rounded sheet uses gray150 (screen gray150) but Swift's body card is gray100 on a gray200 screen

**Detail:** PackageDetailsScreen.kt:103 sets the screen background to colors.gray150 and line 130 paints the rounded content sheet gray150. Swift truth: view background gray200 (FigmaPackageDetailsViewController.swift:72) and the rounded body card is gray100 (line 110). Because the inner section cards are gray100 with iconShape borders in both apps, on Android they melt into an equally-light sheet in light mode and the tonal hierarchy (gray200 page → gray100 card) is lost; also flips wrong in dark mode.

**Fix:** Set the screen root background to colors.gray200 and the rounded top sheet (line 130) to colors.gray100, keeping section cards gray100 + border as-is per Swift.

---

## [MEDIUM] Package details (Shipment Timeline)
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:286` — Timeline renders metro-style status icons with connector lines; the shipped Swift design renders 10pt status-tinted bullet dots with comment and raw date rows

**Detail:** Kotlin builds MetroStep rows (icon glyph per status + 1dp connector, date formatted "12th Jan, 2024, 3:14pm" in textPlaceholder, comment merged into the same body2 Text — PackageDetailsScreen.kt:286-315, ShipmentsUi.kt:724-768). Swift's shipped screen (FigmaPackageDetailsViewController.swift:441-487, applyDetail:971-990) renders a 10x10 rounded bullet filled with statusColor, the status name in subtitle1 tinted the same color, then optional comment and date as separate body3 textDescription lines. The two renderings look completely different at a glance.

**Fix:** Replace MetroStep usage here with a bullet-row composable: 10dp circle + status name (subtitle1, timelineStatusColor), comment (body3, textDescription) and date (body3, textDescription) as separate lines, 12dp row spacing, no connector — matching FigmaPackageDetailsViewController.makeTimelineRow. Keep MetroStep only if another screen (payment package details) actually uses it in Swift.

---

## [MEDIUM] Package details (Summary/Timeline/Charges cards)
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:263` — Section cards get a gray200 banded header and per-row dividers with subtitle1 values; Swift renders inline titles inside plain gray100 cards, no dividers, title2 values

**Detail:** PackageDetailsContent uses ShipmentsSectionCard (ShipmentsUi.kt:413-451), which draws a full-width gray200 header band with its own border, and ShipmentsListRow (ShipmentsUi.kt:455-482) adds a 1dp divider under every row and styles values as subtitle1 (semibold). Swift's Summary panel (FigmaPackageDetailsViewController.swift:321-398) puts the 'Summary ▾' title INSIDE the gray100 card (16pt padding, small 14pt chevron), rows spaced 10pt with no dividers, and values in Typography.title2() (bold). Same inline-title pattern for 'Shipment Timeline' (402-438) and 'Breakdown of Charges' (748-780). The banded style belongs only to the filter sheet's collapsible cards (FigmaPackagesFilterViewController.swift:404-407).

**Fix:** For the details screen, render section titles inline within the gray100 card (title2, 16dp padding, trailing 14dp chevron on Summary only), drop the gray200 header band and the row dividers, and change value text style from subtitle1 to title2.

---

## [MEDIUM] Package details (charges/total footer)
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:376` — Footer omits the 'Exchange Rate' row, wraps Total in an orange box instead of a plain row, and hides the charges panel entirely when there are no itemized charges

**Detail:** Swift (FigmaPackageDetailsViewController.swift:834-890, 1004-1035) shows, when status >= 7: the Breakdown panel (always, with header row + bold Subtotal even if additionalCharges is empty), then an 'Exchange Rate' key/value row ('1 USD = 161.00 JMD', title2 dark) and a 'Total' row (title2 key dark / value orange, right-aligned) followed by the CTA — no orange background box. Kotlin (PackageDetailsScreen.kt:376-381) skips ChargesCard when charges are empty and total is 0 (losing the exchange-rate note that only lives inside ChargesCard at line 577), and renders Total via TotalChargesBox (orange OrangeTertiary6 pill, ShipmentsUi.kt:773-794) which is the Payment-details Figma component (40001464:31296), not this screen's design.

**Fix:** When readyForPickup: always render the Breakdown card (header + Subtotal) per Swift; below it add an 'Exchange Rate' ShipmentsListRow-style pair and a plain Total row (title2, orange value 'USD x / JMD y') instead of TotalChargesBox, then the GradientButton.

---

## [MEDIUM] Payments
`app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt:642` — Invoice download button sits bottom-right beside Amount; Swift places it in the card's top-right corner, smaller and gray

**Detail:** Swift (FigmaPaymentsViewController.swift:328-355): download button pinned top-right (trailing -16, top 14, 28pt hit area, 22pt DownloadFile glyph tinted textDescription), with the key/value rows inset 52pt on the right to clear it. Kotlin renders the icon in the Amount row at the card bottom (ShipmentsUi.kt:634-651), 24dp tinted iconSelected.

**Fix:** Move the download control to a top-right overlay of PaymentCard (Box alignment TopEnd, ~14dp top / 16dp end offsets), size the glyph 22dp, tint colors.textDescription, and keep the busy spinner swap in place.

---

## [MEDIUM] Payments / Orders
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentsScreen.kt:88` — No pull-to-refresh on either list; Swift attaches a UIRefreshControl (orange tint) that resets to page 1

**Detail:** FigmaPaymentsViewController.swift:72-74 and FigmaOrdersViewController.swift:72-74 wire refreshControl → loadPayments/loadOrders(reset: true). Kotlin PaymentsScreen/OrdersScreen expose viewModel.refresh() but nothing in the UI triggers it — swipe-down does nothing.

**Fix:** Wrap both LazyColumns in a PullToRefreshBox (material3) with indicator color BrandPalette.OrangeMain calling viewModel.refresh(); drive isRefreshing from state.loading.

---

## [MEDIUM] PaymentPackageDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:240` — 'View History' button scrolls with the content; Swift pins it in a fixed 96pt footer with a top divider

**Detail:** FigmaPaymentPackageDetailsViewController.swift:262-300 builds a fixed footer (gray100 bg, 1px gray300 divider on top, 50pt outline button inset 20pt, scrollView bottom anchored to footer top). Kotlin places OutlineButton inside the vertical scroll after TotalChargesBox (PaymentPackageDetailsScreen.kt:240), so on long content the primary action is off-screen.

**Fix:** Restructure the screen as a Column: scrollable content in weight(1f), then an opaque gray100 footer (divider + OutlineButton, 20dp padding) that is always visible.

---

## [MEDIUM] PaymentPackageDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:144` — Row label 'Invoice Amount' truncates Swift's 'Invoice Amount (Declared Value/Cost)'

**Detail:** FigmaPaymentPackageDetailsViewController.swift:794 labels the row "Invoice Amount (Declared Value/Cost)" (mirrors RN SummaryDetailsPaymentPackages). Kotlin shows just "Invoice Amount" (PaymentPackageDetailsScreen.kt:143-150).

**Fix:** Change the label string to "Invoice Amount (Declared Value/Cost)".

---

## [MEDIUM] InvoiceViewer
`app/src/main/java/com/ga/airdrop/feature/shipments/InvoiceViewerScreen.kt:98` — Page/content surface colors are inverted versus Swift (page should be gray100, preview panel gray150)

**Detail:** Swift: view.backgroundColor = gray100 (FigmaInvoiceViewerScreenViewController.swift:68) and contentContainer.backgroundColor = gray150 with iconShape border (lines 86-89). Kotlin: Column background colors.gray150 (line 98) and inner Box background colors.gray100 (line 108) — swapped in both light and dark modes.

**Fix:** Swap the two tokens: root Column → colors.gray100, preview Box → colors.gray150.

---

## [MEDIUM] InvoiceViewer
`app/src/main/java/com/ga/airdrop/feature/shipments/InvoiceViewerScreen.kt:346` — 'Share' sends the invoice URL as plain text; Swift shares the actual downloaded file

**Detail:** Swift downloads the invoice to cache and shares the file itself via UIActivityViewController (FigmaInvoiceViewerScreenViewController.swift:383-391), letting the user AirDrop/save the PDF. Kotlin shareInvoice() (InvoiceViewerScreen.kt:346-355) fires ACTION_SEND type text/plain with the raw URL — recipients get a link (possibly an auth-protected or file:/ URL that they cannot open), not the document.

**Fix:** For remote URLs download to cacheDir first (or reuse the WebView-downloaded file), then share a FileProvider content:// URI with type application/pdf (or image/*) and FLAG_GRANT_READ_URI_PERMISSION; for file:// sources share the FileProvider URI directly.

---

## [MEDIUM] PaymentPackageDetails › View History timeline
`app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt:755` — Timeline connector is always the gray divider color; Swift colors the connector with the step's status color, and the missing-date fallback/date font also drift

**Detail:** Swift timeline row (FigmaPaymentPackageDetailsViewController.swift:1164-1166) sets line.backgroundColor = color — the same green/orange/placeholder color as the row title — so completed segments read as a colored progress spine. Kotlin MetroStep hardcodes colors.divider for the connector (ShipmentsUi.kt:750-757). Additionally Swift shows "-" for stops without a history date (line 1232-1238) while Kotlin shows "N/A" (PaymentPackageDetailsScreen.kt:318), and Swift renders the date in body3 (12pt, line 1178) while MetroStep uses body2 (14pt, ShipmentsUi.kt:765).

**Fix:** Add a connectorColor (default titleColor) parameter to MetroStep and pass the step color from PaymentShipmentTimeline; change the fallback string to "-"; use AirdropType.body3 for the date label.

---

## [MEDIUM] OrderDetails / ProductPaymentDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/OrderDetailsScreen.kt:62` — Hero product image is a fixed 245x149dp box; Swift renders a full-width image in a 209pt (OrderDetails, 20pt insets) / 219pt (ProductPaymentDetails, 30pt insets) panel

**Detail:** Swift OrderDetails: imageWrap height 209 with 20pt insets on all sides → image ~169pt tall spanning screen width minus 40 (FigmaOrderDetailsViewController.swift:103-109). Swift ProductPaymentDetails: wrap height 219 with 30pt insets → ~159pt tall, width minus 60 (FigmaProductPaymentDetailsViewController.swift:118-124). Kotlin uses Modifier.size(245.dp, 149.dp) inside a Spacing.lg(30dp)-padded Box on both screens (OrderDetailsScreen.kt:61-82, ProductPaymentDetailsScreen.kt:64-85) — narrower and shorter than iOS on typical devices, and the padding is wrong (20 vs 30) on OrderDetails.

**Fix:** OrderDetails: Box padding 20.dp, image fillMaxWidth().height(169.dp). ProductPaymentDetails: Box padding 30.dp, image fillMaxWidth().height(159.dp). Keep ContentScale.Fit.

---

## [MEDIUM] PaymentPackageDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:176` — CIF pill is 59dp tall with a 24dp info icon; Swift is 48pt with a 20pt icon

**Detail:** FigmaPaymentPackageDetailsViewController.swift:479 sets the pill height to 48 and the infoCircle to 20pt tinted textDarkTitle (lines 487, 500-501). Kotlin uses .height(59.dp) and a 24dp ic_info tinted colors.iconSelected (PaymentPackageDetailsScreen.kt:173-192).

**Fix:** Change the Row height to 48.dp, icon size to 20.dp and tint to colors.textDarkTitle.

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

## [MEDIUM] Documents
`app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt:221` — The Download|Upload action bar is a flush edge-to-edge card footer with a full-height divider; Swift renders it as an inset, bordered, radius-10, 48pt split row inside the card padding.

**Detail:** FigmaDocumentsViewController.makeActionsRow (FigmaDocumentsViewController.swift:422-473): the actions row is its own view with cornerRadius 10, 1pt iconShape border, height 48, added INSIDE the card's 16pt-padded content stack; the vertical divider is inset 8pt top/bottom. Kotlin instead draws a full-width divider (colors.divider) across the card and a 54dp Row spanning the card edge-to-edge with a full-height 1dp separator (lines 220-248), which visibly changes the card design.

**Fix:** Move the split actions Row inside the padded content Column, give it Modifier.height(48.dp).border(1.dp, colors.iconShape, RoundedCornerShape(Radius.xs)).clip(...), make the center divider 1.dp wide with 8.dp vertical padding, and delete the edge-to-edge top divider.

---

## [MEDIUM] Documents
`app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt:173` — Card and uploaded-file-row geometry drifts from Swift: card radius 10 vs 15, card gap 20dp vs 12pt, file row has an extra border and oversized trash/eye icons (24dp spaced 30dp vs 18pt spaced ~6pt).

**Detail:** Swift: card cornerRadius 15 (FigmaDocumentsViewController.swift:252), listStack.spacing 12 (line 131); file row is peachLight, radius 8, height 56, NO border, pdf icon 28pt, name = body2, size line = body3 textDescription, trash/eye are 18pt glyphs in 28pt buttons 6pt apart (lines 330-419). Kotlin: card RoundedCornerShape(Radius.xs=10), verticalArrangement spacedBy(Spacing.md=20) (line 106), file row has 1dp iconShape border + radius 10, name = subtitle2, size line = textPlaceholder, trash/eye 24dp spaced Spacing.lg=30dp.

**Fix:** Card: RoundedCornerShape(Radius.s=15); list spacing 12.dp. UploadedFileRow: drop the border, radius 8.dp, height 56.dp, name AirdropType.body2, size text colors.textDescription, trash/eye 18.dp glyphs with small (6-8dp) gap.

---

## [MEDIUM] Edit Profile
`app/src/main/java/com/ga/airdrop/feature/more/ProfileScreen.kt:289` — Avatar geometry drifts from Swift: 107dp circle with gray200 fill and iconShape ring + 28dp badge, vs Swift's 80pt gray300 circle (no ring) with a 24pt edit badge tucked at bottom-right +2pt.

**Detail:** FigmaProfileViewController.makeAvatar (FigmaProfileViewController.swift:295-365): circle width/height 80, cornerRadius 40, backgroundColor gray300, no border; edit badge constrained to 24x24 at trailing/bottom +2; wrap height 88; placeholder glyph 44pt tinted gray500. Kotlin renders a 107dp circle, gray200 fill, 1dp iconShape border, and a 28dp badge offset (79,79).

**Fix:** Size the avatar Box 80.dp (radius 40), fill colors.gray300, drop the border, badge 24.dp positioned bottom-end with 2.dp outset, container height 88.dp.

---

## [MEDIUM] Preferences
`app/src/main/java/com/ga/airdrop/feature/more/MoreComponents.kt:248` — MoreSelectField styling drifts from Swift's SelectableRow: label 16sp vs 14pt, box gray150 vs gray100, radius 10 vs 12, min-height 50 vs 52, chevron 24dp vs 12pt.

**Detail:** Swift SelectableRow (FigmaPreferencesViewController.swift): titleLabel.font = Typography.subtitle2() (14pt SemiBold, line 478); editable card backgroundColor = gray100 with cornerRadius 12 (lines 483-484); card.heightAnchor >= 52 (line 512); trailing chevron is a 12x12 FigmaIcon.chevronDown tinted textDarkTitle (lines 520-521, 542-544); disabled value text uses textPlaceholder (line 537). Kotlin MoreSelectField uses AirdropType.subtitle1 (16sp) for the label (line 248), gray150 background with Radius.xs=10 (lines 258-260), defaultMinSize 50dp (line 257), a 24dp ic_small_arrow_down tinted iconSelected (lines 281-287), and textDescription for disabled values (line 274). Each drift is small but together the field block reads noticeably different from iOS, especially the oversized chevron and heavier label.

**Fix:** In MoreSelectField: change the label style to AirdropType.subtitle2, use colors.gray100 for the enabled background, RoundedCornerShape(12.dp), minHeight 52.dp, shrink the trailing chevron to 12.dp tinted colors.textDarkTitle, and use colors.textPlaceholder for the disabled value color.

---

## [MEDIUM] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsScreen.kt:80` — Row icons use wrong glyphs/duotone color assignments versus Swift: master bell should be solid iconSelected, Email envelope body should be orange with dark flap, Push bell should be solid orange.

**Detail:** Swift (FigmaNotificationSettingsViewController.swift): master row uses FigmaIcon.bell(primary: orangeMain, secondary: iconSelected) and FigmaIcon_Bell paints ALL paths with `secondary` (FigmaIcons.swift lines 634-641), so the master bell renders entirely iconSelected (dark/white) — and it is the plain bell glyph, not a bell-with-sound-waves. Email rows use mail(primary: iconSelected, secondary: orangeMain) — FigmaIcon_Mail paints the flap with primary (dark) and the envelope body with secondary (orange) (FigmaIcons.swift lines 1011-1016). Push rows use bell(primary: iconSelected, secondary: orangeMain) → fully ORANGE bell. Kotlin instead uses: ic_settings_notifications (bell-with-waves, orange accent strokes + duotone body) for the master row; ic_mail whose flap is orange and body duotone (inverted vs Swift); ic_notifications whose every stroke is @color/icon_duotone (no orange at all) for Push. Only the SMS chat icon (orange dots, duotone bubble) matches Swift. Users comparing the two apps see three of the four icon styles wrong in both light and dark mode.

**Fix:** Add screen-specific drawables (or color variants): master row = plain bell fully @color/icon_duotone (reuse ic_notifications as-is); Email rows = envelope with body #F15114 and flap @color/icon_duotone (invert ic_mail's colors); Push rows = plain bell fully #F15114. Then point the ToggleRow iconRes at those variants (lines 80, 96/137, 118/159).

---

## [MEDIUM] Invite Friend
`app/src/main/java/com/ga/airdrop/feature/more2/InviteFriendScreen.kt:129` — Contacts row icon is tinted solid orange, flattening the duotone glyph; Swift renders orange signal waves with a dark handset.

**Detail:** InviteFriendScreen applies colorFilter = ColorFilter.tint(BrandPalette.OrangeMain) to ic_contact_number. The drawable is already correctly duotone (waves #F15114, handset @color/icon_duotone), so the tint overwrites the handset to orange as well. Swift uses FigmaIcon.contactNumber(primary: orangeMain, secondary: iconSelected) (FigmaInviteFriendViewController.swift lines 364-366), and FigmaIcon_ContactNumber paints the waves with primary (orange) and the handset with secondary (iconSelected/dark) (FigmaIcons.swift lines 968-973). The solid-orange tint also breaks dark mode, where the handset should flip to white via icon_duotone.

**Fix:** Remove the colorFilter argument from the Contacts row Image so the duotone drawable renders with its own colors (orange waves + theme-aware handset).

---

## [MEDIUM] Terms & Conditions / Privacy Policy (live CMS content)
`app/src/main/java/com/ga/airdrop/feature/more2/LegalContent.kt:113` — Live CMS legal content renders ALL text — including headings — in textDescription gray; Swift colors headings textDarkTitle and only body textDescription.

**Detail:** LegalHtmlContent sets a single `view.setTextColor(bodyColor)` where bodyColor = colors.textDescription (LegalContent.kt:94,113), so h1–h4 headings produced by markdownToHtml()/CMS HTML render in the muted description gray in both light and dark mode. Swift walks the parsed attributed string and assigns per-element dynamic colors: any run with pointSize > 15 (headings) gets DesignTokens.Color.textDarkTitle, body runs get textDescription — FigmaTermsConditionsViewController.swift:554-566 and FigmaPrivacyPolicyViewController.swift:436-444 (applyDynamicColors). Once /content/terms-conditions or /content/privacy-policy returns real markdown/HTML with headings, the Android page loses its heading contrast versus iOS (near-black headings in light mode, white in dark mode). Link color is already correct (OrangeMain == Swift orangeDark == #F15114).

**Fix:** After HtmlCompat.fromHtml(), post-process the Spanned instead of relying on the TextView-wide color: copy it to a SpannableStringBuilder, enumerate RelativeSizeSpan (HtmlCompat emits these for h1–h6; proportion > 1.0) and apply ForegroundColorSpan(colors.textDarkTitle.toArgb()) over those ranges, leaving everything else at textDescription. Keep link recoloring via setLinkTextColor as-is. Recompute when the theme changes (key the remember on html + isDark).

---

## [LOW] Package details (invoice file rows)
`app/src/main/java/com/ga/airdrop/feature/shipments/PackageDetailsScreen.kt:508` — Invoice row action icons are ordered eye-then-trash; Swift orders trash-then-eye

**Detail:** Kotlin InvoiceFileRow renders ic_eye then ic_trash (PackageDetailsScreen.kt:508-523). Swift stacks [icon, text, spacer, trashBtn, viewBtn] (FigmaPackageDetailsViewController.swift:687), i.e. trash first, eye at the far trailing edge.

**Fix:** Swap the two trailing Images so the delete (trash) icon precedes the view (eye) icon.

---

## [LOW] Orders
`app/src/main/java/com/ga/airdrop/feature/shipments/OrdersScreen.kt:90` — Orders header is missing the trailing rounded-square ellipsis accessory that Swift renders for Figma parity

**Detail:** FigmaOrdersViewController.swift:154-181 draws the same more-square icon as Payments in the header trailing slot (decorative, no action — comment: 'Figma renders the same trailing accessory for visual parity'). Kotlin OrdersScreen calls ShipmentsDetailHeader without rightIconRes, leaving an empty spacer.

**Fix:** Pass rightIconRes = R.drawable.ic_shipments_more_square (no-op onRightClick) to ShipmentsDetailHeader in OrdersScreen.

---

## [LOW] ProductPaymentDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/ProductPaymentDetailsScreen.kt:96` — Section titles 'Product Summary'/'Payment Summary' overridden to subtitle1; Swift uses title2 (Bold)

**Detail:** FigmaProductPaymentDetailsViewController.swift:288 and :370 set barTitle.font = Typography.title2(). Kotlin passes titleStyle = AirdropType.subtitle1 to both ShipmentsSectionCard calls (lines 96 and 126), diverging from every other section card that keeps the title2 default.

**Fix:** Remove the titleStyle overrides so the cards use the default AirdropType.title2.

---

## [LOW] PaymentPackageDetails › View History timeline
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:265` — Timeline stop 18 labelled 'Paid and Ready for Pickup'; Swift timeline says 'Paid and Ready for Pick Up'

**Detail:** FigmaPaymentShipmentTimelineViewController timelineOrder (FigmaPaymentPackageDetailsViewController.swift:977) uses "Paid and Ready for Pick Up" (with space). Kotlin timelineStops uses "Paid and Ready for Pickup".

**Fix:** Change the label string for id 18 to "Paid and Ready for Pick Up".

---

## [LOW] PaymentPackageDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentPackageDetailsScreen.kt:155` — Package-summary Status value uses title2 (Bold); Swift renders all summary values, including Status, in subtitle1

**Detail:** Swift makeSummaryRow (FigmaPaymentPackageDetailsViewController.swift:671) uses Typography.subtitle1() for every value; the Status row only changes color to Alert.completed (line 795). Kotlin passes valueStyle = AirdropType.title2 for the Status row (PaymentPackageDetailsScreen.kt:151-157).

**Fix:** Drop the valueStyle override on the Status ShipmentsListRow (keep valueColor = AlertPalette.Completed).

---

## [LOW] All section cards in this group
`app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt:432` — Section-card header row draws a full 4-sided border inside the already-bordered card, doubling the outline; Swift has only a 1px divider under the bar

**Detail:** ShipmentsUi.kt:432 applies .border(1.dp, colors.iconShape) to the gray200 header Row, adding visible extra lines at the card's top and sides on top of the outer card border. Swift cards (e.g. FigmaOrderDetailsViewController.swift:310-333) draw a single gray300 divider between bar and body only.

**Fix:** Remove the border on the header Row and add a 1dp divider Box (colors.divider) between the header and content().

---

## [LOW] PaymentPackageDetails / ProductPaymentDetails
`app/src/main/java/com/ga/airdrop/feature/shipments/ShipmentsUi.kt:198` — 'Amount Paid'/Total strings use comma-grouped numbers and render zero amounts; Swift prints ungrouped %.2f and shows '-' when the amount is 0

**Detail:** Swift: String(format: "USD %.2f / JMD %.2f") (FigmaPaymentPackageDetailsViewController.swift:812, FigmaProductPaymentDetailsViewController.swift:592) → "JMD 64841.58" without thousands separators, and ProductPaymentDetails guards `usd > 0 else "-"` (lines 587-593, 617-623). Kotlin ShipmentsFormat.usdJmd groups with commas and formats any non-null value including 0.0. (Note OrderDetails is the opposite — Swift explicitly comma-groups there, which Kotlin already matches.)

**Fix:** For the two payment-details screens use an ungrouped two-decimal formatter for the USD/JMD pair, and in ProductPaymentDetails return "-" when the resolved amount is null or <= 0.

---

## [LOW] Payments
`app/src/main/java/com/ga/airdrop/feature/shipments/PaymentsScreen.kt:142` — List fetch failures pop a modal alert; Swift fails silently to the empty state (alerts only for invoice-download failures)

**Detail:** Kotlin surfaces every repo error (including pagination/search failures) as a ShipmentsAlertDialog titled 'Payments' (PaymentsScreen.kt:142-150 wired to state.error set in PaymentsViewModel.load onFailure). Swift only prints list-fetch errors and shows 'No payments found' (FigmaPaymentsViewController.swift:563-571); the only alert is 'Download failed' for the invoice action (lines 393-400). A flaky connection on Android interrupts the user with modals iOS never shows.

**Fix:** Only set state.error from downloadInvoice (title it 'Download failed'); on list-load failure just clear loading flags and let the empty label show, matching Swift.

---

## [LOW] Shop root — Feature Products row
`app/src/main/java/com/ga/airdrop/feature/shop/ShopScreen.kt:134` — The featured-row empty card is rendered full-width; Swift renders a fixed 240pt-wide card inside the horizontal row.

**Detail:** Swift: row.addArrangedSubview(makeEmptyCard(text: "No featured products", fixedWidth: 240)) (FigmaShopViewController.swift:490). Kotlin ShopEmptyCard fills max width (ShopScreen.kt:181-198).

**Fix:** Pass a width modifier: ShopEmptyCard(text = "No featured products", modifier = Modifier.width(240.dp)) for the featured section (auction section stays full-width, matching Swift's grid empty card).

---

## [LOW] Product details
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:495` — Description markdown '**bold**' spans are stripped to plain text; Swift renders them as bold Cairo-Bold spans.

**Detail:** cleanDescription does .replace("**", "") (AuctionProductDetailsScreen.kt:489-497). Swift's renderProductDescription parses \*\*(.+?)\*\* and applies Cairo-Bold to the inner text (FigmaAuctionProductDetailsViewController.swift:840-891) — this was an explicit user-flagged fix on iOS.

**Fix:** Build an AnnotatedString: split on the same regex and apply SpanStyle(fontWeight = FontWeight.Bold) to matched inner spans instead of deleting the markers.

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

## [LOW] Product details
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:403` — Related Products section disappears entirely when the related fetch fails or returns empty; Swift always renders the section (with two placeholder cards) in auction mode.

**Detail:** Kotlin gates on `!featured && related.isNotEmpty()` (AuctionProductDetailsScreen.kt:403); Swift unconditionally adds buildRelatedHeader() + buildRelatedRow() for mode == .auction (FigmaAuctionProductDetailsViewController.swift:238-241), rendering 2 skeleton cards (:653-656). Android's use of real shortlist data is an improvement worth keeping, but the section should not vanish.

**Fix:** Render the Related Products header + two ShopSkeletonCard placeholders when `related` is empty in auction mode, keeping the real-data path when it loads.

---

## [LOW] Product details (featured mode)
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:158` — 'Purchase Product' silently does nothing when the Amazon URL is missing; Swift shows a 'Product link unavailable' alert.

**Detail:** Kotlin: `if (raw.isNotEmpty()) { ... }` with no else branch (AuctionProductDetailsScreen.kt:157-165). Swift presents UIAlertController(title: "Product link unavailable", message: "No purchase link was returned for this feature product.") for empty/invalid URLs (FigmaAuctionProductDetailsViewController.swift:817-831). A dead-feeling button is user-visible.

**Fix:** Add an else branch that surfaces the same alert (reuse the existing AlertDialog state pattern, e.g. an errorTitle/errorMessage in ProductDetailsUiState).

---

## [LOW] Auction Product Details
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionProductDetailsScreen.kt:489` — Description drops **bold** emphasis (markers stripped, no bold span) and null-description fallback copy differs from Swift

**Detail:** Swift renderProductDescription (FigmaAuctionProductDetailsViewController.swift:840-891) converts **...** runs to Cairo-Bold spans; Kotlin cleanDescription just removes the asterisks so emphasis is lost. Swift's nil-description fallback is the long 'Detailed product description will be loaded…' sentence (line 574); Kotlin shows 'No description available.'

**Fix:** Build an AnnotatedString in cleanDescription: parse \*\*(.+?)\*\* and apply SpanStyle(fontWeight = FontWeight.Bold); align the fallback string with Swift's copy.

---

## [LOW] Auction Checkout
`app/src/main/java/com/ga/airdrop/feature/shop/AuctionCheckoutScreen.kt:109` — Hero placeholder is the Airdrop logo instead of Swift's gift emoji, no image-error fallback, and the unauthenticated error isn't special-cased

**Detail:** Swift shows an 80pt '🎁' label as the hero placeholder and re-shows it when the image load fails (FigmaAuctionProductCheckoutViewController.swift:267-285). Kotlin shows img_airdrop_logo and has no onError handling on the AsyncImage. Swift also maps APIError.unauthenticated to a 'Sign in required' alert (lines 488-494); Kotlin surfaces every failure as generic 'Checkout failed'.

**Fix:** Use an 80sp '🎁' Text placeholder, add AsyncImage onError to swap back to it, and map the repository's 401/unauthenticated failure to title 'Sign in required' / message 'Log in to your Airdropja account before checking out.'

---

## [LOW] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsViewModel.kt:104` — Enabling a Push toggle never re-registers the FCM device token with the backend, which Swift does on every sync when push is wanted.

**Detail:** Swift syncToBackend (FigmaNotificationSettingsViewController.swift:446-456) calls AirdropAPI.shared.registerFCMToken(deviceToken:deviceType:deviceInfo:) when pushWanted and a stored token exists. The Kotlin app has the plumbing (MiscRepository.registerFcmToken, used by AirdropMessagingService.onNewToken) but NotificationSettingsViewModel.syncToBackend only PUTs the three profile flags; if the token was issued before login (onNewToken skips when unauthenticated), enabling Push here never registers it.

**Fix:** In syncToBackend(), when push is enabled and a cached FCM token exists (FirebaseMessaging.getInstance().token or a stored copy), call MiscRepository.registerFcmToken(token, "android") best-effort.

---

## [LOW] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsScreen.kt:187` — Row heights and section gaps drift from Swift: all rows are 59dp with 10dp base spacing and ~30dp section gaps; Swift uses 60pt master/section rows, 56pt sub rows, 12pt base spacing, 20pt section gaps.

**Detail:** FigmaNotificationSettingsViewController.makeRow sets card height = style == .sub ? 56 : 60 (line 282), stack.spacing 12 (line 154), setCustomSpacing(20) after the master row and after packagePush (lines 167, 187). Kotlin ToggleRow is uniformly height(59.dp), Column spacedBy(Spacing.sm=10), and the extra Spacer(Modifier.height(Spacing.sm)) rows produce ~30dp section gaps.

**Fix:** Use 60.dp for the master/section rows, 56.dp for sub rows, spacedBy(12.dp), and replace the Spacer rows so section gaps total 20.dp.

---

## [LOW] Documents
`app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt:135` — Info dialog confirm button says 'OK'; Swift uses 'Got it' for the document info alert.

**Detail:** FigmaDocumentsViewController.onInfoTapped (FigmaDocumentsViewController.swift:550-555) adds UIAlertAction(title: "Got it"). Kotlin reuses MoreAlertDialog whose confirm label is hard-coded 'OK'.

**Fix:** Add a confirmLabel parameter (default "OK") to MoreAlertDialog and pass "Got it" for the info dialog.

---

## [LOW] Documents
`app/src/main/java/com/ga/airdrop/feature/more/DocumentsScreen.kt:103` — No pull-to-refresh and no reload when returning to the screen; Swift attaches a UIRefreshControl and reloads documents on every viewDidAppear.

**Detail:** FigmaDocumentsViewController sets scrollView.refreshControl (lines 124-126) and calls loadDocuments() from viewDidAppear (line 112). Kotlin DocumentsViewModel loads once in init and only reloads after its own upload/delete; documents changed elsewhere (or a failed first load) require leaving and recreating the screen.

**Fix:** Wrap the scroll column in a PullToRefresh container calling viewModel.load(), and/or trigger load() from a LifecycleResumeEffect.

---

## [LOW] Edit Profile
`app/src/main/java/com/ga/airdrop/feature/more/ProfileScreen.kt:347` — Date-of-birth picker allows future dates; Swift caps the DOB wheel at today.

**Detail:** FigmaProfileViewController sets dobPicker.maximumDate = Date() (FigmaProfileViewController.swift:233). Kotlin's rememberDatePickerState() has no bound, so users can pick a future birthday.

**Fix:** Create the state with rememberDatePickerState(selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis() }).

---

## [LOW] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsScreen.kt:187` — Row heights and inter-row spacing drift: all rows 59dp with 10dp gaps vs Swift 60pt (master/section) and 56pt (sub) rows with 12pt gaps.

**Detail:** Swift makeRow sets card height 60 for master/section styles and 56 for sub rows (FigmaNotificationSettingsViewController.swift line 282), with stack spacing 12 and custom 20pt spacing after the master row and after the Package Push row (lines 154, 167, 187). Kotlin ToggleRow is a uniform 59.dp (line 187) and the Column uses Arrangement.spacedBy(Spacing.sm = 10.dp) (line 75); the section breaks come out at 20dp via extra Spacers (correct) but every other gap is 10dp instead of 12dp.

**Fix:** Make ToggleRow height style-dependent (60.dp for master/section, 56.dp for sub rows) and change the Column arrangement to Arrangement.spacedBy(12.dp), adjusting the section-break Spacers to 8.dp so the breaks stay at 20dp.

---

## [LOW] Notification Settings
`app/src/main/java/com/ga/airdrop/feature/more/NotificationSettingsViewModel.kt:104` — Push-enable never (re)registers the FCM device token, and the RECONCILE comment claiming 'no FCM stack in the Android app yet' is stale.

**Detail:** Swift syncToBackend registers the FCM token with device info whenever any push toggle is enabled (FigmaNotificationSettingsViewController.swift lines 449-456: registerFCMToken(deviceToken:deviceType:deviceInfo:) guarded by pushWanted). The Android app DOES have an FCM stack now — core/push/AirdropMessagingService.kt calls MiscRepository.registerFcmToken on onNewToken, and MiscRepository.kt:128 exposes the endpoint — but NotificationSettingsViewModel.syncToBackend only PUTs the three profile flags and the class doc still says FCM is absent. If the initial registration failed or the user re-enables push after the server pruned the token, iOS self-heals and Android does not.

**Fix:** In syncToBackend, compute pushWanted = master && (packagePush || promosPush); when true, fetch FirebaseMessaging.getInstance().token and call MiscRepository.registerFcmToken(token, "android", deviceInfo) best-effort. Delete the stale RECONCILE comment.

---

## [LOW] FAQs
`app/src/main/java/com/ga/airdrop/feature/more2/LegalContent.kt:146` — FAQ accordion question→chevron gap is 5dp (Spacing.xs) but Swift FAQ uses 10pt (Spacing.sm).

**Detail:** The shared AccordionCard pads the title with `end = Spacing.xs` (5dp, LegalContent.kt:146). That matches Terms (FigmaTermsConditionsViewController.swift:313, -Spacing.xs) and Privacy (FigmaPrivacyPolicyViewController.swift:278, -Spacing.xs), but the FAQ screen in Swift uses a 10pt gap: FigmaFAQViewController.swift:282 `questionLabel.trailingAnchor.constraint(equalTo: chevron.leadingAnchor, constant: -DesignTokens.Spacing.sm)`. Long questions therefore wrap 5dp later on Android FAQ than on iOS.

**Fix:** Add a `titleEndGap: Dp = Spacing.xs` parameter to AccordionCard and pass Spacing.sm from FaqScreen.kt:51 (keep the default for Terms/Privacy).

---
