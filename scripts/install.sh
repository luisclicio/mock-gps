#!/bin/sh
set -eu

REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"

if ! command -v adb >/dev/null 2>&1; then
    echo "adb não encontrado. Instale o Android Platform Tools no computador." >&2
    exit 1
fi

if [ ! -f "$APK" ]; then
    echo "APK não encontrado. Execute ./scripts/build.sh primeiro." >&2
    exit 1
fi

cd "$REPO_ROOT"
adb install -r "$APK"
