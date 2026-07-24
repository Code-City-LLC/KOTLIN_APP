# DELIVERY CENTER — CANONICAL HANDOFF (read this before touching delivery code)

**This branch is the single source of truth for the AirDrop Delivery Center.**
If your local repo does not contain this file on `claude/delivery-center-tracking`,
**you are on a STALE clone — `git fetch --all` first.**

## The canonical state
- Branch: **`claude/delivery-center-tracking`**
- Canonical HEAD (design + reconciliation): **`6e7745b`** — the code in this file's branch.
- PR: **#166** (open, CI-green, merge-ready).
- Canonical APK: **v25** on the Mini ledger (`~/Desktop/airdrop-apk/airdrop-v25.apk`, `git=6e7745b`).

**This is the LATEST work. Anything that diverges from it is a REGRESSION** and must be
reconciled ONTO this branch — never the other way around. In particular, the parallel
Delivery Center on `codex/android-update-notice` (`f47c1397`, shipped as v20) is
**superseded**. Do not build the Delivery Center anywhere except this branch.

## If you're the agent that was rebuilding this
Your plumbing is ALREADY here and kept intact — you do NOT need to rebuild it:
- `data/model/DeliveryTracking.kt`, `data/repo/DeliveryTrackingRepository.kt`
  (fail-closed contract), `data/api/AirdropApiService.kt` tracking endpoints.
- `feature/delivery/DeliveryCenterViewModel.kt` — the **0/1/many state machine**
  (active list → per-package live detail, refresh, retry, session hygiene, pagination).
- The full test suite (VM / contract / presentation / flow / home-nav).
It was cherry-picked from `f47c1397` and reconciled UNDER the approved design (you are
credited as co-author on `1fe9176`). **Stop diverging; rebase any further delivery work
onto `origin/claude/delivery-center-tracking`.**

## The approved design (do not re-drift)
- Two entries, ONE screen: `orderReference` (just paid → deterministic journey) and
  `packageId`/none (live 0/1/many).
- **Tracking page = the approved journey layout**, always the canonical **four stages**:
  Order Confirmed → Preparing for Dispatch → Out for Delivery → Delivered (with the
  approved copy). Live server data maps onto it (`canonicalJourney()`:
  assigned→Preparing for Dispatch), timestamps render under the copy; unknown server
  projections fall back to raw server stages.
- **Hero:** the delivery-truck illustration BIG, inside its rounded gray box (full-width
  `gray150` well, art ~90% Fit, nothing cropped). NOT a circle, NOT free-floating.
- Heading: **"Your Delivery"** + labeled reference (live: `Tracking #AIR-xxxx`,
  journey: `Invoice #AIR-xxxx`) + description line.
- Nodes: OUTLINED (no fill), 44dp, 22dp icon — passed **green `#2E9E5B`**, current
  **soft-orange `#E06B3E`**, pending grey. Upcoming stages fade to 40%. Current node
  pulses; the connector leaving it streams soft-orange dots.
- Contact: compact **solid phone** (`ic_phone`) + "Contact us for more information",
  pinned above the gesture bar. No floating "Refresh" link on the detail page.
- Topographic "waves" OUTSIDE the cards (page background, 8%). Card runs close to the
  contact; the whole thing fits ONE frame, no scroll.
- Multi-package **list → per-package detail** is preserved (the state machine).
- Rollout shim: `activeDeliveries` 404 → honest empty state until Laravel ships
  `GET deliveries/active` + `GET packages/{id}/delivery-tracking`. Remove once live.

## Next
Verify CI on `6e7745b`, **merge PR #166 to main.** Do not re-diverge.
— GreenForest (Kotlin), 2026-07-22.
