#!/usr/bin/env bash
# Run the Health Trainer :core unit tests and report an authoritative pass/fail count
# plus the :core purity check. Works WITHOUT a system gradle (uses ./gradlew) and WITHOUT
# an Android SDK (settings.gradle.kts excludes :app, so :core:test runs standalone).
#
# Usage:
#   core-test.sh            # run :core:test (incremental)
#   core-test.sh --rerun    # force re-execution (ignore up-to-date cache)
#
# Exit codes: 0 = tests passed and :core is pure; non-zero = test failure or purity violation.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

if [ ! -x ./gradlew ]; then
  echo "ERROR: ./gradlew not found. Run scripts/bootstrap-gradle.sh first." >&2
  exit 2
fi

RERUN_FLAG=""
if [ "${1:-}" = "--rerun" ]; then RERUN_FLAG="--rerun-tasks"; fi

echo "== Running :core:test ${RERUN_FLAG} =="
./gradlew :core:test ${RERUN_FLAG}

echo ""
echo "== Authoritative totals (JUnit XML) =="
xml_dir="core/build/test-results/test"
if ls "$xml_dir"/*.xml >/dev/null 2>&1; then
  awk -F'"' '
    /<testsuite / {
      for (i = 1; i <= NF; i++) {
        if ($(i-1) ~ /tests=/)    t += $i
        if ($(i-1) ~ /failures=/) f += $i
        if ($(i-1) ~ /errors=/)   e += $i
      }
    }
    END { printf "TOTAL: tests=%d failures=%d errors=%d\n", t, f, e }
  ' "$xml_dir"/*.xml
else
  echo "WARN: no JUnit XML found at $xml_dir (no tests ran?)."
fi

echo ""
echo "== :core purity (no Android/MediaPipe imports) =="
if grep -rnE 'import (android|androidx|com\.google\.mediapipe)' core/src; then
  echo "PURITY VIOLATION: :core must stay Android-free (so it runs with only a JDK)." >&2
  exit 1
fi
echo "PURITY OK: :core is Android-free."
