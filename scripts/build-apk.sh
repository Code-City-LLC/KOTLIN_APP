#!/usr/bin/env bash
#
# build-apk.sh — the ONE sanctioned way to produce an Airdrop APK.
# ---------------------------------------------------------------------------
# RULE (do not work around this):
#   * Only this script produces APKs. Never run a raw `gradlew assemble…` and
#     copy the artifact by hand, and never drop APKs into Downloads/Desktop/
#     random folders. That is the "APK spam" this script exists to kill.
#   * Every APK lands in ONE place:            <repo>/apk/
#   * Naming is simple and monotonic:          airdrop-v1.apk, airdrop-v2.apk, …
#     The number is a build counter (apk/.build-number), independent of the
#     Gradle versionCode/versionName. It only ever goes up.
#   * airdrop-latest.apk  ->  symlink to the newest build (convenience).
#   * Retention: only the last $KEEP builds are kept; older ones are pruned
#     automatically so the disk never fills with stale APKs.
#   * Traceability: apk/BUILD_LOG.txt records vN, UTC time, variant, the Gradle
#     version, the git sha, and the byte size for every build.
#
# Usage:
#   scripts/build-apk.sh [variant]
#     variant ∈ { staging | staging-release | prod | prod-release }
#     default: staging   (== stagingDebug — installable, points at pre-staging)
#
#   scripts/build-apk.sh --self-test
#     Exercise the versioning/pruning/symlink/log bookkeeping with a fabricated
#     APK (no Gradle). Used to verify the plumbing without a full build.
#
#   scripts/build-apk.sh --validate-apk <path> <variant>
#     Read-only ZIP/package/version/signature validation. Publishes nothing.
#
#   Env:
#     KEEP=<n>   override retention count (default 3)
#     MIN_FREE_GB=<n>   override pre-flight free-disk guard (default 3)
# ---------------------------------------------------------------------------
set -euo pipefail

# --- Resolve repo root from this script's location (works in any worktree) ---
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." >/dev/null 2>&1 && pwd -P)"

# Self-test owns an isolated store even when the build host exports its real
# publication directory. Internal test mode is guarded and never used by a
# normal build.
SELF_TEST_MODE=0
SELF_TEST_ROOT=""
if [ "${1:-}" = "--self-test" ]; then
  SELF_TEST_MODE=1
  SELF_TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/airdrop-apk-selftest.XXXXXX")"
  APK_DIR="$SELF_TEST_ROOT/output"
elif [ "${1:-}" = "--test-publish" ] && [ "${AIRDROP_INTERNAL_TESTING:-}" = "1" ]; then
  SELF_TEST_MODE=1
  APK_DIR="${4:-}"
  [ -n "$APK_DIR" ] || { printf 'missing test publication directory\n' >&2; exit 2; }
  case "$APK_DIR" in
    "${TMPDIR:-/tmp}"/*) ;;
    *) printf 'test publication directory must be under TMPDIR\n' >&2; exit 2 ;;
  esac
else
  APK_DIR="${AIRDROP_APK_DIR:-$REPO_ROOT/apk}"
fi
COUNTER_FILE="$APK_DIR/.build-number"
LOG_FILE="$APK_DIR/BUILD_LOG.txt"
LATEST_LINK="airdrop-latest.apk"          # relative name inside APK_DIR
KEEP="${KEEP:-3}"
MIN_FREE_GB="${MIN_FREE_GB:-3}"
LOCK_DIR="$APK_DIR/.publish.lock"
TXN_DIR=""
TXN_ACTIVE=0

log()  { printf '  %s\n' "$*"; }
die()  { printf '\n[build-apk] ERROR: %s\n' "$*" >&2; exit 1; }
step() { printf '\n[build-apk] %s\n' "$*"; }

# --- Map friendly variant -> Gradle task + output subdir ---------------------
resolve_variant() {
  case "${1:-staging}" in
    staging|staging-debug)   GRADLE_TASK="assembleStagingDebug";   OUT_SUBDIR="staging/debug"  ;;
    staging-release)         GRADLE_TASK="assembleStagingRelease"; OUT_SUBDIR="staging/release";;
    prod|prod-debug)         GRADLE_TASK="assembleProdDebug";      OUT_SUBDIR="prod/debug"     ;;
    prod-release)            GRADLE_TASK="assembleProdRelease";    OUT_SUBDIR="prod/release"   ;;
    *) die "unknown variant '$1' (use: staging | staging-release | prod | prod-release)";;
  esac
  VARIANT_LABEL="$1"
}

next_build_number() {
  local n=0
  if [ -e "$COUNTER_FILE" ]; then
    [ -f "$COUNTER_FILE" ] || die "counter is not a regular file: $COUNTER_FILE"
    n="$(cat "$COUNTER_FILE")"
    [[ "$n" =~ ^[0-9]+$ ]] || die "counter must contain one non-negative integer"
  fi
  echo $(( n + 1 ))
}

app_version() {
  local gradle_file="$REPO_ROOT/app/build.gradle.kts"
  local name code
  name="$(grep -m1 -E 'versionName[[:space:]]*=' "$gradle_file" 2>/dev/null | grep -oE '"[^"]+"' | tr -d '"')"
  code="$(grep -m1 -E 'versionCode[[:space:]]*=' "$gradle_file" 2>/dev/null | grep -oE '[0-9]+' | head -1)"
  echo "${name:-?}(${code:-?})"
}

gradle_version() {
  local props="$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" version
  version="$(sed -nE 's#.*gradle-([0-9][0-9.]*)-(bin|all)\.zip.*#\1#p' "$props" | head -1)"
  echo "${version:-?}"
}

git_sha() { git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo "nogit"; }

# --- Toolchain discovery -----------------------------------------------------
# The single builder owns its environment: a bare ssh / cron shell often lacks
# the JAVA_HOME / ANDROID_HOME exports that a login shell has. Rather than fail
# with "No Java compiler found", locate a JDK (17+) and the Android SDK here.
resolve_toolchain() {
  # --- JDK (Android Gradle Plugin needs 17+) ---
  if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME:-}/bin/javac" ]; then
    local cand=""
    if [ -x /usr/libexec/java_home ]; then
      cand="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)"
    fi
    for guess in "$cand" \
                 "$HOME"/android-toolchain/jdk-*/Contents/Home \
                 /opt/homebrew/opt/openjdk@21 /opt/homebrew/opt/openjdk@17 /opt/homebrew/opt/openjdk \
                 /usr/lib/jvm/java-21-openjdk* /usr/lib/jvm/java-17-openjdk* \
                 "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
      [ -n "$guess" ] || continue
      # expand a possible glob
      for g in $guess; do
        if [ -x "$g/bin/javac" ]; then JAVA_HOME="$g"; break 2; fi
      done
    done
  fi
  [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ] || \
    die "no JDK 17+ found. Install one (e.g. 'brew install openjdk@21') or export JAVA_HOME."
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"

  # --- Android SDK ---
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$sdk" ] || [ ! -d "$sdk/platform-tools" -a ! -d "$sdk/platforms" ]; then
    for guess in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" /opt/android-sdk; do
      if [ -d "$guess" ]; then sdk="$guess"; break; fi
    done
  fi
  # fall back to sdk.dir already recorded in local.properties
  if [ -z "$sdk" ] && [ -f "$REPO_ROOT/local.properties" ]; then
    sdk="$(grep -m1 '^sdk.dir=' "$REPO_ROOT/local.properties" | cut -d= -f2-)"
  fi
  [ -n "$sdk" ] && [ -d "$sdk" ] || \
    die "no Android SDK found. Set ANDROID_HOME or install the SDK (~/Library/Android/sdk)."
  export ANDROID_HOME="$sdk"
  export ANDROID_SDK_ROOT="$sdk"
  # Gradle's Android plugin also reads sdk.dir from local.properties (gitignored).
  if [ "${AIRDROP_VALIDATION_ONLY:-0}" != 1 ] && \
     { [ ! -f "$REPO_ROOT/local.properties" ] || ! grep -q '^sdk.dir=' "$REPO_ROOT/local.properties" 2>/dev/null; }; then
    echo "sdk.dir=$sdk" >> "$REPO_ROOT/local.properties"
  fi

  log "JDK:       $JAVA_HOME"
  log "Android:   $ANDROID_HOME"
}

free_gb() {
  # Available GB on the volume backing APK_DIR (portable-ish: df -g on macOS).
  df -g "$APK_DIR" 2>/dev/null | awk 'NR==2 {print $4}' || echo 0
}

validate_numeric_inputs() {
  [[ "$KEEP" =~ ^[1-9][0-9]*$ ]] || die "KEEP must be an integer >= 1"
  [[ "$MIN_FREE_GB" =~ ^[0-9]+$ ]] || die "MIN_FREE_GB must be a non-negative integer"
}

latest_build_tool() {
  local tool="$1" root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools" candidate="" version
  [ -d "$root" ] || return 1
  for candidate in "$root"/*/"$tool"; do
    [ -x "$candidate" ] || continue
    version="$(basename "$(dirname "$candidate")")"
    printf '%s\t%s\n' "$version" "$candidate"
  done | sort -t. -k1,1n -k2,2n -k3,3n | tail -1 | cut -f2-
}

validate_apk() {
  local src="$1" variant="$2" aapt apksigner badging package expected name code expected_app
  [ -s "$src" ] || die "APK is missing or empty: $src"
  command -v unzip >/dev/null 2>&1 || die "unzip is required to validate APK structure"
  unzip -tq "$src" >/dev/null || die "APK ZIP validation failed: $src"
  aapt="$(latest_build_tool aapt)" || die "Android aapt was not found"
  apksigner="$(latest_build_tool apksigner)" || die "Android apksigner was not found"
  badging="$($aapt dump badging "$src")" || die "unable to read APK manifest"
  package="$(printf '%s\n' "$badging" | sed -nE "s/^package: name='([^']+)'.*/\1/p" | head -1)"
  name="$(printf '%s\n' "$badging" | sed -nE "s/^package:.* versionName='([^']*)'.*/\1/p" | head -1)"
  code="$(printf '%s\n' "$badging" | sed -nE "s/^package:.* versionCode='([^']*)'.*/\1/p" | head -1)"
  case "$variant" in
    staging*) expected="com.ga.airdrop.app.staging" ;;
    prod*) expected="com.ga.airdrop.app" ;;
    *) die "cannot validate unknown APK variant: $variant" ;;
  esac
  expected_app="$(app_version)"
  [ "$package" = "$expected" ] || die "APK package mismatch: expected $expected, found ${package:-unknown}"
  [ "$name($code)" = "$expected_app" ] || die "APK version mismatch: expected $expected_app, found ${name:-?}(${code:-?})"
  "$apksigner" verify --verbose "$src" >/dev/null || die "APK signature verification failed"
}

rollback_publish() {
  [ "$TXN_ACTIVE" = 1 ] || return 0
  rm -f "$COUNTER_FILE" "$LOG_FILE" "$APK_DIR/$LATEST_LINK" "$TXN_DIR/staged.apk" "$TXN_DIR/latest.tmp"
  [ -f "$TXN_DIR/counter.before" ] && cp -p "$TXN_DIR/counter.before" "$COUNTER_FILE"
  [ -f "$TXN_DIR/log.before" ] && cp -p "$TXN_DIR/log.before" "$LOG_FILE"
  [ -f "$TXN_DIR/latest.before" ] && ln -s "$(cat "$TXN_DIR/latest.before")" "$APK_DIR/$LATEST_LINK"
  if [ -d "$TXN_DIR/pruned" ]; then
    for f in "$TXN_DIR"/pruned/*; do [ -e "$f" ] && mv "$f" "$APK_DIR/"; done
  fi
  [ -n "${PUBLISH_DEST:-}" ] && rm -f "$PUBLISH_DEST"
  TXN_ACTIVE=0
}

release_publish_lock() {
  rollback_publish
  [ -n "$TXN_DIR" ] && rm -rf "$TXN_DIR"
  rm -f "$LOCK_DIR/pid"
  rmdir "$LOCK_DIR" 2>/dev/null || true
  if [ -n "$SELF_TEST_ROOT" ]; then rm -rf "$SELF_TEST_ROOT"; fi
}

abort_publish() {
  local status=$?
  trap - EXIT HUP INT TERM
  release_publish_lock
  [ "$status" -ne 0 ] || status=1
  exit "$status"
}

fail_at() {
  if [ "${AIRDROP_TEST_FAIL_AT:-}" = "$1" ]; then
    die "injected publication failure at $1"
  fi
}

acquire_publish_lock() {
  mkdir -p "$APK_DIR"
  mkdir "$LOCK_DIR" 2>/dev/null || die "another APK publication owns $LOCK_DIR"
  trap abort_publish EXIT HUP INT TERM
  printf '%s\n' "$$" > "$LOCK_DIR/pid"
  TXN_DIR="$(mktemp -d "$APK_DIR/.publish-txn.XXXXXX")"
  if [ "${AIRDROP_TEST_HOLD_LOCK_SECONDS:-0}" -gt 0 ]; then
    sleep "$AIRDROP_TEST_HOLD_LOCK_SECONDS"
  fi
}

# Publish one already-built APK. Validation happens before publication state is
# locked or mutated; the lock then covers number allocation through pruning.
publish_apk() {
  local src="$1" variant="$2" n dest counter_tmp latest_tmp pruned=0 listing count ndel f bytes mb
  [ -f "$src" ] || die "expected APK not found at: $src"
  validate_numeric_inputs
  [ "$SELF_TEST_MODE" = 1 ] || validate_apk "$src" "$variant"
  acquire_publish_lock
  n="$(next_build_number)"
  dest="$APK_DIR/airdrop-v${n}.apk"
  PUBLISH_DEST="$dest"
  [ ! -e "$dest" ] || die "counter collision: $dest already exists"

  [ -f "$COUNTER_FILE" ] && cp -p "$COUNTER_FILE" "$TXN_DIR/counter.before"
  [ -f "$LOG_FILE" ] && cp -p "$LOG_FILE" "$TXN_DIR/log.before"
  [ -L "$APK_DIR/$LATEST_LINK" ] && readlink "$APK_DIR/$LATEST_LINK" > "$TXN_DIR/latest.before"
  mkdir "$TXN_DIR/pruned"
  TXN_ACTIVE=1

  cp "$src" "$TXN_DIR/staged.apk"
  mv "$TXN_DIR/staged.apk" "$dest"
  fail_at after_artifact

  bytes="$(stat -f '%z' "$dest" 2>/dev/null || stat -c '%s' "$dest")"
  printf 'v%s\t%s\t%s\tapp_version=%s\tgradle_version=%s\tgit=%s\t%s bytes\n' \
    "$n" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$variant" "$(app_version)" \
    "$(gradle_version)" "$(git_sha)" "$bytes" >> "$LOG_FILE"
  fail_at after_log

  counter_tmp="$TXN_DIR/counter.tmp"
  printf '%s\n' "$n" > "$counter_tmp"
  mv "$counter_tmp" "$COUNTER_FILE"
  fail_at after_counter

  latest_tmp="$TXN_DIR/latest.tmp"
  ln -s "airdrop-v${n}.apk" "$latest_tmp"
  mv -f "$latest_tmp" "$APK_DIR/$LATEST_LINK"
  fail_at after_latest

  listing="$(for f in "$APK_DIR"/airdrop-v[0-9]*.apk; do
    [ -e "$f" ] || continue
    printf '%s\t%s\n' "$(basename "$f" | sed -E 's/^airdrop-v([0-9]+)\.apk$/\1/')" "$f"
  done | sort -n -k1,1)"
  count="$(printf '%s' "$listing" | grep -c . || true)"
  if [ "${count:-0}" -gt "$KEEP" ]; then
    ndel=$(( count - KEEP ))
    printf '%s\n' "$listing" | head -n "$ndel" | cut -f2- | while IFS= read -r f; do
      [ -n "$f" ] && mv "$f" "$TXN_DIR/pruned/"
    done
    pruned="$ndel"
  fi
  fail_at during_prune

  # Flush the same-volume artifact and ledger updates before dropping rollback
  # state. The lock remains held until this completes.
  if [ "$SELF_TEST_MODE" != 1 ]; then sync; fi

  TXN_ACTIVE=0
  rm -rf "$TXN_DIR"
  TXN_DIR=""
  rm -f "$LOCK_DIR/pid"
  rmdir "$LOCK_DIR"
  trap - EXIT HUP INT TERM
  mb="$(awk -v b="$bytes" 'BEGIN{printf "%.1f", b/1048576}')"
  step "Done."
  log "APK:     $dest  (${mb} MB)"
  log "latest:  $APK_DIR/$LATEST_LINK -> airdrop-v${n}.apk"
  log "kept:    $(ls -1 "$APK_DIR"/airdrop-v[0-9]*.apk 2>/dev/null | wc -l | tr -d ' ') build(s), pruned $pruned"
  log "install: adb install -r \"$dest\""
  echo "$dest"
}

# --- Modes -------------------------------------------------------------------
if [ "${1:-}" = "--self-test" ]; then
  trap 'rm -rf "$SELF_TEST_ROOT"' EXIT HUP INT TERM
  step "SELF-TEST — isolated bookkeeping test (no Gradle, no real APK store)"
  mkdir -p "$APK_DIR"
  tmp="$SELF_TEST_ROOT/fake.apk"
  printf 'self-test-only\n' > "$tmp"
  KEEP=2 publish_apk "$tmp" "self-test" >/dev/null
  KEEP=2 publish_apk "$tmp" "self-test" >/dev/null
  KEEP=2 publish_apk "$tmp" "self-test" >/dev/null
  trap 'rm -rf "$SELF_TEST_ROOT"' EXIT HUP INT TERM
  [ "$(cat "$COUNTER_FILE")" = 3 ] || die "self-test counter mismatch"
  [ "$(readlink "$APK_DIR/$LATEST_LINK")" = airdrop-v3.apk ] || die "self-test latest mismatch"
  [ "$(find "$APK_DIR" -name 'airdrop-v*.apk' -type f | wc -l | tr -d ' ')" = 2 ] || die "self-test retention mismatch"
  rm -rf "$SELF_TEST_ROOT"
  trap - EXIT HUP INT TERM
  step "Self-test succeeded in an isolated temporary store; no publication state was touched."
  exit 0
fi

if [ "${1:-}" = "--validate-apk" ]; then
  [ -n "${2:-}" ] && [ -n "${3:-}" ] || die "usage: --validate-apk <path> <staging|prod variant>"
  AIRDROP_VALIDATION_ONLY=1
  resolve_toolchain
  validate_apk "$2" "$3"
  step "APK validation passed; nothing was published."
  exit 0
fi

if [ "${1:-}" = "--test-publish" ] && [ "${AIRDROP_INTERNAL_TESTING:-}" = 1 ]; then
  publish_apk "${2:-}" "${3:-self-test}" >/dev/null
  exit 0
fi

validate_numeric_inputs
mkdir -p "$APK_DIR"

resolve_variant "${1:-staging}"

step "Pre-flight"
resolve_toolchain
avail="$(free_gb)"
log "variant:   $VARIANT_LABEL  ->  $GRADLE_TASK"
log "output:    $APK_DIR/"
log "free disk: ${avail} GB (guard: need >= ${MIN_FREE_GB} GB)"
if [ "${avail:-0}" -lt "$MIN_FREE_GB" ]; then
  die "only ${avail} GB free — refusing to start a Gradle build (raise with MIN_FREE_GB=… once disk is freed)."
fi

step "Building  ($GRADLE_TASK) …"
( cd "$REPO_ROOT" && ./gradlew "$GRADLE_TASK" )

# Locate the freshly-built apk (newest .apk in the variant output dir).
OUT_DIR="$REPO_ROOT/app/build/outputs/apk/$OUT_SUBDIR"
built="$(ls -1t "$OUT_DIR"/*.apk 2>/dev/null | head -1 || true)"
[ -n "${built:-}" ] || die "no .apk found in $OUT_DIR after build"

publish_apk "$built" "$VARIANT_LABEL"
