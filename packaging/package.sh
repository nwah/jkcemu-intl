#!/usr/bin/env bash
#
# Builds a double-clickable, self-contained JKCEMU Multilingual package for the
# current OS with jpackage (JDK 17+). jpackage cannot cross-build, so
# run this once per OS (see .github/workflows/release.yml).
#
#   macOS    dist/JKCEMU-Multilingual-<version>-macos-<arch>.dmg
#   Windows  dist/JKCEMU-Multilingual-<version>-windows-x64.zip   (portable folder)
#   Linux    dist/JKCEMU-Multilingual-<version>-linux-x64.tar.gz  (portable folder)
#            dist/jkcemu-multilingual_<version>_amd64.deb
#
# Usage: packaging/package.sh [path/to/jkcemu.jar]

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${1:-$ROOT/jkcemu.jar}"
ICONS="$ROOT/packaging/icons"
DIST="$ROOT/dist"
# not under build/: "ant jar" packs everything in there
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

NAME="JKCEMU Multilingual"
SLUG="JKCEMU-Multilingual"      # for file names
VENDOR="Jens Mueller"
DESC="Emulator for East German home and microcomputers"
URL="https://github.com/nwah/jkcemu-multilingual"
VERSION="$(sed -n 's/.*String VERSION *= *"\([^"]*\)".*/\1/p' \
		"$ROOT/src/jkcemu/Main.java")"

# jdeps reports java.base,java.desktop,java.naming; jdk.naming.dns is
# loaded via JNDI for dns:// lookups, jdk.localedata for non-English
# number/date formatting.
MODULES="java.base,java.desktop,java.naming,jdk.naming.dns,jdk.localedata"

[ -f "$JAR" ] || { echo "missing $JAR (run 'ant jar' first)" >&2; exit 1; }
[ -n "$VERSION" ] || { echo "cannot read VERSION from Main.java" >&2; exit 1; }

mkdir -p "$WORK/input" "$DIST"
cp "$JAR" "$WORK/input/jkcemu.jar"

common=(
  --name "$NAME"
  --vendor "$VENDOR"
  --description "$DESC"
  --input "$WORK/input"
  --main-jar jkcemu.jar
  --main-class jkcemu.Main
  --add-modules "$MODULES"
  --jlink-options "--strip-debug --no-header-files --no-man-pages"
)

case "$(uname -s)" in
  Darwin)
    arch="$(uname -m)"; [ "$arch" = "x86_64" ] && arch="x64"
    # macOS rejects app versions starting with 0, so build with a
    # placeholder, then write the real version into Info.plist and
    # re-sign (ad hoc) since editing the bundle breaks the signature.
    jpackage "${common[@]}" --type app-image --app-version 1 \
      --icon "$ICONS/jkcemu.icns" \
      --mac-package-identifier org.jens-mueller.jkcemu \
      --mac-package-name JKCEMU \
      --dest "$WORK"
    plist="$WORK/$NAME.app/Contents/Info.plist"
    /usr/libexec/PlistBuddy \
      -c "Set :CFBundleName $NAME" \
      -c "Add :CFBundleDisplayName string $NAME" \
      -c "Set :CFBundleShortVersionString $VERSION" \
      -c "Set :CFBundleVersion $VERSION" "$plist"
    codesign --force --deep --sign - "$WORK/$NAME.app"
    jpackage --type dmg --name "$NAME" --app-version 1 \
      --app-image "$WORK/$NAME.app" --dest "$WORK"
    mv "$WORK/$NAME-1.dmg" "$DIST/$SLUG-$VERSION-macos-$arch.dmg"
    ;;

  MINGW*|MSYS*|CYGWIN*)
    # Portable folder: no WiX toolset needed, users unzip and
    # double-click "JKCEMU Multilingual.exe".
    jpackage "${common[@]}" --type app-image --app-version "$VERSION" \
      --icon "$ICONS/jkcemu.ico" \
      --dest "$WORK"
    (cd "$WORK" && powershell -NoProfile -Command \
      "Compress-Archive -Force -Path '$NAME' -DestinationPath '$SLUG.zip'")
    mv "$WORK/$SLUG.zip" "$DIST/$SLUG-$VERSION-windows-x64.zip"
    ;;

  Linux)
    jpackage "${common[@]}" --type app-image --app-version "$VERSION" \
      --icon "$ICONS/jkcemu.png" \
      --dest "$WORK"
    tar -C "$WORK" -czf "$DIST/$SLUG-$VERSION-linux-x64.tar.gz" "$NAME"
    if command -v dpkg-deb >/dev/null; then
      jpackage "${common[@]}" --type deb --app-version "$VERSION" \
        --icon "$ICONS/jkcemu.png" \
        --linux-package-name jkcemu-multilingual \
        --linux-app-category Emulator \
        --linux-menu-group "Game;Emulator;" \
        --linux-shortcut \
        --about-url "$URL" \
        --dest "$DIST"
    fi
    ;;

  *)
    echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

ls -l "$DIST"
