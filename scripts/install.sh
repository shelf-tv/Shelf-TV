#!/usr/bin/env bash
# Install the latest Shelf from GitHub on a Google TV over Wi-Fi and make it the Home screen.
#
#   ./scripts/install.sh 192.168.1.42:37xxx            # latest release + replace the Google TV home screen
#   ./scripts/install.sh 192.168.1.42:37xxx --no-home  # install only (open it from Apps)
#   ./scripts/install.sh 192.168.1.42:37xxx --local    # your own build (./gradlew assembleRelease) instead
#
# Use the IP & port from Developer options → Wireless debugging (pair once first; see README).
# A bare IP means the legacy port 5555. Set SHELF_APK=/path/to/shelf.apk to install a specific file.
#
# Undo any time with ./scripts/restore.sh <ip:port>.
set -euo pipefail

usage="usage: install.sh <tv-ip[:port]> [--no-home] [--local]"
TARGET="" NO_HOME=false LOCAL=false
for arg in "$@"; do
  case "$arg" in
    --no-home) NO_HOME=true ;;
    --local) LOCAL=true ;;
    -*) echo "$usage"; exit 1 ;;
    *) TARGET="$arg" ;;
  esac
done
[[ -n "$TARGET" ]] || { echo "$usage"; exit 1; }
[[ "$TARGET" == *:* ]] || TARGET="$TARGET:5555"
SHELF="com.hayatayubi.shelf"
STOCK_LAUNCHER="com.google.android.apps.tv.launcherx"
SETUP_FALLBACK="com.google.android.tungsten.setupwraith"
LATEST="https://github.com/shelf-tv/Shelf-TV/releases/latest/download/shelf.apk"

if [[ -n "${SHELF_APK:-}" ]]; then
  APK="$SHELF_APK"
elif $LOCAL; then
  APK="$(cd "$(dirname "$0")/.." && pwd)/app/build/outputs/apk/release/app-release.apk"
else
  APK="$(mktemp -d)/shelf.apk"
  echo "→ Downloading the latest Shelf from GitHub…"
  curl -fsSL "$LATEST" -o "$APK" || { echo "Couldn't download $LATEST"; exit 1; }
fi
[[ -f "$APK" ]] || { echo "No APK at $APK. Build it first with ./gradlew assembleRelease."; exit 1; }

echo "→ Connecting to $TARGET… (accept the 'Allow debugging?' prompt on the TV, ticking 'Always allow')"
adb connect "$TARGET" >/dev/null
for _ in $(seq 1 30); do
  [[ "$(adb -s "$TARGET" get-state 2>/dev/null)" == "device" ]] && break
  sleep 2
done
[[ "$(adb -s "$TARGET" get-state 2>/dev/null)" == "device" ]] || { echo "Couldn't connect. Is USB debugging on, and are you on the same Wi-Fi?"; exit 1; }
ADB=(adb -s "$TARGET")

echo "→ Installing Shelf…"
if ! out="$("${ADB[@]}" install -r "$APK" 2>&1)"; then
  if [[ "$out" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]]; then
    # A Shelf signed with a different key (e.g. a copy you built yourself) is already on the TV.
    # Android only replaces an app with one signed by the same key, so it has to go first.
    echo
    echo "  The Shelf on this TV is signed differently from this one, so Android won't update it."
    echo "  Uninstalling it fixes that, but resets Shelf's app order and hidden apps (nothing else on the TV)."
    read -r -p "  Uninstall the old Shelf and install this one? [y/N] " answer
    [[ "$answer" == [yY]* ]] || { echo "Nothing changed."; exit 1; }
    "${ADB[@]}" uninstall "$SHELF" >/dev/null
    "${ADB[@]}" install "$APK" >/dev/null
  else
    echo "$out"
    exit 1
  fi
fi
version="$("${ADB[@]}" shell dumpsys package "$SHELF" | grep -m1 versionName= | sed 's/.*versionName=//' | tr -d '\r ')"
echo "→ Installed Shelf $version"
# Compile ahead of time so the first scroll isn't running interpreted code.
echo "→ Optimising for this device…"
"${ADB[@]}" shell cmd package compile -m speed -f "$SHELF" >/dev/null

if $NO_HOME; then
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
  echo "! Home still resolves to '$(home)'. Run ./scripts/restore.sh $TARGET, and open an issue at"
  echo "  https://github.com/shelf-tv/Shelf-TV/issues with this output."
fi
