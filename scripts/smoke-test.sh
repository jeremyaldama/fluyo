#!/usr/bin/env bash
# Fluyo smoke test: installs the debug APK on a running emulator/device, launches the
# app, and fails if MainActivity doesn't come to the foreground or the app crashes on
# startup. This is a "does it even boot" check — not a functional/UI test suite.
#
# Prereqs: an emulator or device already connected (`adb devices`), debug APK built
# (`./gradlew :app:assembleDebug`). Usage: ./scripts/smoke-test.sh
set -euo pipefail

export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
PKG="com.qolve.fluyo"
ACT="$PKG/.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"
SHOT_DIR="build/smoke-test"
SHOT="$SHOT_DIR/smoke-$(date +%Y%m%d-%H%M%S).png"

echo "==> Waiting for a device/emulator..."
adb wait-for-device
# Block until Android finishes booting.
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  sleep 2
done
echo "    device ready: $(adb shell getprop ro.product.model | tr -d '\r')"

echo "==> Installing $APK"
adb install -r -g "$APK" >/dev/null
echo "    installed."

echo "==> Clearing logcat and launching $ACT"
adb logcat -c
adb shell am force-stop "$PKG"
adb shell am start -n "$ACT" >/dev/null

# Give the app a few seconds to start (and crash, if it's going to).
sleep 6

echo "==> Checking foreground activity"
FOCUS="$(adb shell dumpsys activity activities 2>/dev/null | grep -iE 'mResumedActivity|ResumedActivity' | head -1 || true)"
echo "    $FOCUS"

echo "==> Scanning logcat for crashes"
CRASH="$(adb logcat -d 2>/dev/null | grep -iE 'FATAL EXCEPTION|AndroidRuntime.*$PKG|ANR in $PKG' || true)"

FAIL=0
if ! echo "$FOCUS" | grep -q "$PKG"; then
  echo "    ✗ MainActivity is NOT in the foreground"
  FAIL=1
else
  echo "    ✓ Fluyo is in the foreground"
fi

if [ -n "$CRASH" ]; then
  echo "    ✗ Crash detected:"
  echo "$CRASH"
  FAIL=1
else
  echo "    ✓ No fatal exceptions in logcat"
fi

echo "==> Capturing screenshot"
mkdir -p "$SHOT_DIR"
adb exec-out screencap -p > "$SHOT" 2>/dev/null && echo "    saved: $SHOT" || echo "    (screenshot skipped)"

if [ "$FAIL" -ne 0 ]; then
  echo "==> SMOKE TEST FAILED"
  exit 1
fi
echo "==> SMOKE TEST PASSED ✅"
