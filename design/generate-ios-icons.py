#!/usr/bin/env python3
"""Regenerate the iOS AppIcon set from TchatLogo.png.

    python3 design/generate-ios-icons.py        # run from the repo root

Rewrites all 16 PNGs listed in
    iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json
Filenames and sizes come from Contents.json itself, not a literal here, so the two can never
disagree.

Unlike Android, iOS app icons are fully opaque (no alpha channel) and get their own corner mask
from the OS — there is no separate "safe zone" spec to target, so this leaves generous, roughly
Apple-HIG-shaped padding around the glyph rather than trying to hit an exact percentage.

TCHAT: this project's AppIcon.appiconset is the traditional flat-PNG-per-size format (as declared
in its own Contents.json), not an Xcode 16+ Icon Composer `.icon` bundle -- so a plain composite+
resize script is a legitimate, self-contained way to regenerate it. It intentionally does not try
to match design/generate-android-icons.py's safe-circle percentage; see design/README.md for why
the two platforms are not meant to be pixel-identical.

Requires Pillow.
"""
import json
import re
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'design' / 'TchatLogo.png'
APPICONSET = ROOT / 'iosApp' / 'iosApp' / 'Assets.xcassets' / 'AppIcon.appiconset'
CONTENTS_JSON = APPICONSET / 'Contents.json'

# How much of the 1024 master canvas the glyph's own tight bounding box fills.
GLYPH_FRACTION = 0.58
MASTER_PX = 1024


def background_colour():
    themes = (ROOT / 'app' / 'src' / 'main' / 'res' / 'values' / 'themes.xml').read_text()
    m = re.search(r'name="splash_background">#([0-9A-Fa-f]{6})<', themes)
    if not m:
        raise SystemExit('could not read splash_background from app/src/main/res/values/themes.xml')
    h = m.group(1)
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def render_master(bg):
    glyph = Image.open(SRC).convert('RGBA')
    bbox = glyph.getbbox()
    if bbox is None:
        raise SystemExit(f'{SRC} has no non-transparent pixels')
    tight = glyph.crop(bbox)

    target_w = round(MASTER_PX * GLYPH_FRACTION)
    scale = target_w / tight.width
    resized = tight.resize((target_w, max(1, round(tight.height * scale))), Image.LANCZOS)

    canvas = Image.new('RGBA', (MASTER_PX, MASTER_PX), bg + (255,))
    x = (MASTER_PX - resized.width) // 2
    y = (MASTER_PX - resized.height) // 2
    canvas.alpha_composite(resized, (x, y))
    # iOS app icons must have no alpha channel at all.
    return canvas.convert('RGB')


def main():
    bg = background_colour()
    master = render_master(bg)

    entries = json.loads(CONTENTS_JSON.read_text())['images']
    for entry in entries:
        size_str, scale_str = entry['size'], entry.get('scale', '1x')
        size = float(size_str.split('x')[0])
        scale = int(scale_str.rstrip('x'))
        px = round(size * scale)
        out = master.resize((px, px), Image.LANCZOS)
        out.save(APPICONSET / entry['filename'])

    print(f'background #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X} · glyph at {GLYPH_FRACTION * 100:.0f}% of the 1024 canvas')
    print(f'wrote {len(entries)} icons to {APPICONSET}')


if __name__ == '__main__':
    main()
