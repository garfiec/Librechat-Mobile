#!/usr/bin/env python3
"""Put a device frame around a raw screenshot, the way the README and store images show them.

    scripts/screenshots/frame.py android closed raw.png out.png        # folded Pixel 10 Pro Fold
    scripts/screenshots/frame.py android default raw.png out.png       # unfolded
    scripts/screenshots/frame.py ios "iPhone 17 Pro" raw.png out.png
    scripts/screenshots/frame.py ios "iPad Pro 13-inch (M5)" raw.png out.png
    scripts/screenshots/frame.py --self-test

Android frames are the emulator skins Android Studio's "screenshot with frame" uses
(~/Library/Android/sdk/skins/<skin>/<posture>/): the device image, then the screen at the
layout's offset, then the mask on top, which is the rounded corners and the camera hole.

iOS frames are the Simulator's own device chrome (/Library/Developer/DeviceKit/Chrome), found
through the device type's chromeIdentifier. They're vector PDFs: a single composite where the
chrome has one, otherwise nine pieces (corners, stretched edges, a fill) laid out by
chrome.json, with the side buttons under the frame. Take the screenshot with
`simctl io <device> screenshot --mask=alpha` so its corners are transparent.

These Simulator frames are for the GitHub README. App Store images use Apple's product bezels.

Output is full resolution; --scale 0.5 gives the size the README and store listing use.
"""

from __future__ import annotations

import argparse
import functools
import json
import plistlib
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageChops

ANDROID_SKINS = Path.home() / "Library/Android/sdk/skins"
DEVICE_TYPES = Path("/Library/Developer/CoreSimulator/Profiles/DeviceTypes")
CHROMES = Path("/Library/Developer/DeviceKit/Chrome")


# ----------------------------------------------------------------------------- Android

def android_layout(skin_dir: Path) -> tuple[tuple[int, int], tuple[int, int]]:
    """The display's (width, height) and its (x, y) offset in the frame, from the skin's
    `layout` file."""
    text = (skin_dir / "layout").read_text()
    display = re.search(r"display\s*\{[^}]*?width\s+(\d+)[^}]*?height\s+(\d+)", text, re.S)
    device = re.search(r"name\s+device\s+x\s+(-?\d+)\s+y\s+(-?\d+)", text)
    if not (display and device):
        raise SystemExit(f"can't read the display geometry in {skin_dir / 'layout'}")
    return (int(display[1]), int(display[2])), (int(device[1]), int(device[2]))


def frame_android(raw: Image.Image, posture: str, skin: str = "pixel_10_pro_fold") -> Image.Image:
    skin_dir = ANDROID_SKINS / skin / posture
    size, offset = android_layout(skin_dir)
    if raw.size != size:
        raise SystemExit(f"screenshot is {raw.size[0]}x{raw.size[1]}, but the {posture} "
                         f"display is {size[0]}x{size[1]}. Capture the right display "
                         "(screencap -d <id>) in the right posture.")
    frame = Image.open(skin_dir / "back.webp").convert("RGBA")
    frame.alpha_composite(raw.convert("RGBA"), offset)
    frame.alpha_composite(Image.open(skin_dir / "mask.webp").convert("RGBA"), offset)
    return frame


# ----------------------------------------------------------------------------- iOS

def chrome_for(device_type: str) -> Path:
    profile = DEVICE_TYPES / f"{device_type}.simdevicetype/Contents/Resources/profile.plist"
    if not profile.exists():
        raise SystemExit(f"no Simulator device type '{device_type}' in {DEVICE_TYPES}")
    identifier = plistlib.loads(profile.read_bytes())["chromeIdentifier"]
    path = CHROMES / f"{identifier.rsplit('.', 1)[-1]}.devicechrome/Contents/Resources"
    if not path.exists():
        raise SystemExit(f"chrome {identifier} isn't installed at {path}")
    return path


@functools.cache
def render_pdf(pdf: Path, scale: float) -> Image.Image:
    """A PDF page rasterized with a transparent background at `scale` pixels per point."""
    if not shutil.which("pdftocairo"):
        raise SystemExit("pdftocairo is needed for iOS frames (brew install poppler)")
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "page"
        subprocess.run(["pdftocairo", "-png", "-transp", "-singlefile", "-r", str(72 * scale),
                        str(pdf), str(out)], check=True)
        return Image.open(f"{out}.png").convert("RGBA").copy()


def ios_scale(device_type: str, raw: Image.Image) -> int:
    """Pixels per point: 3 for current iPhones, 2 for iPads."""
    return 2 if device_type.startswith("iPad") else 3


def frame_ios(raw: Image.Image, device_type: str, *, nine_slice: bool = False) -> Image.Image:
    return frame_ios_at(raw, device_type, nine_slice=nine_slice)[0]


def frame_ios_at(raw: Image.Image, device_type: str, *,
                 nine_slice: bool = False) -> tuple[Image.Image, tuple[int, int]]:
    """The framed image and where the screen's top-left corner landed in it."""
    res = chrome_for(device_type)
    spec = json.loads((res / "chrome.json").read_text())
    images = spec["images"]
    scale = ios_scale(device_type, raw)
    pt = lambda v: round(v * scale)  # noqa: E731

    # `sizing` overlaps the screen by a point: phone11's composite has a 17pt border where its
    # sizing says 18.
    border = images["sizing"]
    left, right = pt(border["leftWidth"] - 1), pt(border["rightWidth"] - 1)
    top, bottom = pt(border["topHeight"] - 1), pt(border["bottomHeight"] - 1)
    composite = images.get("composite")

    if composite and not nine_slice:
        device = render_pdf(res / f"{composite}.pdf", scale)
        # The composite's border is the device outline around the screen, centred.
        left = right = (device.width - raw.width) // 2
        top = bottom = (device.height - raw.height) // 2
    else:
        device = Image.new("RGBA", (raw.width + left + right, raw.height + top + bottom))
        corners = {k: render_pdf(res / f"{images[k]}.pdf", scale)
                   for k in ("topLeft", "topRight", "bottomLeft", "bottomRight")}
        w, h = device.size
        edge = lambda key, size: render_pdf(res / f"{images[key]}.pdf", scale).resize(size)  # noqa: E731
        tl, tr, bl, br = (corners[k] for k in ("topLeft", "topRight", "bottomLeft", "bottomRight"))
        fill = render_pdf(res / f"{images['screen']}.pdf", scale).resize(
            (w - tl.width - tr.width + 2, h - tl.height - bl.height + 2))
        device.alpha_composite(fill, (tl.width - 1, tl.height - 1))
        device.alpha_composite(edge("top", (w - tl.width - tr.width, tl.height)), (tl.width, 0))
        device.alpha_composite(edge("bottom", (w - bl.width - br.width, bl.height)),
                               (bl.width, h - bl.height))
        device.alpha_composite(edge("left", (tl.width, h - tl.height - bl.height)), (0, tl.height))
        device.alpha_composite(edge("right", (tr.width, h - tr.height - br.height)),
                               (w - tr.width, tr.height))
        device.alpha_composite(tl, (0, 0))
        device.alpha_composite(tr, (w - tr.width, 0))
        device.alpha_composite(bl, (0, h - bl.height))
        device.alpha_composite(br, (w - br.width, h - br.height))

    pad = images.get("devicePadding", {})
    pl, pr, ptop, pb = (pt(pad.get(k, 0)) for k in ("left", "right", "top", "bottom"))
    canvas = Image.new("RGBA", (device.width + pl + pr, device.height + ptop + pb))
    dx, dy, dw, dh = pl, ptop, device.width, device.height

    # Buttons sit under the frame and stick out past it. Offsets are measured inward from the
    # anchored edge: x on the left and right edges, y on the top; `leading`/`trailing` measures
    # the other coordinate from the start or the end of that edge.
    above = []
    for button in spec.get("inputs", []):
        if "image" not in button:
            continue
        img = render_pdf(res / f"{button['image']}.pdf", scale)
        off = button["offsets"]["normal"]
        ox, oy = pt(off["x"]), pt(off["y"])
        anchor, align = button["anchor"], button.get("align", "leading")
        if anchor in ("left", "right"):
            x = dx + ox - img.width if anchor == "left" else dx + dw + ox
            y = dy + oy if align == "leading" else dy + dh - oy - img.height
        else:
            y = dy + oy - img.height if anchor == "top" else dy + dh + oy
            x = dx + ox if align == "leading" else dx + dw + ox - img.width
        if button.get("onTop"):
            above.append((img, (x, y)))
        else:
            canvas.alpha_composite(img, (x, y))
    canvas.alpha_composite(device, (dx, dy))
    canvas.alpha_composite(raw.convert("RGBA"), (dx + left, dy + top))
    for img, at in above:
        canvas.alpha_composite(img, at)
    return canvas, (dx + left, dy + top)


# ----------------------------------------------------------------------------- self-test

def self_test() -> int:
    failures = []

    def check(name, cond):
        print(f"{'ok  ' if cond else 'FAIL'} {name}")
        if not cond:
            failures.append(name)

    for posture in ("closed", "default"):
        skin = ANDROID_SKINS / "pixel_10_pro_fold" / posture
        if not skin.exists():
            print(f"skip android {posture}: no skin at {skin}")
            continue
        size, offset = android_layout(skin)
        mask = Image.open(skin / "mask.webp")
        back = Image.open(skin / "back.webp")
        check(f"android {posture}: the mask matches the display size", mask.size == size)
        check(f"android {posture}: the display fits inside the frame",
              offset[0] + size[0] <= back.width and offset[1] + size[1] <= back.height)
        framed = frame_android(Image.new("RGBA", size, (255, 0, 0, 255)), posture)
        corner = framed.getpixel((0, 0))
        centre = framed.getpixel((offset[0] + size[0] // 2, offset[1] + size[1] // 2))
        check(f"android {posture}: outside the device stays transparent", corner[3] == 0)
        check(f"android {posture}: the screen shows through", centre[:3] == (255, 0, 0))

    if (DEVICE_TYPES / "iPhone 17 Pro.simdevicetype").exists() and shutil.which("pdftocairo"):
        raw = Image.new("RGBA", (1206, 2622), (0, 128, 255, 255))
        whole = frame_ios(raw, "iPhone 17 Pro")
        pieced = frame_ios(raw, "iPhone 17 Pro", nine_slice=True)
        check("ios: the nine-piece frame is the composite's size", whole.size == pieced.size)
        if whole.size == pieced.size:
            diff = ImageChops.difference(whole, pieced).convert("L")
            mean = sum(diff.histogram()[i] * i for i in range(256)) / (diff.width * diff.height)
            check(f"ios: the nine-piece frame matches Apple's composite (mean diff {mean:.2f})",
                  mean < 3)
        ipad = frame_ios(Image.new("RGBA", (2064, 2752), (0, 128, 255, 255)),
                         "iPad Pro 13-inch (M5)")
        check("ios: the iPad frame surrounds the screen",
              ipad.width > 2064 and ipad.height > 2752 and ipad.getpixel((0, 0))[3] == 0)
    else:
        print("skip ios: no Simulator device types or pdftocairo")

    print(f"self-test: {'ok' if not failures else f'{len(failures)} failure(s)'}")
    return 1 if failures else 0


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("platform", nargs="?", choices=["android", "ios"])
    p.add_argument("device", nargs="?",
                   help="android: closed or default (posture); ios: Simulator device type")
    p.add_argument("raw", nargs="?")
    p.add_argument("out", nargs="?")
    p.add_argument("--scale", type=float, default=1.0, help="resize the result, e.g. 0.5")
    p.add_argument("--self-test", action="store_true")
    args = p.parse_args()
    if args.self_test:
        return self_test()
    if not all((args.platform, args.device, args.raw, args.out)):
        p.error("platform, device, raw and out are required")
    raw = Image.open(args.raw)
    framed = (frame_android(raw, args.device) if args.platform == "android"
              else frame_ios(raw, args.device))
    if args.scale != 1:
        framed = framed.resize((round(framed.width * args.scale),
                                round(framed.height * args.scale)), Image.LANCZOS)
    framed.save(args.out)
    print(f"{args.out}: {framed.width}x{framed.height}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
