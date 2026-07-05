# HANDOFF — Airdrop Android (KOTLIN_APP)

**Date:** 2026-07-04 (late) · **From:** Claude Fable 5 session (ORC codename **BlueDeer**) · **To:** successor model (Opus or whichever picks this up)
**Repo:** `/Users/codecityceo/Documents/GitHub/KOTLIN_APP` · branch `main` · HEAD `9c4620e` (pushed to `https://github.com/Code-City-LLC/KOTLIN_APP.git`)

Read this whole file before touching code. Everything below is runtime-verified fact, not aspiration.

---

## 1. Mission & source-of-truth hierarchy (Kemar's standing rulings)

Build the native Android (Kotlin/Compose) port of the Airdrop product, Play-Store ready. Truth order:

1. **Swift iOS app** — `/Users/codecityceo/Documents/GitHub/SWIFT_APP` — behavioral/flow truth AND updated-design truth. The shipped design lives in the `Figma*ViewController.swift` files (e.g. `FigmaHomeViewController.swift`, `FigmaTabHeader.swift`, `FigmaCartViewController.swift`). Cite line numbers in code comments when porting.
2. **Figma** — file key `N4k6jzpeLZgeRS5O1xfyIv` (app screens canvas node `40000002:83125`) — visual truth, **and it WINS where Swift is missing a designed element** (Kemar's explicit ruling after the Government Charges episode: "government charges should be there, swift is missing it"). Precedents applied: Government Charges screen (node `40001817:20681`), CIF bottom sheet (`40001817:20191`), Help Live Chat row.
3. **React Native app** — `/Users/codecityceo/Documents/GitHub/AD-REACT_NATIVE_APP-OLD` — structural reference only.
4. **Laravel backend** — `/Users/codecityceo/Documents/GitHub/AIRDROP-LARAVEL` — API contract. Kotlin lane never pushes there (that repo is GreenPuma's, `pre_staging` only).

**Non-negotiables:** no invention (every color/size/copy cites Swift lines or Figma nodes); nothing is "done" without on-device verification (light AND dark); never commit credentials; KOTLIN_APP pushes go to its own `origin main`.

## 2. Current state — what is DONE and verified

~60 screens, MVVM (StateFlow), Retrofit+kotlinx-serialization, Coil, EncryptedSharedPreferences, FCM scaffolding (inert until google-services.json). All of the following verified on the emulator against pre-staging with a real login, in light **and** dark:

- **Auth** incl. keyboard/IME handling, autofill (`ContentType`), full-size logo.
- **Chrome (this session):** `AirdropHeader` + `AirdropBottomBar` + all inner headers are **opaque** (Swift `FigmaTabHeader` rule: "never a translucent wash"); per-tier accent colors (`Ruby Starter #D2554D`, map in AirdropHeader.kt); 28dp tab icons.
- **Dark theme (this session):** 113 drawables had hardcoded `#292929` fills/strokes that vanished on dark → all now reference `@color/icon_duotone` (values-night flips to white). Pattern to follow for any new icon.
- **Home (this session):** rebuilt to `FigmaHomeViewController` geometry — hero photo fixed 534dp + flat 10% black scrim (NO gradient fades), content column starts at y=326 and spills below the photo, warehouse cards 238×326 **whole-card tappable** → `warehouses?type={type}`, activity tiles 108dp, auction cards 160×245 with **working cart toggle** + live badge, refer-a-friend 59dp.
- **Help (this session):** rebuilt to Figma `40001617:20377` — **Live Chat** row → `LiveAgentChatScreen` (Trengo web chat, key `VEoeiGPVu2O9GGh`, same as Swift `FigmaLiveAgentChatViewController`); grouped contact cards; values SubTitle2-14 (fixed the "everything bold" complaint).
- **Shipments hub (this session):** Swift parity — method branding `AirDrop Standard`/#10BBE9 etc. with method-tinted icons, Title1 section headers, gray150 93dp stat tiles, full-bleed carousels, 280dp cards.
- **AirCoin (this session):** balance page pixel-matched to Figma `40001911:22972` (64dp stat rows + dividers), History plain gray100.
- **Calculator:** Government Charges + CIF sheet restored per Figma; results disclaimer link navigates to Government Charges.
- **CRITICAL fix (this session):** the orphan `ShipmentsCartStore` is gone — ALL package cart actions build real `CartLine`s through `feature.cart.CartStore` (`ShipmentsContracts.kt` has `ShipmentPackage.toCartLine()` / `ShipmentPackageDetail.toCartLine()`); screens observe `CartStore.items` for live +/check icons. Device-verified end-to-end: + → check → badge → Cart line → Order Total.
- **Cart (this session):** full Swift `FigmaCartViewController` parity (page gray100, hairline item rows, note card 90dp, gray150 charges card with orange in-card Total, blueMain Promise card, unwrapped billing form, 56dp Payment Method row, opaque bottom bar with orange Order Total + solid orange radius-10 button, qty always 1).
- **Release signing:** `release` buildType signs with the debug keystore so `assembleProdRelease` is sideload-installable (comment in `app/build.gradle.kts` explains the Play-upload swap). `~/Desktop/Airdrop.apk` = signed prod release @ 9c4620e.

**Recent commits (read these diffs to understand the patterns):** `e69747d` (gov charges restore), `48db012` (chrome/Home/dark/Help/Shipments/AirCoin), `9222e1d` (36 audit fixes incl. cart unification), `9c4620e` (batch 2 + backlog doc).

## 3. THE REMAINING WORK — `docs/PARITY_BACKLOG.md`

A 176-agent audit (every screen vs Swift vs Figma, each finding **adversarially verified**) confirmed 117 defects. **63 are fixed; the 54 remaining live in `docs/PARITY_BACKLOG.md`** — each entry has file:line, the Swift/Figma citation, and the **exact fix**. Work that list top-down (it's sorted by severity). Highlights:

- `PackagesFilterSheet.kt` — should be a gray100 page-sheet with working expand/collapse; leading status icons; consistent fonts.
- `PackageDetailsScreen.kt` — body card gray100-on-gray200; Swift renders status-tinted 10dp bullet dots (not metro icons); plain section cards; footer needs Exchange Rate row.
- `InvoiceViewerScreen.kt` — surface colors inverted; Share should share the downloaded file, not the URL.
- `PaymentPackageDetailsScreen.kt` — pinned View History footer, CIF pill 48dp, full "Invoice Amount (Declared Value/Cost)" label.
- Shop: per-context `ShopProductCard` geometry (root 1-line title vs list 2-line), root content 126dp, `ShopDropdownField` restyle, hero placeholders (gray airplane / 🎁), `**bold**` spans in descriptions, purchase-link-unavailable alert, PTR orange tint.
- More: NotificationSettings glyph/tint corrections + row heights, Documents action-bar restyle + PTR + "Got it", DOB picker cap, avatar 80pt geometry, `MoreSelectField` restyle, InviteFriend duotone icon, FCM token re-register on push-enable.
- LegalContent.kt — CMS headings should be textDarkTitle (body stays textDescription); FAQ chevron gap 10dp.
- GoldPriority — tier-name auto-shrink; force light status-bar icons over the header image.

After each batch: compile, install staging debug, screenshot light+dark, keep zero-FATAL logcat.

## 4. Toolchain & verification recipe (exact, working)

```bash
export JAVA_HOME=$(/bin/ls -d ~/android-toolchain/jdk*/Contents/Home | head -1)
export ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SDK_ROOT=$ANDROID_HOME
GRADLE=~/android-toolchain/gradle-8.14/bin/gradle
$GRADLE :app:compileStagingDebugKotlin        # fast loop
$GRADLE :app:testProdDebugUnitTest :app:assembleStagingDebug :app:assembleStagingRelease :app:assembleProdRelease
```

- Emulator: AVD `airdrop_test`, **must** launch with `-gpu host` (swiftshader ANRs). `adb install -r app/build/outputs/apk/staging/debug/app-staging-debug.apk` (staging debug vs staging release have different signatures — uninstall when switching).
- Staging flavor (`com.ga.airdrop.app.staging`) → pre-staging backend. Test creds: ORC room topic `test-credentials` (Agent Mail `fetch_topic`) or agent memory `prestaging-test-credentials.md`. Pre-staging ONLY; never commit them.
- adb text quirks: `input text` can't type `+` → `input keyevent 81`; avoid taps in the bottom ~40px gesture zone; dark mode toggle: `adb shell "cmd uimode night yes|no"`.
- Screenshot: `adb exec-out screencap -p > file.png`, then READ the image — that's the verification bar.
- R8 safety: proguard keeps cover `com.ga.airdrop.**` serializers; any new `@Serializable` outside that needs a rule. Verify minified with `assembleStagingRelease` + real login when touching networking.
- Ship step: `cp app/build/outputs/apk/prod/release/app-prod-release.apk ~/Desktop/Airdrop.apk` (it's signed).

## 5. ORC group protocol (how to plug into the fleet)

- Agent Mail JSON-RPC: `http://127.0.0.1:8766/api/`. **Reuse the BlueDeer identity** — registration token at `~/.claude/projects/-Users-codecityceo-Documents-GitHub-KOTLIN-APP/memory/.orc_token_bluedeer` (agent id 430, project_key `/Users/codecityceo/Documents/GitHub/AIRDROP-LARAVEL`). Only register fresh if that token is rejected.
- **Verified arg shapes** (the server renamed params; older docs are wrong):
  - `fetch_inbox`: `{registration_token, project_key, agent_name:"BlueDeer", limit}`
  - `send_message`: `{sender_name, sender_token, project_key, to:[<registered names>] or broadcast:true with to:[], thread_id:"AGENT-MAIL-WORKROOM", topic:"AGENT-MAIL-WORKROOM", subject, importance, body_md}`
  - `resource://agents/...` listing currently 500s — pull recipient names from `fetch_inbox` `from` fields (e.g. `Resident-Codex`).
- RAG/memory on the Mini: `bash ~/code-city-orc/orc_recall.sh "<query>" 3`, save with `orc_remember.sh`. OKRs: `GET http://127.0.0.1:9898/api/objectives`.
- Rules: post progress with proof; executor ≠ verifier; sensitive items (credentials/payments/production/DB) → HOLD and ask Kemar; git push only to this repo's `origin main`.

## 6. Blocked on Kemar (do not guess)

1. **Play upload keystore** — release is debug-signed (fine for sideload, NOT for Play). Wire via gitignored `keystore.properties` when provided.
2. **google-services.json** for `com.ga.airdrop.app` — FCM is inert until then (plugin alias commented in `app/build.gradle.kts`).
3. Figma's newer checkout/delivery flow (sections 40008282/40008798/40008740) is not in Swift and unbuilt — needs Kemar's direction.

## 7. Working style that got approved (keep it)

- Fix → build → install → screenshot light+dark → only then claim done. Kemar rejects untested claims and personally re-tests on device.
- Cite sources in comments (`Swift FigmaX.swift:123`, `Figma 40001817:20681`) — reviewers rely on those.
- Use the ultracode Workflow tool for audits/parity sweeps (fan-out finders + adversarial verify); apply fixes in the main loop with per-batch compiles.
- Session limits can kill subagent fleets mid-run (happened at ~9pm ET reset) — keep workflows resumable and prefer main-loop edits when capacity is tight.
