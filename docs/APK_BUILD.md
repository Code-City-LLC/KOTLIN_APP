# APK build discipline (Kemar, 2026-07-11)

**One builder, one location, simple `vN` names, no spam.**

## The rule

1. **One builder — Claude, on demand.** APKs are produced *only* by the
   sanctioned build script, run deliberately when a new build is actually
   needed. There is **no timer**. The old launchd job that rebuilt `origin/main`
   every 30 minutes (`com.codecity.airdrop-apk-build`) has been **retired** — it
   spammed the Desktop with a fresh 59 MB copy of the *same* commit twice an
   hour. Do not reintroduce any scheduled/loop APK build.

2. **One location.** Every APK lives in exactly one place on the Mini:
   `/Users/codecityceo/Desktop/airdrop-apk`. Never scatter APKs into Downloads,
   the repo tree, or anywhere else.

3. **Simple, monotonic names.** `airdrop-v1.apk`, `airdrop-v2.apk`, … The number
   lives in `.build-number` and only ever goes up. It is a build counter, *not*
   the Gradle `versionCode`/`versionName`.
   - `airdrop-latest.apk` → symlink to the newest build.
   - `BUILD_LOG.txt` → one line per build: `vN`, UTC time, variant, git sha, bytes.

4. **Auto-prune.** Only the last **3** builds are kept; older `airdrop-vN.apk`
   are deleted on each build so the Desktop never fills with stale APKs.

## How to build

**On the Mini (the canonical build host — has the JDK + Android SDK):**

```bash
ssh codes-mac-mini /Users/codecityceo/airdrop-apk-build/build-apk.sh          # build current src -> next vN
ssh codes-mac-mini /Users/codecityceo/airdrop-apk-build/build-apk.sh --pull   # sync origin/main first, then build
```

- Toolchain (baked into the Mini script):
  JDK `~/android-toolchain/jdk-17.0.19+10`, SDK `~/Library/Android/sdk`,
  Gradle `~/android-toolchain/gradle-8.14`, task `:app:assembleStagingDebug`.
- Retention override: `KEEP=5 build-apk.sh`.

**Portable reference:** `scripts/build-apk.sh` in this repo is the same logic in
portable form (auto-discovers JDK/SDK, disk pre-flight guard, `--self-test`).
Default output is `<repo>/apk/` (gitignored); override with `AIRDROP_APK_DIR`.
It is the source of truth the Mini builder is derived from.

`--self-test` always uses and removes its own temporary publication store. It
ignores `AIRDROP_APK_DIR`, so fabricated bytes can never advance the real build
counter, ledger, retention set, or `airdrop-latest.apk` link. Run the shell
regression suite with:

```bash
scripts/test-build-apk.sh
```

Real publication validates the APK ZIP, package ID, app version, and signature
before taking an exclusive publication lock. Number allocation, artifact
staging, counter, ledger, latest link, and numeric retention then commit under
that lock with rollback on a trapped failure. The ledger records app and Gradle
versions separately.

An existing artifact can be checked without publishing or changing build state:

```bash
scripts/build-apk.sh --validate-apk /path/to/app-staging-debug.apk staging
```

Before a release build, audit the existing counter/ledger/latest relationship
without changing it:

```bash
AIRDROP_APK_DIR=/Users/codecityceo/Desktop/airdrop-apk \
  scripts/build-apk.sh --check-publication-store
```

The final ledger row must match the counter, use the current provenance schema,
and reference a commit present in the checkout. Handwritten or `git=nogit`
records fail closed.

## Google Play upload bundle

The Desktop `airdrop-vN.apk` artifacts are staging/debug sideload builds. They
are not Google Play submissions. Play production is known to use versionCode 21,
but that is only a floor: an internal, closed, or open track may already contain
a higher code that cannot be reused.

1. Recover the existing Play-authorized upload key or complete an owner-approved
   upload-key reset in Play Console. Never generate or reset a key by assumption.
2. In Play Console's App Bundle Explorer, verify the highest versionCode across
   every uploaded artifact and track. Choose a new integer greater than that
   maximum and no greater than Play's `2,100,000,000` limit; do not infer it
   from the Production track alone.
3. Copy `keystore.properties.example` to the gitignored
   `keystore.properties` and set the four real values on the release host.
4. Build the minified production bundle with the verified code. Replace `NN`
   with that owner-verified integer:

   ```bash
   ./gradlew :app:bundleProdRelease -PplayVersionCode=NN
   ```

Only `prodRelease` uses the Play upload signing configuration. Staging release
keeps its local debug signature and repository version. Missing, incomplete, or
invalid upload-key configuration disables the entire `prodRelease` variant. The
variant is also disabled when `playVersionCode` (or `PLAY_VERSION_CODE`) is
missing, non-numeric, not greater than the known Production floor of 21, or over
Play's limit, so Gradle cannot emit an unsigned, debug-signed, knowingly reused,
or out-of-range Play candidate.

Verify the resulting `app/build/outputs/bundle/prodRelease/app-prod-release.aab`
with `jarsigner -verify -verbose -certs`, compare its certificate fingerprint to
the authorized Play upload certificate, and record its SHA-256. Upload first to
the approved internal test track; production rollout remains gated by issue
#112 and explicit release approval.

## Reverting the spammer (if ever needed)

The launchd job was unloaded/disabled and its plist backed up, not deleted:
`~/Library/LaunchAgents/com.codecity.airdrop-apk-build.plist.disabled-by-claude-20260711`
and the old script is `~/airdrop-apk-build/build.sh.retired-by-claude-20260711`.
Restoring them would bring the 30-minute spam back — don't, unless Kemar asks.
