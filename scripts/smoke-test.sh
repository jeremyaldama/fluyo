#!/usr/bin/env bash
# Installs the debug APK, launches MainActivity, and fails if the app does not
# remain alive/in the foreground or if its process logs a startup crash.
#
# Prerequisites: a booting/booted device or emulator and :app:assembleDebug.
# Optional environment variables:
#   ADB=/path/to/adb
#   ANDROID_SERIAL=emulator-5554
#   SMOKE_DEVICE_TIMEOUT_SECONDS=60
#   SMOKE_BOOT_TIMEOUT_SECONDS=180
#   SMOKE_COMMAND_TIMEOUT_SECONDS=60
#   SMOKE_SETTLE_SECONDS=6
set -Eeuo pipefail

readonly PKG="com.qolve.fluyo"
readonly ACT="$PKG/.MainActivity"
readonly APK="app/build/outputs/apk/debug/app-debug.apk"
readonly SHOT_DIR="build/smoke-test"
readonly SHOT="$SHOT_DIR/smoke-$(date +%Y%m%d-%H%M%S).png"
readonly DEVICE_TIMEOUT_SECONDS="${SMOKE_DEVICE_TIMEOUT_SECONDS:-60}"
readonly BOOT_TIMEOUT_SECONDS="${SMOKE_BOOT_TIMEOUT_SECONDS:-180}"
readonly COMMAND_TIMEOUT_SECONDS="${SMOKE_COMMAND_TIMEOUT_SECONDS:-60}"
readonly SETTLE_SECONDS="${SMOKE_SETTLE_SECONDS:-6}"

for value in \
    "$DEVICE_TIMEOUT_SECONDS" \
    "$BOOT_TIMEOUT_SECONDS" \
    "$COMMAND_TIMEOUT_SECONDS" \
    "$SETTLE_SECONDS"; do
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    echo "Timeout values must be non-negative integers (received: $value)" >&2
    exit 2
  fi
done

resolve_adb() {
  if [[ -n "${ADB:-}" && -x "${ADB}" ]]; then
    printf '%s\n' "$ADB"
  elif command -v adb >/dev/null 2>&1; then
    command -v adb
  elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
    printf '%s\n' "$ANDROID_SDK_ROOT/platform-tools/adb"
  elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
    printf '%s\n' "$ANDROID_HOME/platform-tools/adb"
  else
    echo "adb was not found. Put it on PATH or set ADB/ANDROID_SDK_ROOT/ANDROID_HOME." >&2
    return 1
  fi
}

# Portable timeout helper (works on macOS without GNU coreutils). It owns and
# reaps both child PIDs, so a timed-out adb command cannot linger in the shell.
run_with_timeout() {
  local seconds="$1"
  shift

  "$@" &
  local command_pid=$!
  (
    sleep "$seconds"
    kill -TERM "$command_pid" 2>/dev/null || true
  ) &
  local watchdog_pid=$!

  local status
  set +e
  wait "$command_pid"
  status=$?
  set -e

  if kill -0 "$watchdog_pid" 2>/dev/null; then
    kill "$watchdog_pid" 2>/dev/null || true
    wait "$watchdog_pid" 2>/dev/null || true
  else
    wait "$watchdog_pid" 2>/dev/null || true
    echo "Command timed out after ${seconds}s: $*" >&2
    return 124
  fi

  return "$status"
}

if [[ ! -f "$APK" ]]; then
  echo "Debug APK not found at $APK. Run ./gradlew :app:assembleDebug first." >&2
  exit 2
fi

ADB_BIN="$(resolve_adb)"
SERIAL="${ANDROID_SERIAL:-}"

echo "==> Waiting up to ${DEVICE_TIMEOUT_SECONDS}s for one online device/emulator"
device_deadline=$((SECONDS + DEVICE_TIMEOUT_SECONDS))
while true; do
  if [[ -n "$SERIAL" ]]; then
    if [[ "$("$ADB_BIN" -s "$SERIAL" get-state 2>/dev/null || true)" == "device" ]]; then
      break
    fi
  else
    online_devices="$("$ADB_BIN" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1 }')"
    online_count="$(printf '%s\n' "$online_devices" | awk 'NF { count++ } END { print count + 0 }')"
    if [[ "$online_count" -eq 1 ]]; then
      SERIAL="$(printf '%s\n' "$online_devices" | awk 'NF { print; exit }')"
      break
    elif [[ "$online_count" -gt 1 ]]; then
      echo "More than one online device found; set ANDROID_SERIAL explicitly:" >&2
      "$ADB_BIN" devices -l >&2
      exit 2
    fi
  fi

  if (( SECONDS >= device_deadline )); then
    echo "No online device became available before the timeout." >&2
    "$ADB_BIN" devices -l >&2 || true
    exit 1
  fi
  sleep 1
done

ADB_CMD=("$ADB_BIN" -s "$SERIAL")

echo "==> Waiting up to ${BOOT_TIMEOUT_SECONDS}s for Android to finish booting"
boot_deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
while [[ "$("${ADB_CMD[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; do
  if (( SECONDS >= boot_deadline )); then
    echo "Device $SERIAL did not finish booting before the timeout." >&2
    exit 1
  fi
  sleep 2
done
echo "    device ready: $SERIAL ($("${ADB_CMD[@]}" shell getprop ro.product.model | tr -d '\r'))"

echo "==> Installing $APK"
run_with_timeout "$COMMAND_TIMEOUT_SECONDS" "${ADB_CMD[@]}" install -r -g "$APK" >/dev/null
echo "    installed"

echo "==> Clearing logcat and launching $ACT"
"${ADB_CMD[@]}" logcat -c
"${ADB_CMD[@]}" shell am force-stop "$PKG"
run_with_timeout "$COMMAND_TIMEOUT_SECONDS" \
  "${ADB_CMD[@]}" shell am start -W -n "$ACT" >/dev/null

initial_pid="$("${ADB_CMD[@]}" shell pidof -s "$PKG" 2>/dev/null | tr -d '\r' || true)"
sleep "$SETTLE_SECONDS"
current_pid="$("${ADB_CMD[@]}" shell pidof -s "$PKG" 2>/dev/null | tr -d '\r' || true)"

echo "==> Checking process and foreground activity"
focus="$("${ADB_CMD[@]}" shell dumpsys activity activities 2>/dev/null \
  | grep -iE 'mResumedActivity|ResumedActivity' | head -1 || true)"
echo "    PID after launch: ${initial_pid:-<none>}"
echo "    PID after settle: ${current_pid:-<none>}"
echo "    ${focus:-<no resumed activity>}"

logcat_output="$("${ADB_CMD[@]}" logcat -d -v threadtime 2>/dev/null || true)"
if [[ -n "$initial_pid" ]]; then
  app_log="$(printf '%s\n' "$logcat_output" \
    | grep -E "[[:space:]]${initial_pid}[[:space:]]|ANR in ${PKG}" || true)"
else
  app_log="$(printf '%s\n' "$logcat_output" | grep -F "$PKG" || true)"
fi
crash="$(printf '%s\n' "$app_log" \
  | grep -iE 'FATAL EXCEPTION|AndroidRuntime|ANR in' || true)"

fail=0
if [[ -z "$initial_pid" || -z "$current_pid" || "$initial_pid" != "$current_pid" ]]; then
  echo "    ✗ App process did not remain alive with the same PID"
  fail=1
else
  echo "    ✓ App process is alive (PID $current_pid)"
fi

if ! grep -q "$PKG" <<<"$focus"; then
  echo "    ✗ MainActivity is not in the foreground"
  fail=1
else
  echo "    ✓ Fluyo is in the foreground"
fi

if [[ -n "$crash" ]]; then
  echo "    ✗ Startup crash detected:"
  printf '%s\n' "$crash"
  fail=1
else
  echo "    ✓ No fatal exception or ANR found for the app process"
fi

echo "==> Capturing screenshot"
mkdir -p "$SHOT_DIR"
if run_with_timeout "$COMMAND_TIMEOUT_SECONDS" \
    "${ADB_CMD[@]}" exec-out screencap -p >"$SHOT" 2>/dev/null; then
  echo "    saved: $SHOT"
else
  rm -f "$SHOT"
  echo "    (screenshot skipped)"
fi

if [[ "$fail" -ne 0 ]]; then
  echo "==> SMOKE TEST FAILED"
  exit 1
fi

echo "==> SMOKE TEST PASSED ✅"
