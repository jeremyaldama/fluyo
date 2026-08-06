#!/usr/bin/env bash
# Lanza el emulador fluyo_avd, espera a que arranque, instala el APK debug y abre la app.
# Uso: ./scripts/run-fluyo.sh   (debe ejecutarse desde la raíz del repo)
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
JAVA_HOME="${JAVA_HOME:-$ANDROID_HOME/jdk17}"
export ANDROID_HOME JAVA_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

PKG="com.qolve.fluyo"
ACT="$PKG/.MainActivity"
AVD="fluyo_avd"
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "==> (1/4) Lanzando emulador '$AVD' en segundo plano..."
if ! adb get-state >/dev/null 2>&1; then
  nohup emulator -avd "$AVD" -no-snapshot-load -no-boot-anim >/tmp/fluyo-emulator.log 2>&1 &
  echo "    emulador arrancando (log: /tmp/fluyo-emulator.log)"
else
  echo "    ya hay un dispositivo conectado"
fi

echo "==> (2/4) Esperando a que el dispositivo termine de bootear..."
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
echo "    dispositivo listo"

echo "==> (3/4) Construyendo e instalando APK..."
cd "$(dirname "$0")/.."
./gradlew :app:assembleDebug --console=plain >/dev/null
adb install -r -g "$APK" >/dev/null
echo "    instalado"

echo "==> (4/4) Abriendo Fluyo..."
adb shell am start -n "$ACT" >/dev/null
echo "==> Listo ✅  La app debería verse en el emulador."
