#!/usr/bin/env python3
"""Google Play listing images: framed screenshots on a branded canvas, and the feature graphic.

    scripts/screenshots/play.py set out/                    # the whole listing, from play-set.toml
    scripts/screenshots/play.py phone  shot.png out.png --title "Make it yours" [--subtitle "…"]
    scripts/screenshots/play.py tablet shot.png out.png --title "Built for big screens"
    scripts/screenshots/play.py pair a.png b.png out.png    # two framed shots side by side
    scripts/screenshots/play.py feature out.png
    scripts/screenshots/play.py --self-test

`shot.png` is either a store screenshot (already framed, as `android.py shot` writes it) or an
unframed emulator capture (`android.py shot … --raw raw.png`), which is framed here: a folded one
(1080x2364) for `phone`, an unfolded one (2076x2152) for `tablet`. The store screenshots are
half size, but at canvas size that costs nothing visible.

Why a canvas at all: Play wants JPEG or 24-bit PNG with no alpha, the longest side at most twice
the shortest, and large-screen images at 16:9 or 9:16. A framed Fold screenshot breaks all three
(transparent corners; a folded Fold is 2.02:1; unfolded is nearly square). So phone images are
1080x1920 and tablet images 1920x1080, with the caption held to the top 20% so the app stays the
subject.

The feature graphic in use is fastlane/'s featureGraphic.png (`feature_graphic` in
play-set.toml), which `set` checks and copies. `feature` draws the alternative, a wordmark
banner with no device imagery (design/feature-graphic-wordmark.png).
See https://support.google.com/googleplay/android-developer/answer/9866151.

`pair` is for the hero shot: the same chat in Material and in Liquid Glass. Its output is a
store screenshot like any other, so it goes in fastlane/ and the canvas is built from it. On a
phone canvas the two phones overlap, since side by side they'd leave it mostly empty.

Play takes these through the Console (play.yml uploads only the app bundle), so `set` writes
them to a directory of your choice and nothing here is committed: rebuild after the store
screenshots change.
"""

from __future__ import annotations

import argparse
import sys
import tomllib
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import frame  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
LOGO = REPO / "design/SwitchboardLogo.png"
SET = Path(__file__).resolve().parent / "play-set.toml"
FONTS = Path.home() / "Library/Android/sdk/platforms/android-29/data/fonts"

# The Switchboard icon's palette.
SLATE_DARK = (22, 27, 32)
SLATE = (40, 48, 55)
TEAL = (9, 255, 206)
TEAL_TEXT = (150, 245, 226)
WHITE = (255, 255, 255)

PHONE = (1080, 1920)
TABLET = (1920, 1080)
FEATURE = (1024, 500)
CAPTION_SHARE = 0.20
# The emulator's screen sizes, folded and unfolded: an image this size is an unframed capture.
RAW = {"closed": (1080, 2364), "default": (2076, 2152)}


def font(weight: str, size: int) -> ImageFont.FreeTypeFont:
    path = FONTS / f"Roboto-{weight}.ttf"
    if not path.exists():
        raise SystemExit(f"Roboto isn't at {path}; install an Android SDK platform with fonts")
    return ImageFont.truetype(str(path), size)


def device(shot: Image.Image, posture: str) -> Image.Image:
    """The framed device: `shot` as it is if it's framed already, else framed here."""
    shot = shot.convert("RGBA")
    return frame.frame_android(shot, posture) if shot.size == RAW[posture] else shot


def pair(a: Image.Image, b: Image.Image, gap: int) -> Image.Image:
    """Two framed shots side by side on a transparent background, bottoms aligned."""
    a, b = a.convert("RGBA"), b.convert("RGBA")
    h = max(a.height, b.height)
    out = Image.new("RGBA", (a.width + gap + b.width, h), (0, 0, 0, 0))
    out.alpha_composite(a, (0, h - a.height))
    out.alpha_composite(b, (a.width + gap, h - b.height))
    return out


def unpair(img: Image.Image) -> list[Image.Image]:
    """Split a `pair` back into its two shots at the clear gap between them; one shot if none."""
    alpha = img.convert("RGBA").getchannel("A")
    w, h = img.size
    clear = [alpha.crop((x, 0, x + 1, h)).getbbox() is None for x in range(w)]
    runs, start = [], None
    for x, c in enumerate(clear + [False]):
        if c and start is None:
            start = x
        elif not c and start is not None:
            if 0 < start and x < w:
                runs.append((start, x))
            start = None
    if not runs:
        return [img]
    x0, x1 = max(runs, key=lambda r: r[1] - r[0])
    return [img.crop((0, 0, x0, h)), img.crop((x1, 0, w, h))]


def stagger(back: Image.Image, front: Image.Image, overlap: float, drop: float) -> Image.Image:
    """`front` overlapping `back`'s right side and lower, with its own shadow falling on `back`."""
    back, front = back.convert("RGBA"), front.convert("RGBA")
    fx, fy = round(back.width * (1 - overlap)), round(back.height * drop)
    out = Image.new("RGBA", (fx + front.width, max(back.height, fy + front.height)), (0, 0, 0, 0))
    out.alpha_composite(back)
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    shadow.paste(Image.new("RGBA", front.size, (0, 0, 0, 255)), (fx - front.width // 40, fy),
                 front.getchannel("A").point(lambda a: a * 0.5))
    shadow = shadow.filter(ImageFilter.GaussianBlur(front.width // 30))
    out.alpha_composite(Image.composite(shadow, Image.new("RGBA", out.size, (0, 0, 0, 0)),
                                        out.getchannel("A")))
    out.alpha_composite(front, (fx, fy))
    return out


def background(size: tuple[int, int], glow_at: tuple[float, float], glow_radius: float) -> Image.Image:
    """Slate, darker at the top, with a soft teal glow where the device sits."""
    w, h = size
    top = Image.new("RGB", (1, 2), SLATE_DARK)
    top.putpixel((0, 1), SLATE)
    bg = top.resize(size, Image.BICUBIC)
    glow = Image.new("L", size, 0)
    r = glow_radius * min(w, h)
    cx, cy = glow_at[0] * w, glow_at[1] * h
    ImageDraw.Draw(glow).ellipse((cx - r, cy - r * 0.8, cx + r, cy + r * 0.8), fill=70)
    glow = glow.filter(ImageFilter.GaussianBlur(r * 0.45))
    return Image.composite(Image.new("RGB", size, TEAL), bg, glow)


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt, width: int) -> list[str]:
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=fnt) <= width or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def caption(canvas: Image.Image, title: str, subtitle: str | None, box: tuple[int, int, int, int],
            title_size: int, subtitle_size: int) -> int:
    """Centre the title and subtitle in `box`; returns the bottom of the text."""
    draw = ImageDraw.Draw(canvas)
    x0, y0, x1, y1 = box
    tf, sf = font("Bold", title_size), font("Regular", subtitle_size)
    title_lines = wrap(draw, title, tf, x1 - x0)
    sub_lines = wrap(draw, subtitle, sf, x1 - x0) if subtitle else []
    gap = round(title_size * 0.25)
    heights = [title_size * 1.15] * len(title_lines) + [subtitle_size * 1.3] * len(sub_lines)
    total = sum(heights) + (gap if sub_lines else 0)
    y = y0 + (y1 - y0 - total) / 2
    for line in title_lines:
        draw.text(((x0 + x1) / 2, y), line, font=tf, fill=WHITE, anchor="ma")
        y += title_size * 1.15
    y += gap if sub_lines else 0
    for line in sub_lines:
        draw.text(((x0 + x1) / 2, y), line, font=sf, fill=TEAL_TEXT, anchor="ma")
        y += subtitle_size * 1.3
    return round(y)


def place(canvas: Image.Image, dev: Image.Image, box: tuple[int, int, int, int]):
    """Scale `dev` to fit `box`, centre it, and drop a soft shadow under it."""
    x0, y0, x1, y1 = box
    scale = min((x1 - x0) / dev.width, (y1 - y0) / dev.height)
    dev = dev.resize((round(dev.width * scale), round(dev.height * scale)), Image.LANCZOS)
    x = x0 + (x1 - x0 - dev.width) // 2
    y = y0 + (y1 - y0 - dev.height) // 2
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    alpha = dev.getchannel("A").point(lambda a: a * 0.55)
    shadow.paste(Image.new("RGBA", dev.size, (0, 0, 0, 255)), (x, y + round(dev.height * 0.015)), alpha)
    shadow = shadow.filter(ImageFilter.GaussianBlur(max(6, dev.width // 40)))
    base = canvas.convert("RGBA")
    base.alpha_composite(shadow)
    base.alpha_composite(dev, (x, y))
    canvas.paste(base.convert("RGB"))


def phone(shot: Image.Image, title: str, subtitle: str | None) -> Image.Image:
    w, h = PHONE
    canvas = background(PHONE, (0.5, 0.62), 0.55)
    band = round(h * CAPTION_SHARE)
    caption(canvas, title, subtitle, (70, 60, w - 70, band), 76, 40)
    shots = unpair(shot)
    dev = (stagger(*shots, overlap=0.25, drop=0.1) if len(shots) == 2
           else device(shot, "closed"))
    place(canvas, dev, (40, band + 30, w - 40, h - 50))
    return canvas


def tablet(shot: Image.Image, title: str, subtitle: str | None) -> Image.Image:
    w, h = TABLET
    canvas = background(TABLET, (0.5, 0.6), 0.6)
    band = round(h * CAPTION_SHARE)
    caption(canvas, title, subtitle, (120, 34, w - 120, band), 64, 34)
    place(canvas, device(shot, "default"), (120, band + 20, w - 120, h - 40))
    return canvas


def feature() -> Image.Image:
    w, h = FEATURE
    canvas = background(FEATURE, (0.24, 0.5), 0.5)
    logo = Image.open(LOGO).convert("RGBA")
    logo.thumbnail((300, 300), Image.LANCZOS)
    base = canvas.convert("RGBA")
    base.alpha_composite(logo, (80, (h - logo.height) // 2))
    canvas = base.convert("RGB")
    draw = ImageDraw.Draw(canvas)
    draw.text((430, 175), "Switchboard", font=font("Bold", 84), fill=WHITE)
    for i, line in enumerate(("A native app for your", "LibreChat server")):
        draw.text((434, 285 + i * 46), line, font=font("Regular", 38), fill=TEAL_TEXT)
    return canvas


def check(image: Image.Image, kind: str) -> list[str]:
    """Play's rules for a listing image, as problems found."""
    problems = []
    if image.mode != "RGB":
        problems.append(f"mode {image.mode}, Play wants no alpha")
    w, h = image.size
    if kind == "feature":
        if (w, h) != FEATURE:
            problems.append(f"feature graphic is {w}x{h}, not 1024x500")
        return problems
    if max(w, h) > 2 * min(w, h):
        problems.append(f"{w}x{h} is longer than 2:1")
    if kind == "tablet" and not (1080 <= min(w, h) and max(w, h) <= 7680 and
                                 abs(max(w, h) / min(w, h) - 16 / 9) < 0.01):
        problems.append(f"tablet image {w}x{h} isn't 16:9 within 1080–7680 px")
    if kind == "phone" and not (320 <= min(w, h) and max(w, h) <= 3840):
        problems.append(f"phone image {w}x{h} is outside 320–3840 px")
    return problems


def save(img: Image.Image, kind: str, out: Path):
    problems = check(img, kind)
    if problems:
        raise SystemExit(f"{out}: " + "; ".join(problems))
    img.save(out, optimize=True)
    print(f"{out}: {img.width}x{img.height}")


def build_set(out_dir: Path, manifest: Path):
    """Every listing image in `manifest`, numbered in listing order, plus the feature graphic."""
    spec = tomllib.loads(manifest.read_text())
    images = REPO / spec["images"]
    out_dir.mkdir(parents=True, exist_ok=True)
    for kind, build in (("phone", phone), ("tablet", tablet)):
        for i, entry in enumerate(spec.get(kind, []), 1):
            shot = Image.open(images / entry["image"])
            img = build(shot, entry["title"], entry.get("subtitle"))
            save(img, kind, out_dir / f"{kind}-{i:02d}-{Path(entry['image']).stem.split('-', 1)[-1]}.png")
    graphic = spec.get("feature_graphic")
    save(Image.open(images / graphic).convert("RGB") if graphic else feature(), "feature",
         out_dir / "feature-graphic.png")


def self_test() -> int:
    failures = []
    for kind, posture, build in (("phone", "closed", phone), ("tablet", "default", tablet)):
        raw = Image.new("RGBA", RAW[posture], (240, 240, 250, 255))
        framed = frame.frame_android(raw, posture)
        half = framed.resize((framed.width // 2, framed.height // 2))
        for name, shot in (("raw", raw), ("framed", half),
                           ("pair", pair(half, half, 40))):
            img = build(shot, "A caption long enough to wrap onto a second line", "And a subtitle")
            problems = check(img, kind)
            print(f"{'ok  ' if not problems else 'FAIL'} {kind} from {name}: {img.size} {problems or ''}")
            failures += problems
        split = [len(unpair(s)) for s in (half, pair(half, half, 40))]
        print(f"{'ok  ' if split == [1, 2] else 'FAIL'} {kind}: a shot stays whole, a pair splits")
        failures += [] if split == [1, 2] else [f"{kind} unpair {split}"]
    img = feature()
    problems = check(img, "feature")
    print(f"{'ok  ' if not problems else 'FAIL'} feature: {img.size} {problems or ''}")
    failures += problems
    framed = frame.frame_android(Image.new("RGBA", RAW["closed"], (255, 0, 0, 255)), "closed")
    broken = check(framed.convert("RGB").resize((618, 1248)), "phone")
    print(f"{'ok  ' if broken else 'FAIL'} a bare framed Fold shot is rejected: {broken}")
    if not broken:
        failures.append("2:1 check")
    spec = tomllib.loads(SET.read_text())
    missing = [e["image"] for kind in ("phone", "tablet") for e in spec.get(kind, [])
               if not (REPO / spec["images"] / e["image"]).exists()]
    print(f"{'ok  ' if not missing else 'FAIL'} play-set.toml names existing images {missing or ''}")
    failures += missing
    print(f"self-test: {'ok' if not failures else f'{len(failures)} failure(s)'}")
    return 1 if failures else 0


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("kind", nargs="?", choices=["set", "phone", "tablet", "pair", "feature"])
    p.add_argument("args", nargs="*", help="see the usage above")
    p.add_argument("--title")
    p.add_argument("--subtitle")
    p.add_argument("--gap", type=int, default=40, help="pixels between the two shots of a pair")
    p.add_argument("--manifest", type=Path, default=SET)
    p.add_argument("--self-test", action="store_true")
    a = p.parse_args()
    if a.self_test:
        return self_test()
    if a.kind == "set":
        if len(a.args) != 1:
            p.error("set takes out-dir")
        build_set(Path(a.args[0]), a.manifest)
    elif a.kind == "feature":
        if len(a.args) != 1:
            p.error("feature takes out.png")
        save(feature(), "feature", Path(a.args[0]))
    elif a.kind == "pair":
        if len(a.args) != 3:
            p.error("pair takes a.png b.png out.png")
        img = pair(Image.open(a.args[0]), Image.open(a.args[1]), a.gap)
        img.save(a.args[2], optimize=True)
        print(f"{a.args[2]}: {img.width}x{img.height}")
    elif a.kind in ("phone", "tablet"):
        if len(a.args) != 2 or not a.title:
            p.error(f"{a.kind} takes shot.png out.png and --title")
        build = phone if a.kind == "phone" else tablet
        save(build(Image.open(a.args[0]), a.title, a.subtitle), a.kind, Path(a.args[1]))
    else:
        p.error("say what to build")
    return 0


if __name__ == "__main__":
    sys.exit(main())
