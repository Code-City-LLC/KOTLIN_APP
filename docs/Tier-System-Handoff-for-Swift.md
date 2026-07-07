# Tier System Handoff — Kotlin → Swift

**From:** BlueDeer (Kotlin/Android lane) · **For:** the Swift/iOS lane (SwiftForge, EmeraldPuma) and Kemar
**Date:** July 7, 2026
**Goal:** whatever the tier system does on Android, iOS does identically — same features, same design, same flow — and vice versa. This document says exactly where each side stands and exactly what to build to close the gaps.

**Kotlin reference code:** KOTLIN_APP pull request #34 (branch `add-tier-system-api`, latest commit `80ea70d`).
**Swift reference code:** branch `staging/figma-redesign-testflight` (latest `3f00930`).
**Backend contract:** `AIRDROP-LARAVEL/docs/TIER_SYSTEM_API.md` on pre_staging.

---

## 1. Feature parity scoreboard (who has what today)

| Feature | Android (Kotlin) | iOS (Swift) | What to do |
|---|---|---|---|
| Customer Tier page — swipeable tier cards with gradient art | ✅ Done | ✅ Done | Nothing — already matched |
| Benefit bullets come from the backend (`benefits_summary`), not hardcoded | ✅ Done, device-verified (`50a14ba`) | ⚠️ Please confirm your flip landed and displays verbatim | Swift: confirm or finish |
| Upgrade / downgrade with confirmation sheet (glass design, "what you'll unlock" / "what you'd give up") | ✅ Done, live round-trip verified | ✅ Done (`a883332`) | Nothing — already matched |
| AirCoins hidden for tiers that don't earn them (RUBY / SAVR) | ✅ Done | ✅ Done (`3a68b23`) | Nothing |
| Machine-readable error codes (`error_code`) wired through the API layer | ✅ Done (`50a14ba`) | ✅ Done (`970e197`) | Nothing |
| **Package insurance — choose or decline coverage on Package Details** | ✅ **Done + device-verified (`80ea70d`)** | ❌ **API functions exist but NO screen uses them** | **Swift: build it — full spec in section 2** |
| "No rate card" (NO_RATE_CARD) message on the calculator | ➖ Deliberately not wired — Kotlin Calculator files are a frozen lane (ruling #15079) | ✅ Done in calculator + results | Divergence is documented and intentional until the freeze lifts |
| Customs estimate endpoint | 🧊 Typed, wired to NO screen (CUSTOMS FREEZE ruling #16965) | 🧊 Same | Nothing — do not wire on either side |
| Free-returns eligibility / return quote endpoints | Typed in the API layer, no screen yet | Typed in the API layer, no screen yet | Parity is even; neither side builds until the group picks an owner |

**Bottom line: one real gap — iOS has no insurance screen. Section 2 is everything needed to build it identically.**

---

## 2. The insurance flow — build spec for iOS

Android is the reference implementation (see `PackageInsuranceUi.kt`, `PackageDetailsViewModel.kt` in PR #34). Mirror it in `FigmaPackageDetailsViewController`.

### 2a. Where it lives
On **Package Details**, directly **below the CIF Value row**, add an **"Insurance" row** in the same card style as the CIF row (gray surface, 1pt border, rounded corners, 48pt tall, label left / status right).

The row **only appears when `GET /api/v1/packages/{id}/tier` answers** for that package. If that call fails, show nothing (the rest of the page is unaffected).

### 2b. The row's three states (right-side status text)
| Backend state (`insurance` object on package tier) | Status text | Color |
|---|---|---|
| Nothing recorded yet | `Choose` | Brand orange |
| Recorded selected | `Covered · $1.50` (the recorded premium) | Green `#2E9E5B` |
| Recorded declined | `Declined` | Muted gray (description text color) |

### 2c. Tapping the row opens the insurance sheet
A bottom sheet in the **same design language as the tier-change sheet**:

- Sheet container: near-black `#1B1B1B`, custom small grab handle (white 25%).
- **Hero panel**: rounded 20, diagonal gradient **`#0A96D4` → `#004B6C`** (the shipment blue — deliberately NOT a tier color, so the sheet reads as its own thing). Eyebrow text `PROTECT THIS PACKAGE` (12pt, letter-spaced, white 80%), title `Package Insurance` (28pt bold white).
- **Quote panel** (frosted glass: white 8% fill, white 15% border, rounded 16) with three rows, all values **verbatim from the API** — the app never computes money:
  - `Declared value` → `insured_value` (send the package's declared value — Kotlin uses the package `amount` field — as the `insured_value` query param)
  - `Premium` → `premium`, with a small caption under the label: `$1.50 per $100 of value` (from `rate_per_100` and `block_size`)
  - `Covered value` → `covered_value`
- If the quote says `can_decline: false`, show this line under the panel (white 70%): **"Insurance is required for your tier and can't be declined."**
- **Primary button** (white fill, dark text, rounded 14, 52pt tall, spinner-in-place while posting): **`Add Insurance — $1.50`** (the live premium).
- **Secondary button** — ONLY when the quote says `can_decline: true` (SAVR customers today): outlined coral `#F17A72`, text **`Decline coverage`**.

### 2d. The API calls
1. Open sheet → `GET /api/v1/insurance/options?insured_value={declared}&tier_code={package tier code}` → render quote.
2. Primary → `POST /api/v1/packages/{id}/insurance-selection` body `{ "selected": true, "insured_value": {declared} }`.
3. Decline → same endpoint, body `{ "declined": true }`.
4. On success: close the sheet, update the row to the recorded state.

### 2e. Error behavior (the error_code pact — this is the important one)
- If a decline comes back **422 `INSURANCE_MANDATORY`** (a non-SAVR customer somehow declining): **do not close the sheet.** Remove the decline button, and show: **"Insurance is required for your tier and can't be declined."** The sheet "snaps back" to the covered option. Both apps already carry `error_code` in their API error types — branch on it.
- `INSURANCE_CHOICE_REQUIRED` → show: **"Please add or decline insurance to continue."**
- Any other failure → show the server's own `message`, keep the decline button.

### 2f. QA checklist (what Android verified live on pre-staging)
- [x] Row appears on a package, status `Choose` (orange)
- [x] Sheet opens with a real quote — GOLD customer, $12.00 declared → $1.50 premium
- [x] GOLD (mandatory tier): NO decline button + the "required for your tier" line
- [x] Add Insurance posts, sheet closes, row flips to `Covered · $1.50` (green)
- [ ] SAVR customer: decline button shows, decline records, row shows `Declined` *(needs a SAVR account — SwiftForge's QA signup flow makes fresh RUBY accounts; downgrade one to SAVR first)*
- [x] INSURANCE_MANDATORY snap-back (unit-tested on Android; reproduce by posting a decline as GOLD)

---

## 3. Shared design recipe (so neither side drifts)

| Element | Value |
|---|---|
| Sheet container | `#1B1B1B` |
| Glass pill / CTA fill | white 18%, border white 35%, radius 14 |
| Glass panel | white 8% fill, white 15% border, radius 16 |
| Buttons | radius 14, min height 52, spinner replaces label while busy |
| Destructive accent | coral `#F17A72` |
| Insurance hero gradient | `#0A96D4` → `#004B6C` diagonal |
| Tier gradients | Gold `#EFBF04→#8C6F01` · Ruby `#D2554D→#5C262E` · Sapphire `#0A96D4→#004B6C` · Platinum `#CACACA→#737373` · Diamond `#6B6B6B→#292929` |
| Downgrade sheet | "Are you sure?" + "Here's what you'd be giving up" rows with ✕ marks; safe choice ("Keep my tier") is the primary button |
| Upgrade sheet | "UPGRADE TO" eyebrow + "What you'll unlock" ✓ rows; confirm is primary |

On Android these buttons are one shared component pair (`GlassPrimaryButton` / `GlassSecondaryButton`) — recommend the same consolidation on iOS.

---

## 4. Backend quick reference

- `GET /service-tiers` — 5 tiers, `benefits_summary` is a **string array** (display verbatim; Android shipped a fix because it was typed as a single string and the parse broke when the field went live — check your decoder type)
- `GET /customers/me/tier` · `PATCH /customers/me/tier` — current tier + backend-decided upgrade/downgrade directions
- `GET /insurance/options` · `POST /packages/{id}/insurance-selection` — the flow in section 2
- `GET /packages/{id}/tier` — per-package tier snapshot + recorded insurance (drives the row)
- Error envelope: `{"success": false, "message": "…", "error_code": "NOT_FOUND"}` — codes: `NOT_FOUND, FORBIDDEN, VALIDATION_ERROR, INSURANCE_MANDATORY, INSURANCE_CHOICE_REQUIRED, NO_RATE_CARD`
- Everything is computed by the backend. Neither app ever recalculates premiums, totals, eligibility, or direction.

---

## 5. Open items after this handoff

1. **Swift builds the insurance flow** (section 2) — the one real gap.
2. **Swift confirms** benefit bullets render from `benefits_summary` verbatim (Android verified byte-for-byte on device).
3. **Tier catalog consolidation** — Android proposed a single shared TierCatalog source file per app (names, codes, gradients in ONE place); waiting on SwiftForge's layout confirmation so both apps consolidate the same way.
4. **Returns UI** — unowned on both sides; group should assign before either lane builds.
