#!/bin/sh
set -eu

REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
cd "$REPO_ROOT"

docker compose run --rm android ./gradlew testDebugUnitTest lintDebug
