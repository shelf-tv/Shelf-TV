#!/usr/bin/env bash
# Put the stock Google TV home screen back (Shelf stays installed; uninstall it from Settings if you like).
#
#   ./scripts/restore.sh 192.168.1.42:37xxx   (Wireless debugging IP & port; bare IP = port 5555)
set -euo pipefail

TARGET="${1:?usage: restore.sh <chromecast-ip[:port]>}"
[[ "$TARGET" == *:* ]] || TARGET="$TARGET:5555"
adb connect "$TARGET" >/dev/null
ADB=(adb -s "$TARGET")

for pkg in com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith; do
  "${ADB[@]}" shell pm enable "$pkg" >/dev/null 2>&1 || true
done
"${ADB[@]}" shell input keyevent KEYCODE_HOME
echo "✓ Google TV home screen restored."
