#!/usr/bin/env bash
# Builds the Go engine into app/libs/netmerge.aar with gomobile.
# Needs Go, the Android SDK (ANDROID_HOME) and an NDK (ANDROID_NDK_HOME).
set -euo pipefail
cd "$(dirname "$0")/../golib"

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "${ANDROID_NDK_HOME:-}" ]; then
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/ndk" ]; then
    export ANDROID_NDK_HOME=$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -n 1)
  fi
fi
echo "Using ANDROID_NDK_HOME=$ANDROID_NDK_HOME"

# gomobile and gobind at the versions pinned in go.mod.
GOBIN="$PWD/.bin" go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind
export PATH="$PWD/.bin:$PATH"
targets="${GOMOBILE_TARGETS:-android/arm64}"
mkdir -p ../app/libs
gomobile bind -v -trimpath -ldflags="-s -w" \
  -target="$targets" -androidapi 26 \
  -o ../app/libs/netmerge.aar ./netmerge
