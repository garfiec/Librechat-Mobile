#!/usr/bin/env python3
"""Contact sheet of before/after renders: one row per case, columns BEFORE | AFTER.

Usage: sheet.py <case_dir>    (holds before/, after/ and the meta.json render.py wrote)

Writes <case_dir>/sheet-1.png, sheet-2.png, ..., starting a new page before 12000px. Each row is
labelled with the render's name, size, scale, locale and style. Read the sheets yourself before
reporting a fix: an after render that doesn't show the fix is not a fix.
"""
import glob
import json
import os
import sys
import textwrap

from PIL import Image, ImageDraw, ImageFont

case_dir = sys.argv[1]
before, after = os.path.join(case_dir, "before"), os.path.join(case_dir, "after")
meta = {}
mp = os.path.join(case_dir, "meta.json")
if os.path.exists(mp):
    meta = {m["name"]: m for m in json.load(open(mp))}
names = sorted({os.path.basename(p)[:-4] for d in (before, after) for p in glob.glob(os.path.join(d, "*.png"))})
if not names:
    sys.exit(f"no renders under {case_dir}")
try:
    font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 26)
except OSError:
    font = ImageFont.load_default()

MAX_H, COL_W, LEFT, PAD, HEAD = 12000, 760, 420, 24, 56
WRAP = 28  # label characters per line at this font size


def load(d, n):
    p = os.path.join(d, n + ".png")
    if not os.path.exists(p):
        return None
    im = Image.open(p).convert("RGB")
    if im.width > COL_W:
        im = im.resize((COL_W, round(im.height * COL_W / im.width)))
    return im


def label(n):
    m = meta.get(n.removesuffix("-scrolled"), {})
    bits = [f"{m['width']}×{m['height']}dp" if m.get("width") else "", f"{m['scale']}x" if m.get("scale") else "",
            m.get("locale") or ("en" if m else ""), m.get("style") or "", "scrolled" if n.endswith("-scrolled") else ""]
    lines = [n, " · ".join(b for b in bits if b)] + ([m["note"]] if m.get("note") else [])
    return [chunk for line in lines for chunk in textwrap.wrap(line, WRAP, break_on_hyphens=False) or [""]]


rows = [(n, load(before, n), load(after, n)) for n in names]
pages, cur, h = [], [], HEAD
for r in rows:
    rh = max((im.height for im in r[1:] if im), default=80) + PAD
    if cur and h + rh > MAX_H:
        pages.append(cur)
        cur, h = [], HEAD
    cur.append(r)
    h += rh
pages.append(cur)

for i, page in enumerate(pages, 1):
    height = HEAD + sum(max((im.height for im in r[1:] if im), default=80) + PAD for r in page) + PAD
    sheet = Image.new("RGB", (LEFT + 2 * (COL_W + PAD) + PAD, height), (245, 245, 245))
    dr = ImageDraw.Draw(sheet)
    dr.text((LEFT + PAD, 14), "BEFORE", fill=(150, 40, 40), font=font)
    dr.text((LEFT + COL_W + 2 * PAD, 14), "AFTER", fill=(30, 110, 50), font=font)
    y = HEAD
    for n, b, a in page:
        for j, line in enumerate(label(n)):
            dr.text((12, y + j * 32), line, fill=(0, 0, 0), font=font)
        for k, im in enumerate((b, a)):
            x = LEFT + PAD + k * (COL_W + PAD)
            if im:
                sheet.paste(im, (x, y))
            else:
                dr.text((x, y), "no render", fill=(120, 120, 120), font=font)
        y += max((im.height for im in (b, a) if im), default=80) + PAD
    out = os.path.join(case_dir, f"sheet-{i}.png")
    sheet.save(out)
    print(out, sheet.size)
