# Kotlin lane handoff — 2026-08-03

Written so the next session reads this instead of reconstructing state from git.
Everything below was measured, not assumed; where a claim was later found wrong
it is corrected in place rather than deleted.

Agent: **NavyBrook** (Kotlin/Android). Formerly **GreenForest** — that identity's
ORC `registration_token` stopped authenticating and there is no self-service
recovery, so a fresh identity was registered with Kemar's approval. **Re-issue
GreenForest's token on the ORC host when convenient.**

---

## Where the work is

`Code-City-LLC/KOTLIN_APP`, branch **`feat/track-journeys-root`**, PR **#236**
open against `main`.

| SHA | What |
|---|---|
| `697bb09e` | read `GET /packages/journeys` — DTOs, endpoint, repository guards |
| `2036bb7f` | Track root + detail gate — a pickup is finally visible |
| `21aae88c` | a package on two pages no longer blanks the whole screen |
| `beead87a` | a pickup with nothing recorded is not a delivery dead end |
| `462ebdf8` | JMD/NCB checkout default-ON — matches iOS `5932f70` |

Local `HEAD` == `origin`. All worktrees clean, 0 dirty, 0 untracked.

### The bug this branch exists for

Track's root read `GET /deliveries/active`, backed by the `package_deliveries`
table. A package held for **pickup** has no row there, so it could not appear no
matter what the client did — a customer whose package was ready to collect
opened Track and read *"No shipments to track."* The root is now
`GET /packages/journeys` (pickup + delivery, each with a server-composed rail),
and the detail screen no longer requires a non-null delivery record.

The contract is mirrored field-for-field from the shipped iOS client
(`AirdropAPI.PackageJourney` / `JourneysPayload`) so both platforms render Track
from identical bytes. **Do not "simplify" those guards without reading
`FigmaDeliveryCenterViewController.swift` first** — several of them exist only
because iOS rejects or accepts a specific shape.

---

## Verification standard used here

- **729/729** unit × prod and staging, forced clean rerun.
- **155/155** connected on `issue101-api35` — delivery 28, shipments 71,
  home 27, cart 29.
- **16 guards revert-checked one at a time.** Each must turn a *named* test red
  when removed. This is the bar; a test that merely documents a bug does not
  count.

### Three mistakes worth not repeating

1. **Two guards were not held, and the tests looked fine.** Every absent-data
   test carried `success:false`, so the success guard rejected them a line
   earlier — replacing the `data` guard with `.orEmpty()` left them all green.
   Right assertion, wrong guard producing it. Only a revert-check found it.
2. **The revert-check harness read Gradle's XML report without deleting it
   first**, so a mutant that failed to *compile* scored as SURVIVED against the
   previous run's results — a false all-clear on the guard that mattered most.
3. **Adversarial review verified findings independently**, so when four lenses
   raised the same defect each skeptic saw it once in isolation and refuted it
   3–0. Cross-lens corroboration was invisible to the verifiers. It was only
   caught by reading the *refuted* list by hand.

Also: `git checkout --` to undo a revert-check mutation wipes uncommitted edits.
That happened twice.

---

## NCB / JMD payment flag — settled, do not re-litigate

`AirdropFeatureFlags.jmdNcbCheckout` is **`true`** as of `462ebdf8`, matching
iOS. Kemar's explicit, fully-informed directive of 2026-08-02, reaffirmed
2026-08-03.

This flag has moved three times, twice on secondhand reports. **The standing
rule is: read the other platform's SOURCE, never a relay.** Current truth was
read from `SWIFT_APP main` (`38a11f4`), `AirdropFeatureFlags.swift:246-247`:
`if UserDefaults.standard.object(forKey:) == nil { return true }` — unset means
ON.

The safety analysis that argued for OFF is **kept**, not deleted, on the flag's
KDoc: an unresolved unknown-outcome double-charge path, and a settlement proof
where `/spi/Payment` returned HTTP 500. The owner was shown all of it and chose
to proceed; that is recorded so the next reader does not assume nobody looked.
**Re-raising it after the decision was the error, not the decision.** Turning it
back OFF is one boolean plus one assertion; every boundary gate still works in
both directions and the gate-OFF cases are not deleted.

---

## Branches: retained, not dropped. Nothing was deleted.

| Branch | Verdict |
|---|---|
| `fix/notification-push-toggles` | Pushed to preserve (was **local-only**). **Do not merge** — superseded |
| `fix/forced-signout-sweep` | Pushed to preserve (was **local-only**, ex-`preview/all-verified`). **Do not merge** — superseded |
| `tealsnow/*`, `pr206*`, 4 July stashes, `brave-cartwright` worktree | Not this lane's. Untouched |

Both rescued branches are stale snapshots: applied to current `main` they would
**delete** 3,549 and 11,672 lines respectively. Their substance already landed —
`ForcedSignOutSweep.kt` is in `main` and wired at `AuthInterceptor.kt:129-144`
with Kemar's ruling in the comment; `PushChannelGate.kt` is in `main` at 152
lines versus the branch's 148, so `main` is ahead. They are preserved on origin
and merged nowhere.

---

## Open

- **PR #236** — first CI run this work has ever had. All green to date is local.
- **No Play upload.** A versionCode is burned by being **uploaded**, not by
  shipping. Nothing is authorized; do not upload without Kemar saying so.
- **GreenForest's ORC token** still needs re-issuing.
