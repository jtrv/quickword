#!/usr/bin/env bash
# The whole gate. One command, cargo/clippy-style: format -> analyze/lint -> test,
# across every Dart workspace package and every Gradle build in the repo.
# Exit non-zero on any failure; print a findable summary, not a wall of logs.
set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

FAILURES=()
STEP_NO=0

run_step() { # run_step <label> <cmd...>
  STEP_NO=$((STEP_NO + 1))
  local label="$1"; shift
  local start=$SECONDS
  printf '\n\033[1m[%d] %s\033[0m\n' "$STEP_NO" "$label"
  if "$@"; then
    printf '\033[32m    ok\033[0m (%ss)\n' "$((SECONDS - start))"
  else
    printf '\033[31m    FAIL\033[0m (%ss)\n' "$((SECONDS - start))"
    FAILURES+=("$label")
  fi
}

# Summarise a dart test JSON report: test name, error, first repo stack frame.
summarize_dart_tests() { # <json-file>
  python3 - "$1" <<'EOF'
import json, sys
tests, errors = {}, []
for line in open(sys.argv[1], encoding="utf-8"):
    try: ev = json.loads(line)
    except ValueError: continue
    if ev.get("type") == "testStart":
        tests[ev["test"]["id"]] = ev["test"]["name"]
    elif ev.get("type") == "error":
        frame = next((l for l in ev.get("stackTrace", "").splitlines()
                      if "package:" in l or "test/" in l), "")
        errors.append((tests.get(ev["testID"], "?"), ev.get("error", ""), frame))
for name, err, frame in errors:
    print(f"  ✗ {name}\n    {err.splitlines()[0] if err else ''}\n    {frame.strip()}")
if errors: print(f"  {len(errors)} failing test(s)")
EOF
}

dart_test_step() { # <pkg-dir> <tool: dart|flutter>
  local pkg="$1" tool="$2"
  mkdir -p "$pkg/build"
  local report="$pkg/build/test-results.json"
  if (cd "$pkg" && "$tool" test --file-reporter "json:build/test-results.json" >/dev/null 2>&1); then
    return 0
  fi
  summarize_dart_tests "$report"
  return 1
}

# --- Preconditions (in the script, not in your head) -------------------------
[ -d "${TMPDIR:-/tmp}" ] || { echo "TMPDIR '$TMPDIR' does not exist — flutter/dart test will die confusingly"; exit 1; }

# --- Discover work ------------------------------------------------------------
# Dart packages: root workspace manifest if present, else any tracked pubspec.yaml.
# Split flutter- from dart-tooled by whether the pubspec depends on the Flutter SDK.
DART_PKGS=()
if [ -f pubspec.yaml ]; then
  while IFS= read -r p; do DART_PKGS+=("$p"); done \
    < <(python3 -c '
import re, sys, pathlib
root = pathlib.Path(".")
text = (root / "pubspec.yaml").read_text()
m = re.search(r"^workspace:\s*\n((?:\s+-\s+.*\n?)+)", text, re.M)
if m:
    for line in m.group(1).splitlines():
        print(line.strip().lstrip("- ").strip())
else:
    print(".")
')
fi

GRADLE_DIRS=()
while IFS= read -r g; do GRADLE_DIRS+=("$(dirname "$g")"); done \
  < <(find . -maxdepth 2 -name "gradlew" -not -path "./reference/*" 2>/dev/null)

if [ ${#DART_PKGS[@]} -eq 0 ] && [ ${#GRADLE_DIRS[@]} -eq 0 ]; then
  echo "verify.sh: found no Dart packages and no Gradle builds — nothing verified is not a pass." >&2
  exit 1
fi

# --- Dart / Flutter -----------------------------------------------------------
if [ ${#DART_PKGS[@]} -gt 0 ]; then
  # Format git's view of the tree (tracked + untracked-not-ignored), never build output.
  run_step "dart format (repo, git view)" bash -c '
    git ls-files --cached --others --exclude-standard -- "*.dart" \
      | xargs -r dart format --output=none --set-exit-if-changed'
fi
for pkg in "${DART_PKGS[@]}"; do
  tool=dart
  grep -q "sdk: flutter" "$pkg/pubspec.yaml" 2>/dev/null && tool=flutter
  run_step "analyze $pkg" bash -c "cd '$pkg' && $tool analyze --fatal-infos --fatal-warnings"
  [ -d "$pkg/test" ] && run_step "test $pkg" dart_test_step "$pkg" "$tool"
done

# --- Kotlin / Android ----------------------------------------------------------
# `check` is the Gradle lifecycle verification task; ktlint, detekt, Android Lint
# and unit tests all attach to it. Configure the plugins in the build, not here.
for gdir in "${GRADLE_DIRS[@]}"; do
  run_step "gradle check ($gdir)" bash -c "cd '$gdir' && ./gradlew --console=plain -q check"
done

# --- Summary -------------------------------------------------------------------
echo
if [ ${#FAILURES[@]} -gt 0 ]; then
  printf '\033[31mGATE RED\033[0m — %d step(s) failed:\n' "${#FAILURES[@]}"
  printf '  ✗ %s\n' "${FAILURES[@]}"
  exit 1
fi
printf '\033[32mGATE GREEN\033[0m — %d step(s) passed.\n' "$STEP_NO"
