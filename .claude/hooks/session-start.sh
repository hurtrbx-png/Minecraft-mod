#!/bin/bash
# Cloud-session setup: Minecraft 26.x needs JDK 25, but the container only ships an older JDK.
# Installs Temurin 25 next to the project, points Gradle at it, and warms the Gradle caches
# (Minecraft jars, Loom, Fabric API) so builds and in-game tests start fast. Idempotent.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$PWD}"
JDK_DIR="${GIANTESS_JDK_DIR:-$(dirname "$PROJECT_DIR")/tools/jdk-25}"

if [ ! -x "$JDK_DIR/bin/java" ]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  info="$(curl -fsS "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse")"
  read -r link sum <<<"$(printf '%s' "$info" | python3 -I -c "import sys,json; p=json.load(sys.stdin)[0]['binary']['package']; print(p['link'], p['checksum'])")"
  curl -fsSL -o "$tmp/jdk.tar.gz" "$link"
  echo "$sum  $tmp/jdk.tar.gz" | sha256sum -c - > /dev/null
  mkdir -p "$JDK_DIR"
  tar -xzf "$tmp/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
fi

# Gradle (and the Loom run tasks it launches) must use JDK 25.
mkdir -p "$HOME/.gradle"
props="$HOME/.gradle/gradle.properties"
touch "$props"
{ grep -v '^org.gradle.java.home=' "$props" || true; echo "org.gradle.java.home=$JDK_DIR"; } > "$props.tmp"
mv "$props.tmp" "$props"

# Headless client: Mesa software GL through SDL's offscreen driver (needs EGL), no audio device.
if ! ldconfig -p | grep -q 'libEGL.so.1'; then
  apt-get update -qq > /dev/null
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends libegl1 libegl-mesa0 libgles2 > /dev/null
fi

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export JAVA_HOME=\"$JDK_DIR\""
    echo 'export SDL_VIDEODRIVER=offscreen'
    echo 'export LIBGL_ALWAYS_SOFTWARE=1'
    echo 'export ALSOFT_DRIVERS=null'
  } >> "$CLAUDE_ENV_FILE"
fi

# Warm the Gradle caches (Minecraft jars, Loom, Fabric API, game assets); a failure here is not fatal.
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" --console=plain classes clientClasses gametestClasses downloadAssets > /tmp/giantess-warmup.log 2>&1 \
  || echo "Gradle warm-up failed (see /tmp/giantess-warmup.log); builds will download what they need."
