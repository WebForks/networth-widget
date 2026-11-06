#!/usr/bin/env bash
# Build the app and install it on the phone connected over USB.
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk-21}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No phone found. Enable USB debugging, plug in, and accept the prompt on the phone."
  "$ADB" devices
  exit 1
fi

./gradlew installRelease
"$ADB" shell am start -n com.webforks.networth/.MainActivity
echo "Installed. Add the widget: long-press home screen → Widgets → Net Worth."
