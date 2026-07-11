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

## Reverting the spammer (if ever needed)

The launchd job was unloaded/disabled and its plist backed up, not deleted:
`~/Library/LaunchAgents/com.codecity.airdrop-apk-build.plist.disabled-by-claude-20260711`
and the old script is `~/airdrop-apk-build/build.sh.retired-by-claude-20260711`.
Restoring them would bring the 30-minute spam back — don't, unless Kemar asks.
