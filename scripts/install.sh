#!/usr/bin/env bash
# Install Shelf on a Chromecast with Google TV over Wi-Fi and make it the Home screen.
#
#   ./scripts/install.sh 192.168.1.42:37xxx            # install + replace the Google TV home screen
#   ./scripts/install.sh 192.168.1.42:37xxx --no-home  # install only (open it from Apps / a button remap)
#
# Use the IP & port from Developer options → Wireless debugging (pair once first; see README).
# A bare IP means the legacy port 5555.
#
# Undo any time with ./scripts/restore.sh <ip>.
set -euo pipefail

TARGET="${1:?usage: install.sh <chromecast-ip[:port]> [--no-home]}"
[[ "$TARGET" == *:* ]] || TARGET="$TARGET:5555"
MODE="${2:-}"
SHELF="com.hayatayubi.shelf"
STOCK_LAUNCHER="com.google.android.apps.tv.launcherx"
SETUP_FALLBACK="com.google.android.tungsten.setupwraith"
LATEST="https://github.com/hayatayubi/shelf-tv/releases/latest/download/shelf.apk"

# Use $SHELF_APK, else a local build, else the latest release.
APK="${SHELF_APK:-$(cd "$(dirname "$0")/.." && pwd)/app/build/outputs/apk/release/app-release.apk}"
if [[ ! -f "$APK" ]]; then
  APK="$(mktemp -d)/shelf.apk"
  echo "→ Downloading the latest Shelf…"
  curl -fsSL "$LATEST" -o "$APK" || { echo "Couldn't download $LATEST"; exit 1; }
fi

echo "→ Connecting to $TARGET… (accept the 'Allow debugging?' prompt on the TV, ticking 'Always allow')"
adb connect "$TARGET" >/dev/null
for _ in $(seq 1 30); do
  [[ "$(adb -s "$TARGET" get-state 2>/dev/null)" == "device" ]] && break
  sleep 2
done
[[ "$(adb -s "$TARGET" get-state 2>/dev/null)" == "device" ]] || { echo "Couldn't connect. Is USB debugging on, and are you on the same Wi-Fi?"; exit 1; }
ADB=(adb -s "$TARGET")

echo "→ Installing Shelf…"
"${ADB[@]}" install -r "$APK" >/dev/null
# Compile ahead of time so the first scroll isn't running interpreted code.
echo "→ Optimising for this device…"
"${ADB[@]}" shell cmd package compile -m speed -f "$SHELF" >/dev/null

if [[ "$MODE" == "--no-home" ]]; then
  echo "✓ Installed. Open Shelf from Google TV's Apps row."
  exit 0
fi

# Google TV's launcher claims Home at a higher priority than any third-party app,
# so the only way to make Shelf the Home screen is to switch the stock one off.
echo "→ Switching off the Google TV home screen…"
"${ADB[@]}" shell pm disable-user --user 0 "$STOCK_LAUNCHER" >/dev/null
"${ADB[@]}" shell cmd package set-home-activity "$SHELF/.MainActivity" >/dev/null || true

home() { "${ADB[@]}" shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME | grep -m1 packageName= | sed 's/.*packageName=//' | tr -d '\r '; }

# On some firmware the setup app steps in as Home once the launcher is gone.
if [[ "$(home)" == "$SETUP_FALLBACK" ]]; then
  "${ADB[@]}" shell pm disable-user --user 0 "$SETUP_FALLBACK" >/dev/null
fi

"${ADB[@]}" shell input keyevent KEYCODE_HOME
if [[ "$(home)" == "$SHELF" ]]; then
  echo "✓ Shelf is now your Home screen."
  enabled="$("${ADB[@]}" shell settings get secure enabled_accessibility_services | tr -d '\r')"
  if [[ "$enabled" != *"$SHELF/"* ]]; then
    echo
    echo "  One step left so the remote's Home button always opens Shelf:"
    echo "  on the TV, Settings → System → Accessibility → Shelf Home Button → turn it on."
  fi
else
  echo "! Home still resolves to '$(home)'. Run ./scripts/restore.sh $TARGET and send me this output."
fi
