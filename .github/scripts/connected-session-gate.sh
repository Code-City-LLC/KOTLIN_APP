#!/usr/bin/env bash
set -euo pipefail
classes="com.ga.airdrop.core.session.AuthenticatedResponseSessionBindingTest,com.ga.airdrop.core.push.PushRegistrarSessionBindingTest,com.ga.airdrop.feature.more.NotificationSettingsParityTest,com.ga.airdrop.feature.homedetails.NotificationsScreenParityTest"
proof_root="${RUNNER_TEMP:?RUNNER_TEMP is required}/connected-session-proof"
results_root="app/build/outputs/androidTest-results/connected"
reports_root="app/build/reports/androidTests/connected"
rm -rf "$proof_root"
mkdir -p "$proof_root"

assert_results() {
  local label="$1"
  local xml_root="$2"
  local expected="$3"
  LABEL="$label" XML_ROOT="$xml_root" EXPECTED="$expected" ruby <<'RUBY'
require "rexml/document"

label = ENV.fetch("LABEL")
root = ENV.fetch("XML_ROOT")
expected = Integer(ENV.fetch("EXPECTED"), 10)
files = Dir[File.join(root, "**", "TEST-*.xml")]
abort "#{label}: no connected-test XML files under #{root}" if files.empty?

totals = Hash.new(0)
files.each do |path|
  suite = REXML::Document.new(File.read(path)).root
  %w[tests failures errors skipped].each do |key|
    totals[key] += suite.attributes[key].to_i
  end
  puts "#{label}: #{path} tests=#{suite.attributes['tests']} failures=#{suite.attributes['failures']} errors=#{suite.attributes['errors']} skipped=#{suite.attributes['skipped']}"
end

puts "#{label}: total tests=#{totals['tests']} failures=#{totals['failures']} errors=#{totals['errors']} skipped=#{totals['skipped']}"
required = {
  "tests" => expected,
  "failures" => 0,
  "errors" => 0,
  "skipped" => 0,
}
abort "#{label}: expected #{required.inspect}, got #{totals.inspect}" unless totals == required
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
  assert_results "$flavor" "$destination/results" 36
}

./gradlew --no-daemon --stacktrace :app:connectedProdDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$classes"
preserve_flavor prod

rm -rf "$results_root" "$reports_root"
./gradlew --no-daemon --stacktrace :app:connectedStagingDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$classes"
preserve_flavor staging

assert_results aggregate "$proof_root" 72
rm -rf "$results_root" "$reports_root"
