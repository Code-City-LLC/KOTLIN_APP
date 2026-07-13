#!/usr/bin/env bash
set -euo pipefail

mandatory_classes=(
  "com.ga.airdrop.core.session.AuthenticatedResponseSessionBindingTest"
  "com.ga.airdrop.core.push.PushRegistrarSessionBindingTest"
  "com.ga.airdrop.feature.more.NotificationSettingsParityTest"
  "com.ga.airdrop.feature.homedetails.NotificationsScreenParityTest"
)
mandatory_class_counts=(10 9 14 3)
requested_classes=()
proof_root=""
results_root="app/build/outputs/androidTest-results/connected"
reports_root="app/build/reports/androidTests/connected"

add_requested_class() {
  local class_name="$1"
  local existing
  if [[ ! "$class_name" =~ ^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$ ]]; then
    printf 'Invalid fully qualified instrumentation class: %s\n' "$class_name" >&2
    return 1
  fi
  for existing in "${requested_classes[@]}"; do
    if [[ "$existing" == "$class_name" ]]; then
      return 0
    fi
  done
  requested_classes+=("$class_name")
}

test_classes_from_paths() {
  local path
  local relative
  while IFS= read -r path; do
    case "$path" in
      app/src/androidTest/java/*Test.kt)
        relative="${path#app/src/androidTest/java/}"
        printf '%s\n' "${relative%.kt}"
        ;;
    esac
  done | tr '/' '.' | LC_ALL=C sort -u
}

changed_test_classes() {
  local base_sha="${CONNECTED_SESSION_BASE_SHA:-}"
  local head_sha="${CONNECTED_SESSION_HEAD_SHA:-HEAD}"
  if [[ -z "$base_sha" ]]; then
    if [[ "${GITHUB_EVENT_NAME:-}" == "pull_request" ]]; then
      printf 'CONNECTED_SESSION_BASE_SHA is required for pull_request discovery\n' >&2
      return 1
    fi
    printf 'No PR base SHA supplied; selecting mandatory and explicit classes only.\n' >&2
    return 0
  fi
  if ! git rev-parse --verify --quiet "${base_sha}^{commit}" >/dev/null; then
    printf 'Unknown connected-session base commit: %s\n' "$base_sha" >&2
    return 1
  fi
  if ! git rev-parse --verify --quiet "${head_sha}^{commit}" >/dev/null; then
    printf 'Unknown connected-session head commit: %s\n' "$head_sha" >&2
    return 1
  fi
  printf 'Discovering changed instrumentation classes from %s..%s\n' "$base_sha" "$head_sha" >&2
  git diff --name-only --diff-filter=ACMRT "$base_sha" "$head_sha" -- app/src/androidTest/java \
    | test_classes_from_paths
}

add_explicit_classes() {
  local raw="$1"
  local normalized
  local class_name
  if [[ -z "${raw//[[:space:],]/}" ]]; then
    return 0
  fi
  normalized="$({ EXTRA_CLASSES="$raw" ruby <<'RUBY'
ENV.fetch("EXTRA_CLASSES").split(/[\s,]+/).reject(&:empty?).uniq.sort.each do |class_name|
  puts class_name
end
RUBY
  })"
  while IFS= read -r class_name; do
    [[ -z "$class_name" ]] || add_requested_class "$class_name"
  done <<< "$normalized"
}

resolve_requested_classes() {
  local changed
  local class_name
  requested_classes=("${mandatory_classes[@]}")
  changed="$(changed_test_classes)"
  while IFS= read -r class_name; do
    [[ -z "$class_name" ]] || add_requested_class "$class_name"
  done <<< "$changed"
  add_explicit_classes "${CONNECTED_SESSION_EXTRA_CLASSES:-}"
}

join_requested_classes() {
  local IFS=,
  printf '%s' "${requested_classes[*]}"
}

mandatory_count_contract() {
  local index
  if [[ "${#mandatory_classes[@]}" -ne "${#mandatory_class_counts[@]}" ]]; then
    printf 'Mandatory connected-session class/count contract is inconsistent\n' >&2
    return 1
  fi
  for ((index = 0; index < ${#mandatory_classes[@]}; index += 1)); do
    printf '%s=%s\n' "${mandatory_classes[index]}" "${mandatory_class_counts[index]}"
  done
}

print_requested_classes() {
  local class_name
  printf 'Connected session gate requested classes (%s):\n' "${#requested_classes[@]}"
  for class_name in "${requested_classes[@]}"; do
    printf '  %s\n' "$class_name"
  done
}

record_requested_classes() {
  local class_name
  printf '%s\n' "${requested_classes[@]}" > "$proof_root/requested-classes.txt"
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
      printf '### Connected session requested classes\n\n'
      for class_name in "${requested_classes[@]}"; do
        printf -- '- `%s`\n' "$class_name"
      done
    } >> "$GITHUB_STEP_SUMMARY"
  fi
}

assert_results() {
  local label="$1"
  local xml_root="$2"
  local requested
  local mandatory_counts
  requested="$(printf '%s\n' "${requested_classes[@]}")"
  mandatory_counts="$(mandatory_count_contract)"
  LABEL="$label" XML_ROOT="$xml_root" REQUESTED_CLASSES="$requested" MANDATORY_CLASS_COUNTS="$mandatory_counts" ruby <<'RUBY'
require "rexml/document"
require "rexml/xpath"

label = ENV.fetch("LABEL")
root = ENV.fetch("XML_ROOT")
requested = ENV.fetch("REQUESTED_CLASSES").lines.map(&:strip).reject(&:empty?)
abort "#{label}: no requested instrumentation classes" if requested.empty?
mandatory_counts = {}
ENV.fetch("MANDATORY_CLASS_COUNTS").lines.each do |line|
  class_name, count = line.strip.split("=", 2)
  mandatory_counts[class_name] = Integer(count, 10)
end

files = Dir[File.join(root, "**", "TEST-*.xml")].sort
abort "#{label}: no connected-test XML files under #{root}" if files.empty?

totals = Hash.new(0)
class_tests = Hash.new(0)
files.each do |path|
  document = REXML::Document.new(File.read(path))
  suite = document.root
  abort "#{label}: #{path} has no XML root" unless suite

  %w[tests failures errors skipped].each do |key|
    totals[key] += suite.attributes[key].to_i
  end
  REXML::XPath.each(document, "//testcase") do |testcase|
    class_tests[testcase.attributes["classname"].to_s] += 1
  end
  puts "#{label}: #{path} tests=#{suite.attributes['tests']} failures=#{suite.attributes['failures']} errors=#{suite.attributes['errors']} skipped=#{suite.attributes['skipped']}"
end

puts "#{label}: total tests=#{totals['tests']} failures=#{totals['failures']} errors=#{totals['errors']} skipped=#{totals['skipped']}"
abort "#{label}: connected tests produced no testcases" unless totals["tests"].positive?
%w[failures errors skipped].each do |key|
  abort "#{label}: expected #{key}=0, got #{totals[key]}" unless totals[key].zero?
end

requested.each do |class_name|
  expected = mandatory_counts[class_name]
  expectation = expected ? "expected=#{expected}" : "minimum=1"
  puts "#{label}: requested class=#{class_name} tests=#{class_tests[class_name]} #{expectation}"
end
mandatory_mismatches = mandatory_counts.each_with_object([]) do |(class_name, expected), mismatches|
  actual = class_tests[class_name]
  mismatches << "#{class_name} expected=#{expected} actual=#{actual}" unless actual == expected
end
abort "#{label}: mandatory class testcase count mismatch: #{mandatory_mismatches.join('; ')}" unless mandatory_mismatches.empty?

extra_classes = requested.reject { |class_name| mandatory_counts.key?(class_name) }
missing_extras = extra_classes.reject { |class_name| class_tests[class_name].positive? }
abort "#{label}: requested extra classes missing result testcases: #{missing_extras.join(', ')}" unless missing_extras.empty?
RUBY
}

preserve_flavor() {
  local flavor="$1"
  local destination="$proof_root/$flavor"
  test -d "$results_root"
  test -d "$reports_root"
  mkdir -p "$destination"
  cp -R "$results_root" "$destination/results"
  cp -R "$reports_root" "$destination/reports"
  assert_results "$flavor" "$destination/results"
}

write_xml_fixture() {
  local destination="$1"
  shift
  local fixture_classes
  fixture_classes="$(printf '%s\n' "$@")"
  DESTINATION="$destination" FIXTURE_CLASSES="$fixture_classes" ruby <<'RUBY'
require "fileutils"
require "rexml/document"
require "rexml/formatters/pretty"

classes = ENV.fetch("FIXTURE_CLASSES").lines.map(&:strip).reject(&:empty?)
FileUtils.mkdir_p(ENV.fetch("DESTINATION"))
document = REXML::Document.new
suite = document.add_element("testsuite", {
  "name" => "connected-session-self-test",
  "tests" => classes.length.to_s,
  "failures" => "0",
  "errors" => "0",
  "skipped" => "0",
})
classes.each_with_index do |class_name, index|
  suite.add_element("testcase", {
    "name" => "fixture_#{index}",
    "classname" => class_name,
  })
end
File.open(File.join(ENV.fetch("DESTINATION"), "TEST-connected-session-self-test.xml"), "w") do |file|
  REXML::Formatters::Pretty.new(2).write(document, file)
end
RUBY
}

run_self_test() (
  set -euo pipefail
  local discovered
  local class_name
  local actual
  local expected
  local temp_root
  local index
  local repeat
  local valid_fixture_classes=()
  local missing_class_fixture=()
  local wrong_mandatory_count_fixture=()

  requested_classes=("${mandatory_classes[@]}")
  discovered="$(printf '%s\n' \
    'app/src/androidTest/java/com/ga/airdrop/feature/more/TextSizeSettingsRowParityTest.kt' \
    'app/src/androidTest/java/com/ga/airdrop/core/session/FakeAuthenticatedSessionBoundary.kt' \
    'README.md' \
    | test_classes_from_paths)"
  while IFS= read -r class_name; do
    [[ -z "$class_name" ]] || add_requested_class "$class_name"
  done <<< "$discovered"
  add_explicit_classes 'com.ga.airdrop.feature.more.SettingsCompactLargestParityTest, com.ga.airdrop.feature.more.TextSizeSettingsRowParityTest'

  actual="$(join_requested_classes)"
  expected="com.ga.airdrop.core.session.AuthenticatedResponseSessionBindingTest,com.ga.airdrop.core.push.PushRegistrarSessionBindingTest,com.ga.airdrop.feature.more.NotificationSettingsParityTest,com.ga.airdrop.feature.homedetails.NotificationsScreenParityTest,com.ga.airdrop.feature.more.TextSizeSettingsRowParityTest,com.ga.airdrop.feature.more.SettingsCompactLargestParityTest"
  if [[ "$actual" != "$expected" ]]; then
    printf 'class-selection self-test mismatch\nexpected: %s\nactual:   %s\n' "$expected" "$actual" >&2
    return 1
  fi
  if add_explicit_classes 'not-a-qualified-class' >/dev/null 2>&1; then
    printf 'invalid explicit class unexpectedly passed validation\n' >&2
    return 1
  fi

  temp_root="$(mktemp -d "${TMPDIR:-/tmp}/connected-session-gate-self-test.XXXXXX")"
  trap 'rm -rf "$temp_root"' EXIT
  for ((index = 0; index < ${#mandatory_classes[@]}; index += 1)); do
    for ((repeat = 0; repeat < mandatory_class_counts[index]; repeat += 1)); do
      valid_fixture_classes+=("${mandatory_classes[index]}")
    done
  done
  for ((index = ${#mandatory_classes[@]}; index < ${#requested_classes[@]}; index += 1)); do
    valid_fixture_classes+=("${requested_classes[index]}")
  done

  write_xml_fixture "$temp_root/prod" "${valid_fixture_classes[@]}"
  write_xml_fixture "$temp_root/staging" "${valid_fixture_classes[@]}"
  assert_results self-test-prod "$temp_root/prod"
  assert_results self-test-staging "$temp_root/staging"

  for ((index = 0; index < ${#valid_fixture_classes[@]} - 1; index += 1)); do
    missing_class_fixture+=("${valid_fixture_classes[index]}")
  done
  write_xml_fixture "$temp_root/missing" "${missing_class_fixture[@]}"
  if assert_results self-test-missing "$temp_root/missing" >/dev/null 2>&1; then
    printf 'missing-class XML self-test unexpectedly passed\n' >&2
    return 1
  fi

  for ((index = 1; index < ${#valid_fixture_classes[@]}; index += 1)); do
    wrong_mandatory_count_fixture+=("${valid_fixture_classes[index]}")
  done
  write_xml_fixture "$temp_root/wrong-mandatory-count" "${wrong_mandatory_count_fixture[@]}"
  if assert_results self-test-wrong-mandatory-count "$temp_root/wrong-mandatory-count" >/dev/null 2>&1; then
    printf 'wrong-mandatory-count XML self-test unexpectedly passed\n' >&2
    return 1
  fi
  printf 'connected-session-gate self-test: PASS\n'
)

main() {
  local mode="${1:-}"
  local classes_csv
  if [[ $# -gt 1 ]]; then
    printf 'Usage: %s [--dry-run|--self-test]\n' "$0" >&2
    return 2
  fi
  case "$mode" in
    --self-test)
      run_self_test
      return
      ;;
    --dry-run|"")
      ;;
    *)
      printf 'Usage: %s [--dry-run|--self-test]\n' "$0" >&2
      return 2
      ;;
  esac

  resolve_requested_classes
  print_requested_classes
  if [[ "$mode" == "--dry-run" || "${CONNECTED_SESSION_DRY_RUN:-0}" == "1" ]]; then
    return
  fi

  proof_root="${RUNNER_TEMP:?RUNNER_TEMP is required}/connected-session-proof"
  rm -rf "$proof_root"
  mkdir -p "$proof_root"
  record_requested_classes
  classes_csv="$(join_requested_classes)"

  ./gradlew --no-daemon --stacktrace :app:connectedProdDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$classes_csv"
  preserve_flavor prod

  rm -rf "$results_root" "$reports_root"
  ./gradlew --no-daemon --stacktrace :app:connectedStagingDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$classes_csv"
  preserve_flavor staging

  rm -rf "$results_root" "$reports_root"
}

main "$@"
