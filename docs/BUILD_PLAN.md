# Airdrop Android (Kotlin) — Build Plan & Audit

> Agent: **BlueDeer** (Claude, Fable 5) · ORC room registered · Date: 2026-07-03
> Repo: `KOTLIN_APP` · Target: Play Store-ready native Android app

## 1. Source-of-truth mapping

| Priority | Source | Location | Used for |
|---|---|---|---|
| 1 | **Figma** "Revamping-AirDrop-App" (`N4k6jzpeLZgeRS5O1xfyIv`) | Figma remote MCP | Pixel-perfect visuals: layout, spacing, colors, type, icons. Screen canvas "UID — App" (node `40000002:83125`); per-screen node map below. |
| 2 | **Swift iOS app** | `../SWIFT_APP` | Behavior truth: 52-screen inventory, navigation, flows, API wiring, DesignTokens.swift, APIEndpoints.swift, route resolver, known gaps. |
| 3 | **React Native app** (old) | `../AD-REACT_NATIVE_APP-OLD` | Reference: route structure, Android identity (`com.ga.airdrop.app`), Cairo fonts, assets, behaviors Swift missed (debounce, logout hygiene). |
| 4 | **Laravel backend** | `../AIRDROP-LARAVEL` | Contract: `/api/v1` (Sanctum bearer), 60+ endpoints, models, envs (prod `app.airdropja.com`, pre-staging `pre-staging.airdropja.com`). |

Conflict rule update (2026-07-05): Swift is the precedence guide whenever
Swift and Figma disagree. Figma remains the pixel-measurement source where
Swift has no designed element or the two agree; RN fills Swift gaps; Laravel
wins data contract. Document every Swift/Figma conflict instead of silently
choosing Figma.

## 2. Screen inventory (52 screens) with Figma nodes

### Auth
| Screen | Figma node | Behavior source |
|---|---|---|
| Login | `40006240:23928` (light) / `40006149:75728` (dark) | FigmaLoginViewController |
| Sign Up | web/storyboard legacy | SignUpViewController (+ RN SignUpView) |
| Forgot Password | legacy | ForgotViewController (+ RN) |

### Tab roots (custom bottom nav, no system tab bar)
| Screen | Figma node | Behavior source |
|---|---|---|
| Home | `40000710:5347` / dark `40000710:5667` | FigmaHomeViewController |
| Shipments | `40000823:9633` | FigmaShipmentsViewController |
| Shop | `40001846:53519` | FigmaShopViewController |
| Contacts/Help | `40001617:20377` | FigmaContactsViewController |
| More | `40001948:22354` | FigmaMoreViewController |

### Home drill-downs
Warehouses `40000944:3571/3698` · Services `40000798:7711` · Sales Taxes `40001531:11704` · Gold Priority `40001432:23506` · Notifications `40007174:63447` · AirCoin History `40001911:22972/23111`

### Shipments drill-downs
Packages `40001666:42198` · Packages Filter `40006358:75618` · Package Details `40001753:15716` · Payments `40001753:18909` · Payment Package Details `40001761:29389` · Product Payment Details `40004950:25064` · Orders `40001753:19595` · Order Details `40001761:28814` · Invoice Viewer (PDF modal)

### Shop drill-downs
Auction `40001846:54117` · Auction Product Details `40002072:24025` · Auction Checkout `40001846:54756` · Featured Products `40001846:54396` · Cart `40008284:26547`

### More drill-downs
Settings `40007388:24260` · Profile `40007189:63763` · Documents `40000975:7748` · Document Downloading (modal) · Notification Settings `40001587:18074` · Backgrounds `40006644:65735` · Refer a Friend `40001940:26885` · Invite Friend `40001940:26797` · Promotions `40001646:14035` · Preferences `40000994:19044` · Authorized Users `40000975:7859` · Add Authorized User `40001541:45296` · Authorized User Detail `40001185:5345` · Account Deletion `40007388:24881` · Deletion Reason `40007388:27504` · Shipping Rates `40001567:54206` · Terms `40001383:9894` · Privacy `40001387:9042` · FAQ `40001387:8896` · Restricted Items `40001432:14025` (+ info)

### Modals / tools
Calculator `40001464:29102/30381/30723` · Calculator Results `40001817:19439/20391/20537` · Drop Alert `40001826:22497`+`40001836:22971`

### Swift gaps we will NOT repeat (RN has them; wire correctly on Android)
1. Authorized-user **edit form** (reuse Add form in edit mode)
2. Profile **avatar upload/delete** (image picker + camera)
3. **Notifications feed** wired live (not hard-coded empty state)
4. **Payment invoice download** from payments list
5. Drop-alert **typed multipart** submit; `/package-statuses` for filter chips
6. Shop search **500 ms debounce**; full logout hygiene (clear FCM token, stores)

## 3. Architecture plan

- **Kotlin 2.x + Jetpack Compose**, single-activity, Material3-free custom design system (Figma tokens), light/dark themes.
- **Modules (packages, single Gradle module `app` for now):**
  - `core/designsystem` — colors (dynamic light/dark), Cairo type scale, spacing/radius, icons, shared components (header "Header Type", bottom "Nav Bar Menu", buttons, cards, status chips)
  - `core/network` — Retrofit + OkHttp + kotlinx-serialization (lenient: Laravel returns numbers-as-strings), auth interceptor (Sanctum bearer), 401 → logout
  - `core/auth` — EncryptedSharedPreferences token store (Keychain equivalent)
  - `core/navigation` — route resolver mirroring `FigmaRouteResolver` (route string + referenceID deep links from push)
  - `data` — API service + models ported from Swift Codable structs / Laravel resources; repositories
  - `feature/{auth,home,shipments,shop,contacts,more,calculator,dropalert,cart}` — screens + ViewModels (MVVM, StateFlow)
- **Config:** `prod` → `https://app.airdropja.com/api/v1`; `staging` → pre-staging; buildConfig flavors `prod`/`staging`.
- **Identity:** `applicationId com.ga.airdrop.app` (matches existing RN Android/Firebase identity), minSdk 26, target/compile 35.
- **Libraries:** Retrofit, OkHttp, kotlinx-serialization, Coil (images), Navigation-Compose, DataStore/EncryptedSharedPreferences, Firebase Messaging (FCM), Stripe Android (PaymentSheet — backend `/payments/create-payment-sheet`), AndroidX Browser (Stripe hosted checkout), Pdf viewer for invoices.
- **Design tokens:** ported verbatim from `SWIFT_APP/Airdrop/DesignTokens.swift` (itself generated from Figma variables): orange `#F15114`, navy `#2a2367`, dynamic gray100–700, status palettes, spacing 5/10/15/20/30/40/50/60, radius 5–30, Cairo type scale h1–body3.

## 4. Repo structure plan

```
KOTLIN_APP/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ga/airdrop/
│       │   ├── AirdropApp.kt / MainActivity.kt
│       │   ├── core/{designsystem,network,auth,navigation,util}/
│       │   ├── data/{api,model,repo}/
│       │   └── feature/{auth,home,shipments,shop,contacts,more,calculator,dropalert,cart}/
│       └── res/{font (Cairo ×8), drawable, mipmap, values}
├── gradle/ (wrapper + libs.versions.toml)
├── build.gradle.kts / settings.gradle.kts
└── docs/BUILD_PLAN.md (this file)
```

## 5. Dependencies / blockers

| Item | Status |
|---|---|
| Java/Android SDK/Gradle absent on machine | **RESOLVED** — installed JDK 17.0.19, SDK 35, build-tools 35.0.0, Gradle 8.14 (local, no brew) |
| Figma access | **OK** — remote Figma MCP resolves all screen nodes; local Dev Mode MCP not enabled (not needed) |
| `google-services.json` for `com.ga.airdrop.app` | **NEEDED from group/Kemar** — FCM push can be scaffolded but not runtime-verified without it |
| Stripe publishable key (Android) | **NEEDED** — exists in RN Config.ts (test key) — will reuse test key for dev |
| Play Store signing keystore | **NEEDED from Kemar** before release build (debug keystore fine for dev) |
| Staging API creds (test account) | **NEEDED for end-to-end auth/data verification** — will ask in room |
| Emulator (AVD system image) | Will install if runtime smoke test required (~2 GB) |

## 6. What I need from the group

1. A **test account** (email/password) on pre-staging or prod for runtime verification of auth + data flows.
2. `google-services.json` for `com.ga.airdrop.app` (Firebase console) when push is wired.
3. Confirmation that **`com.ga.airdrop.app`** is the correct Play Store application id to keep.
4. Nobody else starts Android work — objective claimed as **obj-android-kotlin** by BlueDeer (announced in AGENT-MAIL-WORKROOM).

## Build order

Scaffold → design system → network/auth → nav shell → Login → tab roots → Shipments group → Shop group → Home drill-downs → More group → Calculator/DropAlert → push/Stripe → tests/verification. Commit per milestone with `Agent: BlueDeer` attribution; push to `origin main` of KOTLIN_APP only.
