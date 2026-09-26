#!/usr/bin/env bash
#
# Regenerates the platform icons in packaging/icons/ from a 1024x1024
# RGBA master. Needs macOS (iconutil) and Python with Pillow.
#
# Usage: packaging/make-icons.sh [path/to/master.png]

set -euo pipefail

ICONS="$(cd "$(dirname "$0")" && pwd)/icons"
SRC="${1:-$ICONS/jkcemu-1024.png}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

[ "$SRC" -ef "$ICONS/jkcemu-1024.png" ] || cp "$SRC" "$ICONS/jkcemu-1024.png"
mkdir -p "$TMP/jkcemu.iconset"

python3 - "$ICONS" "$TMP/jkcemu.iconset" <<'EOF'
import sys
from PIL import Image
icons, iconset = sys.argv[1], sys.argv[2]
src = Image.open(icons + '/jkcemu-1024.png').convert('RGBA')
# macOS: Apple's grid puts the icon body at 824px inside a 1024 canvas
mac = Image.new('RGBA', (1024, 1024), (0, 0, 0, 0))
mac.paste(src.resize((824, 824), Image.LANCZOS), (100, 100))
for s in (16, 32, 128, 256, 512):
    mac.resize((s, s), Image.LANCZOS).save('%s/icon_%dx%d.png' % (iconset, s, s))
    mac.resize((s * 2, s * 2), Image.LANCZOS).save('%s/icon_%dx%d@2x.png' % (iconset, s, s))
src.save(icons + '/jkcemu.ico',
         sizes=[(s, s) for s in (16, 20, 24, 32, 40, 48, 64, 128, 256)])
src.resize((512, 512), Image.LANCZOS).save(icons + '/jkcemu.png')
# window/taskbar icons used by the application itself (Main.java)
for s in (16, 20, 24, 32, 48, 256):
    src.resize((s, s), Image.LANCZOS).save(
        '%s/../../src/images/icon/jkcemu_%dx%d.png' % (icons, s, s))
EOF

iconutil -c icns "$TMP/jkcemu.iconset" -o "$ICONS/jkcemu.icns"
ls -l "$ICONS"
