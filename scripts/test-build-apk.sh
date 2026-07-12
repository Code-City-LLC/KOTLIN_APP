#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd -P)"
BUILDER="$SCRIPT_DIR/build-apk.sh"
ROOT="$(mktemp -d "${TMPDIR:-/tmp}/airdrop-apk-tests.XXXXXX")"
trap 'rm -rf "$ROOT"' EXIT HUP INT TERM

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS: %s\n' "$*"; }

snapshot() {
  local dir="$1" path rel
  for path in "$dir"/* "$dir"/.[!.]*; do
    [ -e "$path" ] || [ -L "$path" ] || continue
    rel="${path#$dir/}"
    case "$rel" in .publish.lock|.publish-txn.*) continue ;; esac
    if [ -L "$path" ]; then
      printf 'link\t%s\t%s\n' "$rel" "$(readlink "$path")"
    elif [ -f "$path" ]; then
      printf 'file\t%s\t%s\n' "$rel" \
        "$(shasum -a 256 "$path" | awk '{print $1}')"
    fi
  done | sort
}

test_publish() {
  local store="$1" keep="${2:-3}"
  shift 2 || true
  AIRDROP_INTERNAL_TESTING=1 KEEP="$keep" "$@" "$BUILDER" \
    --test-publish "$ROOT/fake.apk" self-test "$store"
}

printf 'fake-apk-for-bookkeeping-tests\n' > "$ROOT/fake.apk"

# Self-test must ignore an exported real store byte-for-byte and inode-for-inode.
real="$ROOT/real publication"
mkdir -p "$real"
printf '41\n' > "$real/.build-number"
printf 'existing ledger\n' > "$real/BUILD_LOG.txt"
printf 'real apk bytes\n' > "$real/airdrop-v41.apk"
ln -s airdrop-v41.apk "$real/airdrop-latest.apk"
before="$(snapshot "$real")"
inode_before="$(for f in "$real/.build-number" "$real/BUILD_LOG.txt" "$real/airdrop-v41.apk"; do
  stat -f '%i' "$f" 2>/dev/null || stat -c '%i' "$f"
done)"
mkdir -p "$ROOT/self-test-tmp"
TMPDIR="$ROOT/self-test-tmp" AIRDROP_APK_DIR="$real" "$BUILDER" --self-test >/dev/null
[ "$(snapshot "$real")" = "$before" ] || fail "self-test mutated the exported publication store"
inode_after="$(for f in "$real/.build-number" "$real/BUILD_LOG.txt" "$real/airdrop-v41.apk"; do
  stat -f '%i' "$f" 2>/dev/null || stat -c '%i' "$f"
done)"
[ "$inode_after" = "$inode_before" ] || fail "self-test replaced a real publication inode"
[ -z "$(find "$ROOT/self-test-tmp" -mindepth 1 -print -quit)" ] || fail "self-test leaked temporary files"
pass "self-test is isolated from the real publication store"

# Spaces, monotonic numbering, numeric retention, latest, and split provenance.
store="$ROOT/store with spaces"
mkdir -p "$store"
for _ in 1 2 3 4 5; do test_publish "$store" 3; done
[ "$(cat "$store/.build-number")" = 5 ] || fail "counter is not monotonic"
[ "$(readlink "$store/airdrop-latest.apk")" = airdrop-v5.apk ] || fail "latest is not atomic/current"
[ "$(find "$store" -name 'airdrop-v*.apk' -type f | wc -l | tr -d ' ')" = 3 ] || fail "retention count is wrong"
[ ! -e "$store/airdrop-v2.apk" ] && [ -e "$store/airdrop-v3.apk" ] || fail "retention is not numeric"
grep -q 'app_version=.*gradle_version=' "$store/BUILD_LOG.txt" || fail "provenance fields are not split"
pass "spaces, monotonic counter, retention, latest, and provenance"

# Malformed inputs fail without mutation.
bad="$ROOT/bad inputs"
mkdir -p "$bad"
printf 'not-a-number\n' > "$bad/.build-number"
bad_before="$(snapshot "$bad")"
if test_publish "$bad" 3 >/dev/null 2>&1; then fail "malformed counter was accepted"; fi
[ "$(snapshot "$bad")" = "$bad_before" ] || fail "malformed counter mutated state"
rm -f "$bad/.build-number"
bad_before="$(snapshot "$bad")"
if test_publish "$bad" 0 >/dev/null 2>&1; then fail "KEEP=0 was accepted"; fi
[ "$(snapshot "$bad")" = "$bad_before" ] || fail "invalid KEEP mutated state"
pass "malformed counter and KEEP fail closed"

if AIRDROP_INTERNAL_TESTING=1 "$BUILDER" --test-publish "$ROOT/fake.apk" self-test \
  "/Users/Shared/forbidden-apk-test-store" >/dev/null 2>&1; then
  fail "internal fake publisher escaped TMPDIR"
fi
[ ! -e /Users/Shared/forbidden-apk-test-store ] || fail "forbidden test store was created"
pass "internal fake publication is confined to TMPDIR"

# One publisher owns allocation through commit; a concurrent publisher fails.
race="$ROOT/concurrent"
mkdir -p "$race"
test_publish "$race" 3 env AIRDROP_TEST_HOLD_LOCK_SECONDS=2 >/dev/null &
owner=$!
sleep 1
if test_publish "$race" 3 >/dev/null 2>&1; then fail "concurrent publisher acquired the lock"; fi
wait "$owner"
[ "$(cat "$race/.build-number")" = 1 ] || fail "concurrency changed allocation"
[ ! -e "$race/.publish.lock" ] || fail "publication lock leaked"
pass "concurrent publication is locked"

# A terminated publisher releases its lock and leaves the prior state intact.
interrupted="$ROOT/interrupted"
mkdir -p "$interrupted"
test_publish "$interrupted" 3 >/dev/null
interrupted_before="$(snapshot "$interrupted")"
AIRDROP_INTERNAL_TESTING=1 KEEP=3 AIRDROP_TEST_HOLD_LOCK_SECONDS=10 \
  "$BUILDER" --test-publish "$ROOT/fake.apk" self-test "$interrupted" >/dev/null 2>&1 &
publisher=$!
sleep 1
kill -TERM "$publisher"
if wait "$publisher"; then fail "terminated publisher exited successfully"; fi
[ "$(snapshot "$interrupted")" = "$interrupted_before" ] || fail "signal interruption mutated state"
[ ! -e "$interrupted/.publish.lock" ] || fail "signal interruption leaked its lock"
pass "signal interruption is rollback-safe"

# Every injected failure restores counter, log, link, retained APKs, and lock.
rollback="$ROOT/rollback"
mkdir -p "$rollback"
for _ in 1 2 3; do test_publish "$rollback" 3 >/dev/null; done
for point in after_artifact after_log after_counter after_latest during_prune; do
  state="$(snapshot "$rollback")"
  if test_publish "$rollback" 3 env AIRDROP_TEST_FAIL_AT="$point" >/dev/null 2>&1; then
    fail "injected failure $point unexpectedly succeeded"
  fi
  [ "$(snapshot "$rollback")" = "$state" ] || fail "rollback mismatch after $point"
  [ ! -e "$rollback/.publish.lock" ] || fail "lock leaked after $point"
  [ -z "$(find "$rollback" -maxdepth 1 -name '.publish-txn.*' -print -quit)" ] || fail "transaction leaked after $point"
done
pass "failure injection rolls publication state back"

printf 'All APK publication regression tests passed.\n'
