#!/usr/bin/env python3
"""Contact sheet: many screenshots or GIFs on one image, for reviewing a capture batch at a glance.

    scripts/screenshots/sheet.py out.jpg shots/*.png
    scripts/screenshots/sheet.py out.jpg --height 700 play/phone-*.png
    scripts/screenshots/sheet.py out.jpg --frames 6 docs/screenshots/sidebar-phone.gif

Every image is scaled to the same height and labelled with its file name, in rows that wrap at
--width. A GIF contributes --frames evenly spaced frames instead of its first one, so a recording
can be checked for a stalled capture (an "isn't responding" dialog, a frozen screen) without
playing it.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageSequence


def tiles(path: Path, height: int, frames: int) -> list[tuple[str, Image.Image]]:
    im = Image.open(path)
    if getattr(im, "is_animated", False):
        seq = [f.convert("RGBA") for f in ImageSequence.Iterator(im)]
        picks = [round(i * (len(seq) - 1) / max(1, frames - 1)) for i in range(frames)]
        out = [(f"{path.stem} {i + 1}/{len(seq)}", seq[i]) for i in picks]
    else:
        out = [(path.stem, im.convert("RGBA"))]
    scaled = []
    for label, frame in out:
        w = round(frame.width * height / frame.height)
        scaled.append((label, frame.resize((w, height), Image.LANCZOS)))
    return scaled


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("out", type=Path)
    p.add_argument("images", nargs="+", type=Path)
    p.add_argument("--height", type=int, default=520, help="tile height in pixels")
    p.add_argument("--width", type=int, default=2400, help="sheet width in pixels")
    p.add_argument("--frames", type=int, default=4, help="frames sampled from each GIF")
    a = p.parse_args()

    cells = []
    for path in a.images:
        for label, im in tiles(path, a.height, a.frames):
            cell = Image.new("RGB", (im.width, a.height + 30), (225, 225, 225))
            cell.paste(im, (0, 0), im)
            ImageDraw.Draw(cell).text((4, a.height + 8), label[:60], fill=(0, 0, 0))
            cells.append(cell)
    rows, row, x = [], [], 0
    for cell in cells:
        if x + cell.width > a.width and row:
            rows.append(row)
            row, x = [], 0
        row.append(cell)
        x += cell.width + 10
    rows.append(row)
    sheet = Image.new("RGB", (a.width, len(rows) * (a.height + 40)), (255, 255, 255))
    for r, row in enumerate(rows):
        x = 0
        for cell in row:
            sheet.paste(cell, (x, r * (a.height + 40)))
            x += cell.width + 10
    sheet.save(a.out, quality=85)
    print(f"{a.out}: {sheet.width}x{sheet.height}, {len(cells)} tiles")
    return 0


if __name__ == "__main__":
    sys.exit(main())
