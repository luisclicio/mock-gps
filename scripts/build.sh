#!/bin/sh
set -eu

REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
cd "$REPO_ROOT"

docker compose run --rm android ./gradlew assembleDebug
printf '\nAPK criado em: %s\n' "$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
