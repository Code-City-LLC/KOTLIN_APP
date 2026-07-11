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
#   Env:
#     KEEP=<n>   override retention count (default 3)
#     MIN_FREE_GB=<n>   override pre-flight free-disk guard (default 3)
# ---------------------------------------------------------------------------
set -euo pipefail

# --- Resolve repo root from this script's location (works in any worktree) ---
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." >/dev/null 2>&1 && pwd -P)"

# Single output location. Defaults to <repo>/apk for local builds; the Mini
# build host overrides this to the Desktop store via AIRDROP_APK_DIR.
APK_DIR="${AIRDROP_APK_DIR:-$REPO_ROOT/apk}"
COUNTER_FILE="$APK_DIR/.build-number"
LOG_FILE="$APK_DIR/BUILD_LOG.txt"
LATEST_LINK="airdrop-latest.apk"          # relative name inside APK_DIR
KEEP="${KEEP:-3}"
MIN_FREE_GB="${MIN_FREE_GB:-3}"

log()  { printf '  %s\n' "$*"; }
die()  { printf '\n[build-apk] ERROR: %s\n' "$*" >&2; exit 1; }
step() { printf '\n[build-apk] %s\n' "$*"; }

# --- Map friendly variant -> Gradle task + output subdir ---------------------
resolve_variant() {
  case "${1:-staging}" in
    staging|staging-debug)   GRADLE_TASK="assembleStagingDebug";   OUT_SUBDIR="stagingDebug"  ;;
    staging-release)         GRADLE_TASK="assembleStagingRelease"; OUT_SUBDIR="stagingRelease";;
    prod|prod-debug)         GRADLE_TASK="assembleProdDebug";      OUT_SUBDIR="prodDebug"     ;;
    prod-release)            GRADLE_TASK="assembleProdRelease";    OUT_SUBDIR="prodRelease"   ;;
    *) die "unknown variant '$1' (use: staging | staging-release | prod | prod-release)";;
  esac
  VARIANT_LABEL="$1"
}

next_build_number() {
  local n=0
  [ -f "$COUNTER_FILE" ] && n="$(tr -dc '0-9' < "$COUNTER_FILE")"
  [ -z "$n" ] && n=0
  echo $(( n + 1 ))
}

gradle_version() {
  local gradle_file="$REPO_ROOT/app/build.gradle.kts"
  local name code
  name="$(grep -m1 -E 'versionName[[:space:]]*=' "$gradle_file" 2>/dev/null | grep -oE '"[^"]+"' | tr -d '"')"
  code="$(grep -m1 -E 'versionCode[[:space:]]*=' "$gradle_file" 2>/dev/null | grep -oE '[0-9]+' | head -1)"
  echo "${name:-?}(${code:-?})"
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
  if [ ! -f "$REPO_ROOT/local.properties" ] || ! grep -q '^sdk.dir=' "$REPO_ROOT/local.properties" 2>/dev/null; then
    echo "sdk.dir=$sdk" >> "$REPO_ROOT/local.properties"
  fi

  log "JDK:       $JAVA_HOME"
  log "Android:   $ANDROID_HOME"
}

free_gb() {
  # Available GB on the volume backing APK_DIR (portable-ish: df -g on macOS).
  df -g "$APK_DIR" 2>/dev/null | awk 'NR==2 {print $4}' || echo 0
}

# --- Publish a produced .apk under the versioned name, prune, symlink, log ----
# $1 = path to the freshly-built apk, $2 = variant label
publish_apk() {
  local src="$1" variant="$2"
  [ -f "$src" ] || die "expected APK not found at: $src"

  local n dest
  n="$(next_build_number)"
  dest="$APK_DIR/airdrop-v${n}.apk"

  cp -f "$src" "$dest"
  echo "$n" > "$COUNTER_FILE"

  # latest symlink (relative, so it survives moves)
  ln -sfn "airdrop-v${n}.apk" "$APK_DIR/$LATEST_LINK"

  # prune: keep the $KEEP highest-numbered builds, delete the rest.
  # Portable across macOS bash 3.2 (no `mapfile`, no GNU `head -n -N`). Key the
  # sort on the extracted number, never the path (which may itself contain 'v').
  local pruned=0 listing count ndel f
  listing="$(
    for f in "$APK_DIR"/airdrop-v[0-9]*.apk; do
      [ -e "$f" ] || continue
      printf '%s\t%s\n' "$(basename "$f" | sed -E 's/^airdrop-v([0-9]+)\.apk$/\1/')" "$f"
    done | sort -n -k1,1
  )"
  count="$(printf '%s' "$listing" | grep -c . || true)"
  if [ "${count:-0}" -gt "$KEEP" ]; then
    ndel=$(( count - KEEP ))
    printf '%s\n' "$listing" | head -n "$ndel" | cut -f2- | while IFS= read -r f; do
      [ -n "$f" ] && rm -f "$f"
    done
    pruned="$ndel"
  fi

  local bytes mb
  bytes="$(stat -f '%z' "$dest" 2>/dev/null || stat -c '%s' "$dest")"
  mb="$(awk -v b="$bytes" 'BEGIN{printf "%.1f", b/1048576}')"

  printf 'v%s\t%s\t%s\tgradle=%s\tgit=%s\t%s bytes\n' \
    "$n" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$variant" "$(gradle_version)" "$(git_sha)" "$bytes" \
    >> "$LOG_FILE"

  step "Done."
  log "APK:     $dest  (${mb} MB)"
  log "latest:  $APK_DIR/$LATEST_LINK -> airdrop-v${n}.apk"
  log "kept:    $(ls -1 "$APK_DIR"/airdrop-v[0-9]*.apk 2>/dev/null | wc -l | tr -d ' ') build(s), pruned $pruned"
  log "install: adb install -r \"$dest\""
  # Expose the path for callers/automation.
  echo "$dest"
}

# --- Modes -------------------------------------------------------------------
mkdir -p "$APK_DIR"

if [ "${1:-}" = "--self-test" ]; then
  step "SELF-TEST — fabricating a dummy APK to exercise bookkeeping (no Gradle)"
  tmp="$(mktemp -t airdrop-selftest-XXXX).apk"
  head -c 1048576 /dev/urandom > "$tmp"     # 1 MB fake apk
  publish_apk "$tmp" "self-test" >/dev/null
  rm -f "$tmp"
  step "Self-test build succeeded (see apk/ + BUILD_LOG.txt)."
  exit 0
fi

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
