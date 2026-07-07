# HANDOFF — Airdrop Android (KOTLIN_APP)

**Date:** 2026-07-05 · **From:** BlueDeer (Opus 4.8 session, ORC fleet) · **To:** successor Opus (or AmberOtter / TopazGlacier taking the lane)
**Repo:** `/Users/codecityceo/Documents/GitHub/KOTLIN_APP` · branch `main` · **HEAD `a1768d2`** — pushed to `https://github.com/Code-City-LLC/KOTLIN_APP.git`, `origin/main` IN SYNC.

> ⚠️ **READ THIS FIRST — the EOS dispatch that assigned this lane pointed at the STALE commit `08e36e2`. That is 4 commits behind.** Do a `git pull` and start from **`a1768d2`**. Twelve of the "54 backlog items" are already fixed, verified on-device, and pushed (list in §3). **Do not redo them — you will conflict with shipped work.** Everything below is runtime-verified fact, not aspiration.

---

## 1. Mission & source-of-truth hierarchy (Kemar's standing rulings)

Build the native Android (Kotlin/Compose) port of the Airdrop product, Play-Store ready. Truth order:

1. **Swift iOS app** — `/Users/codecityceo/Documents/GitHub/SWIFT_APP` — behavioral/flow truth AND updated-design truth. Shipped design lives in the `Figma*ViewController.swift` files (`FigmaHomeViewController.swift`, `FigmaTabHeader.swift`, `FigmaPackageDetailsViewController.swift`, …). Cite line numbers in code comments when porting.
2. **Figma** — file key `N4k6jzpeLZgeRS5O1xfyIv` (app-screens canvas node `40000002:83125`) — visual truth, **and it WINS where Swift is missing a designed element** (Kemar's ruling after the Government Charges episode). Precedents: Government Charges screen (`40001817:20681`), CIF bottom sheet (`40001817:20191`), Help Live Chat row.
3. **React Native app** — `/Users/codecityceo/Documents/GitHub/AD-REACT_NATIVE_APP-OLD` — structural reference only.
4. **Laravel backend** — `/Users/codecityceo/Documents/GitHub/AIRDROP-LARAVEL` — API contract ONLY. The Kotlin lane never pushes there (that repo is GreenPuma's, `pre_staging` only). Where a Swift↔Android data bug traces to an endpoint, use the **existing** route — e.g. product detail is `GET /products/{product:slug}` at `routes/api.php:188`, NOT the list `?slug=` filter (see §3, the a1768d2 fix).

**Non-negotiables:** no invention (every color/size/copy cites Swift lines or a Figma node); nothing is "done" without on-device verification (light AND dark); never commit credentials; KOTLIN_APP pushes go to its own `origin main`.

## 2. THE MANDATE (Kemar directive, echoed by MagentaCastle msgs 12832/12836, EOS dispatch 12840)

Every change in this lane must satisfy ALL of:
1. **Figma MCP is mandatory** — pull the node with `mcp__plugin_brand-voice_figma__get_screenshot` / `get_design_context` (fileKey `N4k6jzpeLZgeRS5O1xfyIv`) **before** you build (to see the target) **and after** (to compare your render). "It compiles" is not verification.
2. **Swift wins conflicts.** When Figma and Swift disagree on a shipped screen, Swift's `Figma*ViewController.swift` is truth (e.g. Shop uses `FigmaTabHeader` + 1-line titles even though a stale Figma frame shows a header label + 2 lines).
3. **Verify on the emulator, light AND dark.** Screenshot → READ the image. Executor ≠ verifier (EOS assigned @PearlFox / @Resident-Opencode as the device-inspection verifiers).
4. **Do not break what works.** ~60 screens are already correct. Keep edits surgical/additive — when a shared component (e.g. `ShipmentsSectionCard`, `MetroStep`) is used by a working screen, add a LOCAL composable or a new parameter rather than mutating the shared one. (This is exactly how PackageDetails was reworked without touching the filter sheet.)

## 3. Current state — DONE this session (pushed, on-device verified) — DO NOT REDO

All against pre-staging with a real login, light + dark. Read the diffs to learn the patterns.

| Commit | Backlog items closed | What shipped |
|---|---|---|
| **`db84b0d`** | Package details §45, §54, §63, §72 | Root bg gray150→**gray200**, body sheet→**gray100**; LOCAL `DetailSectionCard`/`DetailRow`/`TimelineBulletRow` (inline Title2 header, no banded header, no dividers, title2 values, 10dp status-tinted bullet dots instead of metro icons); footer always renders Breakdown + plain Exchange-Rate row + plain orange Total (dropped `TotalChargesBox`). **Shared card/timeline untouched.** |
| **`6605dd4`** | Payments §81, Payments/Orders §90 | Invoice-download moved to top-right Box overlay (top14/end16, 22dp, textDescription tint), invoice-number row inset 32dp; PullToRefreshBox on both lists, OrangeMain indicator, `viewModel::refresh`. |
| **`e7357a5`** | Shop root+lists §162, Shop root §171, ShopDropdownField §180/§207, Auction Product Details §189, Feature Product Details §198 (+ several LOW shop items) | `ShopProductCard(titleLines, rootInsets)` fixed 245dp; ShopDropdownField restyle (subtitle2 label, OrangeMain star, gray100/12dp/48dp, body1, gray500 chevron); gray-airplane hero placeholder; `**bold**` description spans; "Product link unavailable" alert; search reload fix (removed <3-char early-return); orange PTR. |
| **`a1768d2`** | Live data bug (product detail dead) + HTML-entity decode | Wired `GET /products/{slug}` show route (`AirdropApiService.productDetail` + `ProductDetailEnvelope`); `productBySlug` routes through it first, list `?slug=`/`?id=` as fallback. Root cause: the list `?slug=` filter returns 200 with EMPTY data. `cleanDescription` now decodes `&nbsp;/&amp;/…`. Verified: Elegant Retro Velvet Clutch Bag detail loads fully. |

**Baseline before this session (`08e36e2` and earlier, all verified):** Auth+autofill; opaque chrome (`AirdropHeader`/`AirdropBottomBar`, per-tier accents); dark-theme icon fix (113 drawables → `@color/icon_duotone`); Home (`FigmaHomeViewController` geometry, working cart toggle); Help (Live Chat now targets the native AutoPilot/Nirvana app-channel screen); Shipments hub; AirCoin; Calculator + Government Charges + CIF; **cart unified** through `feature.cart.CartStore` (orphan `ShipmentsCartStore` gone); Cart screen full `FigmaCartViewController` parity; release debug-signed for sideload.

## 4. THE REMAINING WORK — `docs/PARITY_BACKLOG.md` (see the STATUS LEDGER at its top)

Each entry has `file:line`, the Swift/Figma citation, and the **exact fix**. A `## STATUS LEDGER` block at the top of that file now marks DONE vs OPEN with commits. **OPEN items, by lane:**

**BlueDeer lane — Shipments detail (next up, in priority order):**
- `PaymentPackageDetailsScreen.kt:240` §99 — pinned **View History footer** (Column: content weight(1f) + opaque gray100 footer, 1px divider, 50dp OutlineButton, 20dp pad).
- `PaymentPackageDetailsScreen.kt:144` §108 — label → **"Invoice Amount (Declared Value/Cost)"** (one-line string fix).
- `PaymentPackageDetailsScreen.kt:176` §153 — CIF pill **48dp** (icon 20dp, tint textDarkTitle).
- `ShipmentsUi.kt:755` §135 — View-History timeline connector = **step status color** (add `connectorColor` param), "-" fallback, body3 date.
- `InvoiceViewerScreen.kt:98` §117 — **swap surfaces**: root gray100, preview box gray150.
- `InvoiceViewerScreen.kt:346` §126 — **Share the downloaded file** via FileProvider content:// (application/pdf), not the raw URL.
- `OrderDetailsScreen.kt:62` / `ProductPaymentDetailsScreen.kt:64` §144 — hero image **fillMaxWidth**: OrderDetails 20dp pad/169dp, ProductPaymentDetails 30dp pad/159dp.
- `PackagesFilterSheet.kt` §27/§36 — gray100 page-sheet, working expand/collapse, leading status icons, consistent fonts.
- `GoldPriority` §9/§18 — tier-name auto-shrink; force light status-bar icons over the header image.

**MagentaCastle (Codex) lane — More/Legal/Profile** (claimed msg 12824; BLOCKED on Figma-MCP inspection per EOS): Documents §216/§225, Edit Profile §234/§459, Preferences §243, Notification Settings §252/§423/§432/§468/§477, Invite Friend §261, Legal/T&C §270, FAQs §486.

**Unassigned / needs owner (AmberOtter first-pass / TopazGlacier audit):** the LOW batch §279–§486 (shop list polish, section-card tints, DOB cap, etc.), and any MEDIUM above not yet claimed.

After each batch: compile → install staging debug → screenshot light+dark → zero-FATAL logcat → then claim done with the screenshot as proof.

## 5. Toolchain & verification recipe (exact, working)

```bash
export JAVA_HOME=$(/bin/ls -d ~/android-toolchain/jdk*/Contents/Home | head -1)
export ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SDK_ROOT=$ANDROID_HOME
GRADLE=~/android-toolchain/gradle-8.14/bin/gradle
$GRADLE :app:compileStagingDebugKotlin        # fast loop
$GRADLE :app:testProdDebugUnitTest :app:assembleStagingDebug :app:assembleStagingRelease :app:assembleProdRelease
```

- Emulator: AVD `airdrop_test`, launch with `-gpu host` (swiftshader ANRs). `adb install -r app/build/outputs/apk/staging/debug/app-staging-debug.apk` (staging debug vs release have different signatures — uninstall when switching).
- Staging flavor (`com.ga.airdrop.app.staging`) → pre-staging backend. Test creds: ORC room topic `test-credentials` (`fetch_topic`) or agent memory `prestaging-test-credentials.md`. **Pre-staging ONLY; never commit.**
- adb quirks: `input text` can't type `+` → `input keyevent 81`; avoid taps in the bottom ~40px gesture zone; dark toggle `adb shell "cmd uimode night yes|no"`.
- Screenshot: `adb exec-out screencap -p > file.png`, then READ the image — that's the bar.
- Diagnose data bugs with OkHttp logs: `adb logcat | grep okhttp` shows the exact request/response (this is how the product-detail 200-empty bug was found). Use it before claiming a screen "has no data."
- R8: proguard-keeps cover `com.ga.airdrop.**` serializers; a new `@Serializable` outside that needs a rule. Verify minified (`assembleStagingRelease` + real login) when touching networking.
- Ship: `cp app/build/outputs/apk/prod/release/app-prod-release.apk ~/Desktop/Airdrop.apk` (signed).

## 6. ORC group protocol (how to plug into the fleet)

- Agent Mail JSON-RPC: `http://127.0.0.1:8766/api/`. **Reuse the BlueDeer identity** — token at `~/.claude/projects/-Users-codecityceo-Documents-GitHub-KOTLIN-APP/memory/.orc_token_bluedeer` (agent id 430, project_key `/Users/codecityceo/Documents/GitHub/AIRDROP-LARAVEL`). Only register fresh if rejected.
- **Verified arg shapes** (params were renamed; older docs are wrong):
  - `fetch_inbox`: `{registration_token, project_key, agent_name:"BlueDeer", limit}`
  - `fetch_topic`: `{registration_token, project_key, agent_name, topic_name:"AGENT-MAIL-WORKROOM", limit}` (arg is `topic_name`, not `topic`; returns full `body_md`)
  - `send_message`: `{sender_name, sender_token, project_key, to:[<registered names>] (non-empty) OR broadcast:true, thread_id:"AGENT-MAIL-WORKROOM", topic:"AGENT-MAIL-WORKROOM", subject, importance, body_md}`
  - `acknowledge_message`: `{registration_token, project_key, agent_name, message_id}` for ack-required msgs. `get_message` does NOT exist — use `fetch_topic` for bodies.
- Active fleet (from inbox): **MagentaCastle** (Codex, More/Legal/Profile), **MistyStone** (EOS dispatcher, id 6), **MagentaBay** (Laravel heartbeats, read-only), **AmberOtter**/**TopazGlacier** (assigned this lane), **Resident-Opencode**/**PearlFox** (device-inspection verifiers), **GreenPuma** (pre_staging merge readiness).
- File reservations exist (`file_reservation_paths`/`release_file_reservations`) — reserve a path before editing a shared file so two agents don't collide.
- RAG/memory on the Mini: `bash ~/code-city-orc/orc_recall.sh "<query>" 3`, save with `orc_remember.sh`. OKRs: `GET http://127.0.0.1:9898/api/objectives`.
- Rules: post progress with proof; executor ≠ verifier; sensitive items (credentials/payments/production/DB) → HOLD, ask Kemar; git push only to this repo's `origin main`.

## 7. Blocked on Kemar (do not guess)

1. **Play upload keystore** — release is debug-signed (fine for sideload, NOT Play). Wire via gitignored `keystore.properties` when provided.
2. **google-services.json** for `com.ga.airdrop.app` — FCM inert until then (plugin alias commented in `app/build.gradle.kts`).
3. Figma's newer checkout/delivery flow (sections `40008282`/`40008798`/`40008740`) — not in Swift, unbuilt, needs Kemar's direction. (Per EOS lock: **no POS/production/dashboard/Careers/Auction** work without explicit assignment.)

## 8. Working style that got approved (keep it)

- Fix → build → install → screenshot light+dark → THEN claim done. Kemar rejects untested claims and personally re-tests on device.
- Cite sources in comments (`Swift FigmaX.swift:123`, `Figma 40001817:20681`).
- Use the ultracode Workflow tool for audits/parity sweeps (fan-out finders + adversarial verify); apply fixes in the main loop with per-batch compiles.
- Session limits can kill subagent fleets mid-run — keep workflows resumable, prefer main-loop edits when capacity is tight. **If you are the successor to a mid-session model swap: `git pull`, read this file top to bottom, `fetch_inbox` as BlueDeer, then continue §4 top-down.**
