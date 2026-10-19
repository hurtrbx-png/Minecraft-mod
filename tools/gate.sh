#!/bin/bash
# Milestone gate: build + server game tests, headless client game test, filtered log scan.
# Prints only what matters; full Gradle output goes to $GATE_LOG_DIR (default: $TMPDIR/giantess-gate).
#
# usage: tools/gate.sh [--no-client]
# The Minecraft EULA (https://aka.ms/MinecraftEULA) is only accepted when MINECRAFT_EULA=true is set.
# Without a display the client runs on Mesa software GL through SDL's offscreen driver (needs EGL libs).
set -u
P="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="${GATE_LOG_DIR:-${TMPDIR:-/tmp}/giantess-gate}"
mkdir -p "$LOGS"
EULA=()
[ "${MINECRAFT_EULA:-}" = "true" ] && EULA=(-Pminecraft.eula=true)
if [ -z "${DISPLAY:-}" ] && [ -z "${WAYLAND_DISPLAY:-}" ]; then
	export SDL_VIDEODRIVER="${SDL_VIDEODRIVER:-offscreen}" LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}" ALSOFT_DRIVERS="${ALSOFT_DRIVERS:-null}"
fi

# Known environment noise (offline account, Realms, headless window); anything else is worth reading.
NOISE='Failed to fetch user properties|Illegal option value 0 for Anisotropic|Failed to set window icon|end_of_frame|[Rr]ealms|server.properties|Class path entries reference missing|Could not read ./config/giantess.json'

cd "$P" || exit 1
echo "=== build + server game tests"
./gradlew build "${EULA[@]}" --console=plain > "$LOGS/build.log" 2>&1
echo "build rc=$?"
grep -E 'error:|FAILED|BUILD' -A2 "$LOGS/build.log" | cut -c1-220 | head -40
grep -i -E 'required tests|tests? (passed|failed)|failed at' "$P/build/run/gameTest/logs/latest.log" 2>/dev/null | cut -c1-220 | head -12
[ "${1:-}" = "--no-client" ] && exit 0

echo "=== client game test"
timeout 400 ./gradlew runClientGameTest "${EULA[@]}" --console=plain > "$LOGS/client.log" 2>&1
echo "client rc=$?"
pkill -f '[K]notClient'
grep -E 'AssertionError|Exception in|FAILED|error:' -A3 "$LOGS/client.log" | cut -c1-220 | head -20
echo "=== client log: unexpected ERROR/WARN"
grep -E '\] \[[^]]*/(ERROR|WARN)\]' "$P/build/run/clientGameTest/logs/latest.log" 2>/dev/null | grep -v -E "$NOISE" | cut -c1-200
echo "=== server log: unexpected ERROR/WARN"
grep -E '\] \[[^]]*/(ERROR|WARN)\]' "$P/build/run/gameTest/logs/latest.log" 2>/dev/null | grep -v -E "$NOISE" | cut -c1-200
echo "=== screenshots (build/run/clientGameTest/screenshots)"
ls "$P/build/run/clientGameTest/screenshots" 2>/dev/null
